package org.example.FrontendProductos.model;

public class Producto {
    private Long id;
    private String nombre;
    private String descripcion;
    private String subcategoria;
    private Double precio;
    private Double precioxcantidad;
    private String estado;

    public Producto() {
    }

    public Producto(Long id, String nombre, String descripcion, String subcategoria,
                    Double precio, Double precioxcantidad, String estado) {
        this.id = id;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.subcategoria = subcategoria;
        this.precio = precio;
        this.precioxcantidad = precioxcantidad;
        this.estado = estado;
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