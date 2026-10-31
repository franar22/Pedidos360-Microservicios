package com.pedidos360.catalog.service;

import com.pedidos360.catalog.dto.StockItemDTO;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Reserva y devolucion de stock. Lo invoca order-service por REST (sincrono) porque la regla de negocio
 * "no se puede ACEPTAR un pedido sin stock suficiente" necesita respuesta inmediata
 * (la guia del curso: no usar colas cuando el usuario necesita la respuesta ahora).
 *
 * Todo el lote es atomico: si un item no tiene stock, no se descuenta ninguno.
 */
@Service
public class StockService {

    private final ProductRepository productRepository;
    private final ApplicationEventPublisher events;
    private final int lowStockThreshold;

    public StockService(ProductRepository productRepository,
                        ApplicationEventPublisher events,
                        @Value("${pedidos360.catalog.low-stock-threshold}") int lowStockThreshold) {
        this.productRepository = productRepository;
        this.events = events;
        this.lowStockThreshold = lowStockThreshold;
    }

    @Transactional
    public List<Product> reserve(List<StockItemDTO> items) {
        Map<Long, Integer> needed = aggregate(items);
        List<Product> products = loadLocked(needed);

        for (Product product : products) {
            int remaining = product.getStock() - needed.get(product.getId());
            if (remaining < 0) {
                throw new IllegalStateException("Stock insuficiente para el producto: " + product.getName());
            }
            product.setStock(remaining);
        }

        List<Product> saved = productRepository.saveAll(products);
        for (Product product : saved) {
            if (product.getStock() <= lowStockThreshold) {
                events.publishEvent(new LowStockDetected(
                        product.getId(), product.getName(), product.getStock(), lowStockThreshold));
            }
        }
        return saved;
    }

    @Transactional
    public List<Product> release(List<StockItemDTO> items) {
        Map<Long, Integer> returned = aggregate(items);
        List<Product> products = loadLocked(returned);

        for (Product product : products) {
            product.setStock(product.getStock() + returned.get(product.getId()));
        }
        return productRepository.saveAll(products);
    }

    /** Suma cantidades si el mismo producto viene repetido en el lote. */
    static Map<Long, Integer> aggregate(List<StockItemDTO> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (StockItemDTO item : items) {
            result.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return result;
    }

    private List<Product> loadLocked(Map<Long, Integer> quantities) {
        List<Product> products = productRepository.lockAllByIdIn(quantities.keySet());
        if (products.size() != quantities.size()) {
            List<Long> found = new ArrayList<>();
            for (Product p : products) {
                found.add(p.getId());
            }
            for (Long id : quantities.keySet()) {
                if (!found.contains(id)) {
                    throw new NoSuchElementException("Producto no encontrado: " + id);
                }
            }
        }
        return products;
    }
}
