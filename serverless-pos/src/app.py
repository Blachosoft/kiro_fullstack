import base64
import hashlib
import hmac
import json
import os
import time
import uuid
from decimal import Decimal

import boto3
from boto3.dynamodb.conditions import Attr

JWT_SECRET = os.environ.get("JWT_SECRET", "pos-bladimir-secret-2026")
USERS = {"admin": "admin123", "cajero": "cajero123"}


dynamodb = boto3.resource("dynamodb")
products_table = dynamodb.Table(os.environ["PRODUCTS_TABLE_NAME"])
sales_table = dynamodb.Table(os.environ["SALES_TABLE_NAME"])


def handler(event, context):
    method = event.get("requestContext", {}).get("http", {}).get("method", "")
    path = event.get("rawPath", "")

    if method == "OPTIONS":
        return response(204, None)

    try:
        if path == "/api/auth" and method == "POST":
            return response(200, login(parse_body(event)))
        if path == "/api/products" and method == "GET":
            return response(200, list_products())
        if path == "/api/products" and method == "POST":
            return response(201, save_product(parse_body(event)))
        if path == "/api/products/search" and method == "GET":
            return response(200, search_products(event.get("queryStringParameters") or {}))
        if path == "/api/sales" and method == "POST":
            return response(201, save_sale(parse_body(event)))

        return response(404, {"message": "Route not found"})
    except ValueError as error:
        return response(400, {"message": str(error)})
    except Exception as error:
        print(error)
        return response(500, {"message": "Internal server error"})


def login(payload):
    usuario = str(payload.get("usuario") or "").strip()
    contrasena = str(payload.get("contrasena") or "").strip()
    if not usuario or not contrasena:
        raise ValueError("Usuario y contraseña requeridos")
    if USERS.get(usuario) != contrasena:
        raise ValueError("Credenciales incorrectas")
    return {"access_token": _make_jwt(usuario)}


def _make_jwt(username):
    header = base64.urlsafe_b64encode(
        json.dumps({"alg": "HS256", "typ": "JWT"}).encode()
    ).rstrip(b"=").decode()
    payload = base64.urlsafe_b64encode(
        json.dumps({"sub": username, "exp": int(time.time()) + 3600}).encode()
    ).rstrip(b"=").decode()
    msg = f"{header}.{payload}"
    sig = base64.urlsafe_b64encode(
        hmac.new(JWT_SECRET.encode(), msg.encode(), hashlib.sha256).digest()
    ).rstrip(b"=").decode()
    return f"{msg}.{sig}"


def list_products():
    items = scan_all(products_table)
    return sorted([to_frontend_product(item) for item in items], key=lambda product: product["name"])


def search_products(params):
    name = (params.get("name") or "").strip().lower()
    barcode = (params.get("barcode") or "").strip()

    if barcode:
        result = products_table.scan(FilterExpression=Attr("barcode").eq(barcode))
        items = result.get("Items", [])
        if not items:
            return []
        return to_frontend_product(items[0])

    if not name:
        return list_products()

    items = scan_all(products_table)
    matches = [
        to_frontend_product(item)
        for item in items
        if name in str(item.get("name", "")).lower()
        or name in str(item.get("category", "")).lower()
    ]
    return sorted(matches, key=lambda product: product["name"])


def save_product(payload):
    required = ["id", "name", "unitPrice", "category"]
    missing = [field for field in required if payload.get(field) in (None, "")]
    if missing:
        raise ValueError("Missing required fields: " + ", ".join(missing))

    product = {
        "id": Decimal(str(payload["id"])),
        "name": str(payload["name"]),
        "barcode": str(payload.get("barcode") or payload["id"]),
        "unitPrice": Decimal(str(payload["unitPrice"])),
        "availableStock": Decimal(str(payload.get("availableStock", 999))),
        "category": str(payload["category"]),
    }

    products_table.put_item(Item=product)
    return to_frontend_product(product)


def save_sale(payload):
    items = payload.get("items")
    if not isinstance(items, list) or not items:
        raise ValueError("Sale must include at least one item")

    sale_id = payload.get("id") or str(uuid.uuid4())
    now = int(time.time())

    sale = {
        "id": sale_id,
        "createdAt": now,
        "cashier": str(payload.get("cashier") or "cashier"),
        "terminalId": str(payload.get("terminalId") or "POS-01"),
        "items": to_decimal(items),
        "subtotal": Decimal(str(payload.get("subtotal", 0))),
        "discount": Decimal(str(payload.get("discount", 0))),
        "total": Decimal(str(payload.get("total", 0))),
        "payment": to_decimal(payload.get("payment") or {}),
        "status": "COMPLETED",
    }

    sales_table.put_item(Item=sale)
    return from_decimal(sale)


def scan_all(table):
    items = []
    kwargs = {}
    while True:
        result = table.scan(**kwargs)
        items.extend(result.get("Items", []))
        if "LastEvaluatedKey" not in result:
            return items
        kwargs["ExclusiveStartKey"] = result["LastEvaluatedKey"]


def parse_body(event):
    body = event.get("body")
    if not body:
        return {}
    return json.loads(body)


def response(status_code, body):
    return {
        "statusCode": status_code,
        "headers": {
            "Content-Type": "application/json",
            "Access-Control-Allow-Origin": "*",
            "Access-Control-Allow-Headers": "Content-Type,Authorization",
            "Access-Control-Allow-Methods": "GET,POST,OPTIONS",
        },
        "body": "" if body is None else json.dumps(from_decimal(body), ensure_ascii=False),
    }


def to_frontend_product(item):
    return {
        "id": int(item["id"]),
        "name": item.get("name", ""),
        "barcode": item.get("barcode", str(item["id"])),
        "unitPrice": float(item.get("unitPrice", 0)),
        "availableStock": int(item.get("availableStock", 0)),
        "category": item.get("category", ""),
    }


def to_decimal(value):
    if isinstance(value, list):
        return [to_decimal(item) for item in value]
    if isinstance(value, dict):
        return {key: to_decimal(item) for key, item in value.items()}
    if isinstance(value, float):
        return Decimal(str(value))
    return value


def from_decimal(value):
    if isinstance(value, list):
        return [from_decimal(item) for item in value]
    if isinstance(value, dict):
        return {key: from_decimal(item) for key, item in value.items()}
    if isinstance(value, Decimal):
        if value % 1 == 0:
            return int(value)
        return float(value)
    return value
