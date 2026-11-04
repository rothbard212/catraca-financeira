package com.rothbard212.catraca_financeira.service;

import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;
import com.rothbard212.catraca_financeira.gateway.GatewayPagamento;
import com.rothbard212.catraca_financeira.model.*;
import com.rothbard212.catraca_financeira.repository.EventoWebhookRepository;
import com.rothbard212.catraca_financeira.repository.PagamentoRepository;
import com.rothbard212.catraca_financeira.repository.PedidoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class WebhookService {

    private final EventoWebhookRepository eventoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final PedidoRepository pedidoRepository;
    private final GatewayPagamento gateway;


    public WebhookService(EventoWebhookRepository eventoRepository,
                          PagamentoRepository pagamentoRepository,
                          PedidoRepository pedidoRepository,
                          GatewayPagamento gateway) {
        this.eventoRepository = eventoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.pedidoRepository = pedidoRepository;
        this.gateway = gateway;
    }

    @Transactional
    public void processar(String chaveIdempotencia, String payload, String mpPaymentId) {
        EventoWebhook evento = new EventoWebhook();
        evento.setChaveIdempotencia(chaveIdempotencia);
        evento.setPayload(payload);
        evento.setAssinaturaValida(true);
        evento.setRecebidoEm(LocalDateTime.now());
        eventoRepository.saveAndFlush(evento);   // duplicado: o banco recusa aqui

        Pagamento pagamento = pagamentoRepository.findByMpPaymentId(mpPaymentId)
                .orElseThrow(() -> new IllegalStateException("Pagamento local não encontrado: " + mpPaymentId));
        DadosPagamentoGateway dados = gateway.consultar(mpPaymentId);

        evento.setResultado(aplicar(pagamento, dados));
        evento.setProcessadoEm(LocalDateTime.now());
    }

    private String aplicar(Pagamento pagamento, DadosPagamentoGateway dados) {
        if (pagamento.getStatus() == dados.status()) {
            return "SEM_MUDANCA";
        }
        if (!pagamento.getStatus().podeIrPara(dados.status())) {
            return "TRANSICAO_INVALIDA";
        }
        pagamento.setStatus(dados.status());
        pagamento.setAtualizadoEm(LocalDateTime.now());

        if (dados.status() == StatusPagamento.APROVADO) {
            Pedido pedido = pagamento.getPedido();
            if (pedido.getValorTotal().compareTo(dados.valor()) != 0) {
                return "VALOR_DIVERGENTE";
            }
            pedido.setStatus(StatusPedido.PAGO);
        }
        return "PROCESSADO";
    }

    @Transactional
    public void registrarAssinaturaInvalida(String payload) {
        EventoWebhook evento = new EventoWebhook();
        evento.setChaveIdempotencia("invalido-" + UUID.randomUUID());
        evento.setPayload(payload);
        evento.setAssinaturaValida(false);
        evento.setRecebidoEm(LocalDateTime.now());
        evento.setProcessadoEm(LocalDateTime.now());
        evento.setResultado("ASSINATURA_INVALIDA");
        eventoRepository.save(evento);
    }
}