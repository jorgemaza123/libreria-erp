package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "orden_servicio_pagos", indexes = {
        @Index(name = "idx_orden_servicio_pago_orden", columnList = "orden_servicio_id, fecha"),
        @Index(name = "idx_orden_servicio_pago_fecha", columnList = "fecha")
})
public class OrdenServicioPago {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "orden_servicio_id", nullable = false)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private OrdenServicio ordenServicio;

    private LocalDateTime fecha;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal monto = BigDecimal.ZERO;

    @Column(length = 30)
    private String metodoPago;

    @Column(length = 200)
    private String concepto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "movimiento_caja_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private MovimientoCaja movimientoCaja;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Usuario usuario;

    @PrePersist
    protected void onCreate() {
        if (this.fecha == null) {
            this.fecha = LocalDateTime.now();
        }
        if (this.monto == null) {
            this.monto = BigDecimal.ZERO;
        }
        if (this.metodoPago == null || this.metodoPago.isBlank()) {
            this.metodoPago = "EFECTIVO";
        }
    }
}
