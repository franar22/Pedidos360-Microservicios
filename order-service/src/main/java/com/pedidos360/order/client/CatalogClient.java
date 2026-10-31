package com.pedidos360.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pedidos360.order.dto.ApiResponseDTO;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Unica clase que conoce los endpoints de catalog-service. Traduce los errores HTTP a excepciones de
 * negocio para que OrderService no dependa del detalle de la comunicacion.
 */
@Component
public class CatalogClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient restClient, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.objectMapper = objectMapper;
    }

    public ProductView getProduct(Long productId) {
        try {
            ApiResponseDTO<ProductView> body = restClient.get()
                    .uri("/api/catalog/products/{id}", productId)
                    .retrieve()
                    .body(new ParameterizedTypeReference<ApiResponseDTO<ProductView>>() {});
            if (body == null || body.getData() == null) {
                throw new NoSuchElementException("Producto no encontrado: " + productId);
            }
            return body.getData();
        } catch (HttpClientErrorException ex) {
            throw translate(ex, "Producto no encontrado: " + productId);
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new CatalogUnavailableException("catalog-service no disponible", ex);
        }
    }

    /** Descuenta stock de todo el lote (atomico en catalog-service). 409 si no alcanza. */
    public void reserveStock(List<StockItemRequest> items) {
        postStock("/api/catalog/stock/reserve", items);
    }

    /** Devuelve stock (al cancelar un pedido que ya lo habia descontado). */
    public void releaseStock(List<StockItemRequest> items) {
        postStock("/api/catalog/stock/release", items);
    }

    private void postStock(String path, List<StockItemRequest> items) {
        try {
            restClient.post()
                    .uri(path)
                    .body(Map.of("items", items))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException ex) {
            throw translate(ex, "Producto no encontrado");
        } catch (HttpServerErrorException | ResourceAccessException ex) {
            throw new CatalogUnavailableException("catalog-service no disponible", ex);
        }
    }

    private RuntimeException translate(HttpClientErrorException ex, String notFoundMessage) {
        HttpStatusCode status = ex.getStatusCode();
        String remoteMessage = extractMessage(ex);
        if (status.value() == 404) {
            return new NoSuchElementException(remoteMessage != null ? remoteMessage : notFoundMessage);
        }
        if (status.value() == 409) {
            return new IllegalStateException(remoteMessage != null ? remoteMessage : "Conflicto en catalogo");
        }
        if (status.value() == 400) {
            return new IllegalArgumentException(remoteMessage != null ? remoteMessage : "Solicitud invalida");
        }
        if (status.value() == 401 || status.value() == 403) {
            return new AccessDeniedException("catalog-service rechazo la operacion (" + status.value() + ")");
        }
        return new CatalogUnavailableException("catalog-service respondio " + status.value(), ex);
    }

    private String extractMessage(HttpClientErrorException ex) {
        try {
            JsonNode node = objectMapper.readTree(ex.getResponseBodyAsString());
            return node.hasNonNull("message") ? node.get("message").asText() : null;
        } catch (Exception ignored) {
            return null;
        }
    }
}
