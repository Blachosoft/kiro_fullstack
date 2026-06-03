# Workshop: Spec-Driven Development (SDD) — Supermarket POS Sales API

## Course: Software Coding and Testing

### Objective

In this workshop, you will use **Kiro AWS** to apply **Spec-Driven Development (SDD)**. You will write a detailed prompt describing the specifications for a **Sales REST API** for a supermarket POS system, and Kiro will generate the three spec files (`requirements.md`, `design.md`, `tasks.md`) from your description. Then, Kiro will implement the API based on those specs.

Your job is to **write the best possible specification prompt** — clear, complete, and unambiguous — so that Kiro produces high-quality specs and code.

---

## What is Spec-Driven Development?

SDD is a development approach where:

1. You describe **what** the system should do in natural language
2. Kiro generates structured spec files (requirements, design, tasks)
3. You **review and refine** the generated specs
4. Kiro implements the code following those specs

The quality of the final product depends directly on the quality of your initial description.

---

## Prerequisites

- Kiro IDE installed and configured
- Java 17+ (JDK) installed
- Maven 3.9+ or Gradle 8+ installed
- Postman, Insomnia, or `curl` for API testing
- Basic knowledge of Spring Boot and REST APIs

---

## Workshop Steps

### Step 1: Understand the Business Domain

Read the **Sales API Specification** section below carefully. This is the system you need to describe to Kiro.

### Step 2: Write Your Specification Prompt

Open Kiro and create a new spec by clicking on the Specs panel. When Kiro asks what you want to build, write a **detailed description** of the Sales API.

Your prompt must cover:
- What the API does (sales management for a supermarket POS)
- What external services it depends on (Product API, Customer API)
- Every feature: sale creation, payment types, returns, cancellations, sale freezing
- Business rules and constraints
- Error scenarios
- Tech stack

**This is the core deliverable** — your ability to communicate specifications clearly to an AI agent.

### Step 3: Let Kiro Generate the Spec Files

Kiro will generate three files:
- `requirements.md` — User stories and acceptance criteria
- `design.md` — Architecture, components, data model
- `tasks.md` — Ordered implementation steps

### Step 4: Review and Refine

Read each generated spec file critically:
- Are all features covered?
- Are the acceptance criteria testable?
- Is the data model complete?
- Are edge cases addressed?

If something is missing or wrong, **tell Kiro to fix it**. Iterate until the specs are solid.

### Step 5: Let Kiro Implement

Once the specs are approved, let Kiro execute the tasks one by one. Review the generated code after each task.

### Step 6: Validate with Tests

Verify that the generated test suite covers the business rules. Run the tests and check coverage.

---

## Sales API Specification

This is the business context you must communicate to Kiro. Read it, understand it, and then **rewrite it in your own words** as a prompt for Kiro.

> **Important:** Do NOT copy-paste this section into Kiro. You must write your own description. The evaluation measures your ability to communicate specifications effectively.

### System Context

The supermarket already has two existing APIs:
- **Product API** — Manages the product catalog (CRUD, stock, pricing). Your API consumes it to search and retrieve product information.
- **Customer API** — Manages customer records (name, ID, credit status). Your API consumes it to search and retrieve customer information.

Your API is the **Sales API**. It handles everything related to sales transactions at the POS terminal.

### Feature 1: Product Search (via external API)

The Sales API must allow cashiers to search for products by calling the external Product API.

- Search products by name (partial match, case-insensitive)
- Search products by barcode
- The response must include: product ID, name, barcode, unit price, available stock, and category
- If the Product API is unavailable, return a `503 Service Unavailable` with a clear message

### Feature 2: Customer Search (via external API)

The Sales API must allow cashiers to search for customers by calling the external Customer API.

- Search customers by name (partial match)
- Search customers by document number (exact match)
- The response must include: customer ID, full name, document type, document number, and credit status (APPROVED, REJECTED, PENDING)
- Associating a customer to a sale is optional for cash sales but **mandatory for credit sales**

### Feature 3: Sale Creation and Management

A sale represents a transaction at the POS terminal.

#### Sale Lifecycle States

```
ACTIVE → COMPLETED (checkout successful)
ACTIVE → CANCELLED (cashier cancels before checkout)
ACTIVE → FROZEN (cashier pauses the sale to attend another customer)
FROZEN → ACTIVE (cashier resumes the frozen sale)
COMPLETED → RETURNED (full return after checkout)
COMPLETED → PARTIALLY_RETURNED (partial return of specific items)
```

#### Creating a Sale

- A new sale starts in `ACTIVE` status
- The sale is associated with a POS terminal ID (sent in the request)
- Optionally associated with a customer (by customer ID)
- The sale records the cashier ID (from request header or auth token)

#### Adding Items to a Sale

- Add a product by product ID or barcode
- Each item records: product ID, product name, unit price (snapshot at time of adding), quantity, and line total
- If the product already exists in the sale, increment the quantity
- Quantity must be >= 1
- Validate that requested quantity does not exceed available stock (call Product API)
- Recalculate sale totals on every item change

#### Updating and Removing Items

- Update the quantity of an existing item
- Remove an item from the sale
- Totals are recalculated automatically

### Feature 4: Payment Types

The sale must support two payment types:

#### Cash Sale (`paymentType: CASH`)

- Customer association is optional
- The cashier enters the amount received
- Amount received must be >= sale total
- The system calculates and returns the change amount
- On completion, the sale status changes to `COMPLETED`

#### Credit Sale (`paymentType: CREDIT`)

- Customer association is **mandatory**
- The customer's credit status must be `APPROVED` (validate via Customer API)
- If credit status is not APPROVED, reject the sale with `422 Unprocessable Entity`
- Credit sales record the customer ID and a credit reference number (auto-generated)
- On completion, the sale status changes to `COMPLETED`

### Feature 5: Sale Checkout

- Checkout validates: sale has items, payment information is complete, stock is available
- On successful checkout:
  - Sale status → `COMPLETED`
  - Stock is decremented (call Product API)
  - A receipt is generated with a unique transaction ID
- If any product has insufficient stock at checkout time, return `409 Conflict` with the list of out-of-stock items

### Feature 6: Sale Cancellation

- Only `ACTIVE` or `FROZEN` sales can be cancelled
- Cancellation sets the sale status to `CANCELLED`
- Cancelled sales cannot be modified or completed
- A cancellation reason is required (free text, max 255 characters)
- No stock changes occur (items were never checked out)

### Feature 7: Sale Freezing (Hold)

Freezing allows a cashier to pause a sale and start a new one (e.g., customer forgot their wallet, needs to get another item).

- Only `ACTIVE` sales can be frozen
- Freezing sets the sale status to `FROZEN`
- A frozen sale retains all its items and totals
- The cashier can resume a frozen sale, which sets it back to `ACTIVE`
- A POS terminal can have multiple frozen sales
- Frozen sales are listed by terminal ID so the cashier can pick which one to resume
- Frozen sales expire after a configurable time (default: 2 hours) and are automatically cancelled

### Feature 8: Returns

Returns are processed after a sale has been completed.

#### Full Return

- All items in the sale are returned
- Sale status changes to `RETURNED`
- Stock is incremented for all returned items (call Product API)
- A return reason is required
- A return receipt is generated referencing the original transaction ID

#### Partial Return

- Specific items and quantities are selected for return
- Sale status changes to `PARTIALLY_RETURNED`
- Only the returned items have their stock incremented
- Returned quantity cannot exceed the originally purchased quantity
- A return reason is required per item
- A return receipt is generated with only the returned items

#### Return Rules

- Only `COMPLETED` sales can be returned
- A sale can only be returned once (no return of a return)
- `PARTIALLY_RETURNED` sales can receive additional partial returns (for remaining items)
- Returns for credit sales generate a credit note instead of a cash refund

### Feature 9: Sale Totals and Calculations

- **Subtotal** = sum of all (unit price × quantity)
- **Tax** = subtotal × tax rate (configurable, default: 19%)
- **Discount** = optional, percentage or fixed amount (same rules as POS frontend workshop)
- **Total** = subtotal + tax − discount
- All monetary values use `BigDecimal` with 2 decimal precision
- All calculations use integer arithmetic internally (cents) to avoid floating-point errors

### Feature 10: Receipts

- Generated automatically on successful checkout
- Contains: store name, terminal ID, cashier ID, date/time, customer info (if present), all items with prices, subtotal, tax, discount, total, payment method, amount received (cash), change (cash), transaction ID
- Return receipts reference the original transaction ID and list only returned items

---

## Tech Stack (Required)

| Component | Technology |
|-----------|------------|
| Language | Java 17+ |
| Framework | Spring Boot 3.x |
| Build Tool | Maven or Gradle |
| Database | H2 (dev/test), PostgreSQL-compatible schema |
| ORM | Spring Data JPA / Hibernate |
| Validation | Jakarta Bean Validation |
| HTTP Client | RestTemplate or WebClient (for external API calls) |
| Testing | JUnit 5, Mockito, Spring Boot Test |
| Coverage | JaCoCo |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| External APIs | Mock with WireMock or MockRestServiceServer in tests |

---

## Testing Requirements

### Unit Tests

- Test each service method in isolation using Mockito
- Mock external API calls (Product API, Customer API)
- Cover all business rules: payment type validation, credit status check, stock validation, return quantity limits
- Test state transitions: ACTIVE → COMPLETED, ACTIVE → FROZEN → ACTIVE, COMPLETED → RETURNED
- Test edge cases: empty sale checkout, return exceeding purchased quantity, freeze expired sale, credit sale without customer

### Integration Tests

- Use `@SpringBootTest` with H2 database
- Mock external APIs with WireMock
- Test complete flows:
  - Cash sale: create → add items → checkout → verify receipt
  - Credit sale: create → associate customer → add items → checkout → verify credit reference
  - Freeze flow: create → add items → freeze → resume → checkout
  - Return flow: complete sale → full return → verify stock restored
  - Partial return: complete sale → return 2 of 5 items → verify partial stock restore
  - Cancellation: create → add items → cancel → verify cannot modify

### Test Coverage

- Minimum **80% line coverage** across the project
- Service layer must have **90%+ coverage**
- Use JaCoCo for coverage reporting

---

## Evaluation Criteria

| Criteria | Weight |
|----------|--------|
| Quality of the specification prompt written for Kiro | 25% |
| Generated spec files quality (after your review and refinement) | 15% |
| Working API implementation (all endpoints functional) | 20% |
| Test suite quality and coverage (≥80%) | 30% |
| Code quality (clean architecture, error handling, naming) | 10% |

---

## Deliverables

1. **Your specification prompt** — The text you wrote and gave to Kiro (save it as `my-prompt.txt`)
2. **The three spec files** generated by Kiro inside `.kiro/specs/pos-api/`
3. **The working Spring Boot REST API** generated by Kiro
4. **Test suite** with JaCoCo report showing ≥80% coverage
5. **Short reflection** (max 1 page) answering:
   - What did you have to clarify or fix in the generated specs?
   - How did the quality of your prompt affect the generated code?
   - What would you do differently next time?

---

## Tips

- Be specific about state transitions — if you don't specify them, Kiro will guess
- Explicitly mention error codes and error messages for each failure scenario
- Describe the external API contracts (what you send, what you expect back) so Kiro can mock them properly
- Don't forget to mention that monetary calculations must use BigDecimal
- Specify which fields are required vs optional for each request
- Think about what happens in edge cases: What if the Product API is down? What if a customer has no credit? What if someone tries to return an already returned sale?
- After Kiro generates the specs, read them as if you were a developer who knows nothing about the project — are they clear enough?

---

*Workshop designed for the Software Coding and Testing course.*
