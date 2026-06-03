/**
 * api.js — Fetch wrapper with JWT injection and centralised error handling.
 *
 * All API calls in the POS app should go through `apiFetch` so that:
 *  - The Bearer token is injected automatically.
 *  - HTTP 401 clears the session and redirects to /login.html.
 *  - HTTP 403 surfaces a user-friendly "Inactive user" message.
 *  - Other 4xx/5xx responses throw a typed ApiError.
 *  - Network failures throw ApiError(0, "Connection error.").
 */

import { getToken, clearSession } from "./auth.js";

const BACKEND_API_BASE_URL = window.KIRO_BACKEND_API_BASE_URL || "http://localhost:8080";

/**
 * Typed error for non-2xx HTTP responses and network failures.
 */
export class ApiError extends Error {
  /**
   * @param {number} status  HTTP status code (0 for network errors)
   * @param {string} message Human-readable description
   */
  constructor(status, message) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

/**
 * Performs an authenticated fetch request against the same-origin API.
 *
 * @param {string} path     Absolute path, e.g. "/productos?nombre=leche"
 * @param {RequestInit} [options={}]  Standard fetch options (method, body, etc.)
 * @returns {Promise<any>}  Parsed JSON response body
 * @throws {ApiError}       On HTTP errors or network failures
 */
export async function apiFetch(path, options = {}) {
  const token = getToken();
  const baseUrl = options.baseUrl !== undefined ? options.baseUrl : BACKEND_API_BASE_URL;
  const url = path.startsWith("/api/")
    ? `${baseUrl}${path}`
    : path;

  const headers = {
    "Content-Type": "application/json",
    ...(options.headers ?? {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  let response;
  try {
    response = await fetch(url, { ...options, headers });
  } catch {
    throw new ApiError(0, "Connection error.");
  }

  if (response.status === 401) {
    clearSession();
    window.location.replace("/login.html");
    // Throw so callers don't continue processing after redirect
    throw new ApiError(401, "Session expired. Redirecting to login.");
  }

  if (response.status === 403) {
    throw new ApiError(403, "Inactive user. Contact your administrator.");
  }

  if (!response.ok) {
    let message = `Server error (${response.status}).`;
    try {
      const body = await response.json();
      if (body?.message) message = body.message;
    } catch {
      // ignore parse errors — keep the default message
    }
    throw new ApiError(response.status, message);
  }

  // Parse JSON; return null for empty bodies (e.g. 204 No Content)
  const text = await response.text();
  if (!text) return null;

  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError(0, "Invalid JSON response from server.");
  }
}
