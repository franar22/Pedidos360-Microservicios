package com.pedidos360.order.service;

import com.pedidos360.order.event.OrderEvent;
import com.pedidos360.order.model.OrderTicket;
import com.pedidos360.order.repository.OrderTicketRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.StringJoiner;

/**
 * Genera el ticket (comprobante) de un pedido aceptado. Lo invoca el consumidor RabbitMQ de tickets.
 * Idempotente: un pedido tiene un unico ticket (order_id UNIQUE).
 * Si hay bucket S3 configurado (TICKETS_BUCKET) tambien sube el comprobante (patron claim check).
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    private final OrderTicketRepository ticketRepository;
    private final ObjectProvider<S3Client> s3Client;
    private final String bucket;

    public TicketService(OrderTicketRepository ticketRepository,
                         ObjectProvider<S3Client> s3Client,
                         @Value("${pedidos360.aws.s3.tickets-bucket:}") String bucket) {
        this.ticketRepository = ticketRepository;
        this.s3Client = s3Client;
        this.bucket = bucket;
    }

    @Transactional
    public OrderTicket generate(OrderEvent event) {
        if (ticketRepository.existsByOrderId(event.orderId())) {
            log.info("Ticket del pedido {} ya existe, se ignora el duplicado", event.orderId());
            return ticketRepository.findByOrderId(event.orderId()).orElseThrow();
        }

        String content = render(event);
        OrderTicket ticket = new OrderTicket();
        ticket.setOrderId(event.orderId());
        ticket.setContent(content);

        if (bucket != null && !bucket.isBlank() && s3Client.getIfAvailable() != null) {
            String key = "tickets/pedido-" + event.orderId() + ".txt";
            s3Client.getObject().putObject(
                    PutObjectRequest.builder().bucket(bucket).key(key).contentType("text/plain").build(),
                    RequestBody.fromString(content));
            ticket.setS3Key(key);
            log.info("Ticket del pedido {} subido a s3://{}/{}", event.orderId(), bucket, key);
        }

        OrderTicket saved = ticketRepository.save(ticket);
        log.info("Ticket generado pedido={} total={}", event.orderId(), event.total());
        return saved;
    }

    private String render(OrderEvent event) {
        StringJoiner sj = new StringJoiner("\n");
        sj.add("=== PEDIDOS360 - TICKET ===");
        sj.add("Pedido: #" + event.orderId());
        sj.add("Cliente: " + event.customerEmail());
        sj.add("Estado: " + event.status());
        sj.add("---------------------------");
        if (event.items() != null) {
            for (OrderEvent.Item item : event.items()) {
                sj.add(item.quantity() + " x " + item.productName() + " @ " + item.unitPrice());
            }
        }
        sj.add("---------------------------");
        sj.add("TOTAL: " + event.total());
        return sj.toString();
    }
}
