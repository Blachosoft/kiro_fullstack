package org.example.FrontendProductos.model;

public class Usuario {
    private String usuario;
    private String contrasena;
    private String estado;
    private String rol;

    public Usuario() {
    }

    public Usuario(String usuario, String contrasena, String estado, String rol) {
        this.usuario = usuario;
        this.contrasena = contrasena;
        this.estado = estado;
        this.rol = rol;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getContrasena() {
        return contrasena;
    }

    public void setContrasena(String contrasena) {
        this.contrasena = contrasena;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}