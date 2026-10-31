package com.pedidos360.catalog.controller;

import com.pedidos360.catalog.dto.ApiResponseDTO;
import com.pedidos360.catalog.dto.ProductRequestDTO;
import com.pedidos360.catalog.dto.StockUpdateDTO;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.service.ProductService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogo de productos (mismas rutas y reglas que el monolito):
 *  - Ver catalogo: ADMIN, OPERADOR, CLIENTE
 *  - Crear/editar producto completo: ADMIN
 *  - Administrar stock: ADMIN, OPERADOR
 *  - Eliminar producto: ADMIN
 */
@RestController
@RequestMapping("/api/catalog/products")
public class CatalogController {

    private final ProductService productService;

    public CatalogController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<List<Product>>> list() {
        List<Product> products = productService.findAll();
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Productos obtenidos satisfactoriamente",
                products, (long) products.size()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR', 'CLIENTE')")
    public ResponseEntity<ApiResponseDTO<Product>> getById(@PathVariable Long id) {
        Product product = productService.findById(id);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Producto encontrado", product, 1L));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<Product>> create(@Valid @RequestBody ProductRequestDTO dto) {
        Product product = productService.create(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(new ApiResponseDTO<>(
                true, HttpStatus.CREATED.value(), "Producto creado satisfactoriamente", product, 1L));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<Product>> update(@PathVariable Long id,
                                                          @Valid @RequestBody ProductRequestDTO dto) {
        Product product = productService.update(id, dto);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Producto actualizado satisfactoriamente", product, 1L));
    }

    @PatchMapping("/{id}/stock")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERADOR')")
    public ResponseEntity<ApiResponseDTO<Product>> updateStock(@PathVariable Long id,
                                                               @Valid @RequestBody StockUpdateDTO dto) {
        Product product = productService.updateStock(id, dto.getStock());
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Stock actualizado satisfactoriamente", product, 1L));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponseDTO<Object>> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.ok(new ApiResponseDTO<>(
                true, HttpStatus.OK.value(), "Producto eliminado satisfactoriamente", null, 1L));
    }
}
