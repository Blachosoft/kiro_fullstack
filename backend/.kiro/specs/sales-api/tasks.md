# Implementation Plan

- [x] 1. Set up the Spring Boot project foundation
  - Configure Java 17, Spring Boot 3.x, Spring Data JPA, Validation, SpringDoc OpenAPI, PostgreSQL, H2, and test dependencies
  - Add application properties for development, test, and production profiles
  - Configure external Product API and Customer API base URLs, connection timeouts, and read timeouts
  - _Requirements: 20.1, 21.1, 21.2, 21.3, 21.5, 21.6, 22.1, 22.2, 22.3_

- [x] 2. Implement core domain entities and enums
  - Create Sale, SaleItem, Payment, Return, and ReturnItem JPA entities with relationships, timestamps, monetary precision, and status fields
  - Create SaleState, PaymentType, CreditStatus, and ReturnType enums
  - Ensure monetary columns use BigDecimal with 2 decimal places
  - _Requirements: 3.5, 3.6, 3.7, 6.1, 6.2, 21.4_

- [x] 3. Create repositories for persisted sales data
  - Implement Spring Data JPA repositories for Sale, SaleItem, Payment, Return, and ReturnItem
  - Add finder methods for frozen sales by terminal ID ordered by freeze timestamp descending
  - Add lookup support for returns and previously returned quantities by original sale and product
  - _Requirements: 13.1, 13.3, 16.4, 17.5, 21.3_

- [x] 4. Build request and response DTOs with validation
  - Create request DTOs for sale creation, item add/update, checkout, cancellation, full return, and partial return
  - Create response DTOs for sales, sale items, checkout, receipts, frozen sales, products, customers, and returns
  - Apply Jakarta Bean Validation annotations for required fields, quantity minimums, non-negative monetary values, and max string lengths
  - _Requirements: 3.2, 3.3, 4.3, 5.2, 10.3, 15.2, 16.3, 19.1, 19.2, 19.3, 19.4, 19.5, 19.6_

- [x] 5. Implement global error handling
  - Create business, conflict, not found, external service unavailable, and external service error exceptions
  - Add a global exception handler that maps validation errors to HTTP 400, conflicts to HTTP 409, business rule violations to HTTP 422, unavailable external services to HTTP 503, upstream errors to HTTP 502, and unexpected errors to HTTP 500
  - Return consistent error response bodies with clear messages and validation details
  - _Requirements: 1.4, 1.5, 2.5, 4.5, 4.7, 5.2, 5.5, 7.3, 8.2, 8.4, 9.2, 9.5, 10.4, 11.4, 12.3, 15.5, 16.4, 23.1, 23.2, 23.3, 23.4, 23.5, 23.6_

- [x] 6. Implement the financial calculator
  - Add BigDecimal calculations for line total, subtotal, tax, discount, total, and cash change
  - Use the default tax rate of 0.19 and HALF_UP rounding to 2 decimal places
  - Cover calculator behavior with focused unit tests
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5, 6.6, 6.7, 7.4, 24.5_

- [x] 7. Implement external Product API integration
  - Create ProductClient methods for search by name, search by barcode, product lookup by ID, stock decrement, and stock increment
  - Map product responses to internal DTOs with ID, name, barcode, unit price, available stock, and category
  - Handle connection failures, timeouts, upstream errors, and request/response logging
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 4.4, 7.6, 8.7, 15.3, 16.5, 22.4, 22.5_

- [x] 8. Implement external Customer API integration
  - Create CustomerClient methods for search by name, search by document number, and customer lookup by ID
  - Map customer responses to internal DTOs with ID, full name, document type, document number, and credit status
  - Support APPROVED, REJECTED, and PENDING credit statuses
  - Handle connection failures, timeouts, upstream errors, and request/response logging
  - _Requirements: 2.1, 2.2, 2.3, 2.4, 2.5, 8.3, 22.4, 22.5_

- [x] 9. Add product and customer search endpoints
  - Implement GET /api/products/search with name and barcode query support
  - Implement GET /api/customers/search with name and documentNumber query support
  - Return external service errors using the shared error model
  - _Requirements: 1.1, 1.2, 1.3, 1.4, 1.5, 2.1, 2.2, 2.3, 2.4, 2.5_

- [x] 10. Implement sale creation and retrieval
  - Create POST /api/sales to initialize ACTIVE sales with terminal ID, cashier ID, optional customer ID, zero totals, unique sale ID, and creation timestamp
  - Create GET /api/sales/{saleId} to return sale details, items, totals, status, and timestamps
  - Add unit tests for creation defaults and not found behavior
  - _Requirements: 3.1, 3.2, 3.3, 3.4, 3.5, 3.6, 3.7_

- [x] 11. Implement active sale item management
  - Add POST /api/sales/{saleId}/items to add products to ACTIVE sales using current Product API price and stock
  - Increment quantity when the product already exists in the sale
  - Add PUT /api/sales/{saleId}/items/{itemId} to update quantities with stock validation
  - Add DELETE /api/sales/{saleId}/items/{itemId} to remove items
  - Recalculate sale totals after every item change and reject modifications for non-ACTIVE sales
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 12. Implement checkout validation
  - Validate that the sale has at least one item before checkout
  - Validate payment information based on payment type
  - Revalidate current stock availability for all sale items and return conflict details for insufficient stock
  - Assign a unique transaction ID when checkout succeeds
  - _Requirements: 9.1, 9.2, 9.3, 9.4, 9.5, 9.6_

- [x] 13. Implement cash payment processing
  - Process CASH checkout for ACTIVE sales with amount received
  - Allow cash sales without customer information
  - Reject underpayment, calculate change, create payment records, mark sales COMPLETED, decrement stock, and generate receipts
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5, 7.6, 7.7_

- [x] 14. Implement credit payment processing
  - Process CREDIT checkout for ACTIVE sales with required customer ID
  - Validate customer credit status through Customer API
  - Reject customers whose credit status is not APPROVED
  - Generate credit reference numbers, create payment records, mark sales COMPLETED, decrement stock, and generate receipts
  - _Requirements: 8.1, 8.2, 8.3, 8.4, 8.5, 8.6, 8.7, 8.8_

- [x] 15. Implement receipt generation
  - Generate sale receipts with store name, terminal ID, cashier ID, timestamp, customer information when present, items, subtotal, tax, discount, total, payment method, and transaction ID
  - Include amount received and change for cash receipts
  - Include credit reference number for credit receipts
  - Generate return receipts referencing original transaction IDs and including only returned items
  - _Requirements: 7.7, 8.8, 15.4, 16.6, 18.1, 18.2, 18.3, 18.4, 18.5_

- [x] 16. Implement sale cancellation
  - Add POST /api/sales/{saleId}/cancel for ACTIVE and FROZEN sales
  - Require a cancellation reason of at most 255 characters
  - Record cancellation timestamp and reason without changing product stock
  - Reject cancellation for COMPLETED, RETURNED, and PARTIALLY_RETURNED sales
  - _Requirements: 10.1, 10.2, 10.3, 10.4, 10.5, 10.6_

- [x] 17. Implement sale freezing and resumption
  - Add POST /api/sales/{saleId}/freeze for ACTIVE sales and preserve all items and totals
  - Add POST /api/sales/{saleId}/resume for FROZEN sales and preserve all items and totals
  - Record freeze and resume timestamps and reject invalid state transitions
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.5, 12.1, 12.2, 12.3, 12.4_

- [x] 18. Implement frozen sale listing and expiration
  - Add GET /api/sales/frozen?terminalId=... to list frozen sales for a terminal with customer name when available, item count, total amount, and freeze timestamp
  - Order frozen sales by freeze timestamp descending
  - Add configurable frozen sale expiration timeout with a default of 2 hours
  - Add scheduled expiration that cancels expired frozen sales with reason "Frozen sale expired"
  - _Requirements: 13.1, 13.2, 13.3, 14.1, 14.2, 14.3, 14.4_

- [x] 19. Implement full return processing
  - Add POST /api/sales/{saleId}/return for completed sales
  - Require a return reason of at most 255 characters
  - Create return records with unique return IDs, mark sales RETURNED, increment stock for all original items, record return timestamp and reason, and generate return receipts
  - Reject full returns for non-COMPLETED sales
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 17.1, 17.2, 17.5_

- [x] 20. Implement partial return processing
  - Add POST /api/sales/{saleId}/partial-return for COMPLETED and PARTIALLY_RETURNED sales
  - Require return items with product ID, quantity, and reason
  - Validate return quantities against purchased quantity minus previously returned quantity
  - Create return records with unique return IDs, mark sales PARTIALLY_RETURNED as appropriate, increment stock only for returned items, record return reason per item, and generate return receipts
  - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6, 16.7, 17.1, 17.2, 17.5_

- [x] 21. Apply payment-specific return business rules
  - Generate credit notes for returned credit sales
  - Indicate cash refunds for returned cash sales
  - Prevent returns for fully RETURNED sales
  - Cover return business rules with unit tests
  - _Requirements: 17.2, 17.3, 17.4, 17.5, 24.6_

- [x] 22. Document the REST API with OpenAPI
  - Add SpringDoc configuration and expose Swagger UI at /swagger-ui.html
  - Document all endpoints with descriptions, parameters, request bodies, response schemas, error responses, and examples
  - _Requirements: 20.1, 20.2, 20.3, 20.4, 20.5_

- [x] 23. Add service-layer unit tests
  - Test all services with JUnit 5 and Mockito, mocking repositories and external API clients
  - Cover payment validation, sale state transitions, financial calculations, error conditions, and edge cases
  - Configure service-layer line coverage target of at least 90 percent
  - _Requirements: 24.1, 24.2, 24.3, 24.4, 24.5, 24.6, 24.7_

- [x] 24. Add integration tests for complete workflows
  - Configure Spring Boot Test with H2 and mocked external APIs using WireMock or MockRestServiceServer
  - Test complete cash sale, credit sale, freeze/resume, full return, partial return, and cancellation workflows
  - Verify database persistence, external API interactions, stock updates, receipts, and error responses
  - _Requirements: 25.1, 25.2, 25.3, 25.4, 25.5, 25.6, 25.7, 25.8_

- [x] 25. Configure coverage reporting and build quality gates
  - Add JaCoCo coverage reports in HTML format
  - Fail the build when overall coverage falls below 80 percent
  - Exclude configuration classes and DTOs from coverage requirements
  - Verify the full test suite and coverage thresholds pass
  - _Requirements: 25.9, 26.1, 26.2, 26.3, 26.4_
