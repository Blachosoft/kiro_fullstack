/**
 * receipt.js — Receipt Modal logic for the POS frontend.
 *
 * Manages #receipt-modal. Receives a Transaction object from payment.js
 * and renders a formatted receipt using DOM methods (no innerHTML for
 * dynamic data).
 *
 * Exports:
 *   open(transaction)  — renders the receipt and shows the modal
 *   initReceipt()      — wires the Print and New Transaction buttons
 */

import CartStore from "./cart.js";

// ---------------------------------------------------------------------------
// DOM references (resolved lazily on init)
// ---------------------------------------------------------------------------

let modal;
let receiptContent;
let printBtn;
let newBtn;

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a number as a dollar string: "$X.XX"
 * @param {number} val
 * @returns {string}
 */
function formatPrice(val) {
  return "$" + Number(val).toFixed(2);
}

/**
 * Format a discount descriptor into a short label.
 * e.g. { type: "percent", value: 10 } → "-10%"
 *      { type: "fixed",   value: 5  } → "-$5.00"
 * @param {{ type: "percent"|"fixed", value: number }} discount
 * @returns {string}
 */
function formatDiscount(discount) {
  if (discount.type === "percent") {
    return "-" + Number(discount.value).toFixed(0) + "%";
  }
  return "-" + formatPrice(discount.value);
}

/**
 * Create a simple two-column row element.
 * @param {string} labelText
 * @param {string} valueText
 * @param {string[]} [classes] — extra CSS classes for the row element
 * @param {string[]} [valueClasses] — extra CSS classes for the value span
 * @returns {HTMLElement}
 */
function createRow(labelText, valueText, classes = [], valueClasses = []) {
  const row = document.createElement("div");
  row.className = ["receipt-total-row", ...classes].join(" ").trim();

  const label = document.createElement("span");
  label.textContent = labelText;

  const value = document.createElement("span");
  value.className = valueClasses.join(" ").trim();
  value.textContent = valueText;

  row.appendChild(label);
  row.appendChild(value);
  return row;
}

// ---------------------------------------------------------------------------
// Receipt rendering
// ---------------------------------------------------------------------------

/**
 * Build and return the receipt header section.
 * @param {Object} transaction
 * @returns {HTMLElement}
 */
function buildHeader(transaction) {
  const header = document.createElement("div");
  header.className = "receipt-header";

  // Store name
  const storeName = document.createElement("h3");
  storeName.textContent = "Punto de Venta";
  header.appendChild(storeName);

  // Transaction ID
  const txnId = document.createElement("p");
  txnId.textContent = "#TXN-" + transaction.id.slice(0, 8).toUpperCase();
  header.appendChild(txnId);

  // Date/time
  const dateEl = document.createElement("p");
  dateEl.textContent = new Date(transaction.timestamp).toLocaleString();
  header.appendChild(dateEl);

  // Cashier
  const cashierEl = document.createElement("p");
  cashierEl.textContent = "Cajero: " + transaction.cashier;
  header.appendChild(cashierEl);

  return header;
}

/**
 * Build and return the itemized list section.
 * @param {Object[]} items
 * @returns {HTMLElement}
 */
function buildItemList(items) {
  const ul = document.createElement("ul");
  ul.className = "receipt-items";

  for (const item of items) {
    const li = document.createElement("li");
    li.className = "receipt-item";

    // Wrap item name + qty and line total in a flex row
    const itemRow = document.createElement("div");
    itemRow.style.display = "flex";
    itemRow.style.justifyContent = "space-between";
    itemRow.style.width = "100%";

    const nameQty = document.createElement("span");
    nameQty.textContent = item.nombre + " x" + item.quantity;

    const lineTotal = document.createElement("span");
    lineTotal.textContent = formatPrice(item.lineTotal);

    itemRow.appendChild(nameQty);
    itemRow.appendChild(lineTotal);
    li.appendChild(itemRow);

    // Discount info (if present)
    if (item.discount) {
      const discountEl = document.createElement("div");
      discountEl.style.fontSize = "0.875rem";
      discountEl.style.color = "var(--color-success)";
      discountEl.textContent = formatDiscount(item.discount);
      li.appendChild(discountEl);
    }

    ul.appendChild(li);
  }

  return ul;
}

/**
 * Build and return the order discount row (only if orderDiscount is not null).
 * @param {{ type: "percent"|"fixed", value: number }|null} orderDiscount
 * @param {number} totalDiscount — used to compute the order discount amount display
 * @param {Object[]} items — cart items (to compute item-level discount total)
 * @returns {HTMLElement|null}
 */
function buildOrderDiscountRow(orderDiscount, transaction) {
  if (!orderDiscount) return null;

  // Compute item-level discount total to derive order discount amount
  const itemDiscountsTotal = transaction.items.reduce((sum, item) => {
    if (!item.discount) return sum;
    const lineBase = item.unitPrice * item.quantity;
    if (item.discount.type === "percent") {
      return sum + lineBase * (item.discount.value / 100);
    }
    return sum + Math.min(item.discount.value, lineBase);
  }, 0);

  const orderDiscountAmount = transaction.totalDiscount - itemDiscountsTotal;

  const row = document.createElement("div");
  row.className = "receipt-total-row";

  const label = document.createElement("span");
  label.textContent = "Descuento de orden";

  const value = document.createElement("span");
  value.textContent = "-" + formatPrice(Math.max(0, orderDiscountAmount));

  row.appendChild(label);
  row.appendChild(value);
  return row;
}

/**
 * Build and return the totals section.
 * @param {Object} transaction
 * @returns {HTMLElement}
 */
function buildTotals(transaction) {
  const totals = document.createElement("div");
  totals.className = "receipt-totals";

  // Subtotal
  totals.appendChild(createRow("Subtotal", formatPrice(transaction.subtotal)));

  // Total discount (only if > 0)
  if (transaction.totalDiscount > 0) {
    totals.appendChild(
      createRow("Descuento total", "-" + formatPrice(transaction.totalDiscount))
    );
  }

  // Grand total
  totals.appendChild(
    createRow(
      "TOTAL",
      formatPrice(transaction.grandTotal),
      ["receipt-total-row--grand"],
      []
    )
  );

  return totals;
}

/**
 * Build and return the payment section.
 * @param {{ method: string, cashAmount: number, cardAmount: number, changeDue: number }} payment
 * @returns {HTMLElement}
 */
function buildPayment(payment) {
  const section = document.createElement("div");
  section.style.marginTop = "var(--space-md)";
  section.style.paddingTop = "var(--space-md)";
  section.style.borderTop = "1px solid var(--color-border)";

  // Payment method label
  const methodLabels = {
    cash: "Efectivo",
    card: "Tarjeta",
    mixed: "Mixto (Efectivo + Tarjeta)",
  };

  const methodRow = document.createElement("div");
  methodRow.className = "receipt-total-row";
  const methodLabel = document.createElement("span");
  methodLabel.textContent = "Método de pago";
  const methodValue = document.createElement("span");
  methodValue.textContent = methodLabels[payment.method] || payment.method;
  methodRow.appendChild(methodLabel);
  methodRow.appendChild(methodValue);
  section.appendChild(methodRow);

  // Cash amount (cash or mixed)
  if (payment.method === "cash" || payment.method === "mixed") {
    const cashRow = document.createElement("div");
    cashRow.className = "receipt-total-row";
    const cashLabel = document.createElement("span");
    cashLabel.textContent = "Efectivo";
    const cashValue = document.createElement("span");
    cashValue.textContent = formatPrice(payment.cashAmount);
    cashRow.appendChild(cashLabel);
    cashRow.appendChild(cashValue);
    section.appendChild(cashRow);
  }

  // Card amount (card or mixed)
  if (payment.method === "card" || payment.method === "mixed") {
    const cardRow = document.createElement("div");
    cardRow.className = "receipt-total-row";
    const cardLabel = document.createElement("span");
    cardLabel.textContent = "Tarjeta";
    const cardValue = document.createElement("span");
    cardValue.textContent = formatPrice(payment.cardAmount);
    cardRow.appendChild(cardLabel);
    cardRow.appendChild(cardValue);
    section.appendChild(cardRow);
  }

  // Change due (only if > 0)
  if (payment.changeDue > 0) {
    const changeRow = document.createElement("div");
    changeRow.className = "receipt-total-row";
    const changeLabel = document.createElement("span");
    changeLabel.textContent = "Cambio";
    const changeValue = document.createElement("span");
    changeValue.className = "price--success";
    changeValue.textContent = formatPrice(payment.changeDue);
    changeRow.appendChild(changeLabel);
    changeRow.appendChild(changeValue);
    section.appendChild(changeRow);
  }

  return section;
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Render the given transaction into #receipt-content and show the modal.
 *
 * All dynamic data is set via textContent — never innerHTML.
 *
 * @param {Object} transaction
 */
export function open(transaction) {
  // Resolve DOM references if not yet done
  if (!modal) {
    modal = document.getElementById("receipt-modal");
    receiptContent = document.getElementById("receipt-content");
  }

  // Clear previous content
  receiptContent.textContent = "";

  // 1. Header
  receiptContent.appendChild(buildHeader(transaction));

  // 2. Itemized list
  receiptContent.appendChild(buildItemList(transaction.items));

  // 3. Order discount row (if present)
  const orderDiscountRow = buildOrderDiscountRow(
    transaction.orderDiscount,
    transaction
  );
  if (orderDiscountRow) {
    receiptContent.appendChild(orderDiscountRow);
  }

  // 4. Totals
  receiptContent.appendChild(buildTotals(transaction));

  // 5. Payment breakdown
  receiptContent.appendChild(buildPayment(transaction.payment));

  // Show modal
  modal.style.display = "flex";
}

/**
 * Wire the Print Receipt and New Transaction buttons.
 * Call this once from pos.js on DOMContentLoaded.
 */
export function initReceipt() {
  modal = document.getElementById("receipt-modal");
  receiptContent = document.getElementById("receipt-content");
  printBtn = document.getElementById("receipt-print-btn");
  newBtn = document.getElementById("receipt-new-btn");

  // Task 9.4 — Print button
  printBtn.addEventListener("click", () => {
    window.print();
  });

  // Task 9.5 — New Transaction button
  newBtn.addEventListener("click", () => {
    // 1. Clear the cart (also resets orderDiscount)
    CartStore.clearCart();

    // 2. Hide the receipt modal
    modal.style.display = "none";

    // 3. Focus the product search input
    const searchInput = document.getElementById("search-name");
    if (searchInput) {
      searchInput.focus();
    }
  });
}
