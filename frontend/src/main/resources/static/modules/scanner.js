/**
 * scanner.js — Barcode Scanner module for the POS frontend.
 *
 * Handles both manual barcode entry (keyboard / submit button) and
 * camera-based scanning via the ZXing BrowserMultiFormatReader (loaded
 * as a UMD global: window.ZXingBrowser).
 *
 * HTML elements expected:
 *   #barcode-input       — text input for manual entry
 *   #barcode-submit-btn  — "Buscar" button that triggers lookup
 *   #barcode-error       — inline error container (role="alert")
 *   #camera-start-btn    — "Scan with Camera" button (display:none by default)
 *   #camera-stop-btn     — "Stop Camera" button (display:none by default)
 *   #camera-preview      — <video> element for live camera feed
 */

import { apiFetch } from "./api.js";
import CartStore from "./cart.js";

// ---------------------------------------------------------------------------
// Module-level state
// ---------------------------------------------------------------------------

/** @type {import('@zxing/browser').BrowserMultiFormatReader | null} */
let codeReader = null;

function normalizeProduct(product) {
  return {
    id: product.id,
    nombre: product.nombre ?? product.name,
    subcategoria: product.subcategoria ?? product.category,
    precio: product.precio ?? product.unitPrice,
  };
}

// ---------------------------------------------------------------------------
// DOM helpers
// ---------------------------------------------------------------------------

/**
 * Show an error message in #barcode-error and mark #barcode-input as errored.
 * @param {string} message
 */
function showError(message) {
  const errorEl = document.getElementById("barcode-error");
  const inputEl = document.getElementById("barcode-input");
  if (errorEl) errorEl.textContent = message;
  if (inputEl) inputEl.classList.add("input--error");
}

/**
 * Clear any error state from #barcode-error and #barcode-input.
 */
function clearError() {
  const errorEl = document.getElementById("barcode-error");
  const inputEl = document.getElementById("barcode-input");
  if (errorEl) errorEl.textContent = "";
  if (inputEl) inputEl.classList.remove("input--error");
}

// ---------------------------------------------------------------------------
// Product lookup
// ---------------------------------------------------------------------------

/**
 * Look up a product by barcode value.
 * Matches by product.id (string comparison) or product.nombre (exact match).
 * On success: adds to cart and clears the input.
 * On failure: shows an inline error.
 *
 * @param {string} barcode — the raw barcode string to look up
 */
async function lookupBarcode(barcode) {
  const inputEl = document.getElementById("barcode-input");

  // Empty input guard
  if (!barcode || barcode.trim() === "") {
    showError("Please enter a barcode value.");
    return;
  }

  clearError();

  const productsOpts = { baseUrl: window.KIRO_PRODUCTS_API_BASE_URL ?? "" };
  let data;
  try {
    data = await apiFetch(`/api/products/search?barcode=${encodeURIComponent(barcode)}`, productsOpts);
  } catch {
    showError("Barcode lookup failed. Try again.");
    // Retain the entered value — do not clear the input
    return;
  }

  // The API may return a paginated response ({ content: [...] }) or a plain array
  const products = (Array.isArray(data) ? data : [data]).filter(Boolean).map(normalizeProduct);

  // Find first product matching by id or by exact name
  const match = products.find(
    (p) => String(p.id) === barcode || p.nombre === barcode
  );

  if (!match) {
    showError(`Product not found for barcode '${barcode}'.`);
    return;
  }

  // Success: add to cart, clear input and error
  CartStore.addItem(match);
  if (inputEl) inputEl.value = "";
  inputEl?.focus();
  clearError();
}

// ---------------------------------------------------------------------------
// Manual entry handlers
// ---------------------------------------------------------------------------

/**
 * Handle Enter keydown on #barcode-input.
 * @param {KeyboardEvent} event
 */
function onBarcodeKeydown(event) {
  if (event.key === "Enter") {
    const inputEl = document.getElementById("barcode-input");
    const value = inputEl ? inputEl.value.trim() : "";
    lookupBarcode(value);
  }
}

/**
 * Handle click on #barcode-submit-btn.
 */
function onBarcodeSubmit() {
  const inputEl = document.getElementById("barcode-input");
  const value = inputEl ? inputEl.value.trim() : "";
  lookupBarcode(value);
}

// ---------------------------------------------------------------------------
// Camera scanning helpers
// ---------------------------------------------------------------------------

/**
 * Stop the active camera stream and reset UI to the idle state.
 */
function stopCamera() {
  if (codeReader) {
    codeReader.reset();
    codeReader = null;
  }

  const previewEl = document.getElementById("camera-preview");
  const startBtn = document.getElementById("camera-start-btn");
  const stopBtn = document.getElementById("camera-stop-btn");

  if (previewEl) previewEl.style.display = "none";
  if (stopBtn) stopBtn.style.display = "none";
  if (startBtn) startBtn.style.display = "";
}

/**
 * Handle a successfully decoded barcode from the camera.
 * @param {import('@zxing/browser').Result} result
 */
function onCameraDecode(result) {
  const decodedText = result.getText();

  // Populate the manual input field
  const inputEl = document.getElementById("barcode-input");
  if (inputEl) inputEl.value = decodedText;

  // Stop the camera before triggering lookup
  stopCamera();

  // Reuse the same lookup logic as manual entry
  lookupBarcode(decodedText);
}

/**
 * Handle click on #camera-start-btn.
 * Requests camera permission and starts the ZXing reader.
 */
async function onCameraStart() {
  const startBtn = document.getElementById("camera-start-btn");
  const stopBtn = document.getElementById("camera-stop-btn");
  const previewEl = document.getElementById("camera-preview");

  // Swap button visibility and show preview
  if (startBtn) startBtn.style.display = "none";
  if (stopBtn) stopBtn.style.display = "";
  if (previewEl) previewEl.style.display = "";

  try {
    // Verify camera access before instantiating ZXing
    await navigator.mediaDevices.getUserMedia({ video: true });
  } catch (err) {
    const name = err?.name ?? "";
    if (name === "NotAllowedError" || name === "PermissionDeniedError") {
      showError("Camera access denied. Use manual barcode entry.");
    } else {
      showError("Camera access denied. Use manual barcode entry.");
    }
    // Hide camera UI entirely
    if (startBtn) startBtn.style.display = "none";
    if (previewEl) previewEl.style.display = "none";
    if (stopBtn) stopBtn.style.display = "none";
    return;
  }

  try {
    // ZXingBrowser is loaded as a UMD global from the CDN <script> tag
    codeReader = new window.ZXingBrowser.BrowserMultiFormatReader();

    codeReader.decodeFromVideoDevice(null, "camera-preview", (result, error) => {
      if (result) {
        onCameraDecode(result);
      }
      // Ignore transient decode errors (no barcode in frame yet)
    });
  } catch (err) {
    showError("Camera access denied. Use manual barcode entry.");
    stopCamera();
  }
}

// ---------------------------------------------------------------------------
// Camera availability check
// ---------------------------------------------------------------------------

/**
 * Check whether the MediaDevices API is available.
 * Shows or hides #camera-start-btn accordingly.
 */
function checkCameraAvailability() {
  const startBtn = document.getElementById("camera-start-btn");
  if (!startBtn) return;

  if (navigator.mediaDevices && navigator.mediaDevices.getUserMedia) {
    // Camera API is available — reveal the button
    startBtn.style.display = "inline-flex";
  } else {
    // No camera support — keep button hidden
    startBtn.style.display = "none";
  }
}

// ---------------------------------------------------------------------------
// Public init
// ---------------------------------------------------------------------------

/**
 * Initialise the Barcode Scanner module.
 *
 * 1. Checks camera availability and shows/hides #camera-start-btn.
 * 2. Sets up the Enter-key listener on #barcode-input.
 * 3. Sets up the click listener on #barcode-submit-btn.
 * 4. Sets up the click listener on #camera-start-btn.
 * 5. Sets up the click listener on #camera-stop-btn.
 */
export function initScanner() {
  checkCameraAvailability();

  const barcodeInput = document.getElementById("barcode-input");
  const submitBtn = document.getElementById("barcode-submit-btn");
  const cameraStartBtn = document.getElementById("camera-start-btn");
  const cameraStopBtn = document.getElementById("camera-stop-btn");

  if (barcodeInput) {
    barcodeInput.addEventListener("keydown", onBarcodeKeydown);
  }

  if (submitBtn) {
    submitBtn.addEventListener("click", onBarcodeSubmit);
  }

  if (cameraStartBtn) {
    cameraStartBtn.addEventListener("click", onCameraStart);
  }

  if (cameraStopBtn) {
    cameraStopBtn.addEventListener("click", stopCamera);
  }
}
