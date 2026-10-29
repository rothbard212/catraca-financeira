package com.rothbard212.catraca_financeira.gateway;

import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;

public interface GatewayPagamento {
    DadosPagamentoGateway consultar(String mpPaymentId);
}
