/**
 * pos.js — Main entry point for the POS frontend.
 *
 * Bootstraps all modules after DOMContentLoaded:
 *   - Auth guard (Task 10.4): redirects to /login.html if no valid session
 *   - Header cashier name (Task 10.1): reads username from JWT via getCashierName()
 *   - Header date/time (Task 10.2): displays current time, updates every 60 seconds
 *   - Logout button (Task 10.3): clears session and redirects to /login.html
 *   - Module initialization (Task 11.1): receipt → payment → cartPanel → search
 *
 * Module initialization order matters:
 *   initReceipt() is called BEFORE initPayment(openReceipt) so the receipt
 *   modal DOM references are resolved before payment tries to use them.
 *   openReceipt (the `open` export from receipt.js) is passed into initPayment
 *   to avoid a circular import between payment.js and receipt.js.
 */

import { isAuthenticated, clearSession, getCashierName } from "./modules/auth.js";
import { initSearch } from "./modules/search.js";
import { initCartPanel } from "./modules/cartPanel.js";
import { initPayment } from "./modules/payment.js";
import { initReceipt, open as openReceipt } from "./modules/receipt.js";

document.addEventListener("DOMContentLoaded", () => {
  // Task 10.4 — Auth guard: redirect immediately if no valid session
  if (!isAuthenticated()) {
    window.location.replace("/login.html");
    return;
  }

  // Task 10.1 — Render cashier username in the persistent header
  const cashierEl = document.getElementById("header-cashier");
  if (cashierEl) cashierEl.textContent = getCashierName();

  // Task 10.2 — Display current date/time and update every 60 seconds
  function updateDateTime() {
    const el = document.getElementById("header-datetime");
    if (el) el.textContent = new Date().toLocaleString();
  }
  updateDateTime();
  setInterval(updateDateTime, 60000);

  // Task 10.3 — Wire logout button: clear session and redirect to login
  const logoutBtn = document.getElementById("logout-btn");
  if (logoutBtn) {
    logoutBtn.addEventListener("click", () => {
      clearSession();
      window.location.replace("/login.html");
    });
  }

  // Task 11.1 — Initialize all modules
  // Receipt must be initialized before payment so its DOM refs are ready
  initReceipt();
  initPayment(openReceipt);
  initCartPanel();
  initSearch();

  const searchInput = document.getElementById("search-name");
  if (searchInput) searchInput.focus();

  const categorySelect = document.getElementById("search-category");
  const orderDiscountInput = document.getElementById("order-discount-value");
  const clearCartBtn = document.getElementById("clear-cart-btn");
  const proceedPaymentBtn = document.getElementById("proceed-payment-btn");

  const shortcutsBtn = document.getElementById("shortcuts-btn");
  const shortcutsModal = document.getElementById("shortcuts-modal");
  const shortcutsCloseBtn = document.getElementById("shortcuts-close-btn");

  const isModalOpen = (modal) => modal && modal.style.display !== "none";

  function openShortcuts() {
    if (!shortcutsModal) return;
    shortcutsModal.style.display = "flex";
    shortcutsCloseBtn?.focus();
  }

  function closeShortcuts() {
    if (!shortcutsModal) return;
    shortcutsModal.style.display = "none";
    searchInput?.focus();
  }

  if (shortcutsBtn) {
    shortcutsBtn.addEventListener("click", openShortcuts);
  }

  if (shortcutsCloseBtn) {
    shortcutsCloseBtn.addEventListener("click", closeShortcuts);
  }

  if (shortcutsModal) {
    shortcutsModal.addEventListener("click", (event) => {
      if (event.target === shortcutsModal) {
        closeShortcuts();
      }
    });
  }

  document.addEventListener("keydown", (event) => {
    const activeEl = document.activeElement;
    const isTyping = activeEl && ["INPUT", "TEXTAREA", "SELECT"].includes(activeEl.tagName);

    if ((event.key === "F1" || event.key === "?") && !event.repeat) {
      event.preventDefault();
      if (isModalOpen(shortcutsModal)) {
        closeShortcuts();
      } else {
        openShortcuts();
      }
      return;
    }

    if (event.key === "Escape" && isModalOpen(shortcutsModal)) {
      event.preventDefault();
      closeShortcuts();
      return;
    }

    if (isModalOpen(shortcutsModal)) {
      return;
    }

    if (!isTyping && (event.key === "F2" || event.key === "/")) {
      event.preventDefault();
      searchInput?.focus();
      searchInput?.select();
    }

    if (!isTyping && event.key === "F3") {
      event.preventDefault();
      categorySelect?.focus();
    }

    if (!isTyping && event.key === "F4") {
      event.preventDefault();
      orderDiscountInput?.focus();
      orderDiscountInput?.select();
    }

    if (!isTyping && event.key === "F8") {
      event.preventDefault();
      proceedPaymentBtn?.click();
    }

    if (!isTyping && event.ctrlKey && event.key === "Backspace") {
      event.preventDefault();
      clearCartBtn?.click();
    }
  });
});
