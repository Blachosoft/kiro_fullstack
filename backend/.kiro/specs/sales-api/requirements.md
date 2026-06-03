# Requirements Document

## Introduction

The Sales API is a REST API for a supermarket Point of Sale (POS) system built with Java 17, Spring Boot 3.x, Spring Data JPA, and PostgreSQL. The API manages the complete sales transaction lifecycle including product and customer lookups via external microservices, item management, multiple payment types, sale state transitions, freezing/resuming sales, and processing returns. The system must handle real-time stock validation, credit status verification, and maintain financial accuracy using BigDecimal arithmetic.

## Glossary

- **Sales_API**: The REST API system being specified that manages sales transactions
- **Product_API**: External microservice that provides product catalog, stock, and pricing information
- **Customer_API**: External microservice that provides customer information and credit status
- **Sale**: A transaction record representing a purchase at a POS terminal
- **Sale_Item**: A line item within a sale representing a product, quantity, and price
- **POS_Terminal**: A physical point-of-sale terminal identified by a unique terminal ID
- **Cashier**: The user operating the POS terminal, identified by a cashier ID
- **Payment_Processor**: Component that handles cash and credit payment processing
- **Receipt_Generator**: Component that generates transaction receipts
- **Stock_Validator**: Component that validates product availability via Product_API
- **Credit_Validator**: Component that validates customer credit status via Customer_API
- **Sale_State**: The current status of a sale (ACTIVE, COMPLETED, CANCELLED, FROZEN, RETURNED, PARTIALLY_RETURNED)
- **Freeze_Manager**: Component that manages frozen sales and their expiration
- **Return_Processor**: Component that handles full and partial returns

## Requirements

### Requirement 1: External Product Search Integration

**User Story:** As a cashier, I want to search for products using the Product API, so that I can add items to a sale with accurate pricing and stock information.

#### Acceptance Criteria

1. WHEN a product search by name is requested, THE Sales_API SHALL call the Product_API with the name parameter and return matching products with partial case-insensitive matching
2. WHEN a product search by barcode is requested, THE Sales_API SHALL call the Product_API with the barcode parameter and return the matching product
3. THE Sales_API SHALL return product information including product ID, name, barcode, unit price, available stock, and category
4. IF the Product_API returns a connection error or timeout, THEN THE Sales_API SHALL return HTTP 503 with message "Product service unavailable"
5. IF the Product_API returns an error response, THEN THE Sales_API SHALL return HTTP 502 with message "Product service error"

### Requirement 2: External Customer Search Integration

**User Story:** As a cashier, I want to search for customers using the Customer API, so that I can associate customers with sales and validate credit eligibility.

#### Acceptance Criteria

1. WHEN a customer search by name is requested, THE Sales_API SHALL call the Customer_API with the name parameter and return matching customers with partial matching
2. WHEN a customer search by document number is requested, THE Sales_API SHALL call the Customer_API with the document number parameter and return the exact matching customer
3. THE Sales_API SHALL return customer information including customer ID, full name, document type, document number, and credit status
4. THE Sales_API SHALL support credit status values of APPROVED, REJECTED, and PENDING
5. IF the Customer_API returns a connection error or timeout, THEN THE Sales_API SHALL return HTTP 503 with message "Customer service unavailable"

### Requirement 3: Sale Creation

**User Story:** As a cashier, I want to create a new sale, so that I can begin processing a customer transaction.

#### Acceptance Criteria

1. WHEN a sale creation is requested, THE Sales_API SHALL create a sale with status ACTIVE
2. THE Sales_API SHALL require terminal ID in the sale creation request
3. THE Sales_API SHALL require cashier ID in the sale creation request
4. THE Sales_API SHALL allow optional customer ID in the sale creation request
5. THE Sales_API SHALL initialize the sale with zero items, zero subtotal, zero tax, zero discount, and zero total
6. THE Sales_API SHALL assign a unique sale ID to each created sale
7. THE Sales_API SHALL record the creation timestamp for each sale

### Requirement 4: Add Items to Sale

**User Story:** As a cashier, I want to add products to an active sale, so that I can build the customer's purchase.

#### Acceptance Criteria

1. WHEN an item is added to a sale with status ACTIVE, THE Sales_API SHALL create a sale item with product ID, product name, unit price, quantity, and line total
2. WHEN an item is added for a product that already exists in the sale, THE Sales_API SHALL increment the quantity of the existing sale item
3. THE Sales_API SHALL require quantity to be greater than or equal to 1
4. WHEN an item is added, THE Sales_API SHALL call the Product_API to retrieve current unit price and available stock
5. IF the requested quantity exceeds available stock, THEN THE Sales_API SHALL return HTTP 409 with message "Insufficient stock for product"
6. WHEN an item is added or updated, THE Sales_API SHALL recalculate subtotal, tax, discount, and total
7. IF the sale status is not ACTIVE, THEN THE Sales_API SHALL return HTTP 422 with message "Cannot modify sale in current state"

### Requirement 5: Update and Remove Sale Items

**User Story:** As a cashier, I want to update quantities or remove items from an active sale, so that I can correct mistakes or handle customer changes.

#### Acceptance Criteria

1. WHEN a sale item quantity is updated in a sale with status ACTIVE, THE Sales_API SHALL update the quantity and recalculate the line total
2. WHEN a sale item quantity is updated to a value less than 1, THEN THE Sales_API SHALL return HTTP 400 with message "Quantity must be at least 1"
3. WHEN a sale item is removed from a sale with status ACTIVE, THE Sales_API SHALL delete the sale item and recalculate sale totals
4. WHEN sale items are modified, THE Sales_API SHALL validate stock availability via Product_API
5. IF the sale status is not ACTIVE, THEN THE Sales_API SHALL return HTTP 422 with message "Cannot modify sale in current state"

### Requirement 6: Financial Calculations

**User Story:** As a system administrator, I want all financial calculations to use precise decimal arithmetic, so that monetary values are accurate and consistent.

#### Acceptance Criteria

1. THE Sales_API SHALL use BigDecimal for all monetary values including unit price, line total, subtotal, tax, discount, and total
2. THE Sales_API SHALL store all monetary values with 2 decimal places precision
3. WHEN calculating subtotal, THE Sales_API SHALL sum all line totals where line total equals unit price multiplied by quantity
4. WHEN calculating tax, THE Sales_API SHALL multiply subtotal by the tax rate
5. THE Sales_API SHALL use a default tax rate of 0.19 (19 percent)
6. WHEN calculating total, THE Sales_API SHALL compute subtotal plus tax minus discount
7. THE Sales_API SHALL round all monetary calculations to 2 decimal places using HALF_UP rounding mode

### Requirement 7: Cash Payment Processing

**User Story:** As a cashier, I want to process cash payments, so that I can complete sales transactions and provide change to customers.

#### Acceptance Criteria

1. WHEN a cash payment is processed for a sale with status ACTIVE, THE Sales_API SHALL require payment type CASH and amount received
2. THE Sales_API SHALL allow cash payments without an associated customer
3. IF the amount received is less than the sale total, THEN THE Sales_API SHALL return HTTP 422 with message "Amount received is less than sale total"
4. WHEN a cash payment is processed, THE Sales_API SHALL calculate change as amount received minus sale total
5. WHEN a cash payment is successfully processed, THE Sales_API SHALL update sale status to COMPLETED
6. WHEN a cash payment is successfully processed, THE Sales_API SHALL call Product_API to decrement stock for all sale items
7. WHEN a cash payment is successfully processed, THE Sales_API SHALL generate a receipt with transaction ID, store name, terminal ID, cashier ID, timestamp, customer info if present, all items, subtotal, tax, discount, total, payment method, amount received, and change

### Requirement 8: Credit Payment Processing

**User Story:** As a cashier, I want to process credit payments for approved customers, so that I can complete sales on credit terms.

#### Acceptance Criteria

1. WHEN a credit payment is processed for a sale with status ACTIVE, THE Sales_API SHALL require payment type CREDIT and customer ID
2. IF a credit payment is requested without a customer ID, THEN THE Sales_API SHALL return HTTP 422 with message "Customer is required for credit sales"
3. WHEN a credit payment is processed, THE Sales_API SHALL call Customer_API to retrieve the customer credit status
4. IF the customer credit status is not APPROVED, THEN THE Sales_API SHALL return HTTP 422 with message "Customer credit is not approved"
5. WHEN a credit payment is successfully processed, THE Sales_API SHALL generate a unique credit reference number
6. WHEN a credit payment is successfully processed, THE Sales_API SHALL update sale status to COMPLETED
7. WHEN a credit payment is successfully processed, THE Sales_API SHALL call Product_API to decrement stock for all sale items
8. WHEN a credit payment is successfully processed, THE Sales_API SHALL generate a receipt with transaction ID and credit reference number

### Requirement 9: Sale Checkout Validation

**User Story:** As a system, I want to validate all conditions before completing a sale, so that only valid transactions are processed.

#### Acceptance Criteria

1. WHEN a sale checkout is requested, THE Sales_API SHALL verify the sale has at least one item
2. IF a sale has no items at checkout, THEN THE Sales_API SHALL return HTTP 422 with message "Sale must have at least one item"
3. WHEN a sale checkout is requested, THE Sales_API SHALL verify payment information is complete
4. WHEN a sale checkout is requested, THE Sales_API SHALL call Product_API to verify current stock availability for all items
5. IF any item has insufficient stock at checkout, THEN THE Sales_API SHALL return HTTP 409 with a list of out-of-stock products
6. WHEN a sale checkout is successful, THE Sales_API SHALL assign a unique transaction ID to the sale

### Requirement 10: Sale Cancellation

**User Story:** As a cashier, I want to cancel a sale before checkout, so that I can abort transactions that cannot be completed.

#### Acceptance Criteria

1. WHEN a sale cancellation is requested for a sale with status ACTIVE, THE Sales_API SHALL update the sale status to CANCELLED
2. WHEN a sale cancellation is requested for a sale with status FROZEN, THE Sales_API SHALL update the sale status to CANCELLED
3. THE Sales_API SHALL require a cancellation reason with maximum length of 255 characters
4. IF a cancellation is requested for a sale with status COMPLETED, RETURNED, or PARTIALLY_RETURNED, THEN THE Sales_API SHALL return HTTP 422 with message "Cannot cancel sale in current state"
5. WHEN a sale is cancelled, THE Sales_API SHALL not modify product stock levels
6. WHEN a sale is cancelled, THE Sales_API SHALL record the cancellation timestamp and reason

### Requirement 11: Sale Freezing

**User Story:** As a cashier, I want to freeze an active sale, so that I can pause it and serve another customer.

#### Acceptance Criteria

1. WHEN a sale freeze is requested for a sale with status ACTIVE, THE Sales_API SHALL update the sale status to FROZEN
2. WHEN a sale is frozen, THE Sales_API SHALL record the freeze timestamp
3. WHEN a sale is frozen, THE Sales_API SHALL preserve all sale items and totals
4. IF a freeze is requested for a sale with status other than ACTIVE, THEN THE Sales_API SHALL return HTTP 422 with message "Only active sales can be frozen"
5. THE Sales_API SHALL allow multiple frozen sales per terminal ID

### Requirement 12: Sale Resumption

**User Story:** As a cashier, I want to resume a frozen sale, so that I can continue processing it after serving another customer.

#### Acceptance Criteria

1. WHEN a sale resume is requested for a sale with status FROZEN, THE Sales_API SHALL update the sale status to ACTIVE
2. WHEN a sale is resumed, THE Sales_API SHALL preserve all sale items and totals
3. IF a resume is requested for a sale with status other than FROZEN, THEN THE Sales_API SHALL return HTTP 422 with message "Only frozen sales can be resumed"
4. WHEN a sale is resumed, THE Sales_API SHALL record the resume timestamp

### Requirement 13: Frozen Sale Listing

**User Story:** As a cashier, I want to list all frozen sales for my terminal, so that I can select which sale to resume.

#### Acceptance Criteria

1. WHEN frozen sales are requested for a terminal ID, THE Sales_API SHALL return all sales with status FROZEN for that terminal
2. THE Sales_API SHALL return frozen sale information including sale ID, customer name if present, item count, total amount, and freeze timestamp
3. THE Sales_API SHALL order frozen sales by freeze timestamp in descending order

### Requirement 14: Frozen Sale Expiration

**User Story:** As a system administrator, I want frozen sales to expire automatically, so that terminals are not cluttered with abandoned sales.

#### Acceptance Criteria

1. THE Sales_API SHALL use a configurable freeze expiration timeout with default value of 2 hours
2. WHEN a frozen sale exceeds the expiration timeout, THE Sales_API SHALL automatically update the sale status to CANCELLED
3. WHEN a frozen sale is automatically cancelled, THE Sales_API SHALL record cancellation reason as "Frozen sale expired"
4. THE Sales_API SHALL check for expired frozen sales periodically

### Requirement 15: Full Return Processing

**User Story:** As a cashier, I want to process full returns for completed sales, so that customers can return all items from a purchase.

#### Acceptance Criteria

1. WHEN a full return is requested for a sale with status COMPLETED, THE Sales_API SHALL update the sale status to RETURNED
2. THE Sales_API SHALL require a return reason with maximum length of 255 characters
3. WHEN a full return is processed, THE Sales_API SHALL call Product_API to increment stock for all sale items
4. WHEN a full return is processed, THE Sales_API SHALL generate a return receipt referencing the original transaction ID
5. IF a full return is requested for a sale with status other than COMPLETED, THEN THE Sales_API SHALL return HTTP 422 with message "Only completed sales can be returned"
6. WHEN a full return is processed, THE Sales_API SHALL record the return timestamp and reason

### Requirement 16: Partial Return Processing

**User Story:** As a cashier, I want to process partial returns for completed sales, so that customers can return specific items from a purchase.

#### Acceptance Criteria

1. WHEN a partial return is requested for a sale with status COMPLETED, THE Sales_API SHALL update the sale status to PARTIALLY_RETURNED
2. WHEN a partial return is requested for a sale with status PARTIALLY_RETURNED, THE Sales_API SHALL process the return for the specified items
3. THE Sales_API SHALL require return items with product ID, quantity, and return reason
4. IF a return quantity exceeds the originally purchased quantity minus previously returned quantity, THEN THE Sales_API SHALL return HTTP 422 with message "Return quantity exceeds available quantity"
5. WHEN a partial return is processed, THE Sales_API SHALL call Product_API to increment stock only for the returned items
6. WHEN a partial return is processed, THE Sales_API SHALL generate a return receipt with only the returned items
7. WHEN a partial return is processed, THE Sales_API SHALL record the return timestamp and reason per item

### Requirement 17: Return Business Rules

**User Story:** As a system administrator, I want return processing to follow business rules, so that returns are handled consistently and correctly.

#### Acceptance Criteria

1. THE Sales_API SHALL allow returns only for sales with status COMPLETED or PARTIALLY_RETURNED
2. THE Sales_API SHALL prevent returns of sales with status RETURNED
3. WHEN a credit sale is returned, THE Sales_API SHALL generate a credit note instead of processing a cash refund
4. WHEN a cash sale is returned, THE Sales_API SHALL indicate cash refund in the return receipt
5. THE Sales_API SHALL track all return transactions with unique return IDs

### Requirement 18: Receipt Generation

**User Story:** As a cashier, I want receipts to be generated automatically, so that customers receive transaction documentation.

#### Acceptance Criteria

1. WHEN a sale is completed, THE Receipt_Generator SHALL generate a receipt with store name, terminal ID, cashier ID, date and time, customer information if present, all sale items with unit price and quantity, subtotal, tax, discount, total, payment method, and transaction ID
2. WHEN a cash sale is completed, THE Receipt_Generator SHALL include amount received and change in the receipt
3. WHEN a credit sale is completed, THE Receipt_Generator SHALL include credit reference number in the receipt
4. WHEN a return is processed, THE Receipt_Generator SHALL generate a return receipt referencing the original transaction ID
5. WHEN a return is processed, THE Receipt_Generator SHALL include only returned items in the return receipt

### Requirement 19: Data Validation

**User Story:** As a developer, I want all API inputs to be validated, so that invalid data is rejected with clear error messages.

#### Acceptance Criteria

1. THE Sales_API SHALL use Jakarta Bean Validation annotations for all request DTOs
2. WHEN a request contains null values for required fields, THE Sales_API SHALL return HTTP 400 with field-specific error messages
3. WHEN a request contains invalid values, THE Sales_API SHALL return HTTP 400 with validation error details
4. THE Sales_API SHALL validate quantity values are greater than or equal to 1
5. THE Sales_API SHALL validate monetary values are non-negative
6. THE Sales_API SHALL validate string fields do not exceed maximum length constraints

### Requirement 20: API Documentation

**User Story:** As a developer, I want comprehensive API documentation, so that I can understand and integrate with the Sales API.

#### Acceptance Criteria

1. THE Sales_API SHALL include SpringDoc OpenAPI integration
2. THE Sales_API SHALL expose Swagger UI at the /swagger-ui.html endpoint
3. THE Sales_API SHALL document all endpoints with descriptions, parameters, request bodies, and response schemas
4. THE Sales_API SHALL document all error responses with status codes and example messages
5. THE Sales_API SHALL include example requests and responses for all endpoints

### Requirement 21: Database Configuration

**User Story:** As a developer, I want flexible database configuration, so that I can use H2 for development and PostgreSQL for production.

#### Acceptance Criteria

1. THE Sales_API SHALL support H2 database for development and test profiles
2. THE Sales_API SHALL support PostgreSQL database for production profile
3. THE Sales_API SHALL use Spring Data JPA for database operations
4. THE Sales_API SHALL define JPA entities for Sale, Sale_Item, and related domain objects
5. THE Sales_API SHALL use Hibernate as the JPA implementation
6. THE Sales_API SHALL configure database connection properties via application properties files

### Requirement 22: HTTP Client Configuration

**User Story:** As a developer, I want HTTP clients configured for external API calls, so that the Sales API can communicate with Product API and Customer API.

#### Acceptance Criteria

1. THE Sales_API SHALL use RestTemplate or WebClient for HTTP client operations
2. THE Sales_API SHALL configure base URLs for Product_API and Customer_API via application properties
3. THE Sales_API SHALL configure connection timeout and read timeout for external API calls
4. THE Sales_API SHALL handle connection exceptions and map them to appropriate HTTP status codes
5. THE Sales_API SHALL log all external API requests and responses for debugging

### Requirement 23: Error Handling

**User Story:** As a developer, I want consistent error handling, so that API consumers receive clear and actionable error messages.

#### Acceptance Criteria

1. THE Sales_API SHALL use a global exception handler for all controllers
2. WHEN a validation error occurs, THE Sales_API SHALL return HTTP 400 with validation details
3. WHEN a business rule violation occurs, THE Sales_API SHALL return HTTP 422 with a descriptive message
4. WHEN a resource conflict occurs, THE Sales_API SHALL return HTTP 409 with conflict details
5. WHEN an external service is unavailable, THE Sales_API SHALL return HTTP 503 with service name
6. WHEN an unexpected error occurs, THE Sales_API SHALL return HTTP 500 with a generic error message and log the full exception

### Requirement 24: Unit Testing

**User Story:** As a developer, I want comprehensive unit tests, so that business logic is verified in isolation.

#### Acceptance Criteria

1. THE Sales_API SHALL include unit tests for all service classes using JUnit 5 and Mockito
2. THE Sales_API SHALL mock all external dependencies including Product_API and Customer_API clients
3. THE Sales_API SHALL test all payment type validation rules
4. THE Sales_API SHALL test all sale state transitions
5. THE Sales_API SHALL test all financial calculation methods
6. THE Sales_API SHALL test all error conditions and edge cases
7. THE Sales_API SHALL achieve minimum 90 percent line coverage for service layer

### Requirement 25: Integration Testing

**User Story:** As a developer, I want integration tests, so that complete workflows are verified end-to-end.

#### Acceptance Criteria

1. THE Sales_API SHALL include integration tests using Spring Boot Test with H2 database
2. THE Sales_API SHALL mock external APIs using WireMock or MockRestServiceServer
3. THE Sales_API SHALL test complete cash sale workflow from creation to checkout
4. THE Sales_API SHALL test complete credit sale workflow with customer validation
5. THE Sales_API SHALL test freeze and resume workflow
6. THE Sales_API SHALL test full return workflow with stock restoration
7. THE Sales_API SHALL test partial return workflow
8. THE Sales_API SHALL test sale cancellation workflow
9. THE Sales_API SHALL achieve minimum 80 percent line coverage across the entire project

### Requirement 26: Test Coverage Reporting

**User Story:** As a developer, I want test coverage reports, so that I can identify untested code.

#### Acceptance Criteria

1. THE Sales_API SHALL use JaCoCo for test coverage reporting
2. THE Sales_API SHALL generate coverage reports in HTML format
3. THE Sales_API SHALL configure JaCoCo to fail the build if coverage falls below 80 percent
4. THE Sales_API SHALL exclude configuration classes and DTOs from coverage requirements
