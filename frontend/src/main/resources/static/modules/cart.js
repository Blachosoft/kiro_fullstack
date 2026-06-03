/**
 * CartStore — in-memory state management for the POS frontend.
 *
 * Emits a `cart:updated` CustomEvent on `document` after every mutation.
 *
 * CartItem shape:
 * {
 *   productId: number,
 *   nombre:    string,
 *   unitPrice: number,   // precio at time of adding
 *   quantity:  number,
 *   discount:  { type: "percent"|"fixed", value: number } | null,
 *   lineTotal: number,   // computed: (unitPrice * quantity) - discountAmount, min 0
 * }
 */

// ---------------------------------------------------------------------------
// Internal helpers
// ---------------------------------------------------------------------------

/**
 * Calculate the discount amount for a single cart item.
 * @param {number} unitPrice
 * @param {number} quantity
 * @param {{ type: "percent"|"fixed", value: number }|null} discount
 * @returns {number}
 */
function itemDiscountAmount(unitPrice, quantity, discount) {
  if (!discount) return 0;
  const lineBase = unitPrice * quantity;
  if (discount.type === "percent") {
    return lineBase * (discount.value / 100);
  }
  // fixed — capped at the line base so lineTotal never goes below 0
  return Math.min(discount.value, lineBase);
}

/**
 * Compute the lineTotal for a cart item.
 * @param {number} unitPrice
 * @param {number} quantity
 * @param {{ type: "percent"|"fixed", value: number }|null} discount
 * @returns {number}
 */
function computeLineTotal(unitPrice, quantity, discount) {
  const lineBase = unitPrice * quantity;
  const discAmt = itemDiscountAmount(unitPrice, quantity, discount);
  return Math.max(0, lineBase - discAmt);
}

/**
 * Dispatch the `cart:updated` event on document.
 */
function emitCartUpdated() {
  document.dispatchEvent(new CustomEvent("cart:updated"));
}

// ---------------------------------------------------------------------------
// CartStore
// ---------------------------------------------------------------------------

const CartStore = {
  /** @type {Array<{productId:number, nombre:string, unitPrice:number, quantity:number, discount:Object|null, lineTotal:number}>} */
  items: [],

  /** @type {{ type: "percent"|"fixed", value: number }|null} */
  orderDiscount: null,

  // -------------------------------------------------------------------------
  // Mutation methods
  // -------------------------------------------------------------------------

  /**
   * Add a product to the cart with quantity 1, or increment quantity by 1
   * if the product is already present.
   *
   * @param {{ id: number, nombre: string, precio: number }} product
   */
  addItem(product) {
    const existing = this.items.find((i) => i.productId === product.id);
    if (existing) {
      existing.quantity += 1;
      existing.lineTotal = computeLineTotal(
        existing.unitPrice,
        existing.quantity,
        existing.discount
      );
    } else {
      this.items.push({
        productId: product.id,
        nombre: product.nombre,
        unitPrice: product.precio,
        quantity: 1,
        discount: null,
        lineTotal: product.precio,
      });
    }
    emitCartUpdated();
  },

  /**
   * Remove a cart item by productId.
   * @param {number} productId
   */
  removeItem(productId) {
    this.items = this.items.filter((i) => i.productId !== productId);
    emitCartUpdated();
  },

  /**
   * Set the quantity of a cart item.
   * - n === 0  → removes the item
   * - n < 0   → throws an Error
   *
   * @param {number} productId
   * @param {number} n
   */
  setQuantity(productId, n) {
    if (n < 0) {
      throw new Error(`Quantity must be non-negative, got ${n}`);
    }
    if (n === 0) {
      this.removeItem(productId);
      return; // removeItem already emits
    }
    const item = this.items.find((i) => i.productId === productId);
    if (item) {
      item.quantity = n;
      item.lineTotal = computeLineTotal(item.unitPrice, item.quantity, item.discount);
    }
    emitCartUpdated();
  },

  /**
   * Apply a discount to a specific cart item.
   * @param {number} productId
   * @param {{ type: "percent"|"fixed", value: number }} discount
   */
  setItemDiscount(productId, discount) {
    const item = this.items.find((i) => i.productId === productId);
    if (item) {
      item.discount = discount;
      item.lineTotal = computeLineTotal(item.unitPrice, item.quantity, item.discount);
    }
    emitCartUpdated();
  },

  /**
   * Remove the discount from a specific cart item.
   * @param {number} productId
   */
  removeItemDiscount(productId) {
    const item = this.items.find((i) => i.productId === productId);
    if (item) {
      item.discount = null;
      item.lineTotal = computeLineTotal(item.unitPrice, item.quantity, null);
    }
    emitCartUpdated();
  },

  /**
   * Set an order-level discount.
   * @param {{ type: "percent"|"fixed", value: number }} discount
   */
  setOrderDiscount(discount) {
    this.orderDiscount = discount;
    emitCartUpdated();
  },

  /**
   * Remove the order-level discount.
   */
  removeOrderDiscount() {
    this.orderDiscount = null;
    emitCartUpdated();
  },

  /**
   * Clear all items and discounts from the cart.
   */
  clearCart() {
    this.items = [];
    this.orderDiscount = null;
    emitCartUpdated();
  },

  // -------------------------------------------------------------------------
  // Computed getters
  // -------------------------------------------------------------------------

  /**
   * Sum of (unitPrice * quantity) for every item — before any discounts.
   * @returns {number}
   */
  get subtotal() {
    return this.items.reduce((sum, i) => sum + i.unitPrice * i.quantity, 0);
  },

  /**
   * Total discount amount = sum of all item discounts + order discount.
   *
   * Order discount is applied to (subtotal - itemDiscountsTotal):
   *   - percent: (subtotal - itemDiscountsTotal) * (value / 100)
   *   - fixed:   Math.min(value, Math.max(0, subtotal - itemDiscountsTotal))
   *
   * @returns {number}
   */
  get totalDiscount() {
    const itemDiscountsTotal = this.items.reduce(
      (sum, i) => sum + itemDiscountAmount(i.unitPrice, i.quantity, i.discount),
      0
    );

    let orderDiscountAmount = 0;
    if (this.orderDiscount) {
      const remaining = Math.max(0, this.subtotal - itemDiscountsTotal);
      if (this.orderDiscount.type === "percent") {
        orderDiscountAmount = remaining * (this.orderDiscount.value / 100);
      } else {
        // fixed — floored at 0
        orderDiscountAmount = Math.min(this.orderDiscount.value, remaining);
      }
    }

    return itemDiscountsTotal + orderDiscountAmount;
  },

  /**
   * Grand total = subtotal - totalDiscount, minimum 0.
   * @returns {number}
   */
  get grandTotal() {
    return Math.max(0, this.subtotal - this.totalDiscount);
  },
};

export default CartStore;
