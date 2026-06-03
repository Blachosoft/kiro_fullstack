/**
 * cartPanel.js — Cart Panel UI + Discount UI
 *
 * Manages all dynamic rendering inside the Cart Panel:
 *   - Cart item list (#cart-items)
 *   - Totals footer (#cart-subtotal, #cart-discount, #cart-total)
 *   - Per-item discount inline forms (Task 7)
 *   - Order discount section (#order-discount-section)
 *   - Clear cart confirmation
 *   - Proceed to Payment button state
 *
 * Subscribes to the `cart:updated` CustomEvent dispatched on `document`
 * by CartStore after every mutation.
 */

import CartStore from "./cart.js";

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/**
 * Format a number as a price string: "$X.XX"
 * @param {number} val
 * @returns {string}
 */
function fmt(val) {
  return new Intl.NumberFormat("es-CO", {
    style: "currency",
    currency: "COP",
    maximumFractionDigits: 0,
  }).format(Number(val));
}

// Tracks which productId currently has its discount form open (or null).
let openDiscountFormId = null;

// ---------------------------------------------------------------------------
// Render
// ---------------------------------------------------------------------------

/**
 * Re-render the full cart item list and update all totals.
 * Called on every `cart:updated` event.
 */
function renderCart() {
  renderItems();
  renderTotals();
  renderCartCount();
  renderEmptyState();
  syncOrderDiscountRemoveBtn();
}

/**
 * Render all cart items into #cart-items.
 */
function renderItems() {
  const ul = document.getElementById("cart-items");
  if (!ul) return;

  // Clear existing rows
  ul.innerHTML = "";

  CartStore.items.forEach((item) => {
    const li = buildItemRow(item);
    ul.appendChild(li);

    // If this item's discount form was open before re-render, re-open it
    if (openDiscountFormId === item.productId) {
      const form = buildDiscountForm(item);
      li.appendChild(form);
    }
  });
}

/**
 * Build a single <li> cart item row.
 * Uses textContent for all dynamic data (no innerHTML for user data).
 *
 * @param {{ productId: number, nombre: string, unitPrice: number, quantity: number, discount: Object|null, lineTotal: number }} item
 * @returns {HTMLLIElement}
 */
function buildItemRow(item) {
  const li = document.createElement("li");
  li.className = "cart-item";
  li.dataset.productId = item.productId;

  const productInfo = document.createElement("div");
  productInfo.className = "cart-item__product";

  const name = document.createElement("span");
  name.className = "cart-item__name";
  name.textContent = item.nombre;

  const productId = document.createElement("span");
  productId.className = "cart-item__id";
  productId.textContent = "Cod. " + item.productId;

  productInfo.appendChild(name);
  productInfo.appendChild(productId);
  li.appendChild(productInfo);

  // Quantity controls
  const qtyControls = document.createElement("div");
  qtyControls.className = "cart-item__qty-controls";

  const btnMinus = document.createElement("button");
  btnMinus.className = "cart-item__qty-btn";
  btnMinus.type = "button";
  btnMinus.textContent = "-";
  btnMinus.setAttribute("aria-label", "Disminuir cantidad");
  btnMinus.addEventListener("click", () => {
    CartStore.setQuantity(item.productId, item.quantity - 1);
  });

  const qtyInput = document.createElement("input");
  qtyInput.type = "number";
  qtyInput.className = "cart-item__qty-input";
  qtyInput.value = item.quantity;
  qtyInput.dataset.prevQty = item.quantity;
  qtyInput.setAttribute("aria-label", "Cantidad");
  qtyInput.min = "1";
  wireQtyInput(qtyInput, item.productId);

  const btnPlus = document.createElement("button");
  btnPlus.className = "cart-item__qty-btn";
  btnPlus.type = "button";
  btnPlus.textContent = "+";
  btnPlus.setAttribute("aria-label", "Aumentar cantidad");
  btnPlus.addEventListener("click", () => {
    CartStore.setQuantity(item.productId, item.quantity + 1);
  });

  qtyControls.appendChild(btnMinus);
  qtyControls.appendChild(qtyInput);
  qtyControls.appendChild(btnPlus);
  li.appendChild(qtyControls);

  const unitPrice = document.createElement("span");
  unitPrice.className = "cart-item__unit-price";
  unitPrice.textContent = fmt(item.unitPrice);
  li.appendChild(unitPrice);

  // Discount badge (only when item has a discount)
  if (item.discount) {
    const badge = document.createElement("span");
    badge.className = "cart-item__discount-badge";
    if (item.discount.type === "percent") {
      badge.textContent = "-" + item.discount.value + "%";
    } else {
      badge.textContent = "-" + fmt(item.discount.value);
    }
    productInfo.appendChild(badge);
  }

  // Line total
  const lineTotal = document.createElement("span");
  lineTotal.className = "cart-item__line-total";
  lineTotal.textContent = fmt(item.lineTotal);
  li.appendChild(lineTotal);

  const actions = document.createElement("div");
  actions.className = "cart-item__actions";

  // "Discount" button
  const btnDiscount = document.createElement("button");
  btnDiscount.type = "button";
  btnDiscount.className = "btn btn-secondary btn--sm";
  btnDiscount.textContent = "%";
  btnDiscount.title = "Aplicar descuento";
  btnDiscount.addEventListener("click", () => {
    toggleDiscountForm(item, li);
  });
  actions.appendChild(btnDiscount);

  // "Remove Discount" button — only when item has an active discount
  if (item.discount) {
    const btnRemoveDiscount = document.createElement("button");
    btnRemoveDiscount.type = "button";
    btnRemoveDiscount.className = "btn btn-secondary btn--sm";
    btnRemoveDiscount.textContent = "Quitar %";
    btnRemoveDiscount.addEventListener("click", () => {
      CartStore.removeItemDiscount(item.productId);
    });
    actions.appendChild(btnRemoveDiscount);
  }

  // "Remove" button
  const btnRemove = document.createElement("button");
  btnRemove.type = "button";
  btnRemove.className = "btn btn-danger btn--sm";
  btnRemove.textContent = "Quitar";
  btnRemove.addEventListener("click", () => {
    CartStore.removeItem(item.productId);
  });
  actions.appendChild(btnRemove);

  li.appendChild(actions);

  // Inline qty error placeholder
  const qtyError = document.createElement("div");
  qtyError.className = "inline-error";
  qtyError.setAttribute("role", "alert");
  qtyError.setAttribute("aria-live", "polite");
  qtyError.id = "qty-error-" + item.productId;
  li.appendChild(qtyError);

  return li;
}

/**
 * Wire blur and Enter keydown on a quantity input.
 * @param {HTMLInputElement} input
 * @param {number} productId
 */
function wireQtyInput(input, productId) {
  const commit = () => {
    const raw = input.value.trim();
    const n = parseInt(raw, 10);
    const errorEl = document.getElementById("qty-error-" + productId);

    if (!raw || isNaN(n) || n <= 0 || !Number.isInteger(n)) {
      // Revert to previous valid value
      input.value = input.dataset.prevQty;
      if (errorEl) {
        errorEl.textContent = "La cantidad debe ser un número entero positivo.";
      }
      return;
    }

    if (errorEl) errorEl.textContent = "";
    CartStore.setQuantity(productId, n);
    // prevQty will be updated on next render
  };

  input.addEventListener("blur", commit);
  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      commit();
    }
  });
}

// ---------------------------------------------------------------------------
// Discount form (per-item inline)
// ---------------------------------------------------------------------------

/**
 * Toggle the inline discount form for a cart item.
 * Only one form can be open at a time.
 *
 * @param {{ productId: number, discount: Object|null }} item
 * @param {HTMLLIElement} li
 */
function toggleDiscountForm(item, li) {
  if (openDiscountFormId === item.productId) {
    // Close it
    openDiscountFormId = null;
    renderItems(); // re-render to remove the form
    return;
  }

  // Close any other open form
  openDiscountFormId = item.productId;
  renderItems(); // re-render; buildItemRow will append the form for this item
}

/**
 * Build the inline discount form for a cart item.
 * Pre-populates with existing discount if present.
 *
 * @param {{ productId: number, discount: Object|null }} item
 * @returns {HTMLDivElement}
 */
function buildDiscountForm(item) {
  const form = document.createElement("div");
  form.className = "cart-item__discount-form";

  // Type selector
  const typeSelect = document.createElement("select");
  typeSelect.className = "input select select--sm";
  typeSelect.setAttribute("aria-label", "Tipo de descuento");

  const optPercent = document.createElement("option");
  optPercent.value = "percent";
  optPercent.textContent = "Porcentaje %";

  const optFixed = document.createElement("option");
  optFixed.value = "fixed";
  optFixed.textContent = "Monto fijo $";

  typeSelect.appendChild(optPercent);
  typeSelect.appendChild(optFixed);

  // Value input
  const valueInput = document.createElement("input");
  valueInput.type = "number";
  valueInput.className = "input input--sm";
  valueInput.min = "0";
  valueInput.placeholder = "0";
  valueInput.setAttribute("aria-label", "Valor del descuento");

  // Pre-populate if item already has a discount
  if (item.discount) {
    typeSelect.value = item.discount.type;
    valueInput.value = item.discount.value;
  }

  // Error display
  const errorEl = document.createElement("div");
  errorEl.className = "inline-error";
  errorEl.setAttribute("role", "alert");
  errorEl.setAttribute("aria-live", "polite");

  // Confirm button
  const btnConfirm = document.createElement("button");
  btnConfirm.type = "button";
  btnConfirm.className = "btn btn-primary btn--sm";
  btnConfirm.textContent = "Confirmar";
  btnConfirm.addEventListener("click", () => {
    const type = typeSelect.value;
    const raw = valueInput.value.trim();
    const value = parseFloat(raw);

    if (raw === "" || isNaN(value)) {
      errorEl.textContent = "Ingrese un descuento válido.";
      return;
    }

    if (type === "percent") {
      if (value < 0 || value > 100) {
        errorEl.textContent = "El descuento debe estar entre 0% y 100%.";
        return;
      }
    } else {
      // fixed
      if (value < 0) {
        errorEl.textContent = "El descuento no puede ser negativo.";
        return;
      }
    }

    errorEl.textContent = "";
    CartStore.setItemDiscount(item.productId, { type, value });
    openDiscountFormId = null;
    // CartStore.setItemDiscount emits cart:updated which triggers renderCart
  });

  // Cancel button
  const btnCancel = document.createElement("button");
  btnCancel.type = "button";
  btnCancel.className = "btn btn-secondary btn--sm";
  btnCancel.textContent = "Cancelar";
  btnCancel.addEventListener("click", () => {
    openDiscountFormId = null;
    renderItems();
  });

  form.appendChild(typeSelect);
  form.appendChild(valueInput);
  form.appendChild(btnConfirm);
  form.appendChild(btnCancel);
  form.appendChild(errorEl);

  return form;
}

// ---------------------------------------------------------------------------
// Totals
// ---------------------------------------------------------------------------

/**
 * Update the subtotal, discount, and grand total display elements.
 */
function renderTotals() {
  const subtotalEl = document.getElementById("cart-subtotal");
  const discountEl = document.getElementById("cart-discount");
  const totalEl = document.getElementById("cart-total");

  if (subtotalEl) subtotalEl.textContent = fmt(CartStore.subtotal);
  if (discountEl) discountEl.textContent = "-" + fmt(CartStore.totalDiscount);
  if (totalEl) totalEl.textContent = fmt(CartStore.grandTotal);
}

function renderCartCount() {
  const countEl = document.getElementById("cart-count");
  if (!countEl) return;

  const count = CartStore.items.reduce((sum, item) => sum + item.quantity, 0);
  countEl.textContent = count === 1 ? "1 artículo" : `${count} artículos`;
}

// ---------------------------------------------------------------------------
// Empty state + Proceed to Payment button
// ---------------------------------------------------------------------------

/**
 * Show/hide the empty cart message and enable/disable the payment button.
 */
function renderEmptyState() {
  const isEmpty = CartStore.items.length === 0;

  const proceedBtn = document.getElementById("proceed-payment-btn");
  const emptyMsg = document.getElementById("cart-empty-msg");

  if (proceedBtn) proceedBtn.disabled = isEmpty;
  if (emptyMsg) emptyMsg.style.display = isEmpty ? "" : "none";
}

// ---------------------------------------------------------------------------
// Order Discount section
// ---------------------------------------------------------------------------

/**
 * Show or hide the "Remove Order Discount" button based on CartStore.orderDiscount.
 */
function syncOrderDiscountRemoveBtn() {
  const removeBtn = document.getElementById("order-discount-remove-btn");
  if (!removeBtn) return;
  removeBtn.style.display = CartStore.orderDiscount ? "" : "none";
}

/**
 * Wire the Order Discount apply and remove buttons.
 */
function wireOrderDiscount() {
  const applyBtn = document.getElementById("order-discount-apply-btn");
  const removeBtn = document.getElementById("order-discount-remove-btn");
  const errorEl = document.getElementById("order-discount-error");

  if (applyBtn) {
    applyBtn.addEventListener("click", () => {
      const typeSelect = document.getElementById("order-discount-type");
      const valueInput = document.getElementById("order-discount-value");

      const type = typeSelect ? typeSelect.value : "percent";
      const raw = valueInput ? valueInput.value.trim() : "";
      const value = parseFloat(raw);

      if (raw === "" || isNaN(value)) {
        if (errorEl) errorEl.textContent = "Ingrese un descuento válido.";
        return;
      }

      if (type === "percent") {
        if (value < 0 || value > 100) {
          if (errorEl) errorEl.textContent = "El descuento debe estar entre 0% y 100%.";
          return;
        }
      } else {
        // fixed
        if (value < 0) {
          if (errorEl) errorEl.textContent = "El descuento no puede ser negativo.";
          return;
        }
      }

      if (errorEl) errorEl.textContent = "";
      CartStore.setOrderDiscount({ type, value });
      // cart:updated will trigger renderCart → syncOrderDiscountRemoveBtn
    });
  }

  if (removeBtn) {
    removeBtn.addEventListener("click", () => {
      CartStore.removeOrderDiscount();
      // cart:updated will trigger renderCart → syncOrderDiscountRemoveBtn
    });
  }
}

// ---------------------------------------------------------------------------
// Clear Cart
// ---------------------------------------------------------------------------

/**
 * Wire the "Clear Cart" button with a confirmation dialog.
 */
function wireClearCart() {
  const btn = document.getElementById("clear-cart-btn");
  if (!btn) return;

  btn.addEventListener("click", () => {
    const confirmed = window.confirm(
      "¿Vaciar el carrito? Esta acción no se puede deshacer."
    );
    if (confirmed) {
      openDiscountFormId = null;
      CartStore.clearCart();
    }
  });
}

// ---------------------------------------------------------------------------
// Public init
// ---------------------------------------------------------------------------

/**
 * Initialize the Cart Panel module.
 *
 * 1. Subscribes to `cart:updated` event on document.
 * 2. Wires #clear-cart-btn click.
 * 3. Wires #order-discount-apply-btn and #order-discount-remove-btn clicks.
 * 4. Calls the initial render.
 */
export function initCartPanel() {
  // Subscribe to cart updates
  document.addEventListener("cart:updated", renderCart);

  // Wire buttons
  wireClearCart();
  wireOrderDiscount();

  // Initial render
  renderCart();
}
