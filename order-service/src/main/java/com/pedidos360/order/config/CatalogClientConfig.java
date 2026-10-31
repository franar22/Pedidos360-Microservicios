package com.pedidos360.order.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP hacia catalog-service con "token relay": reenvia el mismo JWT de Cognito del usuario que
 * hizo la peticion, asi catalog-service aplica sus propias reglas de rol (ADMIN/OPERADOR) y no hace falta
 * una cuenta tecnica. Timeouts cortos para no dejar colgado el pedido si el catalogo esta caido.
 */
@Configuration
public class CatalogClientConfig {

    @Bean
    public RestClient catalogRestClient(@Value("${pedidos360.catalog.base-url}") String baseUrl,
                                        @Value("${pedidos360.catalog.connect-timeout-ms}") int connectTimeoutMs,
                                        @Value("${pedidos360.catalog.read-timeout-ms}") int readTimeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);

        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                    if (auth instanceof JwtAuthenticationToken jwtAuth) {
                        request.getHeaders().setBearerAuth(jwtAuth.getToken().getTokenValue());
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}
