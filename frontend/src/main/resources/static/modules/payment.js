/**
 * payment.js — Payment Modal logic for the POS frontend.
 *
 * Manages #payment-modal and all payment method flows:
 *   - Cash: validates cashAmount >= grandTotal, shows change due
 *   - Card: pre-fills amount, requires confirmation checkbox
 *   - Mixed: validates cashAmount + cardAmount >= grandTotal, shows remaining balance
 *
 * Export: initPayment(openReceiptFn)
 *   openReceiptFn — the receipt.open function passed from pos.js to avoid circular imports
 */

import CartStore from "./cart.js";
import { getCashierName } from "./auth.js";
import { apiFetch } from "./api.js";

// ---------------------------------------------------------------------------
// DOM references (resolved lazily on init)
// ---------------------------------------------------------------------------

let modal;
let grandTotalEl;
let methodRadios;
let cashSection;
let cardSection;
let mixedSection;
let cashAmountInput;
let changeDueEl;
let cardAmountInput;
let cardConfirmCheckbox;
let mixedCashInput;
let mixedCardInput;
let remainingEl;
let mixedChangeEl;
let errorEl;
let cancelBtn;
let confirmBtn;
let proceedBtn;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a number as a dollar string: "$X.XX"
 * @param {number} val
 * @returns {string}
 */
function formatPrice(val) {
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(Number(val));
}

/**
 * Show an error message in #payment-error.
 * @param {string} msg
 */
function showError(msg) {
  errorEl.textContent = msg;
}

/**
 * Clear the error message.
 */
function clearError() {
  errorEl.textContent = "";
}

/**
 * Enable or disable the confirm button.
 * @param {boolean} enabled
 */
function setConfirmEnabled(enabled) {
  confirmBtn.disabled = !enabled;
}

/**
 * Get the currently selected payment method.
 * @returns {"cash"|"card"|"mixed"}
 */
function getSelectedMethod() {
  for (const radio of methodRadios) {
    if (radio.checked) return radio.value;
  }
  return "cash";
}

// ---------------------------------------------------------------------------
// Section visibility
// ---------------------------------------------------------------------------

/**
 * Show only the section matching the given method; hide the others.
 * @param {"cash"|"card"|"mixed"} method
 */
function showSection(method) {
  cashSection.style.display = method === "cash" ? "" : "none";
  cardSection.style.display = method === "card" ? "" : "none";
  mixedSection.style.display = method === "mixed" ? "" : "none";
}

// ---------------------------------------------------------------------------
// Open modal
// ---------------------------------------------------------------------------

/**
 * Open the payment modal, reset all inputs, and display the grand total.
 */
function openPaymentModal() {
  const grandTotal = CartStore.grandTotal;

  // Display grand total
  grandTotalEl.textContent = formatPrice(grandTotal);

  // Reset all inputs
  cashAmountInput.value = "";
  changeDueEl.textContent = "$0.00";
  changeDueEl.classList.remove("price--success");

  cardAmountInput.value = "";
  cardConfirmCheckbox.checked = false;

  mixedCashInput.value = "";
  mixedCardInput.value = "";
  remainingEl.textContent = formatPrice(grandTotal);
  mixedChangeEl.textContent = "$0.00";
  mixedChangeEl.classList.remove("price--success");

  // Default to cash method
  for (const radio of methodRadios) {
    radio.checked = radio.value === "cash";
  }
  showSection("cash");

  // Clear error and disable confirm
  clearError();
  setConfirmEnabled(false);

  // Show modal
  modal.style.display = "flex";
  cashAmountInput.focus();
  cashAmountInput.select();
}

// ---------------------------------------------------------------------------
// Cash mode validation
// ---------------------------------------------------------------------------

/**
 * Handle real-time input on #payment-cash-amount.
 */
function onCashAmountInput() {
  const grandTotal = CartStore.grandTotal;
  const cashAmount = parseFloat(cashAmountInput.value) || 0;

  if (cashAmount >= grandTotal) {
    const change = cashAmount - grandTotal;
    changeDueEl.textContent = formatPrice(change);
    changeDueEl.classList.add("price--success");
    clearError();
    setConfirmEnabled(true);
  } else {
    changeDueEl.textContent = "$0.00";
    changeDueEl.classList.remove("price--success");
    showError("El efectivo recibido no alcanza para cubrir la venta.");
    setConfirmEnabled(false);
  }
}

// ---------------------------------------------------------------------------
// Card mode validation
// ---------------------------------------------------------------------------

/**
 * Handle checkbox change on #payment-card-confirm.
 */
function onCardConfirmChange() {
  setConfirmEnabled(cardConfirmCheckbox.checked);
  if (!cardConfirmCheckbox.checked) {
    // No error needed — just disable confirm
  }
}

// ---------------------------------------------------------------------------
// Mixed mode validation
// ---------------------------------------------------------------------------

/**
 * Handle real-time input on either mixed cash or card amount fields.
 */
function onMixedAmountInput() {
  const grandTotal = CartStore.grandTotal;
  const cashAmount = parseFloat(mixedCashInput.value) || 0;
  const cardAmount = parseFloat(mixedCardInput.value) || 0;
  const total = cashAmount + cardAmount;
  const remaining = grandTotal - total;

  if (total >= grandTotal) {
    remainingEl.textContent = "$0.00";
    const change = total - grandTotal;
    if (change > 0) {
      mixedChangeEl.textContent = formatPrice(change);
      mixedChangeEl.classList.add("price--success");
    } else {
      mixedChangeEl.textContent = "$0.00";
      mixedChangeEl.classList.remove("price--success");
    }
    clearError();
    setConfirmEnabled(true);
  } else {
    remainingEl.textContent = formatPrice(remaining);
    mixedChangeEl.textContent = "$0.00";
    mixedChangeEl.classList.remove("price--success");
    showError("El total recibido no alcanza para cubrir la venta.");
    setConfirmEnabled(false);
  }
}

// ---------------------------------------------------------------------------
// Payment method change
// ---------------------------------------------------------------------------

/**
 * Handle payment method radio change.
 * Shows the appropriate section and resets validation state.
 */
function onMethodChange() {
  const method = getSelectedMethod();
  showSection(method);
  clearError();
  setConfirmEnabled(false);

  if (method === "card") {
    // Pre-fill card amount with grand total
    cardAmountInput.value = Number(CartStore.grandTotal).toFixed(2);
    cardConfirmCheckbox.checked = false;
    cardConfirmCheckbox.focus();
  } else if (method === "mixed") {
    mixedCashInput.focus();
  } else {
    cashAmountInput.focus();
  }
}

// ---------------------------------------------------------------------------
// Confirm payment
// ---------------------------------------------------------------------------

/**
 * Build a Transaction object and call openReceiptFn.
 * @param {Function} openReceiptFn
 */
async function persistSale(cashAmount, cardAmount) {
  if (window.KIRO_BACKEND_MODE === "serverless") {
    return apiFetch("/api/sales", {
      method: "POST",
      body: JSON.stringify({
        cashier: getCashierName() || "cashier",
        terminalId: "WEB-POS-01",
        items: CartStore.items.map((item) => ({
          productId: item.productId,
          name: item.nombre,
          unitPrice: item.unitPrice,
          quantity: item.quantity,
          lineTotal: item.lineTotal,
          discount: item.discount,
        })),
        subtotal: CartStore.subtotal,
        discount: CartStore.totalDiscount,
        total: CartStore.grandTotal,
        payment: {
          cashAmount,
          cardAmount,
          changeDue: Math.max(0, cashAmount + cardAmount - CartStore.grandTotal),
        },
      }),
    });
  }

  const sale = await apiFetch("/api/sales", {
    method: "POST",
    body: JSON.stringify({
      terminalId: "WEB-POS-01",
      cashierId: getCashierName() || "cashier",
      customerId: null,
    }),
  });

  let currentSale = sale;
  for (const item of CartStore.items) {
    currentSale = await apiFetch(`/api/sales/${sale.id}/items`, {
      method: "POST",
      body: JSON.stringify({
        productId: item.productId,
        quantity: item.quantity,
      }),
    });
  }

  const paidAmount = Math.max(cashAmount + cardAmount, Number(currentSale.total ?? CartStore.grandTotal));
  return apiFetch(`/api/sales/${sale.id}/checkout`, {
    method: "POST",
    body: JSON.stringify({
      paymentType: "CASH",
      amountReceived: paidAmount,
      customerId: null,
    }),
  });
}

async function onConfirmPayment(openReceiptFn) {
  if (confirmBtn.disabled) return;

  const method = getSelectedMethod();
  const grandTotal = CartStore.grandTotal;

  let cashAmount = 0;
  let cardAmount = 0;
  let changeDue = 0;

  if (method === "cash") {
    cashAmount = parseFloat(cashAmountInput.value) || 0;
    cardAmount = 0;
    changeDue = Math.max(0, cashAmount - grandTotal);
  } else if (method === "card") {
    cashAmount = 0;
    cardAmount = parseFloat(cardAmountInput.value) || grandTotal;
    changeDue = 0;
  } else {
    // mixed
    cashAmount = parseFloat(mixedCashInput.value) || 0;
    cardAmount = parseFloat(mixedCardInput.value) || 0;
    changeDue = Math.max(0, cashAmount + cardAmount - grandTotal);
  }

  const transaction = {
    id: crypto.randomUUID(),
    cashier: getCashierName(),
    timestamp: new Date(),
    items: CartStore.items.map((i) => ({ ...i })),
    orderDiscount: CartStore.orderDiscount,
    subtotal: CartStore.subtotal,
    totalDiscount: CartStore.totalDiscount,
    grandTotal: CartStore.grandTotal,
    payment: {
      method,
      cashAmount,
      cardAmount,
      changeDue,
    },
  };

  try {
    confirmBtn.disabled = true;
    confirmBtn.textContent = "Procesando...";
    const checkout = await persistSale(cashAmount, cardAmount);
    transaction.id = checkout?.transactionId ?? transaction.id;
    transaction.backendReceipt = checkout?.receipt ?? null;
  } catch (error) {
    showError(error?.message ?? "Could not complete the sale in the backend.");
    confirmBtn.disabled = false;
    confirmBtn.textContent = "Confirmar pago";
    return;
  }

  modal.style.display = "none";
  confirmBtn.textContent = "Confirmar pago";

  // Open receipt
  openReceiptFn(transaction);
}

// ---------------------------------------------------------------------------
// Cancel
// ---------------------------------------------------------------------------

/**
 * Close the payment modal without modifying CartStore.
 */
function onCancel() {
  modal.style.display = "none";
  document.getElementById("search-name")?.focus();
}

// ---------------------------------------------------------------------------
// Init
// ---------------------------------------------------------------------------

/**
 * Initialize the payment module.
 *
 * Wires all event listeners for the payment modal.
 *
 * @param {Function} openReceiptFn - The receipt open function (from receipt.js),
 *   passed in from pos.js to avoid circular imports.
 */
export function initPayment(openReceiptFn) {
  // Resolve DOM references
  modal = document.getElementById("payment-modal");
  grandTotalEl = document.getElementById("payment-grand-total");
  methodRadios = document.querySelectorAll('input[name="payment-method"]');
  cashSection = document.getElementById("payment-cash-section");
  cardSection = document.getElementById("payment-card-section");
  mixedSection = document.getElementById("payment-mixed-section");
  cashAmountInput = document.getElementById("payment-cash-amount");
  changeDueEl = document.getElementById("payment-change-due");
  cardAmountInput = document.getElementById("payment-card-amount");
  cardConfirmCheckbox = document.getElementById("payment-card-confirm");
  mixedCashInput = document.getElementById("payment-mixed-cash");
  mixedCardInput = document.getElementById("payment-mixed-card");
  remainingEl = document.getElementById("payment-remaining");
  mixedChangeEl = document.getElementById("payment-mixed-change");
  errorEl = document.getElementById("payment-error");
  cancelBtn = document.getElementById("payment-cancel-btn");
  confirmBtn = document.getElementById("payment-confirm-btn");
  proceedBtn = document.getElementById("proceed-payment-btn");

  // 1. Wire "Proceed to Payment" button
  proceedBtn.addEventListener("click", openPaymentModal);

  // 2. Wire payment method radio change
  for (const radio of methodRadios) {
    radio.addEventListener("change", onMethodChange);
  }

  // 3. Wire cash amount input for real-time validation
  cashAmountInput.addEventListener("input", onCashAmountInput);

  // 4. Wire card amount and card confirm checkbox
  cardConfirmCheckbox.addEventListener("change", onCardConfirmChange);

  // 5. Wire mixed mode inputs
  mixedCashInput.addEventListener("input", onMixedAmountInput);
  mixedCardInput.addEventListener("input", onMixedAmountInput);

  // 6. Wire cancel button
  cancelBtn.addEventListener("click", onCancel);

  // 7. Wire confirm button
  confirmBtn.addEventListener("click", () => onConfirmPayment(openReceiptFn));
  modal.addEventListener("keydown", (event) => {
    if (event.key === "Enter" && !confirmBtn.disabled) {
      event.preventDefault();
      onConfirmPayment(openReceiptFn);
    }
    if (event.key === "Escape") {
      event.preventDefault();
      onCancel();
    }
  });
}
