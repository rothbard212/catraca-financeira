package com.rothbard212.catraca_financeira;

import com.rothbard212.catraca_financeira.gateway.AssinaturaMercadoPago;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;


class AssinaturaMercadoPagoTest {

    private final AssinaturaMercadoPago validador = new AssinaturaMercadoPago("segredo-de-teste");

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', nullValues = "NULL", value = {
            "assinatura valida|ts=1742505638683,v1=fccd8042f92b95a3ebc26e9ecaf15995473c0a4781c1aa603bc6d35f0467bca2|req-1|123456|true",
            "data.id diferente|ts=1742505638683,v1=fccd8042f92b95a3ebc26e9ecaf15995473c0a4781c1aa603bc6d35f0467bca2|req-1|999999|false",
            "v1 adulterado|ts=1742505638683,v1=fccd8042f92b95a3ebc26e9ecaf15995473c0a4781c1aa603bc6d35f0467bca3|req-1|123456|false",
            "sem cabecalho|NULL|req-1|123456|false",
            "sem v1|ts=1742505638683|req-1|123456|false",
            "sem request-id|ts=1742505638683,v1=fccd8042f92b95a3ebc26e9ecaf15995473c0a4781c1aa603bc6d35f0467bca2|NULL|123456|false"
    })
    void validaAssinatura(String descricao, String xSignature, String xRequestId,
                          String dataId, boolean esperado) {
        assertEquals(esperado, validador.valida(xSignature, xRequestId, dataId));
    }
}
