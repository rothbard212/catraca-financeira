package com.rothbard212.catraca_financeira.dto;

public record CheckoutResponse(Long pedidoId, Long pagamentoId, String linkPagamento) {
}
