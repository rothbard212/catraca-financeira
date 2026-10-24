package com.rothbard212.catraca_financeira.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pedidos")
@Getter
@Setter
public class Pedido {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatusPedido status;

    @Column(name = "valor_total", nullable = false,  precision = 12, scale = 2)
    private BigDecimal valorTotal;

    @Column(nullable = false)
    private LocalDateTime criadoEm;

    @Version
    private Long versao;

}
