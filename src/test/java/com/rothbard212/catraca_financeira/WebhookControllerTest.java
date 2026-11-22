package com.rothbard212.catraca_financeira;

import com.rothbard212.catraca_financeira.controller.WebhookController;
import com.rothbard212.catraca_financeira.gateway.AssinaturaMercadoPago;
import com.rothbard212.catraca_financeira.service.WebhookService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookControllerTest {

    @Mock
    private WebhookService webhookService;
    @Mock private AssinaturaMercadoPago assinatura;
    @InjectMocks
    private WebhookController controller;

    @Test
    void ignoraNotificacaoQueNaoEhDePagamento() {
        var resposta = controller.receber(null, null, null, Map.of("topic", "merchant_order"));

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        verifyNoInteractions(webhookService, assinatura);
    }

    @Test
    void rejeitaAssinaturaInvalidaERegistra() {
        when(assinatura.valida(any(), any(), any())).thenReturn(false);

        var resposta = controller.receber("x", "y", "123", Map.of("type", "payment", "id", "evt-1"));

        assertEquals(HttpStatus.UNAUTHORIZED, resposta.getStatusCode());
        verify(webhookService).registrarAssinaturaInvalida(anyString());
        verify(webhookService, never()).processar(any(), any(), any());
    }

    @Test
    void rejeitaPagamentoSemIdOuDataId() {
        when(assinatura.valida(any(), any(), any())).thenReturn(true);

        var resposta = controller.receber("x", "y", null, Map.of("type", "payment"));

        assertEquals(HttpStatus.BAD_REQUEST, resposta.getStatusCode());
        verify(webhookService, never()).processar(any(), any(), any());
    }

    @Test
    void processaPagamentoValido() {
        when(assinatura.valida(any(), any(), any())).thenReturn(true);

        var resposta = controller.receber("x", "y", "123", Map.of("type", "payment", "id", "evt-1"));

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
        verify(webhookService).processar(eq("evt-1"), anyString(), eq("123"));
    }

    @Test
    void respondeOkQuandoEventoEhDuplicado() {
        when(assinatura.valida(any(), any(), any())).thenReturn(true);
        doThrow(new DataIntegrityViolationException("duplicado"))
                .when(webhookService).processar(any(), any(), any());

        var resposta = controller.receber("x", "y", "123", Map.of("type", "payment", "id", "evt-1"));

        assertEquals(HttpStatus.OK, resposta.getStatusCode());
    }
}
