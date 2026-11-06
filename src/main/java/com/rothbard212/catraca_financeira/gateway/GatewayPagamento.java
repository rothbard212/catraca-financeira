package com.rothbard212.catraca_financeira.gateway;

import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;

import java.math.BigDecimal;

public interface GatewayPagamento {
    DadosPagamentoGateway consultar(String mpPaymentId);
    String criarCheckout(String externalReference, String descricao, BigDecimal valor);
}
