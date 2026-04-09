package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "zonas_entrega_personalizado", indexes = {
        @Index(name = "idx_zona_entrega_personalizado_activa", columnList = "activo")
})
public class ZonaEntregaPersonalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String departamento;

    @Column(nullable = false, length = 100)
    private String provincia;

    @Column(nullable = false, length = 100)
    private String distrito;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal tarifaBase = BigDecimal.ZERO;

    @Column(nullable = false)
    private Integer plazoEstimadoDias = 1;

    @Column(nullable = false)
    private Boolean activo = true;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.tarifaBase == null) this.tarifaBase = BigDecimal.ZERO;
        if (this.plazoEstimadoDias == null || this.plazoEstimadoDias <= 0) this.plazoEstimadoDias = 1;
        if (this.activo == null) this.activo = true;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
