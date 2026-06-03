/**
 * auth.js — Session helpers for JWT token management.
 * Stores the JWT under TOKEN_KEY in localStorage.
 */

export const TOKEN_KEY = "access_token";

/**
 * Returns the stored JWT token, or null if not present.
 * @returns {string|null}
 */
export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

/**
 * Persists the JWT token to localStorage.
 * @param {string} token
 */
export function saveToken(token) {
  localStorage.setItem(TOKEN_KEY, token);
}

/**
 * Removes the JWT token from localStorage, ending the session.
 */
export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
}

/**
 * Decodes the JWT payload (middle base64url segment) and returns
 * the `sub` claim, which holds the cashier's username.
 *
 * @returns {string} The cashier username, or an empty string if
 *   the token is absent or malformed.
 */
export function getCashierName() {
  const token = getToken();
  if (!token) return "";

  try {
    // JWT structure: header.payload.signature
    const parts = token.split(".");
    if (parts.length !== 3) return "";

    // Base64url → Base64 → decode
    const base64 = parts[1].replace(/-/g, "+").replace(/_/g, "/");
    // Pad to a multiple of 4
    const padded = base64.padEnd(base64.length + (4 - (base64.length % 4)) % 4, "=");
    const jsonStr = atob(padded);
    const payload = JSON.parse(jsonStr);

    return payload.sub ?? "";
  } catch {
    return "";
  }
}

/**
 * Returns true when a JWT token is present in localStorage.
 * Does not validate the token's signature or expiry.
 * @returns {boolean}
 */
export function isAuthenticated() {
  return !!getToken();
}
