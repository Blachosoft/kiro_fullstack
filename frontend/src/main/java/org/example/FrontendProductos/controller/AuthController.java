package org.example.FrontendProductos.controller;


import org.example.FrontendProductos.dto.AuthRequest;
import org.example.FrontendProductos.dto.AuthResponse;
import org.example.FrontendProductos.model.Usuario;
import org.example.FrontendProductos.segurity.JwtService;
import org.example.FrontendProductos.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;

    public AuthController(AuthService authService, JwtService jwtService) {
        this.authService = authService;
        this.jwtService = jwtService;
    }

    @PostMapping("/auth")
    public ResponseEntity<?> login(@RequestBody AuthRequest request) {
        Usuario usuario = authService.autenticar(request.getUsuario(), request.getContrasena())
                .orElse(null);

        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(java.util.Map.of("message", "Credenciales incorrectas"));
        }

        if (!"activo".equalsIgnoreCase(usuario.getEstado())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(java.util.Map.of("message", "Usuario inactivo"));
        }

        String token = jwtService.generateToken(
                usuario.getUsuario(),
                usuario.getRol(),
                usuario.getEstado()
        );

        return ResponseEntity.ok(new AuthResponse(token, usuario.getUsuario(), usuario.getRol()));
    }
}