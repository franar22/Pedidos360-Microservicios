package com.pedidos360.order.service;

import com.pedidos360.order.client.CatalogClient;
import com.pedidos360.order.client.ProductView;
import com.pedidos360.order.client.StockItemRequest;
import com.pedidos360.order.dto.OrderItemRequestDTO;
import com.pedidos360.order.dto.OrderRequestDTO;
import com.pedidos360.order.event.OrderEvent;
import com.pedidos360.order.model.Order;
import com.pedidos360.order.model.OrderAuditLog;
import com.pedidos360.order.model.OrderItem;
import com.pedidos360.order.model.OrderStatus;
import com.pedidos360.order.model.OrderTicket;
import com.pedidos360.order.repository.OrderAuditLogRepository;
import com.pedidos360.order.repository.OrderRepository;
import com.pedidos360.order.repository.OrderTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Logica de pedidos. Cambios respecto al monolito:
 *  - El catalogo (precio, nombre, stock) ya no se lee con un repositorio: se consulta a catalog-service por REST.
 *  - La llamada remota se hace FUERA de la transaccion de BD; si el guardado falla despues de reservar stock,
 *    se compensa devolviendo el stock (mini-saga).
 *  - Cada cambio publica un OrderEvent. Un listener AFTER_COMMIT lo envia a SNS y RabbitMQ: si el broker
 *    esta caido el pedido igual queda guardado (la mensajeria no afecta la logica de negocio).
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final OrderAuditLogRepository auditLogRepository;
    private final OrderTicketRepository ticketRepository;
    private final CatalogClient catalogClient;
    private final TransactionTemplate tx;
    private final ApplicationEventPublisher events;

    public OrderService(OrderRepository orderRepository,
                        OrderAuditLogRepository auditLogRepository,
                        OrderTicketRepository ticketRepository,
                        CatalogClient catalogClient,
                        TransactionTemplate tx,
                        ApplicationEventPublisher events) {
        this.orderRepository = orderRepository;
        this.auditLogRepository = auditLogRepository;
        this.ticketRepository = ticketRepository;
        this.catalogClient = catalogClient;
        this.tx = tx;
        this.events = events;
    }

    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    public List<Order> findByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId);
    }

    public Order findById(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado"));
    }

    public OrderTicket findTicket(Long orderId) {
        return ticketRepository.findByOrderId(orderId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Aun no hay ticket para el pedido " + orderId + " (se genera al ACEPTAR, de forma asincrona)"));
    }

    public List<OrderAuditLog> findAudit(Long orderId) {
        return auditLogRepository.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    public Order create(OrderRequestDTO dto, String customerId, String customerEmail) {
        // 1) Datos del catalogo (remoto, fuera de la transaccion)
        Map<Long, ProductView> products = new LinkedHashMap<>();
        for (OrderItemRequestDTO itemDto : dto.getItems()) {
            if (!products.containsKey(itemDto.getProductId())) {
                products.put(itemDto.getProductId(), catalogClient.getProduct(itemDto.getProductId()));
            }
        }

        // 2) Persistencia + evento (el evento sale a las colas solo si el commit fue exitoso)
        return tx.execute(status -> {
            Order order = new Order();
            order.setCustomerId(customerId);
            order.setCustomerEmail(customerEmail);
            order.setStatus(OrderStatus.CREADO);

            BigDecimal total = BigDecimal.ZERO;
            for (OrderItemRequestDTO itemDto : dto.getItems()) {
                ProductView product = products.get(itemDto.getProductId());

                OrderItem item = new OrderItem();
                item.setProductId(product.id());
                item.setProductName(product.name());
                item.setQuantity(itemDto.getQuantity());
                item.setUnitPrice(product.price());
                order.addItem(item);

                total = total.add(product.price().multiply(BigDecimal.valueOf(itemDto.getQuantity())));
            }
            order.setTotal(total);

            Order saved = orderRepository.save(order);
            events.publishEvent(OrderEvent.created(saved));
            return saved;
        });
    }

    public Order changeStatus(Long id, OrderStatus newStatus) {
        Order current = findById(id);
        OrderStatus from = current.getStatus();

        OrderStateMachine.assertTransition(from, newStatus);

        List<StockItemRequest> stockItems = current.getItems().stream()
                .map(i -> new StockItemRequest(i.getProductId(), i.getQuantity()))
                .toList();
        boolean reserve = OrderStateMachine.reservesStock(newStatus);
        boolean release = OrderStateMachine.releasesStock(from, newStatus);

        // Regla de negocio: el stock disminuye al ACEPTAR (sincrono: necesitamos saber si alcanza).
        if (reserve) {
            catalogClient.reserveStock(stockItems);
        } else if (release) {
            catalogClient.releaseStock(stockItems);
        }

        try {
            return tx.execute(status -> {
                Order locked = orderRepository.findById(id)
                        .orElseThrow(() -> new NoSuchElementException("Pedido no encontrado"));
                if (locked.getStatus() != from) {
                    throw new IllegalStateException(
                            "El pedido cambio de estado mientras se procesaba (ahora esta " + locked.getStatus() + ")");
                }
                locked.setStatus(newStatus);
                locked.setUpdatedAt(LocalDateTime.now());
                Order saved = orderRepository.save(locked);
                events.publishEvent(OrderEvent.statusChanged(saved, from));
                return saved;
            });
        } catch (RuntimeException ex) {
            compensateStock(reserve, release, stockItems);
            throw ex;
        }
    }

    /** Deshace el movimiento de stock si no se pudo guardar el nuevo estado. */
    private void compensateStock(boolean reserved, boolean released, List<StockItemRequest> items) {
        try {
            if (reserved) {
                catalogClient.releaseStock(items);
                log.warn("Compensacion: stock devuelto porque no se pudo guardar el nuevo estado");
            } else if (released) {
                catalogClient.reserveStock(items);
                log.warn("Compensacion: stock re-reservado porque no se pudo guardar la cancelacion");
            }
        } catch (RuntimeException compensationError) {
            log.error("NO se pudo compensar el stock (revisar manualmente): {}", items, compensationError);
        }
    }
}
