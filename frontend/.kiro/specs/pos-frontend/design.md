# Design Document

## Overview

The POS Frontend is a single-page application (SPA) built with **vanilla HTML5, CSS3, and ES2022 JavaScript** — consistent with the existing project stack (no build tool, no framework). It is served as static files from `src/main/resources/static/` by the Spring Boot backend and communicates with the existing REST API over the same origin using JWT Bearer authentication.

The design follows a **module-per-concern** architecture: each logical area (auth, cart, search, scanner, payment, receipt) lives in its own JS module file. State is managed in a single in-memory `CartStore` object. No external state management library is introduced.

---

## Architecture

### File Structure

```
src/main/resources/static/
├── index.html            # Redirect to login.html (existing)
├── login.html            # Existing login page (unchanged)
├── login.css             # Existing (unchanged)
├── login.js              # Existing (unchanged)
├── pos.html              # NEW: Main POS screen
├── pos.css               # NEW: POS-specific styles
├── pos.js                # NEW: Entry point — bootstraps modules
├── modules/
│   ├── api.js            # NEW: Fetch wrapper with JWT injection & 401 handling
│   ├── auth.js           # NEW: Session helpers (token read/write/clear)
│   ├── cart.js           # NEW: CartStore — in-memory state + mutation methods
│   ├── search.js         # NEW: Product search panel logic
│   ├── scanner.js        # NEW: Barcode manual + camera scanning logic
│   ├── payment.js        # NEW: Payment modal logic
│   └── receipt.js        # NEW: Receipt modal logic
```

### Module Dependency Graph

```
pos.js
  ├── auth.js        (reads JWT_Token, redirects if missing)
  ├── api.js         (all fetch calls; depends on auth.js)
  ├── cart.js        (CartStore; pure state — no DOM)
  ├── search.js      (depends on api.js, cart.js)
  ├── scanner.js     (depends on api.js, cart.js)
  ├── payment.js     (depends on cart.js)
  └── receipt.js     (depends on cart.js, auth.js)
```

---

## Data Models

### Product (from API)

```javascript
/**
 * @typedef {Object} Product
 * @property {number}  id
 * @property {string}  nombre          - Display name
 * @property {string}  descripcion
 * @property {string}  subcategoria    - Category label
 * @property {number}  precio          - Unit price
 * @property {number}  precioxcantidad - Bulk/quantity price
 * @property {string}  estado          - "activo" | "inactivo"
 */
```

### CartItem (in-memory)

```javascript
/**
 * @typedef {Object} CartItem
 * @property {number}  productId
 * @property {string}  nombre
 * @property {number}  unitPrice       - precio at time of adding
 * @property {number}  quantity
 * @property {Object}  discount        - { type: "percent"|"fixed", value: number } | null
 * @property {number}  lineTotal       - computed: (unitPrice * quantity) - discountAmount
 */
```

### PaymentSplit (in-memory)

```javascript
/**
 * @typedef {Object} PaymentSplit
 * @property {"cash"|"card"|"mixed"} method
 * @property {number} cashAmount
 * @property {number} cardAmount
 * @property {number} changedue
 */
```

### Transaction (in-memory, for receipt)

```javascript
/**
 * @typedef {Object} Transaction
 * @property {string}     id            - Generated UUID v4
 * @property {string}     cashier       - Username from JWT payload
 * @property {Date}       timestamp
 * @property {CartItem[]} items
 * @property {Object}     orderDiscount - { type, value } | null
 * @property {number}     subtotal
 * @property {number}     totalDiscount
 * @property {number}     grandTotal
 * @property {PaymentSplit} payment
 */
```

---

## Component Design

### `modules/api.js` — API Client

Exports a single `apiFetch(path, options)` function that:
1. Reads the JWT_Token from `localStorage`.
2. Injects `Authorization: Bearer {token}` into every request.
3. On HTTP 401, calls `auth.clearSession()` and redirects to `/login.html`.
4. Returns the parsed JSON body or throws a typed `ApiError`.

```javascript
// Public API
export async function apiFetch(path, options = {}) { ... }
export class ApiError extends Error {
  constructor(status, message) { ... }
}
```

### `modules/auth.js` — Session Helpers

```javascript
export const TOKEN_KEY = "access_token";
export function getToken()          { return localStorage.getItem(TOKEN_KEY); }
export function saveToken(token)    { localStorage.setItem(TOKEN_KEY, token); }
export function clearSession()      { localStorage.removeItem(TOKEN_KEY); }
export function getCashierName()    { /* decode JWT payload.sub */ }
export function isAuthenticated()   { return !!getToken(); }
```

### `modules/cart.js` — CartStore

The CartStore is a plain object with methods. It emits a custom DOM event `cart:updated` on every mutation so that the Cart_Panel re-renders reactively.

```javascript
const CartStore = {
  items: [],          // CartItem[]
  orderDiscount: null,

  addItem(product)          { ... },  // add or increment qty
  removeItem(productId)     { ... },
  setQuantity(productId, n) { ... },  // throws if n < 0
  setItemDiscount(productId, discount) { ... },
  removeItemDiscount(productId)        { ... },
  setOrderDiscount(discount)           { ... },
  removeOrderDiscount()                { ... },
  clearCart()                          { ... },

  // Computed
  get subtotal()      { /* sum of (unitPrice * qty) per item */ },
  get totalDiscount() { /* sum of item discounts + order discount */ },
  get grandTotal()    { /* subtotal - totalDiscount, min 0 */ },
};

export default CartStore;
```

**Discount calculation rules:**
- Item percent discount: `discountAmount = unitPrice * quantity * (value / 100)`
- Item fixed discount: `discountAmount = Math.min(value, unitPrice * quantity)`
- Order percent discount: applied to `subtotal - itemDiscountsTotal`
- Order fixed discount: applied to `subtotal - itemDiscountsTotal`, floored at 0

### `modules/search.js` — Product Search Panel

Manages the `#search-name` input, `#search-category` select, and `#search-results` list.

- Debounces name input at 300 ms using `setTimeout`/`clearTimeout`.
- On each search, calls `apiFetch('/productos?nombre=...&subcategoria=...&estado=activo&limit=20')`.
- Renders results as clickable `<li>` elements; click calls `CartStore.addItem(product)`.
- Populates category dropdown on init via `apiFetch('/productos?estado=activo&limit=200')` and extracts distinct `subcategoria` values.

### `modules/scanner.js` — Barcode Scanner

**Manual entry:** Listens for `keydown` Enter on `#barcode-input`. Calls `apiFetch('/productos?nombre={value}&estado=activo')` and picks the first exact-id match.

**Camera scanning:**
- Uses the [ZXing-js/browser](https://github.com/zxing-js/browser) library loaded via CDN `<script>` tag.
- `BrowserMultiFormatReader` is instantiated on demand when the Cashier clicks "Scan with Camera".
- On decode, populates `#barcode-input`, triggers product lookup, and calls `codeReader.reset()`.
- Checks `navigator.mediaDevices` availability on page load; hides the camera button if absent.

### `modules/payment.js` — Payment Modal

Manages `#payment-modal`. Reads `CartStore.grandTotal` on open.

State machine:
```
IDLE → OPEN(method=cash|card|mixed) → CONFIRMED → IDLE
                                    ↘ CANCELLED → IDLE
```

- Cash mode: validates `cashAmount >= grandTotal` before enabling confirm.
- Card mode: validates checkbox is checked.
- Mixed mode: validates `cashAmount + cardAmount >= grandTotal`.
- On confirm: builds a `Transaction` object, calls `receipt.open(transaction)`, resets modal state.

### `modules/receipt.js` — Receipt Modal

Manages `#receipt-modal`. Receives a `Transaction` object.

- Renders all transaction fields into `#receipt-content`.
- "Print Receipt": calls `window.print()`. CSS `@media print` hides everything except `#receipt-content`.
- "New Transaction": calls `CartStore.clearCart()`, generates new transaction ID, closes modal, focuses `#search-name`.

---

## Page Layout: `pos.html`

```
┌─────────────────────────────────────────────────────────────┐
│  HEADER: [Logo] [Date/Time]              [Cashier] [Logout] │
├──────────────────────────┬──────────────────────────────────┤
│  LEFT COLUMN             │  RIGHT COLUMN                    │
│                          │                                  │
│  ┌────────────────────┐  │  ┌──────────────────────────┐   │
│  │ BARCODE SCANNER    │  │  │ CART PANEL               │   │
│  │ [input] [Enter]    │  │  │                          │   │
│  │ [📷 Scan Camera]   │  │  │  Item list (scrollable)  │   │
│  │ [video preview]    │  │  │                          │   │
│  └────────────────────┘  │  │  Subtotal: $xxx          │   │
│                          │  │  Discount: -$xxx         │   │
│  ┌────────────────────┐  │  │  TOTAL:    $xxx          │   │
│  │ PRODUCT SEARCH     │  │  │                          │   │
│  │ [name input]       │  │  │  [Order Discount]        │   │
│  │ [category select]  │  │  │  [Clear Cart]            │   │
│  │                    │  │  │  [Proceed to Payment ▶]  │   │
│  │  Results list      │  │  └──────────────────────────┘   │
│  └────────────────────┘  │                                  │
└──────────────────────────┴──────────────────────────────────┘

[PAYMENT MODAL — overlay]
[RECEIPT MODAL — overlay]
```

### Responsive Breakpoint

At `max-width: 767px`, the two-column grid collapses to a single column with the Barcode_Scanner and Product_Search_Panel stacked above the Cart_Panel.

---

## CSS Design Tokens (`pos.css`)

```css
:root {
  /* Colors */
  --color-bg:          #f4f6f9;
  --color-surface:     #ffffff;
  --color-border:      #d1d5db;
  --color-text:        #111827;
  --color-text-muted:  #6b7280;
  --color-primary:     #2563eb;   /* Blue — primary actions */
  --color-primary-hover: #1d4ed8;
  --color-danger:      #dc2626;   /* Red — remove, errors */
  --color-success:     #16a34a;   /* Green — change due, confirmations */
  --color-accent:      #f59e0b;   /* Amber — grand total highlight */
  --color-overlay:     rgba(0,0,0,0.5);

  /* Typography */
  --font-base:   16px;
  --font-price:  20px;
  --font-total:  24px;
  --font-family: 'Segoe UI', system-ui, sans-serif;

  /* Spacing */
  --space-xs:  4px;
  --space-sm:  8px;
  --space-md:  16px;
  --space-lg:  24px;
  --space-xl:  32px;

  /* Touch targets */
  --btn-min-height: 44px;
  --btn-min-width:  44px;
  --btn-radius:     8px;
}
```

---

## API Integration

### Endpoints Used

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/auth` | Login — obtain JWT_Token |
| `GET`  | `/productos?nombre=&subcategoria=&estado=activo&page=1&limit=20` | Search products |
| `GET`  | `/productos?estado=activo&limit=200` | Load all active products for category list |

> No write endpoints are called by the POS frontend. Cart, payment, and receipt data are managed entirely in-memory. A future integration with a sales/orders API can be added without changing the existing product API.

### Error Handling Strategy

| HTTP Status | Handling |
|-------------|----------|
| 200 | Parse JSON, render results |
| 401 | Clear token, redirect to `/login.html` |
| 403 | Show "Inactive user" message |
| 404 | Show "Not found" inline message |
| 5xx | Show "Server error. Try again." inline message |
| Network error | Show "Connection error." inline message |

---

## Security Considerations

- The JWT_Token is read from `localStorage` and injected only into same-origin API requests.
- No sensitive data (card numbers, PINs) is collected or stored — the "Card" payment method records only the amount, not card details.
- All user-supplied search strings are passed as URL query parameters (not interpolated into HTML) to prevent XSS.
- Receipt content is rendered using `textContent` assignments, not `innerHTML`, for all dynamic data fields.

---

## Correctness Properties

### Property 1: Cart Total Invariant
For any sequence of add, remove, and quantity-change operations, `CartStore.grandTotal` must equal `CartStore.subtotal - CartStore.totalDiscount`, and must never be negative.

**Test approach:** Property-based test — generate random sequences of cart mutations and assert the invariant holds after each step.

### Property 2: Discount Bounds
For any Cart_Item with a percentage discount `d`, the discounted line total must satisfy `0 ≤ lineTotal ≤ unitPrice * quantity`. For a fixed discount `f`, `lineTotal = max(0, unitPrice * quantity - f)`.

**Test approach:** Property-based test — generate random products and discount values (including boundary values 0, 100, and values > 100) and assert bounds.

### Property 3: Payment Coverage
For Cash mode, `Confirm Payment` is enabled if and only if `cashAmount >= grandTotal`. For Mixed mode, it is enabled if and only if `cashAmount + cardAmount >= grandTotal`.

**Test approach:** Example-based tests covering exact-match, over-payment, and under-payment cases.

### Property 4: Change Due Calculation
For Cash and Mixed payments, `changeDue = (cashAmount + cardAmount) - grandTotal`, and `changeDue >= 0` whenever payment is confirmed.

**Test approach:** Example-based tests with representative values.

### Property 5: Cart Item Idempotence of Clear
After `CartStore.clearCart()`, `CartStore.items.length === 0`, `CartStore.grandTotal === 0`, and `CartStore.orderDiscount === null`.

**Test approach:** Unit test — populate cart with multiple items and discounts, call `clearCart()`, assert all fields reset.
