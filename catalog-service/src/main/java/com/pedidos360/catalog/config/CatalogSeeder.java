package com.pedidos360.catalog.config;

import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Carga 3 productos de ejemplo si la tabla esta vacia (desactivar con PEDIDOS360_SEED_ENABLED=false). */
@Component
@ConditionalOnProperty(prefix = "pedidos360.seed", name = "enabled", havingValue = "true")
public class CatalogSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CatalogSeeder.class);

    private final ProductRepository productRepository;

    public CatalogSeeder(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Override
    public void run(String... args) {
        if (productRepository.count() > 0) {
            return;
        }
        productRepository.saveAll(List.of(
                new Product(null, "Pizza Napolitana", "Pizza clasica con tomate, mozzarella y albahaca", new BigDecimal("8990"), 25),
                new Product(null, "Hamburguesa Clasica", "Hamburguesa con queso, lechuga y tomate", new BigDecimal("6490"), 30),
                new Product(null, "Bebida 500ml", "Bebida gaseosa lata 500ml", new BigDecimal("1500"), 100)
        ));
        log.info("Catalogo vacio: se cargaron 3 productos de ejemplo");
    }
}
