package com.rothbard212.catraca_financeira.gateway;

import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class GatewayPagamentoMercadoPago implements GatewayPagamento {

    @Override
    public DadosPagamentoGateway consultar(String mpPaymentId) {
        throw new UnsupportedOperationException("Integração com o Mercado Pago ainda não implementada");
    }

    @Override
    public String criarCheckout(String externalReference, String descricao, BigDecimal valor) {
        throw new UnsupportedOperationException("");
    }
}