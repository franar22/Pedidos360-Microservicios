package com.pedidos360.rabbitadmin.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Cliente de la HTTP API de gestion de RabbitMQ (puerto 15672), usado solo para LISTAR (AMQP no lista). */
@Configuration
public class ManagementClientConfig {

    @Bean
    public RestClient rabbitManagementRestClient(@Value("${pedidos360.rabbit.management.url}") String url,
                                                 @Value("${pedidos360.rabbit.management.username}") String user,
                                                 @Value("${pedidos360.rabbit.management.password}") String password) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder()
                .baseUrl(url)
                .requestFactory(factory)
                .defaultHeaders(h -> h.setBasicAuth(user, password))
                .build();
    }
}
