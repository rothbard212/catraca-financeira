package com.rothbard212.catraca_financeira;

import com.rothbard212.catraca_financeira.model.StatusPagamento;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class StatusPagamentoTest {

    @ParameterizedTest(name = "{0} -> {1} deve ser {2}")
    @CsvSource({
            "PENDENTE, APROVADO, true",
            "PENDENTE, RECUSADO, true",
            "PENDENTE, CANCELADO, true",
            "PENDENTE, REEMBOLSADO, false",
            "PENDENTE, CHARGEBACK, false",
            "PENDENTE, PENDENTE, false",
            "APROVADO, REEMBOLSADO, true",
            "APROVADO, CHARGEBACK, true",
            "APROVADO, PENDENTE, false",
            "APROVADO, RECUSADO, false",
            "APROVADO, CANCELADO, false",
            "APROVADO, APROVADO, false"
    })
    void transicoes(StatusPagamento origem, StatusPagamento destino, boolean esperado) {
        Assertions.assertEquals(esperado, origem.podeIrPara(destino));
    }

    @ParameterizedTest
    @EnumSource(value = StatusPagamento.class,
            names = {"RECUSADO", "CANCELADO", "REEMBOLSADO", "CHARGEBACK"})
    void estadosFinaisNaoSaemParaLugarNenhum(StatusPagamento estadoFinal) {
        for (StatusPagamento destino : StatusPagamento.values()) {
            Assertions.assertFalse(estadoFinal.podeIrPara(destino));
        }
    }
}