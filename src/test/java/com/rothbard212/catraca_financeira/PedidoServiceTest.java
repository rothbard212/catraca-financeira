package com.rothbard212.catraca_financeira;

import com.rothbard212.catraca_financeira.dto.CheckoutResponse;
import com.rothbard212.catraca_financeira.dto.CriarPedidoRequest;
import com.rothbard212.catraca_financeira.dto.ItemRequest;
import com.rothbard212.catraca_financeira.gateway.GatewayPagamento;
import com.rothbard212.catraca_financeira.model.*;
import com.rothbard212.catraca_financeira.repository.ItemPedidoRepository;
import com.rothbard212.catraca_financeira.repository.PagamentoRepository;
import com.rothbard212.catraca_financeira.repository.PedidoRepository;
import com.rothbard212.catraca_financeira.service.PedidoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {

    @Mock
    private PedidoRepository pedidoRepository;
    @Mock
    private ItemPedidoRepository itemPedidoRepository;
    @Mock
    private PagamentoRepository pagamentoRepository;
    @Mock
    private GatewayPagamento gateway;

    @InjectMocks
    private PedidoService service;

    @BeforeEach
    void setUp() {
        when(pedidoRepository.save(any(Pedido.class))).thenAnswer(inv -> {
            Pedido p = inv.getArgument(0);
            p.setId(10L);
            return p;
        });
        when(pagamentoRepository.save(any(Pagamento.class))).thenAnswer(inv -> {
            Pagamento p = inv.getArgument(0);
            p.setId(20L);
            return p;
        });
    }

    private CriarPedidoRequest requisicao() {
        return new CriarPedidoRequest(List.of(
                new ItemRequest("Camiseta", 2, new BigDecimal("10.00")),
                new ItemRequest("Bone", 1, new BigDecimal("5.50"))));
    }

    @Test
    void deveCalcularTotalDoPedido() {
        service.criarCheckout(requisicao());

        ArgumentCaptor<Pedido> captor = ArgumentCaptor.forClass(Pedido.class);
        verify(pedidoRepository).save(captor.capture());
        assertEquals(new BigDecimal("25.50"), captor.getValue().getValorTotal());
    }

    @Test
    void deveCriarPagamentoPendenteLigadoAoPedido() {
        service.criarCheckout(requisicao());

        ArgumentCaptor<Pagamento> captor = ArgumentCaptor.forClass(Pagamento.class);
        verify(pagamentoRepository).save(captor.capture());
        Pagamento pagamento = captor.getValue();
        assertEquals(StatusPagamento.PENDENTE, pagamento.getStatus());
        assertEquals(10L, pagamento.getPedido().getId());
        assertEquals(new BigDecimal("25.50"), pagamento.getValor());
    }

    @Test
    void deveUsarIdDoPagamentoComoExternalReferenceEDevolverOLink() {
        when(gateway.criarCheckout("20", "Pedido #10", new BigDecimal("25.50")))
                .thenReturn("https://pagar.exemplo/abc");

        CheckoutResponse resposta = service.criarCheckout(requisicao());

        assertEquals("https://pagar.exemplo/abc", resposta.linkPagamento());
        assertEquals(10L, resposta.pedidoId());
        assertEquals(20L, resposta.pagamentoId());
    }

    @Test
    void deveSalvarUmItemParaCadaItemDaRequisicao() {
        service.criarCheckout(requisicao());

        verify(itemPedidoRepository, times(2)).save(any(ItemPedido.class));
    }

    @Test
    void devePropagarFalhaDoGateway() {
        when(gateway.criarCheckout(anyString(), anyString(), any(BigDecimal.class)))
                .thenThrow(new IllegalStateException("gateway fora do ar"));

        assertThrows(IllegalStateException.class, () -> service.criarCheckout(requisicao()));
    }
}
