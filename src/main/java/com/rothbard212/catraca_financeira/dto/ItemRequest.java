package com.rothbard212.catraca_financeira.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record ItemRequest(@NotBlank String descricao,
                          @NotNull @Positive Integer quantidade,
                          @NotNull @Positive BigDecimal precoUnitario) {
}
