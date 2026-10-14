package com.rothbard212.catraca_financeira.model;

public enum StatusPagamento {
    PENDENTE, APROVADO, RECUSADO, CANCELADO, REEMBOLSADO, CHARGEBACK;

    public boolean podeIrPara(StatusPagamento destino) {
        return switch (this) {
            case PENDENTE -> destino == APROVADO || destino == RECUSADO || destino == CANCELADO;
            case APROVADO -> destino == REEMBOLSADO || destino == CHARGEBACK;
            default -> false;
        };
    }


}
