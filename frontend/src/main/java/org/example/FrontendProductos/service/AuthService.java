package org.example.FrontendProductos.service;


import org.example.FrontendProductos.model.Usuario;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class AuthService {

    private final List<Usuario> usuarios = new ArrayList<>();

    public AuthService() {
        usuarios.add(new Usuario("admin", "admin123", "activo", "ADMIN"));
        usuarios.add(new Usuario("inactivo", "noactivo1", "inactivo", "USER"));
    }

    public Optional<Usuario> autenticar(String usuario, String contrasena) {
        return usuarios.stream()
                .filter(u -> u.getUsuario().equals(usuario) && u.getContrasena().equals(contrasena))
                .findFirst();
    }
}