/**
 * CartStore unit tests — ES module, browser-runnable.
 *
 * Run via tests/cart.test.html in any modern browser.
 */

import CartStore from "../modules/cart.js";

// ---------------------------------------------------------------------------
// Assertion helper
// ---------------------------------------------------------------------------

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (!condition) {
    failed++;
    const msg = `FAIL: ${message}`;
    console.error(msg);
    recordResult(false, message);
    throw new Error(msg);
  }
  passed++;
  console.log(`PASS: ${message}`);
  recordResult(true, message);
}

function assertThrows(fn, message) {
  let threw = false;
  try {
    fn();
  } catch (_) {
    threw = true;
  }
  assert(threw, message);
}

function recordResult(ok, message) {
  if (typeof window !== "undefined" && window.__results) {
    window.__results.push({ ok, message });
  }
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Reset CartStore to a clean state before each test. */
function reset() {
  CartStore.clearCart();
}

/** Build a minimal product object. */
function makeProduct(id, nombre, precio) {
  return { id, nombre, precio };
}

// ---------------------------------------------------------------------------
// Test runner
// ---------------------------------------------------------------------------

const tests = [];

function test(name, fn) {
  tests.push({ name, fn });
}

async function runAll() {
  for (const { name, fn } of tests) {
    reset();
    try {
      await fn();
    } catch (e) {
      // error already recorded by assert()
    }
  }
  console.log(`\n=== Results: ${passed} passed, ${failed} failed ===`);
  return { passed, failed };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

// 1. addItem — adds a new product with quantity 1
test("addItem — adds a new product with quantity 1", () => {
  const p = makeProduct(1, "Apple", 1.5);
  CartStore.addItem(p);
  assert(CartStore.items.length === 1, "cart has 1 item");
  assert(CartStore.items[0].productId === 1, "productId is 1");
  assert(CartStore.items[0].quantity === 1, "quantity is 1");
  assert(CartStore.items[0].unitPrice === 1.5, "unitPrice is 1.5");
  assert(CartStore.items[0].nombre === "Apple", "nombre is Apple");
  assert(CartStore.items[0].lineTotal === 1.5, "lineTotal is 1.5");
});

// 2. addItem — increments quantity when same product added again
test("addItem — increments quantity when same product added again", () => {
  const p = makeProduct(2, "Banana", 0.75);
  CartStore.addItem(p);
  CartStore.addItem(p);
  CartStore.addItem(p);
  assert(CartStore.items.length === 1, "still 1 item in cart");
  assert(CartStore.items[0].quantity === 3, "quantity is 3");
  assert(CartStore.items[0].lineTotal === 2.25, "lineTotal is 0.75 * 3 = 2.25");
});

// 3. removeItem — removes item from cart
test("removeItem — removes item from cart", () => {
  CartStore.addItem(makeProduct(3, "Cherry", 2.0));
  CartStore.addItem(makeProduct(4, "Date", 3.0));
  CartStore.removeItem(3);
  assert(CartStore.items.length === 1, "cart has 1 item after remove");
  assert(CartStore.items[0].productId === 4, "remaining item is product 4");
});

// 4. setQuantity — updates quantity and recalculates lineTotal
test("setQuantity — updates quantity and recalculates lineTotal", () => {
  CartStore.addItem(makeProduct(5, "Elderberry", 4.0));
  CartStore.setQuantity(5, 5);
  assert(CartStore.items[0].quantity === 5, "quantity updated to 5");
  assert(CartStore.items[0].lineTotal === 20.0, "lineTotal is 4.0 * 5 = 20");
});

// 5. setQuantity(id, 0) — removes item from cart
test("setQuantity(id, 0) — removes item from cart", () => {
  CartStore.addItem(makeProduct(6, "Fig", 1.0));
  CartStore.setQuantity(6, 0);
  assert(CartStore.items.length === 0, "item removed when quantity set to 0");
});

// 6. Discount calculation — percent discount on item
test("Discount calculation — percent discount on item", () => {
  CartStore.addItem(makeProduct(7, "Grape", 10.0));
  CartStore.setQuantity(7, 2); // lineBase = 20
  CartStore.setItemDiscount(7, { type: "percent", value: 25 });
  // discountAmount = 20 * 0.25 = 5; lineTotal = 15
  assert(CartStore.items[0].lineTotal === 15, "lineTotal after 25% discount on $20 = $15");
  assert(CartStore.subtotal === 20, "subtotal is still 20 (pre-discount)");
  assert(CartStore.totalDiscount === 5, "totalDiscount is 5");
  assert(CartStore.grandTotal === 15, "grandTotal is 15");
});

// 7. Discount calculation — fixed discount on item (capped at line total)
test("Discount calculation — fixed discount capped at line total", () => {
  CartStore.addItem(makeProduct(8, "Honeydew", 5.0));
  // quantity 1, lineBase = 5; apply fixed discount of 100 (exceeds lineBase)
  CartStore.setItemDiscount(8, { type: "fixed", value: 100 });
  // discountAmount = Math.min(100, 5) = 5; lineTotal = 0
  assert(CartStore.items[0].lineTotal === 0, "lineTotal capped at 0 when fixed discount > lineBase");
  assert(CartStore.grandTotal === 0, "grandTotal is 0");
});

// 8. Order-level percent discount
test("Order-level percent discount", () => {
  CartStore.addItem(makeProduct(9, "Iceberg Lettuce", 2.0));
  CartStore.setQuantity(9, 5); // subtotal = 10
  CartStore.setOrderDiscount({ type: "percent", value: 10 });
  // itemDiscountsTotal = 0; remaining = 10; orderDiscount = 10 * 0.10 = 1
  assert(CartStore.totalDiscount === 1, "totalDiscount is 1");
  assert(CartStore.grandTotal === 9, "grandTotal is 9");
});

// 9. Order-level fixed discount
test("Order-level fixed discount", () => {
  CartStore.addItem(makeProduct(10, "Jalapeño", 3.0));
  CartStore.setQuantity(10, 4); // subtotal = 12
  CartStore.setOrderDiscount({ type: "fixed", value: 3 });
  // itemDiscountsTotal = 0; remaining = 12; orderDiscount = min(3, 12) = 3
  assert(CartStore.totalDiscount === 3, "totalDiscount is 3");
  assert(CartStore.grandTotal === 9, "grandTotal is 9");
});

// 10. clearCart — resets items, orderDiscount, grandTotal to 0
test("clearCart — resets items, orderDiscount, grandTotal to 0", () => {
  CartStore.addItem(makeProduct(11, "Kiwi", 1.5));
  CartStore.addItem(makeProduct(12, "Lemon", 0.5));
  CartStore.setOrderDiscount({ type: "percent", value: 20 });
  CartStore.clearCart();
  assert(CartStore.items.length === 0, "items array is empty after clearCart");
  assert(CartStore.orderDiscount === null, "orderDiscount is null after clearCart");
  assert(CartStore.grandTotal === 0, "grandTotal is 0 after clearCart");
  assert(CartStore.subtotal === 0, "subtotal is 0 after clearCart");
  assert(CartStore.totalDiscount === 0, "totalDiscount is 0 after clearCart");
});

// 11. Grand total invariant: grandTotal === subtotal - totalDiscount, never negative
test("Grand total invariant — grandTotal === subtotal - totalDiscount, never negative", () => {
  // Build a cart with mixed discounts
  CartStore.addItem(makeProduct(13, "Mango", 5.0));
  CartStore.setQuantity(13, 3); // lineBase = 15
  CartStore.setItemDiscount(13, { type: "percent", value: 10 }); // itemDiscount = 1.5

  CartStore.addItem(makeProduct(14, "Nectarine", 2.0));
  CartStore.setQuantity(14, 2); // lineBase = 4
  CartStore.setItemDiscount(14, { type: "fixed", value: 1 }); // itemDiscount = 1

  CartStore.setOrderDiscount({ type: "percent", value: 5 });

  const { subtotal, totalDiscount, grandTotal } = CartStore;
  assert(
    Math.abs(grandTotal - (subtotal - totalDiscount)) < 0.0001,
    `grandTotal (${grandTotal}) === subtotal (${subtotal}) - totalDiscount (${totalDiscount})`
  );
  assert(grandTotal >= 0, "grandTotal is never negative");

  // Edge case: order fixed discount larger than remaining — grandTotal must not go negative
  CartStore.setOrderDiscount({ type: "fixed", value: 9999 });
  assert(CartStore.grandTotal >= 0, "grandTotal stays >= 0 when order discount exceeds remaining");
});

// 12. Discount bounds: lineTotal always >= 0
test("Discount bounds — lineTotal always >= 0", () => {
  // Percent discount of 100% → lineTotal should be 0
  CartStore.addItem(makeProduct(15, "Orange", 3.0));
  CartStore.setItemDiscount(15, { type: "percent", value: 100 });
  assert(CartStore.items[0].lineTotal === 0, "lineTotal is 0 with 100% discount");
  assert(CartStore.items[0].lineTotal >= 0, "lineTotal >= 0 with 100% discount");

  // Fixed discount larger than line total → lineTotal should be 0
  CartStore.addItem(makeProduct(16, "Papaya", 2.0));
  CartStore.setItemDiscount(16, { type: "fixed", value: 500 });
  assert(CartStore.items[1].lineTotal === 0, "lineTotal is 0 when fixed discount > lineBase");
  assert(CartStore.items[1].lineTotal >= 0, "lineTotal >= 0 when fixed discount > lineBase");
});

// ---------------------------------------------------------------------------
// Additional edge-case tests
// ---------------------------------------------------------------------------

// setQuantity with negative value throws
test("setQuantity — throws for negative quantity", () => {
  CartStore.addItem(makeProduct(17, "Quince", 4.0));
  assertThrows(
    () => CartStore.setQuantity(17, -1),
    "setQuantity throws when n < 0"
  );
});

// removeItemDiscount restores original lineTotal
test("removeItemDiscount — restores original lineTotal", () => {
  CartStore.addItem(makeProduct(18, "Raspberry", 2.0));
  CartStore.setQuantity(18, 3); // lineBase = 6
  CartStore.setItemDiscount(18, { type: "fixed", value: 2 });
  assert(CartStore.items[0].lineTotal === 4, "lineTotal is 4 after fixed discount of 2");
  CartStore.removeItemDiscount(18);
  assert(CartStore.items[0].lineTotal === 6, "lineTotal restored to 6 after removing discount");
  assert(CartStore.items[0].discount === null, "discount is null after removeItemDiscount");
});

// removeOrderDiscount restores grandTotal
test("removeOrderDiscount — restores grandTotal", () => {
  CartStore.addItem(makeProduct(19, "Strawberry", 5.0));
  CartStore.setOrderDiscount({ type: "fixed", value: 2 });
  assert(CartStore.grandTotal === 3, "grandTotal is 3 with order discount");
  CartStore.removeOrderDiscount();
  assert(CartStore.grandTotal === 5, "grandTotal restored to 5 after removing order discount");
  assert(CartStore.orderDiscount === null, "orderDiscount is null after removeOrderDiscount");
});

// cart:updated event is dispatched on mutations
test("cart:updated event — dispatched on addItem", () => {
  let eventCount = 0;
  const handler = () => eventCount++;
  document.addEventListener("cart:updated", handler);
  CartStore.addItem(makeProduct(20, "Tangerine", 1.0));
  document.removeEventListener("cart:updated", handler);
  assert(eventCount === 1, "cart:updated event fired once on addItem");
});

// ---------------------------------------------------------------------------
// Export runner for HTML harness
// ---------------------------------------------------------------------------

export { runAll };
