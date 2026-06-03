/**
 * search.js — Product Search Panel logic.
 *
 * Manages:
 *  - #search-name   text input (debounced at 300 ms)
 *  - #search-category select (populated on init from all active products)
 *  - #search-results  <ul> (rendered results, clickable to add to cart)
 *
 * Exports:
 *  - initSearch()  — sets up all event listeners and populates the category dropdown
 */

import { apiFetch } from "./api.js";
import CartStore from "./cart.js";

// ---------------------------------------------------------------------------
// Internal state
// ---------------------------------------------------------------------------

/** @type {ReturnType<typeof setTimeout>|null} */
let debounceTimer = null;
let cachedProducts = [];

// ---------------------------------------------------------------------------
// DOM helpers (resolved lazily so initSearch can be called after DOMContentLoaded)
// ---------------------------------------------------------------------------

function getNameInput()     { return /** @type {HTMLInputElement}  */ (document.getElementById("search-name")); }
function getCategorySelect(){ return /** @type {HTMLSelectElement} */ (document.getElementById("search-category")); }
function getResultsList()   { return /** @type {HTMLUListElement}  */ (document.getElementById("search-results")); }
function getQuickProducts() { return /** @type {HTMLUListElement}  */ (document.getElementById("quick-products")); }

function normalizeProduct(product) {
  return {
    id: product.id,
    nombre: product.nombre ?? product.name,
    subcategoria: product.subcategoria ?? product.category,
    precio: product.precio ?? product.unitPrice,
  };
}

// ---------------------------------------------------------------------------
// Category dropdown population
// ---------------------------------------------------------------------------

/**
 * Fetch all active products, extract distinct subcategoria values, and
 * populate the #search-category select with sorted options.
 */
async function populateCategories() {
  const select = getCategorySelect();
  try {
    const items = await loadProducts();

    // Extract distinct, non-empty subcategoria values
    const categories = [
      ...new Set(
        items
          .map((p) => p.subcategoria)
          .filter((c) => c != null && c !== "")
      ),
    ].sort((a, b) => a.localeCompare(b));

    // Keep the first "All categories" option, then append one per category
    // Remove any previously injected options (in case of re-init)
    while (select.options.length > 1) {
      select.remove(1);
    }

    for (const cat of categories) {
      const option = document.createElement("option");
      option.value = cat;
      option.textContent = cat;
      select.appendChild(option);
    }
  } catch {
    // Non-fatal: category dropdown stays with only the "All categories" option
  }
}

const PRODUCTS_OPTS = { baseUrl: window.KIRO_PRODUCTS_API_BASE_URL ?? "" };

async function loadProducts() {
  if (cachedProducts.length > 0) {
    return cachedProducts;
  }

  const products = await apiFetch("/api/products", PRODUCTS_OPTS);
  cachedProducts = (Array.isArray(products) ? products : []).map(normalizeProduct);
  return cachedProducts;
}

// ---------------------------------------------------------------------------
// Query builder
// ---------------------------------------------------------------------------

/**
 * Build the API path for a product search.
 *
 * @param {string} name      - Current value of #search-name (trimmed)
 * @param {string} category  - Current value of #search-category
 * @returns {string}
 */
function buildSearchPath(name, category) {
  const params = new URLSearchParams();
  if (name) {
    params.set("name", name);
  } else if (category) {
    params.set("name", category);
  }
  return `/api/products/search?${params.toString()}`;
}

function matchesQuery(product, query) {
  const needle = query.toLowerCase();
  return [product.nombre, product.subcategoria]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(needle));
}

// ---------------------------------------------------------------------------
// Result rendering
// ---------------------------------------------------------------------------

/**
 * Format a number as a price string, e.g. 3.5 → "$3.50".
 * @param {number} value
 * @returns {string}
 */
function formatPrice(value) {
  return "$" + Number(value).toFixed(2);
}

/**
 * Render a list of products into #search-results.
 * Each <li> is clickable and calls CartStore.addItem(product).
 *
 * @param {Array<Object>} products
 */
function renderResults(products) {
  const list = getResultsList();
  list.innerHTML = "";

  for (const product of products) {
    const li = document.createElement("li");
    li.className = "search-result-item";
    li.setAttribute("role", "option");
    li.setAttribute("tabindex", "0");

    // Product name (bold)
    const nameEl = document.createElement("span");
    nameEl.className = "search-result-item__name";
    nameEl.textContent = product.nombre;

    // Subcategory (muted)
    const catEl = document.createElement("span");
    catEl.className = "search-result-item__meta";
    catEl.textContent = product.subcategoria ?? "";

    // Unit price
    const priceEl = document.createElement("span");
    priceEl.className = "search-result-item__price font-price";
    priceEl.textContent = formatPrice(product.precio);

    li.appendChild(nameEl);
    li.appendChild(catEl);
    li.appendChild(priceEl);

    // Click handler — add to cart, keep results visible
    const handleSelect = () => {
      CartStore.addItem(product);
      getNameInput().value = "";
      hideResults();
      getNameInput()?.focus();
    };

    li.addEventListener("click", handleSelect);
    li.addEventListener("keydown", (e) => {
      if (e.key === "Enter" || e.key === " ") {
        e.preventDefault();
        handleSelect();
      }
    });

    list.appendChild(li);
  }

  list.style.display = "block";
}

function renderQuickProducts(products) {
  const list = getQuickProducts();
  if (!list) return;

  list.innerHTML = "";
  products.slice(0, 8).forEach((product) => {
    const li = document.createElement("li");
    li.className = "quick-product";

    const button = document.createElement("button");
    button.type = "button";
    button.className = "quick-product__button";

    const name = document.createElement("span");
    name.className = "quick-product__name";
    name.textContent = product.nombre;

    const price = document.createElement("span");
    price.className = "quick-product__price";
    price.textContent = formatPrice(product.precio);

    button.appendChild(name);
    button.appendChild(price);
    button.addEventListener("click", () => {
      CartStore.addItem(product);
      getNameInput()?.focus();
    });

    li.appendChild(button);
    list.appendChild(li);
  });
}

/**
 * Show an empty-state or error message in #search-results.
 * @param {string} message
 * @param {string} [cssClass]
 */
function renderMessage(message, cssClass = "search-no-results") {
  const list = getResultsList();
  list.innerHTML = "";

  const li = document.createElement("li");
  li.className = cssClass;
  li.textContent = message;
  list.appendChild(li);

  list.style.display = "block";
}

/**
 * Hide and clear #search-results.
 */
function hideResults() {
  const list = getResultsList();
  list.innerHTML = "";
  list.style.display = "none";
}

// ---------------------------------------------------------------------------
// Search execution
// ---------------------------------------------------------------------------

/**
 * Execute a product search with the current input values.
 */
async function executeSearch() {
  const name     = getNameInput().value.trim();
  const category = getCategorySelect().value;

  // If both inputs are empty, hide results
  if (!name && !category) {
    hideResults();
    return;
  }

  try {
    const items = await loadProducts();
    let products = items;
    if (name) {
      products = products.filter((product) => matchesQuery(product, name));
    }
    if (category) {
      products = products.filter((product) => product.subcategoria === category);
    }
    products = products.slice(0, 20);

    if (products.length === 0) {
      if (category && !name) {
        renderMessage("No products in this category.");
      } else {
        renderMessage(`No se encontraron productos para '${name}'.`);
      }
    } else {
      renderResults(products);
    }
  } catch {
    renderMessage("No se pudieron cargar productos. Revise la conexión.");
  }
}

// ---------------------------------------------------------------------------
// Event handlers
// ---------------------------------------------------------------------------

/**
 * Handle input events on #search-name with 300 ms debounce.
 */
function onNameInput() {
  clearTimeout(debounceTimer);

  const name     = getNameInput().value.trim();
  const category = getCategorySelect().value;

  // If name is cleared and no category, hide results immediately
  if (!name && !category) {
    hideResults();
    return;
  }

  debounceTimer = setTimeout(executeSearch, 300);
}

/**
 * Handle keydown events on #search-name for fast selection.
 * - Enter: add the first result if available
 * - ArrowDown: move focus to the first result
 * @param {KeyboardEvent} event
 */
function onNameKeydown(event) {
  const list = getResultsList();
  const firstResult = list?.querySelector(".search-result-item");

  if (event.key === "Enter" && firstResult) {
    event.preventDefault();
    firstResult.click();
  }

  if (event.key === "ArrowDown" && firstResult) {
    event.preventDefault();
    firstResult.focus();
  }
}

/**
 * Handle change events on #search-category (no debounce needed).
 */
function onCategoryChange() {
  clearTimeout(debounceTimer);
  executeSearch();
}

// ---------------------------------------------------------------------------
// Public API
// ---------------------------------------------------------------------------

/**
 * Initialise the Product Search Panel.
 * - Populates the category dropdown.
 * - Attaches event listeners to #search-name and #search-category.
 *
 * Should be called once after DOMContentLoaded.
 */
export function initSearch() {
  const nameInput     = getNameInput();
  const categorySelect = getCategorySelect();

  nameInput.addEventListener("input", onNameInput);
  nameInput.addEventListener("keydown", onNameKeydown);
  categorySelect.addEventListener("change", onCategoryChange);

  loadProducts()
    .then((products) => {
      renderQuickProducts(products);
      return populateCategories();
    })
    .catch(() => {
      renderMessage("No se pudieron cargar productos. Revise la conexión.");
    });
}
