package com.rothbard212.catraca_financeira.service;

import com.rothbard212.catraca_financeira.dto.CheckoutResponse;
import com.rothbard212.catraca_financeira.dto.CriarPedidoRequest;
import com.rothbard212.catraca_financeira.dto.ItemRequest;
import com.rothbard212.catraca_financeira.gateway.GatewayPagamento;
import com.rothbard212.catraca_financeira.model.*;
import com.rothbard212.catraca_financeira.repository.ItemPedidoRepository;
import com.rothbard212.catraca_financeira.repository.PagamentoRepository;
import com.rothbard212.catraca_financeira.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

@Service
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final ItemPedidoRepository itemPedidoRepository;
    private final PagamentoRepository pagamentoRepository;
    private final GatewayPagamento gateway;

    public PedidoService(PedidoRepository pedidoRepository,
                         ItemPedidoRepository itemPedidoRepository,
                         PagamentoRepository pagamentoRepository,
                         GatewayPagamento gateway){
        this.pedidoRepository = pedidoRepository;
        this.itemPedidoRepository = itemPedidoRepository;
        this.pagamentoRepository = pagamentoRepository;
        this.gateway = gateway;
    }

    @Transactional
    public CheckoutResponse criarCheckout(CriarPedidoRequest request) {
        BigDecimal total = request.itens().stream()
                .map(i -> i.precoUnitario().multiply(BigDecimal.valueOf(i.quantidade())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);

        Pedido pedido = new Pedido();
        pedido.setStatus(StatusPedido.AGUARDANDO_PAGAMENTO);
        pedido.setValorTotal(total);
        pedido.setCriadoEm(LocalDateTime.now());
        pedido = pedidoRepository.save(pedido);

        for (ItemRequest i : request.itens()) {
            ItemPedido item = new ItemPedido();
            item.setPedido(pedido);
            item.setDescricao(i.descricao());
            item.setQuantidade(i.quantidade());
            item.setPrecoUnitario(i.precoUnitario());
            itemPedidoRepository.save(item);
        }

        Pagamento pagamento = new Pagamento();
        pagamento.setPedido(pedido);
        pagamento.setStatus(StatusPagamento.PENDENTE);
        pagamento.setValor(total);
        pagamento.setAtualizadoEm(LocalDateTime.now());
        pagamento = pagamentoRepository.save(pagamento);

        String link = gateway.criarCheckout(
                String.valueOf(pagamento.getId()), "Pedido #" + pedido.getId(), total);

        return new CheckoutResponse(pedido.getId(), pagamento.getId(), link);
    }
}
