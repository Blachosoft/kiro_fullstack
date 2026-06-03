package org.example.FrontendProductos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.NotNull;

public class ProductoRequest {

    @NotBlank(message = "El nombre es obligatorio")
    private String nombre;

    @NotBlank(message = "La descripción es obligatoria")
    private String descripcion;

    @NotBlank(message = "La subcategoría es obligatoria")
    private String subcategoria;

    @NotNull(message = "El precio es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio debe ser positivo")
    private Double precio;

    @NotNull(message = "El precio por cantidad es obligatorio")
    @DecimalMin(value = "0.01", message = "El precio por cantidad debe ser positivo")
    private Double precioxcantidad;

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "activo|inactivo", message = "El estado debe ser activo o inactivo")
    private String estado;

    public ProductoRequest() {
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

    public Double getPrecio() {
        return precio;
    }

    public void setPrecio(Double precio) {
        this.precio = precio;
    }

    public Double getPrecioxcantidad() {
        return precioxcantidad;
    }

    public void setPrecioxcantidad(Double precioxcantidad) {
        this.precioxcantidad = precioxcantidad;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }
}