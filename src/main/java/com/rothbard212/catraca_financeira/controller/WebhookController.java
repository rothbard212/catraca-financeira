package com.rothbard212.catraca_financeira.controller;

import com.rothbard212.catraca_financeira.dto.NotificacaoMercadoPago;
import com.rothbard212.catraca_financeira.gateway.AssinaturaMercadoPago;
import com.rothbard212.catraca_financeira.service.WebhookService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/webhooks")
public class WebhookController {

    private final WebhookService webhookService;
    private final AssinaturaMercadoPago assinatura;
    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    public WebhookController(WebhookService webhookService, AssinaturaMercadoPago assinatura) {
        this.webhookService = webhookService;
        this.assinatura = assinatura;
    }

    @PostMapping("/mercadopago")
    public ResponseEntity<Void> receber(
            @RequestHeader(value = "x-signature", required = false) String xSignature,
            @RequestHeader(value = "x-request-id", required = false) String xRequestId,
            @RequestParam(value = "data.id", required = false) String dataId,
            @RequestBody NotificacaoMercadoPago notificacao) {

        if (!assinatura.valida(xSignature, xRequestId, dataId)) {
            webhookService.registrarAssinaturaInvalida(notificacao.toString());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            webhookService.processar(notificacao.id(), notificacao.toString(), dataId);
        } catch (DataIntegrityViolationException duplicado) {
            log.info("Evento {} já processado, ignorando reenvio", notificacao.id());
        }
        return ResponseEntity.ok().build();
    }
}