const TOKEN_KEY = "access_token";

const loginForm = document.getElementById("loginForm");
const messageBox = document.getElementById("message");
const productsMessage = document.getElementById("productsMessage");
const productsSection = document.getElementById("productsSection");
const logoutBtn = document.getElementById("logoutBtn");
const loadProductsBtn = document.getElementById("loadProductsBtn");
const productsTableBody = document.getElementById("productsTableBody");
const tokenBox = document.getElementById("tokenBox");

function setMessage(target, type, text) {
    target.innerHTML = `<div class="message ${type}">${text}</div>`;
}

function clearMessage(target) {
    target.innerHTML = "";
}

function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

function showAuthenticatedArea() {
    productsSection.classList.remove("hidden");
    tokenBox.innerHTML = `<strong>Token:</strong><br>${getToken()}`;
}

function hideAuthenticatedArea() {
    productsSection.classList.add("hidden");
    tokenBox.innerHTML = "";
}

async function login(usuario, contrasena) {
    clearMessage(messageBox);

    try {
        const response = await fetch("/auth", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({ usuario, contrasena })
        });

        const data = await response.json();

        if (!response.ok) {
            setMessage(messageBox, "error", data.message || "Error al iniciar sesión");
            return;
        }

        saveToken(data.access_token);
        setMessage(messageBox, "success", "Login exitoso");
        showAuthenticatedArea();
        await loadProducts();
    } catch (error) {
        setMessage(messageBox, "error", "No fue posible conectar con el servidor");
    }
}

async function loadProducts() {
    clearMessage(productsMessage);

    const token = getToken();

    if (!token) {
        setMessage(productsMessage, "error", "No hay token disponible");
        return;
    }

    try {
        const response = await fetch("/productos?page=1&limit=10", {
            method: "GET",
            headers: {
                "Authorization": `Bearer ${token}`
            }
        });

        const data = await response.json();

        if (response.status === 401) {
            clearToken();
            hideAuthenticatedArea();
            setMessage(messageBox, "error", data.message || "Sesión expirada");
            return;
        }

        if (response.status === 403) {
            clearToken();
            hideAuthenticatedArea();
            setMessage(messageBox, "error", data.message || "Usuario inactivo");
            return;
        }

        if (!response.ok) {
            setMessage(productsMessage, "error", data.message || "Error cargando productos");
            return;
        }

        renderProducts(data.productos);
    } catch (error) {
        setMessage(productsMessage, "error", "No fue posible obtener productos");
    }
}

function renderProducts(productos) {
    if (!productos || productos.length === 0) {
        productsTableBody.innerHTML = `
            <tr>
                <td colspan="6">No hay productos disponibles</td>
            </tr>
        `;
        return;
    }

    productsTableBody.innerHTML = productos.map(producto => `
        <tr>
            <td>${producto.nombre}</td>
            <td>${producto.descripcion}</td>
            <td>${producto.subcategoria}</td>
            <td>${producto.precio}</td>
            <td>${producto.precioxcantidad}</td>
            <td>
                <span class="badge ${producto.estado}">
                    ${producto.estado}
                </span>
            </td>
        </tr>
    `).join("");
}

loginForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const usuario = document.getElementById("usuario").value.trim();
    const contrasena = document.getElementById("contrasena").value.trim();

    await login(usuario, contrasena);
});

logoutBtn.addEventListener("click", () => {
    clearToken();
    hideAuthenticatedArea();
    clearMessage(productsMessage);
    setMessage(messageBox, "success", "Sesión cerrada");
});

loadProductsBtn.addEventListener("click", async () => {
    await loadProducts();
});

window.addEventListener("DOMContentLoaded", async () => {
    const token = getToken();
    if (token) {
        showAuthenticatedArea();
        await loadProducts();
    }
});