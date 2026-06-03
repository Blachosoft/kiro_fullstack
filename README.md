# Blachosoft/Proyecto-AWS — Supermarket POS

Sistema de punto de venta (POS) para supermercado desarrollado con arquitectura cliente-servidor, desplegado sobre AWS Serverless y ejecutable localmente con Maven.

---

## Arquitectura cliente-servidor

```
┌─────────────────────────────────────────────────────────────┐
│                        NAVEGADOR                            │
│   login.html / pos.html / productos.html                    │
│   (HTML + CSS + JS vanilla, módulos ES6)                    │
└────────────────────┬────────────────────────────────────────┘
                     │ HTTP / JWT Bearer
        ┌────────────▼────────────┐
        │   Frontend Spring Boot  │  :3000
        │   - Autenticación JWT   │
        │   - Gestión productos   │
        │   - Sirve archivos      │
        │     estáticos           │
        └────────────┬────────────┘
                     │ HTTP
        ┌────────────▼────────────────────────────┐
        │        AWS API Gateway                  │
        │  https://<id>.execute-api.us-east-1...  │
        └──────┬───────────────────┬──────────────┘
               │                   │
   ┌───────────▼──────┐  ┌─────────▼──────────┐
   │  SalesFunction   │  │  ProductFunction    │
   │  (Lambda Java)   │  │  (Lambda Java)      │
   │  /api/sales      │  │  /api/products      │
   │  /api/customers  │  │                     │
   └───────┬──────────┘  └────────┬────────────┘
           │                      │
   ┌───────▼──────┐      ┌────────▼────────┐
   │  PostgreSQL  │      │    DynamoDB     │
   │  (RDS/local) │      │  Products Table │
   └──────────────┘      └─────────────────┘
```

### Capas del sistema

| Capa | Tecnología | Justificación |
|------|-----------|---------------|
| Frontend estático | HTML5 + CSS3 + JavaScript ES6 (módulos) | Sin dependencias de build; el servidor Spring Boot sirve los archivos directamente. Ideal para un POS donde el tiempo de carga es crítico. |
| Servidor frontend | Spring Boot 3.x (Java 17) | Maneja autenticación JWT, gestión de productos locales y sirve la SPA. Comparte el mismo ecosistema Java que el backend. |
| API de ventas | Spring Boot 3.x sobre AWS Lambda | Serverless elimina la administración de servidores y escala automáticamente por demanda del POS. |
| API de productos | Spring Boot 3.x sobre AWS Lambda + DynamoDB | DynamoDB ofrece latencia de un dígito en milisegundos para lecturas del catálogo, esencial en un flujo de caja. |
| Infraestructura | AWS SAM (template.yaml) | Define toda la infraestructura como código (IaC), reproducible en cualquier cuenta AWS con un solo comando. |

---

## Ejecutar localmente

### Requisitos

- Java 17+ (JDK)
- Maven 3.9+
- (Opcional) AWS SAM CLI para despliegue en la nube

### 1. Clonar e instalar dependencias

```bash
# Frontend (servidor :3000 + gestión de productos)
cd frontend
mvn install

# Backend de ventas (servidor :8080)
cd ../backend
mvn install
```

### 2. Iniciar ambos servicios

```bash
# Desde la raíz del proyecto
./run-local.sh
```

O por separado en terminales distintas:

```bash
# Terminal 1 — Backend ventas
cd backend
mvn spring-boot:run

# Terminal 2 — Frontend
cd frontend
mvn spring-boot:run
```

### 3. Abrir la aplicación

```
http://localhost:3000/login.html
```

Credenciales de prueba configuradas en `frontend/src/main/resources/application.properties`.

> **Nota sobre productos en modo local:** En modo local los endpoints `/api/products` son servidos por el mismo Spring Boot del frontend (`data/productos.json`), sin necesitar DynamoDB ni AWS.

---

## Configurar la URL del API Gateway

La URL del API Gateway se configura en un solo archivo:

**`frontend/src/main/resources/static/config.js`**

```js
// URL base para ventas y clientes → AWS API Gateway
window.KIRO_BACKEND_API_BASE_URL = "https://<tu-id>.execute-api.us-east-1.amazonaws.com";

// URL base para productos → "" = mismo servidor (local), o URL de AWS para producción
window.KIRO_PRODUCTS_API_BASE_URL = "";

window.KIRO_PRODUCTS_API_MODE = "local";   // "local" | "serverless"
window.KIRO_BACKEND_MODE = "serverless";   // "local" | "serverless"
```

### Obtener la URL tras desplegar con SAM

```bash
cd backend
sam build
sam deploy --guided
# Al finalizar, SAM imprime:
# Outputs → ApiBaseUrl: https://xxxx.execute-api.us-east-1.amazonaws.com/prod
```

Copia ese valor en `KIRO_BACKEND_API_BASE_URL` y, si también quieres productos desde AWS, en `KIRO_PRODUCTS_API_BASE_URL`.

### Variables de entorno para el perfil AWS

El `template.yaml` requiere estos parámetros en el despliegue:

| Parámetro | Descripción |
|-----------|-------------|
| `DatabaseUrl` | JDBC URL de PostgreSQL/RDS |
| `DatabaseUsername` | Usuario de la base de datos |
| `DatabasePassword` | Contraseña (marcada `NoEcho`) |
| `ProductApiBaseUrl` | URL del API Gateway de productos |
| `CustomerApiBaseUrl` | URL del API Gateway de clientes |

---

## Capturas de pantalla

### Listado de productos cargado desde el API

> Pantalla principal del POS mostrando el panel de búsqueda con productos activos cargados desde `/api/products`. El panel de categorías se pobla automáticamente con los valores distintos de `subcategoria`.

*(Agregar captura aquí)*

---

### Registro de una venta exitosa con respuesta del API visible

> Flujo completo: búsqueda de producto → agregar al carrito → selección de método de pago → confirmación. Se muestra el modal de recibo con el ID de transacción generado por el backend (`POST /api/sales/{id}/checkout`).

*(Agregar captura aquí)*

---

### Manejo de un error — API caído o respuesta inválida

> Cuando el API Gateway no responde o retorna un error, el POS muestra el mensaje "No se pudieron cargar productos. Revise la conexión." en el panel de búsqueda y "Barcode lookup failed. Try again." en el escáner, sin romper la sesión activa.

*(Agregar captura aquí)*

---

## Proceso SDD — Cómo los specs guiaron la implementación

Este proyecto fue construido siguiendo **Spec-Driven Development (SDD)** con Kiro. El flujo fue:

```
Descripción en lenguaje natural
        ↓
Kiro genera specs estructurados
        ↓
Revisión y refinamiento manual
        ↓
Kiro implementa siguiendo los specs
        ↓
Validación con tests
```

### Los tres archivos de spec

Los specs viven en `.kiro/specs/` y fueron la única fuente de verdad durante la implementación:

| Archivo | Propósito | Decisiones que tomó |
|---------|-----------|---------------------|
| `requirements.md` | Historias de usuario + criterios de aceptación | Definió los 10 requisitos del POS frontend y los 10 del Sales API, incluyendo los mensajes de error exactos que aparecen en el código |
| `design.md` | Arquitectura, componentes, modelo de datos | Determinó la separación en módulos ES6 (`api.js`, `cart.js`, `search.js`, `scanner.js`), el uso de `apiFetch` como wrapper central, y el modelo de `Sale` con sus transiciones de estado |
| `tasks.md` | Pasos de implementación ordenados | Guió el orden: primero autenticación, luego carrito, luego pagos — evitando deuda técnica por dependencias entre módulos |

### Cómo los specs evitaron decisiones ad-hoc

**Ejemplo 1 — Mensajes de error consistentes:**
El `requirements.md` especificó los textos exactos ("No se encontraron productos para '{term}'", "Barcode lookup failed. Try again."). Kiro los implementó literalmente en `search.js` y `scanner.js`, garantizando coherencia sin revisión manual de cada mensaje.

**Ejemplo 2 — Transiciones de estado del Sale:**
El spec de diseño definió el diagrama de estados (`ACTIVE → FROZEN → ACTIVE`, `COMPLETED → RETURNED`). El `SaleService` implementa exactamente esas transiciones y rechaza operaciones inválidas, sin lógica ambigua.

**Ejemplo 3 — Separación de responsabilidades en el frontend:**
El spec indicó que `apiFetch` debía ser el único punto de entrada HTTP (con inyección automática de JWT y manejo de 401/403). Esto evitó que cada módulo reimplementara manejo de errores, y fue la base para el cambio posterior que separó la URL de productos de la de ventas con `options.baseUrl`.

**Ejemplo 4 — Arquitectura serverless:**
El `design.md` del backend especificó el `LambdaHandler` como adaptador entre API Gateway y Spring Boot. Kiro generó `LambdaHandler.java` usando `SpringBootLambdaContainerHandler`, lo que permitió que el mismo código corriera localmente con `mvn spring-boot:run` y en AWS Lambda sin modificaciones.

---

## Estructura del proyecto

```
Kiro-fullstack/
├── backend/                    # Sales API (Spring Boot + Lambda)
│   ├── src/main/java/
│   │   └── com/supermarket/sales/
│   │       ├── aws/            # LambdaHandler — adaptador API Gateway
│   │       ├── controller/     # SaleController, ProductSearchController
│   │       ├── service/        # SaleService, FinancialCalculator
│   │       ├── client/         # ProductApiClient, CustomerApiClient
│   │       └── domain/         # Sale, SaleItem, Payment, Return
│   ├── product-service/        # Products microservice (Lambda + DynamoDB)
│   └── template.yaml           # SAM — define API Gateway, Lambdas, DynamoDB
│
└── frontend/                   # Frontend Spring Boot (:3000)
    ├── src/main/java/
    │   └── org/example/FrontendProductos/
    │       ├── controller/     # ProductoController (/productos + /api/products)
    │       ├── service/        # ProductoService (lee data/productos.json)
    │       └── segurity/       # JwtService, JwtAuthFilter
    └── src/main/resources/
        └── static/
            ├── config.js       # ← Configurar URLs aquí
            ├── pos.html / pos.js
            ├── login.html / login.js
            ├── productos.html / productos.js
            └── modules/
                ├── api.js      # Wrapper HTTP con JWT
                ├── cart.js     # Estado del carrito
                ├── search.js   # Búsqueda de productos
                ├── scanner.js  # Escáner de código de barras
                ├── payment.js  # Flujo de pago
                └── receipt.js  # Generación de recibo
```
