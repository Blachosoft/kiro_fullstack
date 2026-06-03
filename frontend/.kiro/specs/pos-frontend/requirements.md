# Requirements Document

## Introduction

This document defines the requirements for the **Supermarket Point of Sale (POS) Frontend**, a browser-based application that enables supermarket cashiers to process customer purchases efficiently. The application integrates with the existing Spring Boot backend (JWT-authenticated REST API at `/productos` and `/auth`) and provides a complete checkout workflow: product search, barcode scanning, shopping cart management, discount application, multi-method payment processing, and receipt generation.

The interface must be optimized for speed and clarity in a cashier environment — large touch-friendly buttons, high-contrast text, and minimal navigation steps.

---

## Glossary

- **POS_App**: The supermarket Point of Sale frontend application described in this document.
- **Cashier**: The supermarket employee who operates the POS_App to process customer purchases.
- **Product**: An item available for sale, identified by `id`, `nombre` (name), `descripcion`, `subcategoria` (category), `precio` (unit price), `precioxcantidad` (bulk price), and `estado` (active/inactive).
- **Barcode**: A numeric or alphanumeric string that uniquely identifies a Product. Mapped to the product `id` field for lookup purposes.
- **Cart**: The in-memory collection of Cart_Items representing the current customer's purchase in progress.
- **Cart_Item**: A single entry in the Cart containing a Product reference, quantity, unit price, applied discount, and line total.
- **Discount**: A percentage or fixed-amount reduction applied either to a single Cart_Item or to the Cart total.
- **Payment**: A financial transaction that settles the Cart total. Supports cash, card, and mixed (partial cash + partial card) methods.
- **Receipt**: A formatted summary of the completed transaction including itemized Cart_Items, discounts, payment breakdown, and change due.
- **Product_Search_Panel**: The UI section where the Cashier searches for products by name or category.
- **Barcode_Scanner**: The UI component that accepts barcode input via keyboard entry or device camera.
- **Cart_Panel**: The UI section displaying current Cart_Items and totals.
- **Payment_Modal**: The UI overlay that guides the Cashier through the payment flow.
- **Receipt_Modal**: The UI overlay that displays the Receipt after a successful payment.
- **API**: The existing Spring Boot REST backend accessible at the same origin, secured with JWT Bearer tokens.
- **JWT_Token**: The JSON Web Token stored in `localStorage` under the key `access_token`, used to authenticate all API requests.
- **Session**: An authenticated period beginning after successful login and ending on logout or token expiry.

---

## Requirements

### Requirement 1: Cashier Authentication

**User Story:** As a Cashier, I want to log in with my credentials, so that I can access the POS_App securely and the system can track who processed each transaction.

#### Acceptance Criteria

1. THE POS_App SHALL display a login form with fields for username and password before granting access to any POS functionality.
2. WHEN the Cashier submits valid credentials, THE POS_App SHALL authenticate against the `/auth` endpoint, store the returned JWT_Token in `localStorage`, and redirect to the main POS screen.
3. WHEN the Cashier submits invalid credentials, THE POS_App SHALL display an error message reading "Incorrect credentials. Please try again." without clearing the username field.
4. WHEN the API returns HTTP 403 during login, THE POS_App SHALL display an error message reading "Inactive user. Contact your administrator." and prevent access.
5. WHILE a Session is active, THE POS_App SHALL include the JWT_Token as a Bearer token in the `Authorization` header of every API request.
6. WHEN the API returns HTTP 401 on any request, THE POS_App SHALL clear the JWT_Token, display a session-expired notification, and redirect the Cashier to the login form.
7. THE POS_App SHALL provide a logout button that clears the JWT_Token and returns the Cashier to the login form.
8. WHEN the POS_App loads and a valid JWT_Token exists in `localStorage`, THE POS_App SHALL skip the login form and navigate directly to the main POS screen.

---

### Requirement 2: Product Search by Name

**User Story:** As a Cashier, I want to search for products by name, so that I can quickly find items to add to the Cart when a barcode is unavailable.

#### Acceptance Criteria

1. THE Product_Search_Panel SHALL display a text input field labeled "Search by name" that is focused automatically when the main POS screen loads.
2. WHEN the Cashier types at least 1 character in the name search field, THE Product_Search_Panel SHALL query the API at `GET /productos?nombre={term}&estado=activo` and display matching results within 500 ms of the last keystroke (debounced at 300 ms).
3. WHEN the API returns matching products, THE Product_Search_Panel SHALL display each result showing product name, subcategory, and unit price.
4. WHEN the Cashier clicks a product in the search results, THE Cart_Panel SHALL add the selected product as a Cart_Item with quantity 1, or increment the quantity by 1 if the product is already in the Cart.
5. WHEN the search term returns zero results, THE Product_Search_Panel SHALL display the message "No products found for '{term}'."
6. IF the API request fails, THEN THE Product_Search_Panel SHALL display "Could not load products. Check your connection." and retain the previous results.
7. WHEN the Cashier clears the search field, THE Product_Search_Panel SHALL hide the results list.

---

### Requirement 3: Product Search by Category

**User Story:** As a Cashier, I want to browse products by category, so that I can find items when I know the department but not the exact name.

#### Acceptance Criteria

1. THE Product_Search_Panel SHALL display a category filter dropdown populated with all distinct `subcategoria` values returned by `GET /productos?estado=activo`.
2. WHEN the Cashier selects a category from the dropdown, THE Product_Search_Panel SHALL query `GET /productos?subcategoria={category}&estado=activo` and display all matching products.
3. WHEN both a name search term and a category filter are active simultaneously, THE Product_Search_Panel SHALL query `GET /productos?nombre={term}&subcategoria={category}&estado=activo` and display the combined filtered results.
4. WHEN the Cashier selects the "All categories" option, THE Product_Search_Panel SHALL remove the category filter and revert to name-only search results.
5. WHEN a category filter is active and returns zero products, THE Product_Search_Panel SHALL display "No products in this category."

---

### Requirement 4: Barcode Scanning via Manual Input

**User Story:** As a Cashier, I want to enter a barcode manually using the keyboard, so that I can add products when a physical scanner is connected or when scanning by camera is not available.

#### Acceptance Criteria

1. THE Barcode_Scanner SHALL display a dedicated text input field labeled "Scan or enter barcode" that accepts numeric and alphanumeric input.
2. WHEN the Cashier presses Enter after entering a barcode value, THE Barcode_Scanner SHALL query `GET /productos?nombre={barcode}&estado=activo` and attempt to match the barcode to a Product `id`.
3. WHEN a matching active Product is found, THE Barcode_Scanner SHALL add the Product to the Cart with quantity 1, or increment the existing Cart_Item quantity by 1, and clear the barcode input field.
4. WHEN no matching Product is found for the entered barcode, THE Barcode_Scanner SHALL display the inline error "Product not found for barcode '{value}'." and highlight the input field in red.
5. WHEN the Cashier enters an empty barcode and presses Enter, THE Barcode_Scanner SHALL display the inline error "Please enter a barcode value." without making an API request.
6. IF the API request fails during barcode lookup, THEN THE Barcode_Scanner SHALL display "Barcode lookup failed. Try again." and retain the entered value in the input field.

---

### Requirement 5: Barcode Scanning via Device Camera

**User Story:** As a Cashier, I want to scan barcodes using the device camera, so that I can add products quickly without typing.

#### Acceptance Criteria

1. THE Barcode_Scanner SHALL display a "Scan with Camera" button that, when clicked, requests access to the device camera using the browser MediaDevices API.
2. WHEN camera permission is granted, THE Barcode_Scanner SHALL activate a live video preview and begin decoding barcodes in real time using a client-side barcode decoding library (e.g., ZXing or QuaggaJS).
3. WHEN a barcode is successfully decoded from the camera feed, THE Barcode_Scanner SHALL populate the barcode input field with the decoded value, trigger the same product lookup as manual entry (Requirement 4, criteria 3–6), and stop the camera stream.
4. WHEN the Cashier clicks "Stop Camera" or a barcode is successfully decoded, THE Barcode_Scanner SHALL stop the camera stream and hide the video preview.
5. IF the browser denies camera permission, THEN THE Barcode_Scanner SHALL display "Camera access denied. Use manual barcode entry." and hide the camera preview area.
6. IF the device has no camera or the MediaDevices API is unavailable, THEN THE Barcode_Scanner SHALL hide the "Scan with Camera" button entirely.

---

### Requirement 6: Shopping Cart Management

**User Story:** As a Cashier, I want to manage the shopping cart, so that I can accurately reflect the customer's intended purchase before payment.

#### Acceptance Criteria

1. THE Cart_Panel SHALL display all Cart_Items in a scrollable list, each showing: product name, unit price, quantity, line discount (if any), and line total.
2. THE Cart_Panel SHALL display a running subtotal, total discount amount, and grand total that update immediately whenever a Cart_Item is added, removed, or modified.
3. WHEN the Cashier increases the quantity of a Cart_Item using the "+" button, THE Cart_Panel SHALL increment the quantity by 1 and recalculate the line total and grand total.
4. WHEN the Cashier decreases the quantity of a Cart_Item using the "−" button and the current quantity is greater than 1, THE Cart_Panel SHALL decrement the quantity by 1 and recalculate totals.
5. WHEN the Cashier decreases the quantity of a Cart_Item to 0 using the "−" button, THE Cart_Panel SHALL remove the Cart_Item from the Cart.
6. WHEN the Cashier clicks the "Remove" button on a Cart_Item, THE Cart_Panel SHALL remove that Cart_Item from the Cart immediately.
7. WHEN the Cashier edits the quantity field of a Cart_Item directly and enters a positive integer, THE Cart_Panel SHALL update the quantity to the entered value and recalculate totals.
8. IF the Cashier enters a non-positive or non-numeric value in the quantity field, THEN THE Cart_Panel SHALL revert the field to the previous valid quantity and display an inline error "Quantity must be a positive whole number."
9. WHEN the Cart is empty, THE Cart_Panel SHALL display the message "Cart is empty. Add products to begin." and disable the "Proceed to Payment" button.
10. THE Cart_Panel SHALL display a "Clear Cart" button that, when clicked, removes all Cart_Items after the Cashier confirms the action in a confirmation dialog.

---

### Requirement 7: Discount Application

**User Story:** As a Cashier, I want to apply discounts to individual products or to the total purchase, so that I can honor promotions and authorized price adjustments.

#### Acceptance Criteria

1. THE Cart_Panel SHALL display a "Discount" button on each Cart_Item row that opens a discount input for that line item.
2. WHEN the Cashier enters a percentage discount (0–100) for a Cart_Item and confirms, THE Cart_Panel SHALL apply the discount to that line item, display the discounted line total, and update the grand total.
3. WHEN the Cashier enters a fixed-amount discount for a Cart_Item and confirms, THE Cart_Panel SHALL apply the fixed discount to that line item, ensuring the discounted line total does not fall below $0.00.
4. IF the Cashier enters a percentage discount greater than 100 or a negative value, THEN THE Cart_Panel SHALL display "Discount must be between 0% and 100%." and reject the input.
5. THE Cart_Panel SHALL display an "Order Discount" section below the item list that allows the Cashier to apply a single percentage or fixed-amount discount to the Cart subtotal.
6. WHEN an order-level discount is applied, THE Cart_Panel SHALL display the discount amount as a separate line and recalculate the grand total.
7. WHEN a Cart_Item already has a discount and the Cashier opens the discount input again, THE Cart_Panel SHALL pre-populate the input with the current discount value.
8. THE Cart_Panel SHALL display a "Remove Discount" option for each Cart_Item that has an active discount, which when clicked removes the discount and restores the original line total.

---

### Requirement 8: Checkout and Payment Processing

**User Story:** As a Cashier, I want to process payment using cash, card, or a combination of both, so that I can complete the customer's transaction accurately.

#### Acceptance Criteria

1. WHEN the Cashier clicks "Proceed to Payment" and the Cart contains at least one Cart_Item, THE Payment_Modal SHALL open displaying the grand total, a payment method selector, and payment input fields.
2. THE Payment_Modal SHALL offer three payment method options: "Cash", "Card", and "Mixed (Cash + Card)".
3. WHEN the Cashier selects "Cash", THE Payment_Modal SHALL display a single input field for the cash amount tendered and a calculated "Change Due" field that updates in real time as the Cashier types.
4. WHEN the Cashier selects "Card", THE Payment_Modal SHALL display a card amount field pre-filled with the grand total and a confirmation checkbox labeled "Card payment confirmed."
5. WHEN the Cashier selects "Mixed (Cash + Card)", THE Payment_Modal SHALL display both a cash amount field and a card amount field, and SHALL display a running "Remaining Balance" that decreases as amounts are entered, reaching $0.00 when the total is covered.
6. IF the total cash and card amounts entered in Mixed mode are less than the grand total, THEN THE Payment_Modal SHALL disable the "Confirm Payment" button and display "Total entered does not cover the purchase amount."
7. IF the Cashier enters a cash amount less than the grand total in Cash-only mode, THEN THE Payment_Modal SHALL disable the "Confirm Payment" button and display "Cash amount is insufficient."
8. WHEN the Cashier clicks "Confirm Payment" and all payment conditions are met, THE Payment_Modal SHALL close and THE Receipt_Modal SHALL open with the completed transaction details.
9. THE Payment_Modal SHALL display a "Cancel" button that closes the modal without modifying the Cart.
10. WHEN the Cashier enters a cash amount greater than the grand total in Cash or Mixed mode, THE Payment_Modal SHALL display the change due as a positive value highlighted in green.

---

### Requirement 9: Receipt Generation

**User Story:** As a Cashier, I want a receipt to be generated after payment, so that I can provide the customer with proof of purchase and close the transaction.

#### Acceptance Criteria

1. WHEN a payment is confirmed, THE Receipt_Modal SHALL display a receipt containing: transaction date and time, list of Cart_Items with quantities and line totals, applied discounts (per item and order-level), subtotal, total discount, grand total, payment method breakdown, and change due (if cash was used).
2. THE Receipt_Modal SHALL display a "Print Receipt" button that triggers the browser's print dialog scoped to the receipt content only.
3. THE Receipt_Modal SHALL display a "New Transaction" button that closes the modal, clears the Cart, resets all discounts, and returns the POS_App to the ready state with the product search focused.
4. WHEN the "New Transaction" button is clicked, THE POS_App SHALL generate and display a new unique transaction ID for the next sale.
5. THE Receipt_Modal SHALL display the cashier's username (from the active Session) on the receipt.
6. IF the browser print dialog is cancelled, THEN THE Receipt_Modal SHALL remain open so the Cashier can retry printing.

---

### Requirement 10: POS Screen Layout and Usability

**User Story:** As a Cashier, I want a clear and fast interface, so that I can process transactions without errors under time pressure.

#### Acceptance Criteria

1. THE POS_App SHALL render the main POS screen as a two-column layout: the left column containing the Product_Search_Panel and Barcode_Scanner, and the right column containing the Cart_Panel.
2. THE POS_App SHALL use a minimum font size of 16px for all interactive labels and 20px for prices and totals to ensure readability at a cashier workstation distance.
3. THE POS_App SHALL render all primary action buttons (add to cart, remove item, proceed to payment, confirm payment) with a minimum height of 44px and minimum width of 44px to meet touch-target accessibility standards.
4. WHEN the Cart_Panel contains more Cart_Items than fit in the visible area, THE Cart_Panel SHALL scroll independently without affecting the Product_Search_Panel layout.
5. THE POS_App SHALL display the current date, time, and logged-in cashier username in a persistent header bar visible on all POS screens.
6. THE POS_App SHALL update the displayed time in the header every 60 seconds.
7. WHEN the POS_App is displayed on a viewport narrower than 768px, THE POS_App SHALL stack the Product_Search_Panel above the Cart_Panel in a single-column layout.
8. THE POS_App SHALL use high-contrast color tokens: dark text on light backgrounds for product lists, and a visually distinct accent color for the grand total and primary action buttons.
