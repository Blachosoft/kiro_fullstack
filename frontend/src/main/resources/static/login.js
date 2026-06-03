const TOKEN_KEY = "access_token";

const loginForm = document.getElementById("loginForm");
const loginMessage = document.getElementById("loginMessage");

function setMessage(type, text) {
    loginMessage.innerHTML = `<div class="message ${type}">${text}</div>`;
}

function saveToken(token) {
    localStorage.setItem(TOKEN_KEY, token);
}

function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

const PRODUCTS_BASE = window.KIRO_PRODUCTS_API_BASE_URL ?? "";

async function validarToken() {
    const token = getToken();
    if (!token) return false;

    try {
        const response = await fetch(`${PRODUCTS_BASE}/api/products`, {
            method: "GET",
            headers: { "Authorization": `Bearer ${token}` }
        });

        if (response.ok) return true;

        clearToken();
        return false;
    } catch {
        clearToken();
        return false;
    }
}

window.addEventListener("DOMContentLoaded", async () => {
    const valido = await validarToken();
    if (valido) {
        window.location.replace("/pos.html");
    }
});

loginForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    loginMessage.innerHTML = "";

    const usuario = document.getElementById("usuario").value.trim();
    const contrasena = document.getElementById("contrasena").value.trim();

    try {
        clearToken();

        const response = await fetch(`/auth`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ usuario, contrasena })
        });

        const text = await response.text();
        let data = {};

        try {
            data = text ? JSON.parse(text) : {};
        } catch {
            data = {};
        }

        if (!response.ok) {
            setMessage("error", data.message || "No fue posible iniciar sesión");
            return;
        }

        if (!data.access_token) {
            setMessage("error", "El servidor no devolvió el token");
            return;
        }

        saveToken(data.access_token);
        window.location.replace("/pos.html");
    } catch (error) {
        setMessage("error", "No fue posible conectar con el servidor");
    }
});