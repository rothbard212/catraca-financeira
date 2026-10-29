package com.rothbard212.catraca_financeira;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.rothbard212.catraca_financeira.dto.DadosPagamentoGateway;
import com.rothbard212.catraca_financeira.gateway.GatewayPagamento;
import com.rothbard212.catraca_financeira.model.*;
import com.rothbard212.catraca_financeira.repository.EventoWebhookRepository;
import com.rothbard212.catraca_financeira.repository.PagamentoRepository;
import com.rothbard212.catraca_financeira.repository.PedidoRepository;
import com.rothbard212.catraca_financeira.service.WebhookService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private EventoWebhookRepository eventoRepository;

    @Mock
    private PagamentoRepository pagamentoRepository;

    @Mock
    private PedidoRepository pedidoRepository;

    @Mock
    private GatewayPagamento gateway;

    @InjectMocks
    private WebhookService service;

    private Pedido pedido;
    private Pagamento pagamento;

    @BeforeEach
    void setUp() {
        pedido = new Pedido();
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setValorTotal(new BigDecimal("100.00"));

        pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setValor(new BigDecimal("100.00"));
        pagamento.setMpPaymentId("MP-1");
    }

    @Test
    void deveAprovarPagamentoEMarcarPedidoComoPago() {

        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.of(pagamento));
        when(gateway.consultar("MP-1")).thenReturn(
                new DadosPagamentoGateway("MP-1", StatusPagamento.APROVADO, new BigDecimal("100.00")));

        service.processar("evt-1", "{}", "MP-1");

        ArgumentCaptor<EventoWebhook> captor = ArgumentCaptor.forClass(EventoWebhook.class);
        verify(eventoRepository).saveAndFlush(captor.capture());

        assertEquals(StatusPagamento.APROVADO, pagamento.getStatus());
        assertEquals(StatusPedido.PAGO, pedido.getStatus());
        assertEquals("PROCESSADO", captor.getValue().getResultado());
    }

    @Test
    void deveRegistrarPagamentoRecusado() {
        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.of(pagamento));
        when(gateway.consultar("MP-1")).thenReturn(
                new DadosPagamentoGateway("MP-1", StatusPagamento.RECUSADO, new BigDecimal("100.00")));

        service.processar("evt-1", "{}", "MP-1");

        ArgumentCaptor<EventoWebhook> captor = ArgumentCaptor.forClass(EventoWebhook.class);
        verify(eventoRepository).saveAndFlush(captor.capture());

        assertEquals(StatusPagamento.RECUSADO, pagamento.getStatus());
        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedido.getStatus());
        assertEquals("PROCESSADO", captor.getValue().getResultado());
    }

    @Test
    void naoDeveMarcarPedidoComoPagoQuandoValorDiverge() {
        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.of(pagamento));
        when(gateway.consultar("MP-1")).thenReturn(
                new DadosPagamentoGateway("MP-1", StatusPagamento.APROVADO, new BigDecimal("90.00")));

        service.processar("evt-1", "{}", "MP-1");

        ArgumentCaptor<EventoWebhook> captor = ArgumentCaptor.forClass(EventoWebhook.class);
        verify(eventoRepository).saveAndFlush(captor.capture());

        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedido.getStatus());
        assertEquals("VALOR_DIVERGENTE", captor.getValue().getResultado());
    }

    @Test
    void deveRetornarSemMudancaQuandoStatusJaEhOMesmo() {
        pagamento.setStatus(StatusPagamento.APROVADO);
        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.of(pagamento));
        when(gateway.consultar("MP-1")).thenReturn(
                new DadosPagamentoGateway("MP-1", StatusPagamento.APROVADO, new BigDecimal("100.00")));

        service.processar("evt-1", "{}", "MP-1");

        ArgumentCaptor<EventoWebhook> captor = ArgumentCaptor.forClass(EventoWebhook.class);
        verify(eventoRepository).saveAndFlush(captor.capture());

        assertEquals(StatusPedido.AGUARDANDO_PAGAMENTO, pedido.getStatus());
        assertEquals("SEM_MUDANCA", captor.getValue().getResultado());
    }

    @Test
    void deveRejeitarTransicaoInvalida() {
        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.of(pagamento));
        when(gateway.consultar("MP-1")).thenReturn(
                new DadosPagamentoGateway("MP-1", StatusPagamento.REEMBOLSADO, new BigDecimal("100.00")));

        service.processar("evt-1", "{}", "MP-1");

        ArgumentCaptor<EventoWebhook> captor = ArgumentCaptor.forClass(EventoWebhook.class);
        verify(eventoRepository).saveAndFlush(captor.capture());

        assertEquals(StatusPagamento.PENDENTE, pagamento.getStatus());
        assertEquals("TRANSICAO_INVALIDA", captor.getValue().getResultado());
    }

    @Test
    void naoDeveConsultarGatewayQuandoEventoDuplicado() {
        when(eventoRepository.saveAndFlush(any(EventoWebhook.class)))
                .thenThrow(new DataIntegrityViolationException("duplicado"));

        assertThrows(DataIntegrityViolationException.class,
                () -> service.processar("evt-1", "{}", "MP-1"));

        verify(gateway, never()).consultar(any());
    }

    @Test
    void deveLancarExcecaoQuandoPagamentoLocalNaoExiste() {
        when(pagamentoRepository.findByMpPaymentId("MP-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.processar("evt-1", "{}", "MP-1"));

        verify(gateway, never()).consultar(any());
    }


}