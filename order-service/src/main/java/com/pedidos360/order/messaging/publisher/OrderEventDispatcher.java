package com.pedidos360.order.messaging.publisher;

import com.pedidos360.order.event.OrderEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Recibe los OrderEvent que publica OrderService y los reparte a los brokers DESPUES del commit.
 * Asi nunca sale un evento de un pedido que finalmente no se guardo, y la logica de negocio queda
 * desacoplada de la mensajeria (OrderService no conoce RabbitMQ ni SNS).
 *
 * Limitacion conocida (dual write): si el proceso muere justo entre el commit y el envio, el evento se pierde.
 * La solucion de produccion es el patron Transactional Outbox (ver docs/ARQUITECTURA.md).
 */
@Component
@ConditionalOnProperty(prefix = "pedidos360.messaging", name = "enabled", havingValue = "true", matchIfMissing = true)
public class OrderEventDispatcher {

    private final RabbitOrderPublisher rabbitPublisher;
    private final SnsOrderPublisher snsPublisher;

    public OrderEventDispatcher(RabbitOrderPublisher rabbitPublisher, SnsOrderPublisher snsPublisher) {
        this.rabbitPublisher = rabbitPublisher;
        this.snsPublisher = snsPublisher;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onOrderEvent(OrderEvent event) {
        rabbitPublisher.publish(event);
        snsPublisher.publish(event);
    }
}
