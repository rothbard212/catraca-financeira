package com.rothbard212.catraca_financeira.dto;

import com.rothbard212.catraca_financeira.model.StatusPagamento;

import java.math.BigDecimal;

public record DadosPagamentoGateway(String mpPaymentId, StatusPagamento status, BigDecimal valor) {

}
