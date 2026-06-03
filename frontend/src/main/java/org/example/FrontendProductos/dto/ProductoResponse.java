package org.example.FrontendProductos.dto;

import java.util.List;

public class ProductoResponse {
    private List<?> productos;
    private int total;
    private int page;
    private int limit;
    private int totalPages;

    public ProductoResponse() {
    }

    public ProductoResponse(List<?> productos, int total, int page, int limit, int totalPages) {
        this.productos = productos;
        this.total = total;
        this.page = page;
        this.limit = limit;
        this.totalPages = totalPages;
    }

    public List<?> getProductos() {
        return productos;
    }

    public void setProductos(List<?> productos) {
        this.productos = productos;
    }

    public int getTotal() {
        return total;
    }

    public void setTotal(int total) {
        this.total = total;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public int getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(int totalPages) {
        this.totalPages = totalPages;
    }
}