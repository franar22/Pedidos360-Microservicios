package com.pedidos360.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de exchanges, colas y routing keys. UNICO lugar donde se definen (application.yml, prefijo
 * pedidos360.rabbit). Ninguna clase de negocio escribe nombres de colas "a mano".
 */
@ConfigurationProperties(prefix = "pedidos360.rabbit")
public record RabbitNamesProperties(
        Exchanges exchanges,
        Queues queues,
        RoutingKeys routingKeys,
        String dlqSuffix) {

    public record Exchanges(String direct, String topic, String dlx) {}

    public record Queues(String notificaciones, String tickets, String inventario) {}

    public record RoutingKeys(String ticket,
                              String notificacionesBinding,
                              String pedidoCreado,
                              String pedidoEstadoPrefix,
                              String inventarioBinding,
                              String stockBajo) {}

    /** Nombre de la DLQ asociada a una cola. */
    public String dlqOf(String queue) {
        return queue + dlqSuffix;
    }
}
