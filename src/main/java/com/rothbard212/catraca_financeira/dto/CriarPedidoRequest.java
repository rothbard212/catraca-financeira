package com.rothbard212.catraca_financeira.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CriarPedidoRequest(@NotEmpty @Valid List<ItemRequest> itens) {
}
