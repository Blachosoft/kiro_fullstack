# Serverless POS

Arquitectura simple del tablero:

```text
POS / Frontend
  -> API Gateway
    -> Lambda
      -> DynamoDB products
      -> DynamoDB sales
```

## Endpoints

```text
GET  /api/products
GET  /api/products/search?name=arroz
GET  /api/products/search?barcode=1
POST /api/products
POST /api/sales
```

## Validar y desplegar

```bash
sam validate --lint
sam build
sam deploy --guided
```

Cuando termine el deploy, SAM mostrará `ApiBaseUrl`.

## Cargar productos de ejemplo

Después del deploy, toma el nombre de la tabla `ProductsTableName` y ejecuta:

```bash
aws dynamodb batch-write-item \
  --request-items '{"NOMBRE_DE_LA_TABLA": []}'
```

Reemplaza `NOMBRE_DE_LA_TABLA` y pega dentro del array el contenido de `seed-products.json`.

## Conectar el frontend

Edita:

```text
frontend/src/main/resources/static/config.js
```

Usa:

```js
window.KIRO_BACKEND_API_BASE_URL = "https://TU_API.execute-api.REGION.amazonaws.com";
window.KIRO_PRODUCTS_API_MODE = "backend";
window.KIRO_BACKEND_MODE = "serverless";
```
