package com.rothbard212.catraca_financeira.gateway;

import com.rothbard212.catraca_financeira.model.StatusPagamento;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

class GatewayPagamentoMercadoPagoTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "approved, APROVADO",
            "in_mediation, APROVADO",
            "rejected, RECUSADO",
            "cancelled, CANCELADO",
            "refunded, REEMBOLSADO",
            "charged_back, CHARGEBACK",
            "pending, PENDENTE",
            "in_process, PENDENTE",
            "status_inventado, PENDENTE"
    })
    void traduzStatusDoMercadoPago(String statusMp, StatusPagamento esperado) {
        assertEquals(esperado, GatewayPagamentoMercadoPago.traduzirStatus(statusMp));
    }
}
