package org.example.FrontendProductos.dto;

public class AuthResponse {

    private String access_token;
    private String usuario;
    private String rol;

    public AuthResponse() {
    }

    public AuthResponse(String access_token, String usuario, String rol) {
        this.access_token = access_token;
        this.usuario = usuario;
        this.rol = rol;
    }

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getUsuario() {
        return usuario;
    }

    public void setUsuario(String usuario) {
        this.usuario = usuario;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
    }
}