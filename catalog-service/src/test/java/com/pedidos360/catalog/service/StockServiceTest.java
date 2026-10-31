package com.pedidos360.catalog.service;

import com.pedidos360.catalog.dto.StockItemDTO;
import com.pedidos360.catalog.model.Product;
import com.pedidos360.catalog.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StockServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private ApplicationEventPublisher events;

    private StockService stockService;

    @BeforeEach
    void setUp() {
        stockService = new StockService(productRepository, events, 5);
    }

    private Product product(long id, String name, int stock) {
        return new Product(id, name, "desc", new BigDecimal("1000"), stock);
    }

    @Test
    void reserve_descuentaStockDeTodoElLote() {
        Product pizza = product(1L, "Pizza", 20);
        when(productRepository.lockAllByIdIn(anyCollection())).thenReturn(List.of(pizza));
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        stockService.reserve(List.of(new StockItemDTO(1L, 2), new StockItemDTO(1L, 3)));

        assertEquals(15, pizza.getStock());
        verify(events, never()).publishEvent(any(LowStockDetected.class));
    }

    @Test
    void reserve_sinStockSuficiente_lanzaIllegalState() {
        Product pizza = product(1L, "Pizza", 1);
        when(productRepository.lockAllByIdIn(anyCollection())).thenReturn(List.of(pizza));

        assertThrows(IllegalStateException.class,
                () -> stockService.reserve(List.of(new StockItemDTO(1L, 2))));

        assertEquals(1, pizza.getStock());
        verify(productRepository, never()).saveAll(anyList());
    }

    @Test
    void reserve_productoInexistente_lanzaNoSuchElement() {
        when(productRepository.lockAllByIdIn(anyCollection())).thenReturn(List.of());

        assertThrows(NoSuchElementException.class,
                () -> stockService.reserve(List.of(new StockItemDTO(99L, 1))));
    }

    @Test
    void reserve_stockBajoUmbral_publicaEventoInterno() {
        Product bebida = product(2L, "Bebida", 8);
        when(productRepository.lockAllByIdIn(anyCollection())).thenReturn(List.of(bebida));
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        stockService.reserve(List.of(new StockItemDTO(2L, 5)));

        assertEquals(3, bebida.getStock());
        verify(events).publishEvent(any(LowStockDetected.class));
    }

    @Test
    void release_devuelveStock() {
        Product pizza = product(1L, "Pizza", 4);
        when(productRepository.lockAllByIdIn(anyCollection())).thenReturn(List.of(pizza));
        when(productRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

        stockService.release(List.of(new StockItemDTO(1L, 6)));

        assertEquals(10, pizza.getStock());
    }
}
