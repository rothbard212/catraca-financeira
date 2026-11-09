package com.rothbard212.catraca_financeira.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class MercadoPagoConfig {

    @Bean
    RestClient mercadoPagoClient(@Value("${mercadopago.access-token}") String token) {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder()
                .baseUrl("https://api.mercadopago.com")
                .defaultHeader("Authorization", "Bearer " + token)
                .requestFactory(factory)
                .build();
    }
}