package org.example.FrontendProductos.util;

import jakarta.servlet.http.HttpSession;

public class SessionsUtil {

    public static final String TOKEN_KEY = "JWT_TOKEN";

    public static void guardarToken(HttpSession session, String token) {
        session.setAttribute(TOKEN_KEY, token);
    }

    public static String obtenerToken(HttpSession session) {
        Object token = session.getAttribute(TOKEN_KEY);
        return token != null ? token.toString() : null;
    }

    public static void limpiarSesion(HttpSession session) {
        session.invalidate();
    }
}