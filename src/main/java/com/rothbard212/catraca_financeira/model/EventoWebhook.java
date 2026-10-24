package com.rothbard212.catraca_financeira.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_webhook")
@Getter
@Setter
public class EventoWebhook {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String chaveIdempotencia;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false)
    private boolean assinaturaValida;

    @Column(nullable = false)
    private LocalDateTime recebidoEm;

    @Column(nullable = true)
    private LocalDateTime processadoEm;

    @Column(nullable = true)
    private String resultado;
}