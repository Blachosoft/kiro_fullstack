package com.example.frontendproductos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class ProductoDto {

    private Long id;

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @NotBlank(message = "La subcategoría es obligatoria")
    private String subcategoria;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser positivo")
    private BigDecimal precio;

    @NotNull(message = "El precio por cantidad es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio por cantidad debe ser positivo")
    private BigDecimal precioxcantidad;

    @NotBlank(message = "El estado es obligatorio")
    private String estado;

    public ProductoDto() {}

    public boolean estadoValido() {
        return "activo".equalsIgnoreCase(estado) || "inactivo".equalsIgnoreCase(estado);
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getSubcategoria() {
        return subcategoria;
    }

    public void setSubcategoria(String subcategoria) {
        this.subcategoria = subcategoria;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public BigDecimal getPrecioxcantidad() {
        return precioxcantidad;
    }

    public void setPrecioxcantidad(BigDecimal precioxcantidad) {
        this.precioxcantidad = precioxcantidad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}