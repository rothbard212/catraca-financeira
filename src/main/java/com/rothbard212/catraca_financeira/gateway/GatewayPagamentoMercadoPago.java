package com.rothbard212.catraca_financeira.gateway;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;
import com.rothbard212.catraca_financeira.model.StatusPagamento;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Component
public class GatewayPagamentoMercadoPago implements GatewayPagamento {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PagamentoMp(Long id, String status,
                       @JsonProperty("transaction_amount") BigDecimal valor,
                       @JsonProperty("external_reference") String externalReference) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record PreferenciaMp(@JsonProperty("init_point") String initPoint) {}

    private final RestClient client;
    private final String urlNotificacao;
    private final CircuitBreaker breaker = CircuitBreaker.of("mercadopago",
            CircuitBreakerConfig.custom()
                    .slidingWindowSize(10)
                    .failureRateThreshold(50)
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .build());

    public GatewayPagamentoMercadoPago(RestClient mercadoPagoClient,
                                       @Value("${mercadopago.notification-url}") String urlNotificacao) {
        this.client = mercadoPagoClient;
        this.urlNotificacao = urlNotificacao;
    }

    @Override
    public DadosPagamentoGateway consultar(String mpPaymentId) {
        PagamentoMp p = breaker.executeSupplier(() -> client.get()
                .uri("/v1/payments/{id}", mpPaymentId)
                .retrieve().body(PagamentoMp.class));
        return new DadosPagamentoGateway(String.valueOf(p.id()), p.externalReference(),
                traduzirStatus(p.status()), p.valor());
    }

    @Override
    public String criarCheckout(String externalReference, String descricao, BigDecimal valor) {
        Map<String, Object> corpo = Map.of(
                "items", List.of(Map.of("title", descricao, "quantity", 1,
                        "unit_price", valor, "currency_id", "BRL")),
                "external_reference", externalReference,
                "notification_url", urlNotificacao);
        PreferenciaMp pref = breaker.executeSupplier(() -> client.post()
                .uri("/checkout/preferences").body(corpo)
                .retrieve().body(PreferenciaMp.class));
        return pref.initPoint();
    }

    static StatusPagamento traduzirStatus(String statusMp) {
        return switch (statusMp) {
            case "approved", "in_mediation" -> StatusPagamento.APROVADO;
            case "rejected" -> StatusPagamento.RECUSADO;
            case "cancelled" -> StatusPagamento.CANCELADO;
            case "refunded" -> StatusPagamento.REEMBOLSADO;
            case "charged_back" -> StatusPagamento.CHARGEBACK;
            default -> StatusPagamento.PENDENTE;
        };
    }
}