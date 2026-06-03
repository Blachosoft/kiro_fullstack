# Implementation Tasks

## Task List

- [x] 1. Project scaffolding and base infrastructure
  - [x] 1.1 Create `pos.html` with two-column layout structure, header bar, and modal placeholders
  - [x] 1.2 Create `pos.css` with design tokens, layout grid, responsive breakpoint, and base component styles
  - [x] 1.3 Create `modules/auth.js` with token read/write/clear helpers and `getCashierName()` JWT decoder
  - [x] 1.4 Create `modules/api.js` with `apiFetch` wrapper that injects Bearer token and handles 401 redirect
  - [x] 1.5 Update `login.js` to redirect to `/pos.html` on successful login instead of the existing products page

- [x] 2. CartStore — in-memory state management
  - [x] 2.1 Create `modules/cart.js` with `CartStore` object: `items` array, `orderDiscount` field, and all mutation methods (`addItem`, `removeItem`, `setQuantity`, `setItemDiscount`, `removeItemDiscount`, `setOrderDiscount`, `removeOrderDiscount`, `clearCart`)
  - [x] 2.2 Implement computed getters: `subtotal`, `totalDiscount`, `grandTotal` with correct discount calculation logic (percent and fixed, item-level and order-level)
  - [x] 2.3 Emit `cart:updated` custom DOM event on every CartStore mutation
  - [x] 2.4 Write unit tests for CartStore: add/remove/quantity mutations, discount calculations, `clearCart` reset, and grand total invariant

- [x] 3. Product Search Panel
  - [x] 3.1 Create `modules/search.js` with debounced name search (300 ms) calling `GET /productos?nombre=&estado=activo`
  - [x] 3.2 Implement category dropdown population on init from `GET /productos?estado=activo&limit=200`, extracting distinct `subcategoria` values
  - [x] 3.3 Implement combined name + category filter query when both inputs are active
  - [x] 3.4 Render search results as clickable list items showing name, subcategory, and price; clicking calls `CartStore.addItem(product)`
  - [x] 3.5 Display "No products found" message when results are empty; display connection error message on API failure

- [x] 4. Barcode Scanner — manual entry
  - [x] 4.1 Create `modules/scanner.js` with `#barcode-input` Enter-key handler that calls `GET /productos?nombre={value}&estado=activo`
  - [x] 4.2 On successful product match, call `CartStore.addItem(product)` and clear the input field
  - [x] 4.3 Display inline error "Product not found for barcode '{value}'." with red input highlight when no match is found
  - [x] 4.4 Validate non-empty input before making API request; display "Please enter a barcode value." for empty submission

- [x] 5. Barcode Scanner — camera scanning
  - [x] 5.1 Add ZXing-js/browser library via CDN `<script>` tag in `pos.html`
  - [x] 5.2 Implement camera availability check on page load using `navigator.mediaDevices`; hide "Scan with Camera" button if unavailable
  - [x] 5.3 Implement "Scan with Camera" button handler: request camera permission, instantiate `BrowserMultiFormatReader`, start live video preview in `#camera-preview`
  - [x] 5.4 On successful barcode decode, populate `#barcode-input`, trigger product lookup (reusing manual entry logic), and call `codeReader.reset()` to stop the stream
  - [x] 5.5 Implement "Stop Camera" button and auto-stop on successful decode; hide video preview after stopping
  - [x] 5.6 Display "Camera access denied. Use manual barcode entry." when `getUserMedia` permission is denied

- [x] 6. Cart Panel UI
  - [x] 6.1 Create Cart_Panel HTML structure in `pos.html`: scrollable item list, subtotal/discount/grand-total footer, "Order Discount" section, "Clear Cart" button, and "Proceed to Payment" button
  - [x] 6.2 Implement Cart_Panel render function that subscribes to `cart:updated` and re-renders the full item list and totals on each event
  - [x] 6.3 Render each Cart_Item row with: name, unit price, quantity controls ("−" / direct input / "+"), discount badge (if active), line total, "Discount" button, and "Remove" button
  - [x] 6.4 Wire "+" and "−" buttons to `CartStore.setQuantity`; wire "Remove" button to `CartStore.removeItem`
  - [x] 6.5 Implement direct quantity input: validate positive integer on blur/Enter; revert to previous value and show inline error on invalid input
  - [x] 6.6 Implement "Clear Cart" button with confirmation dialog (`window.confirm`) before calling `CartStore.clearCart()`
  - [x] 6.7 Disable "Proceed to Payment" button and show "Cart is empty" message when `CartStore.items.length === 0`

- [x] 7. Discount UI
  - [x] 7.1 Implement per-item discount input: clicking "Discount" on a Cart_Item row opens an inline form with type selector (percent/fixed) and value input
  - [x] 7.2 On discount confirm, call `CartStore.setItemDiscount(productId, { type, value })`; validate percent is 0–100 and fixed is non-negative
  - [x] 7.3 Pre-populate discount input with current discount value when the item already has a discount
  - [x] 7.4 Render "Remove Discount" button on Cart_Item rows that have an active discount; clicking calls `CartStore.removeItemDiscount(productId)`
  - [x] 7.5 Implement "Order Discount" section: type selector and value input that calls `CartStore.setOrderDiscount({ type, value })` on confirm
  - [x] 7.6 Display order discount as a separate line in the Cart_Panel footer; render "Remove Order Discount" button when active

- [x] 8. Payment Modal
  - [x] 8.1 Create `#payment-modal` HTML in `pos.html` with method selector (Cash / Card / Mixed), dynamic input area, totals display, "Confirm Payment" button, and "Cancel" button
  - [x] 8.2 Create `modules/payment.js`: open modal on "Proceed to Payment" click, populate grand total from `CartStore.grandTotal`
  - [x] 8.3 Implement Cash mode: single cash amount input with real-time change-due calculation; disable confirm if `cashAmount < grandTotal`
  - [x] 8.4 Implement Card mode: card amount pre-filled with grand total; confirmation checkbox required before enabling confirm
  - [x] 8.5 Implement Mixed mode: cash + card inputs with real-time "Remaining Balance" display; disable confirm if `cashAmount + cardAmount < grandTotal`
  - [x] 8.6 Display change due in green when `cashAmount > grandTotal` (Cash or Mixed mode)
  - [x] 8.7 On "Confirm Payment", build `Transaction` object with UUID, timestamp, cashier name, cart snapshot, and payment split; call `receipt.open(transaction)`
  - [x] 8.8 "Cancel" button closes modal without modifying CartStore

- [x] 9. Receipt Modal
  - [x] 9.1 Create `#receipt-modal` HTML in `pos.html` with `#receipt-content` container, "Print Receipt" button, and "New Transaction" button
  - [x] 9.2 Create `modules/receipt.js`: `open(transaction)` function that renders all transaction fields into `#receipt-content` using `textContent` (no `innerHTML` for dynamic data)
  - [x] 9.3 Render receipt sections: header (transaction ID, date/time, cashier), itemized list (name, qty, unit price, discount, line total), order discount line, subtotal, total discount, grand total, payment breakdown, change due
  - [x] 9.4 Implement "Print Receipt" button: call `window.print()`; add `@media print` CSS rule in `pos.css` that hides everything except `#receipt-content`
  - [x] 9.5 Implement "New Transaction" button: call `CartStore.clearCart()`, close modal, generate new transaction ID, focus `#search-name` input

- [x] 10. Header bar and session management
  - [x] 10.1 Render cashier username from `auth.getCashierName()` in the persistent header bar on POS screen load
  - [x] 10.2 Display current date and time in the header; update the time display every 60 seconds using `setInterval`
  - [x] 10.3 Wire the header logout button to `auth.clearSession()` followed by redirect to `/login.html`
  - [x] 10.4 On `pos.html` load, check `auth.isAuthenticated()`; redirect to `/login.html` if no valid token is present

- [x] 11. Entry point wiring and integration
  - [x] 11.1 Create `pos.js` as the main entry point: import all modules, initialize search, scanner, cart panel, and header on `DOMContentLoaded`
  - [x] 11.2 Verify end-to-end flow: login → product search → add to cart → apply discount → proceed to payment → confirm → receipt → new transaction
  - [x] 11.3 Verify responsive layout at 767px breakpoint: single-column stacking of search panel above cart panel
  - [x] 11.4 Verify all interactive elements meet 44px minimum touch-target size and 16px minimum font size requirements
