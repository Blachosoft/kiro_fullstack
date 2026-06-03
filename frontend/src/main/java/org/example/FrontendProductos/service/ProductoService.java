package org.example.FrontendProductos.service;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.FrontendProductos.dto.ProductoRequest;
import org.example.FrontendProductos.model.Producto;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ProductoService {

    private final List<Producto> productos = new ArrayList<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final File archivo = new File("data/productos.json");

    public ProductoService() {
        cargarProductos();
    }
    public Producto cambiarEstado(Long id) {
        Producto producto = productos.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if ("activo".equalsIgnoreCase(producto.getEstado())) {
            producto.setEstado("inactivo");
        } else {
            producto.setEstado("activo");
        }

        guardarProductos();
        return producto;
    }

    private void cargarProductos() {
        try {
            if (!archivo.getParentFile().exists()) {
                archivo.getParentFile().mkdirs();
            }

            if (archivo.exists()) {
                List<Producto> cargados = objectMapper.readValue(
                        archivo,
                        new TypeReference<List<Producto>>() {}
                );
                productos.clear();
                productos.addAll(cargados);
            } else {
                productos.add(new Producto(1L, "Arroz Premium", "Arroz blanco de alta calidad", "Granos", 5200.0, 1100.0, "activo"));
                productos.add(new Producto(2L, "Aceite Vegetal", "Aceite para cocina de 1 litro", "Despensa", 14500.0, 14500.0, "activo"));
                productos.add(new Producto(3L, "Jabón Líquido", "Jabón antibacterial para manos", "Aseo", 8900.0, 4450.0, "inactivo"));
                productos.add(new Producto(4L, "Azúcar Morena", "Azúcar natural de caña", "Granos", 4300.0, 860.0, "activo"));
                guardarProductos();
            }
        } catch (IOException e) {
            throw new RuntimeException("No fue posible cargar los productos", e);
        }
    }

    private void guardarProductos() {
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(archivo, productos);
        } catch (IOException e) {
            throw new RuntimeException("No fue posible guardar los productos", e);
        }
    }

    public List<Producto> listar(String nombre, String subcategoria, String estado) {
        return productos.stream()
                .filter(p -> nombre == null || nombre.isBlank() || p.getNombre().toLowerCase().contains(nombre.toLowerCase()))
                .filter(p -> subcategoria == null || subcategoria.isBlank() || p.getSubcategoria().toLowerCase().contains(subcategoria.toLowerCase()))
                .filter(p -> estado == null || estado.isBlank() || p.getEstado().equalsIgnoreCase(estado))
                .collect(Collectors.toList());
    }

    public Producto crear(ProductoRequest request) {
        Long nuevoId = productos.stream()
                .mapToLong(Producto::getId)
                .max()
                .orElse(0L) + 1;

        Producto producto = new Producto(
                nuevoId,
                request.getNombre(),
                request.getDescripcion(),
                request.getSubcategoria(),
                request.getPrecio(),
                request.getPrecioxcantidad(),
                request.getEstado()
        );

        productos.add(producto);
        guardarProductos();
        return producto;
    }

    public Producto actualizar(Long id, ProductoRequest request) {
        Producto producto = productos.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setNombre(request.getNombre());
        producto.setDescripcion(request.getDescripcion());
        producto.setSubcategoria(request.getSubcategoria());
        producto.setPrecio(request.getPrecio());
        producto.setPrecioxcantidad(request.getPrecioxcantidad());
        producto.setEstado(request.getEstado());

        guardarProductos();
        return producto;
    }

    public Producto eliminar(Long id) {
        Producto producto = productos.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        productos.remove(producto);
        guardarProductos();
        return producto;
    }
}