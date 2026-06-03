# AWS Microservices Migration Plan

## Current State

The current project is a Spring Boot REST API for sales. It exposes HTTP endpoints through Spring MVC controllers and persists data with Spring Data JPA repositories.

Current runtime shape:

- One monolithic Spring Boot application: `sales-api`
- REST controllers:
  - `SaleController`
  - `ProductSearchController`
  - `CustomerSearchController`
- Persistence:
  - JPA entities: `Sale`, `SaleItem`, `Payment`, `Return`, `ReturnItem`
  - JPA repositories: `SaleRepository`, `SaleItemRepository`, `PaymentRepository`, `ReturnRepository`, `ReturnItemRepository`
  - H2 for test/dev and PostgreSQL-compatible production profile
- AWS Lambda support already started:
  - `com.supermarket.sales.aws.LambdaHandler`
  - `aws-serverless-java-container-springboot3`

This means the API is REST, but it is not yet a DynamoDB-based microservice architecture.

## Target AWS Architecture

Recommended target:

```text
API Gateway
  |
  |-- /sales/*   -> Sales Lambda
  |
  |-- /products/* -> Products Lambda
  |
  |-- /customers/* -> Customers Lambda, optional if customer API is also part of the project

Sales Lambda
  |
  |-- DynamoDB: Sales table
  |-- DynamoDB: Payments table or embedded payment document
  |-- DynamoDB: Returns table or embedded return documents
  |-- Calls Products API/Lambda for product lookup and stock updates
  |-- Calls Customers API/Lambda for credit validation

Products Lambda
  |
  |-- DynamoDB: Products table
  |-- Provides product search, product lookup, stock decrement, stock increment
```

## Microservice Split

### Sales Microservice

Owns all sales transaction behavior:

- Create sale
- Add, update, and remove sale items
- Checkout
- Cash and credit payment validation
- Freeze and resume sales
- Cancel sales
- Full and partial returns
- Receipt generation

Suggested API Gateway routes:

- `POST /sales`
- `GET /sales/{saleId}`
- `POST /sales/{saleId}/items`
- `PUT /sales/{saleId}/items/{itemId}`
- `DELETE /sales/{saleId}/items/{itemId}`
- `POST /sales/{saleId}/checkout`
- `POST /sales/{saleId}/cancel`
- `POST /sales/{saleId}/freeze`
- `POST /sales/{saleId}/resume`
- `GET /sales/frozen?terminalId=...`
- `POST /sales/{saleId}/return`
- `POST /sales/{saleId}/partial-return`

### Products Microservice

Owns product catalog and stock:

- Product search by name
- Product search by barcode
- Product lookup by ID
- Stock decrement after checkout
- Stock increment after returns

Suggested API Gateway routes:

- `GET /products/search?name=...`
- `GET /products/search?barcode=...`
- `GET /products/{productId}`
- `POST /products/{productId}/stock/decrement`
- `POST /products/{productId}/stock/increment`

## DynamoDB Table Design

### Option A: Simple Tables

This is easier for the current codebase.

#### `Sales`

Partition key:

- `saleId` string

Attributes:

- `terminalId`
- `cashierId`
- `customerId`
- `status`
- `items` list
- `subtotal`
- `tax`
- `discount`
- `total`
- `transactionId`
- `createdAt`
- `frozenAt`
- `resumedAt`
- `completedAt`
- `cancelledAt`
- `cancellationReason`

Indexes:

- GSI `TerminalFrozenSalesIndex`
  - partition key: `terminalId`
  - sort key: `frozenAt`
  - filter or projected status: `FROZEN`

#### `Payments`

Partition key:

- `saleId` string

Attributes:

- `paymentType`
- `amountReceived`
- `changeAmount`
- `creditReferenceNumber`
- `paymentDate`

#### `Returns`

Partition key:

- `saleId` string

Sort key:

- `returnId` string

Attributes:

- `returnType`
- `returnDate`
- `items`

#### `Products`

Partition key:

- `productId` string

Attributes:

- `name`
- `barcode`
- `unitPrice`
- `availableStock`
- `category`

Indexes:

- GSI `BarcodeIndex`
  - partition key: `barcode`
- GSI `NameIndex`
  - partition key or normalized search token field: `nameSearch`

Important note: DynamoDB is not good at arbitrary case-insensitive partial text search. For the workshop, use one of these approaches:

- Store normalized search tokens in DynamoDB for simple prefix/contains behavior.
- Use OpenSearch for real product text search if the requirement is strict.
- Keep Product API responsible for search and let Sales only call it.

## Code Changes Needed

### 1. Keep Lambda Handler

The existing `LambdaHandler` can be kept for the Sales Lambda:

- Handler: `com.supermarket.sales.aws.LambdaHandler::handleRequest`
- API Gateway payload: REST API or HTTP API proxy integration

### 2. Replace JPA Repositories

Current JPA repositories must be replaced because DynamoDB does not use Hibernate/JPA.

Replace:

- `SaleRepository extends JpaRepository`
- `SaleItemRepository extends JpaRepository`
- `PaymentRepository extends JpaRepository`
- `ReturnRepository extends JpaRepository`
- `ReturnItemRepository extends JpaRepository`

With DynamoDB adapters:

- `DynamoSaleRepository`
- `DynamoPaymentRepository`
- `DynamoReturnRepository`

Recommended dependency:

- AWS SDK v2 DynamoDB Enhanced Client

### 3. Change IDs

Current code uses generated `Long` IDs from the database.

For DynamoDB and Lambda, use generated string IDs:

- `saleId = "SALE-" + UUID`
- `itemId = "ITEM-" + UUID`
- `transactionId = "TX-" + UUID`
- `returnId = "RT-" + UUID`

This affects DTOs, domain objects, tests, and route path variables.

### 4. Remove JPA Entity Coupling

Domain classes should become plain Java objects or DynamoDB documents.

Remove from domain objects:

- `@Entity`
- `@Table`
- `@Id`
- `@GeneratedValue`
- `@OneToMany`
- `@ManyToOne`
- `@Column`

### 5. Split Product Code

Right now the sales API only has product search proxy endpoints. For a real products microservice, create a separate module or separate project:

```text
sales-service/
products-service/
template.yaml
```

Products service owns product persistence in DynamoDB. Sales service calls products service through API Gateway or Lambda URL.

### 6. Infrastructure as Code

Use AWS SAM or Serverless Framework.

Minimal SAM resources:

- `AWS::Serverless::Api`
- `AWS::Serverless::Function` for Sales
- `AWS::Serverless::Function` for Products
- `AWS::DynamoDB::Table` for Sales
- `AWS::DynamoDB::Table` for Products
- IAM policies allowing each Lambda to access only its own tables

## Suggested Migration Order

1. Keep the current REST behavior and tests green.
2. Add AWS SAM template for API Gateway + Sales Lambda.
3. Deploy current Spring Boot Lambda using PostgreSQL or H2 only as a temporary step.
4. Introduce repository interfaces independent from Spring Data JPA.
5. Implement DynamoDB repositories behind those interfaces.
6. Convert IDs from `Long` to `String`.
7. Move Products into its own Lambda/service.
8. Update Sales product client to call Products through API Gateway.
9. Add DynamoDB integration tests with LocalStack or DynamoDB Local.
10. Deploy both Lambdas and run end-to-end API Gateway tests.

## Main Risk Areas

- DynamoDB partial product search is not equivalent to SQL `LIKE`.
- JPA relationships do not translate directly to DynamoDB.
- Current `Long` generated IDs do not fit serverless/DynamoDB well.
- Spring Boot on Lambda works, but cold starts are heavier than a native lightweight Lambda handler.
- Scheduled frozen-sale expiration currently uses `@Scheduled`; in AWS it should be EventBridge Scheduler invoking the Sales Lambda periodically.

## Recommended Final Shape for This Workshop

For a clean educational AWS version:

- Keep Spring Boot controllers and business services for Sales.
- Use Spring Cloud Function or AWS Serverless Java Container for Lambda integration.
- Replace JPA persistence with DynamoDB repository adapters.
- Create a second Spring Boot or lightweight Lambda project for Products.
- Put both behind one API Gateway with `/sales/*` and `/products/*` routes.
- Use DynamoDB tables with string UUID-based keys.
