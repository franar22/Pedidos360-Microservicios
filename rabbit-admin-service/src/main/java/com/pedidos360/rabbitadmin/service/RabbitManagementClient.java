package com.pedidos360.rabbitadmin.service;

import com.pedidos360.rabbitadmin.exception.BrokerUnavailableException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Map;

/**
 * Lectura de la HTTP API de gestion de RabbitMQ (listar colas, exchanges y bindings).
 * AMQP puede crear/borrar pero no listar, por eso las consultas pasan por aqui.
 */
@Component
public class RabbitManagementClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LIST_TYPE =
            new ParameterizedTypeReference<>() {};

    private final RestClient restClient;
    private final String vhost;

    public RabbitManagementClient(@Qualifier("rabbitManagementRestClient") RestClient restClient,
                                  @Value("${pedidos360.rabbit.management.vhost}") String vhost) {
        this.restClient = restClient;
        this.vhost = vhost;
    }

    public List<Map<String, Object>> listQueues() {
        return list("/api/queues/{vhost}");
    }

    public List<Map<String, Object>> listExchanges() {
        return list("/api/exchanges/{vhost}");
    }

    public List<Map<String, Object>> listBindings() {
        return list("/api/bindings/{vhost}");
    }

    /** true si existe; lanza BrokerUnavailableException si la API de gestion no responde. */
    public boolean exchangeExists(String name) {
        try {
            restClient.get().uri("/api/exchanges/{vhost}/{name}", vhost, name).retrieve().toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound ex) {
            return false;
        } catch (RestClientException ex) {
            throw new BrokerUnavailableException("No se pudo consultar la API de gestion de RabbitMQ", ex);
        }
    }

    private List<Map<String, Object>> list(String path) {
        try {
            List<Map<String, Object>> result = restClient.get().uri(path, vhost).retrieve().body(LIST_TYPE);
            return result == null ? List.of() : result;
        } catch (RestClientException ex) {
            throw new BrokerUnavailableException("No se pudo consultar la API de gestion de RabbitMQ", ex);
        }
    }
}
