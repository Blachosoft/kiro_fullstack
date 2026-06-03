const TOKEN_KEY = "access_token";
const BACKEND_API_BASE_URL = window.KIRO_BACKEND_API_BASE_URL || "http://localhost:8080";
const USE_BACKEND_PRODUCTS = window.KIRO_PRODUCTS_API_MODE === "backend";

const logoutBtn = document.getElementById("logoutBtn");
const reloadBtn = document.getElementById("reloadBtn");
const openCreateBtn = document.getElementById("openCreateBtn");
const closeFormBtn = document.getElementById("closeFormBtn");
const formPanel = document.getElementById("formPanel");

const globalMessage = document.getElementById("globalMessage");
const productosBody = document.getElementById("productosBody");
const productoForm = document.getElementById("productoForm");
const formTitle = document.getElementById("formTitle");
const clearBtn = document.getElementById("clearBtn");

const filtroNombre = document.getElementById("filtroNombre");
const filtroSubcategoria = document.getElementById("filtroSubcategoria");
const filtroEstado = document.getElementById("filtroEstado");
const prevBtn = document.getElementById("prevBtn");
const nextBtn = document.getElementById("nextBtn");
const paginationInfo = document.getElementById("paginationInfo");

const fields = {
    productoId: document.getElementById("productoId"),
    nombre: document.getElementById("nombre"),
    descripcion: document.getElementById("descripcion"),
    subcategoria: document.getElementById("subcategoria"),
    precio: document.getElementById("precio"),
    precioxcantidad: document.getElementById("precioxcantidad"),
    estado: document.getElementById("estado")
};

const errors = {
    nombre: document.getElementById("errorNombre"),
    descripcion: document.getElementById("errorDescripcion"),
    subcategoria: document.getElementById("errorSubcategoria"),
    precio: document.getElementById("errorPrecio"),
    precioxcantidad: document.getElementById("errorPrecioxcantidad"),
    estado: document.getElementById("errorEstado")
};

const state = {
    page: 1,
    limit: 5,
    totalPages: 1
};

function getToken() {
    return localStorage.getItem(TOKEN_KEY);
}

function clearToken() {
    localStorage.removeItem(TOKEN_KEY);
}

function setMessage(type, text) {
    globalMessage.innerHTML = `<div class="message ${type}">${text}</div>`;
}

function clearMessage() {
    globalMessage.innerHTML = "";
}

function redirectToLogin() {
    window.location.replace("/login.html");
}

function openForm(mode = "create") {
    formPanel.classList.remove("hidden-panel");
    if (mode === "create") {
        formTitle.textContent = "Crear producto";
    }
}

function closeForm() {
    formPanel.classList.add("hidden-panel");
}

function clearFieldErrors() {
    Object.values(errors).forEach(el => el.textContent = "");
}

function validateForm() {
    clearFieldErrors();
    let valid = true;

    if (!fields.nombre.value.trim()) {
        errors.nombre.textContent = "El nombre es obligatorio";
        valid = false;
    }
    if (!fields.descripcion.value.trim()) {
        errors.descripcion.textContent = "La descripción es obligatoria";
        valid = false;
    }
    if (!fields.subcategoria.value.trim()) {
        errors.subcategoria.textContent = "La subcategoría es obligatoria";
        valid = false;
    }
    if (!fields.precio.value || Number(fields.precio.value) <= 0) {
        errors.precio.textContent = "El precio debe ser positivo";
        valid = false;
    }
    if (!fields.precioxcantidad.value || Number(fields.precioxcantidad.value) <= 0) {
        errors.precioxcantidad.textContent = "El precio por cantidad debe ser positivo";
        valid = false;
    }
    if (!["activo", "inactivo"].includes(fields.estado.value)) {
        errors.estado.textContent = "Estado inválido";
        valid = false;
    }

    return valid;
}

function resetForm() {
    fields.productoId.value = "";
    fields.nombre.value = "";
    fields.descripcion.value = "";
    fields.subcategoria.value = "";
    fields.precio.value = "";
    fields.precioxcantidad.value = "";
    fields.estado.value = "activo";
    formTitle.textContent = "Crear producto";
    clearFieldErrors();
}

function fillForm(producto) {
    openForm("edit");
    fields.productoId.value = producto.id;
    fields.nombre.value = producto.nombre;
    fields.descripcion.value = producto.descripcion;
    fields.subcategoria.value = producto.subcategoria;
    fields.precio.value = producto.precio;
    fields.precioxcantidad.value = producto.precioxcantidad;
    fields.estado.value = producto.estado;
    formTitle.textContent = `Editar producto #${producto.id}`;
    window.scrollTo({ top: 0, behavior: "smooth" });
}

function normalizeProduct(product) {
    return {
        id: product.id,
        nombre: product.nombre ?? product.name,
        descripcion: product.descripcion ?? "",
        subcategoria: product.subcategoria ?? product.category,
        precio: product.precio ?? product.unitPrice,
        precioxcantidad: product.precioxcantidad ?? product.unitPrice,
        estado: product.estado ?? "activo"
    };
}

function toBackendProductPayload(producto, id) {
    return {
        id: Number(id),
        name: producto.nombre,
        barcode: String(id),
        unitPrice: producto.precio,
        availableStock: 999,
        category: producto.subcategoria
    };
}

function paginate(items) {
    const total = items.length;
    const totalPages = Math.max(1, Math.ceil(total / state.limit));
    const safePage = Math.min(state.page, totalPages);
    const start = (safePage - 1) * state.limit;
    return {
        productos: items.slice(start, start + state.limit),
        total,
        page: safePage,
        totalPages
    };
}

async function apiFetch(url, options = {}) {
    const token = getToken();

    if (!token) {
        redirectToLogin();
        return null;
    }

    const requestUrl = USE_BACKEND_PRODUCTS && url.startsWith("/api/")
        ? `${BACKEND_API_BASE_URL}${url}`
        : url;

    const response = await fetch(requestUrl, {
        ...options,
        headers: {
            "Content-Type": "application/json",
            "Authorization": `Bearer ${token}`,
            ...(options.headers || {})
        }
    });

    let data = null;
    const text = await response.text();

    try {
        data = text ? JSON.parse(text) : {};
    } catch {
        data = {};
    }

    if (response.status === 401 || response.status === 403) {
        clearToken();
        redirectToLogin();
        return null;
    }

    if (!response.ok) {
        throw data;
    }

    return data;
}

async function loadProductos() {
    clearMessage();

    if (USE_BACKEND_PRODUCTS) {
        await loadBackendProductos();
        return;
    }

    const params = new URLSearchParams({
        page: state.page,
        limit: state.limit,
        nombre: filtroNombre.value.trim(),
        subcategoria: filtroSubcategoria.value.trim(),
        estado: filtroEstado.value
    });

    try {
        const data = await apiFetch(`/productos?${params.toString()}`);
        if (!data) return;

        renderProductos(data.productos || []);
        state.totalPages = data.totalPages || 1;
        paginationInfo.textContent = `Página ${data.page} de ${data.totalPages} — ${data.total} productos`;
    } catch (error) {
        setMessage("error", error.message || "No fue posible cargar productos");
    }
}

async function loadBackendProductos() {
    try {
        const data = await apiFetch("/api/products");
        if (!data) return;

        let productos = (Array.isArray(data) ? data : []).map(normalizeProduct);
        const nombre = filtroNombre.value.trim().toLowerCase();
        const subcategoria = filtroSubcategoria.value.trim().toLowerCase();
        const estado = filtroEstado.value;

        productos = productos.filter(producto => {
            const matchesNombre = !nombre || producto.nombre.toLowerCase().includes(nombre);
            const matchesSubcategoria = !subcategoria || producto.subcategoria.toLowerCase().includes(subcategoria);
            const matchesEstado = !estado || producto.estado === estado;
            return matchesNombre && matchesSubcategoria && matchesEstado;
        });

        const page = paginate(productos);
        state.page = page.page;
        state.totalPages = page.totalPages;
        renderProductos(page.productos);
        paginationInfo.textContent = `Página ${page.page} de ${page.totalPages} — ${page.total} productos`;
    } catch (error) {
        setMessage("error", error.message || "No fue posible cargar productos desde AWS");
    }
}

function renderProductos(productos) {
    if (!productos.length) {
        productosBody.innerHTML = `
      <tr>
        <td colspan="7">No hay productos registrados</td>
      </tr>
    `;
        return;
    }

    productosBody.innerHTML = productos.map(producto => `
    <tr class="${producto.estado === 'inactivo' ? 'row-inactive' : ''}">
      <td>${producto.nombre}</td>
      <td>${producto.descripcion}</td>
      <td>${producto.subcategoria}</td>
      <td>${producto.precio}</td>
      <td>${producto.precioxcantidad}</td>
      <td>
        <span class="badge ${producto.estado}">
          ${producto.estado === 'activo' ? 'Activo' : 'Eliminado'}
        </span>
      </td>
      <td>
        <div class="row-actions">
          <button class="btn btn-secondary small-btn"
            onclick="editarProducto(${producto.id})">
            Editar
          </button>

          <button class="btn ${producto.estado === 'activo' ? 'btn-danger' : 'btn-primary'} small-btn"
            onclick="toggleEstadoProducto(${producto.id})">
            ${producto.estado === 'activo' ? 'Eliminar' : 'Restaurar'}
          </button>
        </div>
      </td>
    </tr>
  `).join("");

    window._productosActuales = productos;
}

window.editarProducto = function(id) {
    const producto = (window._productosActuales || []).find(p => p.id === id);
    if (producto) fillForm(producto);
};

window.eliminarProducto = async function(id) {
    const confirmado = confirm("¿Seguro que deseas eliminar este producto?");
    if (!confirmado) return;

    try {
        const data = await apiFetch(`/productos/${id}`, {
            method: "DELETE"
        });
        if (!data) return;

        setMessage("success", data.message || "Producto eliminado correctamente");
        resetForm();
        closeForm();
        await loadProductos();
    } catch (error) {
        setMessage("error", error.message || "No fue posible eliminar");
    }
};
window.toggleEstadoProducto = async function(id) {
    if (USE_BACKEND_PRODUCTS) {
        setMessage("error", "En modo AWS esta pantalla crea y edita productos; eliminar/restaurar no está disponible en el backend SAM actual.");
        return;
    }

    const producto = (window._productosActuales || []).find(p => p.id === id);
    if (!producto) return;

    const accion = producto.estado === "activo" ? "desactivar" : "activar";
    const confirmado = confirm(`¿Seguro que deseas ${accion} este producto?`);
    if (!confirmado) return;

    try {
        const data = await apiFetch(`/productos/${id}/estado`, {
            method: "PATCH"
        });

        if (!data) return;

        setMessage("success", data.message || "Estado actualizado correctamente");
        resetForm();
        closeForm();
        await loadProductos();
    } catch (error) {
        setMessage("error", error.message || "No fue posible cambiar el estado");
    }
};

productoForm.addEventListener("submit", async (e) => {
    e.preventDefault();
    clearMessage();

    if (!validateForm()) return;

    const payload = {
        nombre: fields.nombre.value.trim(),
        descripcion: fields.descripcion.value.trim(),
        subcategoria: fields.subcategoria.value.trim(),
        precio: Number(fields.precio.value),
        precioxcantidad: Number(fields.precioxcantidad.value),
        estado: fields.estado.value
    };

    const id = fields.productoId.value;
    const isEdit = Boolean(id);
    const backendId = isEdit ? id : Date.now();

    try {
        const data = USE_BACKEND_PRODUCTS
            ? await apiFetch("/api/products", {
                method: "POST",
                body: JSON.stringify(toBackendProductPayload(payload, backendId))
            })
            : await apiFetch(isEdit ? `/productos/${id}` : "/productos", {
                method: isEdit ? "PUT" : "POST",
                body: JSON.stringify(payload)
            });

        if (!data) return;

        setMessage("success", data.message || (isEdit ? "Producto actualizado" : "Producto creado"));
        resetForm();
        closeForm();
        await loadProductos();
    } catch (error) {
        if (error.errores) {
            Object.keys(error.errores).forEach(key => {
                if (errors[key]) errors[key].textContent = error.errores[key];
            });
        } else {
            setMessage("error", error.message || "No fue posible guardar");
        }
    }
});

openCreateBtn.addEventListener("click", () => {
    resetForm();
    openForm("create");
});

closeFormBtn.addEventListener("click", () => {
    resetForm();
    closeForm();
});

clearBtn.addEventListener("click", resetForm);
reloadBtn.addEventListener("click", loadProductos);

logoutBtn.addEventListener("click", () => {
    clearToken();
    redirectToLogin();
});

filtroNombre.addEventListener("input", () => {
    state.page = 1;
    loadProductos();
});

filtroSubcategoria.addEventListener("input", () => {
    state.page = 1;
    loadProductos();
});

filtroEstado.addEventListener("change", () => {
    state.page = 1;
    loadProductos();
});

prevBtn.addEventListener("click", () => {
    if (state.page > 1) {
        state.page--;
        loadProductos();
    }
});

nextBtn.addEventListener("click", () => {
    if (state.page < state.totalPages) {
        state.page++;
        loadProductos();
    }
});

window.addEventListener("DOMContentLoaded", async () => {
    if (!getToken()) {
        redirectToLogin();
        return;
    }

    resetForm();
    closeForm();
    await loadProductos();
});
