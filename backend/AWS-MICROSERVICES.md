# AWS microservices target

## Current state

This repository currently contains the Sales API as a Spring Boot REST application. It already has the right seam for microservices:

- `SaleController` exposes sales endpoints.
- `SaleService` owns sale lifecycle, checkout, payments, freeze/resume, cancellation, and returns.
- `ProductClient` is an outbound HTTP port used by sales to read products and update stock.
- `CustomerClient` is an outbound HTTP port used by sales to validate credit.

That means Sales can become its own Lambda without rewriting the business logic. Product should be a separate microservice/Lambda with its own database/table because product catalog and stock are a different bounded context.

## Target AWS architecture

```text
Client/Postman
  |
  v
Amazon API Gateway HTTP API
  |-- /api/sales/** --------> Sales Lambda  --------> RDS PostgreSQL or Aurora Serverless: sales schema
  |                              |
  |                              | HTTP
  |                              v
  |-- /api/products/** -----> Product Lambda -------> RDS/DynamoDB: product catalog + stock
  |
  |-- /api/customers/** ----> Customer Lambda -------> RDS/DynamoDB: customers + credit status
```

For the workshop instruction "ventas y productos", the current split is:

- **sales-service**: this project, deployed as `SalesFunction`.
- **product-service**: the `product-service/` project, deployed as `ProductFunction`, backed by DynamoDB.

## What was added here

- `com.supermarket.sales.aws.LambdaHandler`: AWS Lambda entrypoint that adapts API Gateway proxy events to the existing Spring MVC controllers.
- `application-aws.yml`: AWS runtime profile using environment variables for database and external service URLs.
- `template.yaml`: AWS SAM template with API Gateway HTTP API, Sales Lambda routes, Product Lambda routes, and a DynamoDB products table.
- `product-service/`: Product microservice with REST endpoints, Lambda handler, DynamoDB repository, and service tests.

## API Gateway route ownership

In a final microservices deployment, configure API Gateway routes like this:

| Route | Lambda owner |
| --- | --- |
| `ANY /api/sales` | `SalesFunction` |
| `ANY /api/sales/{proxy+}` | `SalesFunction` |
| `ANY /api/products` | `ProductFunction` |
| `ANY /api/products/{proxy+}` | `ProductFunction` |
| `GET /api/customers/search` | `CustomerFunction` |
| `GET /api/customers/{customerId}` | `CustomerFunction` |

The included `template.yaml` now maps `/api/products/**` to `ProductFunction`. `SalesFunction` receives `PRODUCT_API_BASE_URL` pointing to the same API Gateway stage by default, so the existing `ProductApiClient` can call Product through HTTP.

Customer remains as an external service placeholder. When a Customer Lambda exists, move `/api/customers/**` to that function and set `CUSTOMER_API_BASE_URL` to the API Gateway base URL.

## Product service contract needed by Sales

The Product Lambda exposes the endpoints already consumed by `ProductApiClient`:

```text
POST /api/products
GET  /api/products/search?name={name}
GET  /api/products/search?barcode={barcode}
GET  /api/products/{productId}
POST /api/products/{productId}/stock/decrement?quantity={quantity}
POST /api/products/{productId}/stock/increment?quantity={quantity}
```

Response shape:

```json
{
  "id": 1,
  "name": "Milk",
  "barcode": "111",
  "unitPrice": 10.00,
  "availableStock": 20,
  "category": "Dairy"
}
```

## Build and deploy

Local verification:

```bash
mvn verify
```

SAM build:

```bash
sam build
```

SAM deploy example:

```bash
sam deploy --guided \
  --parameter-overrides \
    DatabaseUrl='jdbc:postgresql://<rds-endpoint>:5432/sales' \
    DatabaseUsername='<user>' \
    DatabasePassword='<password>' \
    CustomerApiBaseUrl='https://<api-id>.execute-api.<region>.amazonaws.com/prod'
```

Product service local verification:

```bash
cd product-service
mvn test
```

## Important AWS notes

- If the database is RDS inside a VPC, the Lambda needs VPC subnet/security-group config in `template.yaml`.
- Put database credentials in Secrets Manager for production. The current SAM parameters are fine for workshop/demo, but not ideal for real production.
- Scheduled frozen-sale expiration currently uses `@Scheduled`. In Lambda, scheduled background work is not reliable because Lambda only runs on invocation. Move expiration to an EventBridge scheduled Lambda invocation or a separate cleanup Lambda before production.
- Cold starts are expected with Spring Boot on Lambda. For a class project this is acceptable; for production, consider SnapStart, higher memory, or splitting hot paths into lighter functions.
