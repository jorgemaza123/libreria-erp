package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "reposicion_pendiente", indexes = {
        @Index(name = "idx_reposicion_estado_prioridad", columnList = "estado, prioridad"),
        @Index(name = "idx_reposicion_ultima_venta", columnList = "ultimaVenta")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_reposicion_producto", columnNames = "producto_id")
})
public class ReposicionPendiente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(precision = 14, scale = 3)
    private BigDecimal cantidadPendiente = BigDecimal.ZERO;

    @Column(precision = 14, scale = 3)
    private BigDecimal cantidadVendidaAcumulada = BigDecimal.ZERO;

    @Column(precision = 14, scale = 3)
    private BigDecimal cantidadCompradaAplicada = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal montoVentaAcumulado = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal montoReposicionPendiente = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal utilidadBrutaAcumulada = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal costoIndirectoAcumulado = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    private BigDecimal utilidadNetaEstimadaAcumulada = BigDecimal.ZERO;

    private Integer vecesVendida = 0;

    private Integer vecesComprada = 0;

    @Column(length = 20)
    private String estado = "PENDIENTE";

    @Column(length = 20)
    private String prioridad = "MEDIA";

    @Column(length = 500)
    private String notas;

    private LocalDateTime primeraVenta;
    private LocalDateTime ultimaVenta;
    private LocalDateTime ultimaCompra;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        normalizar();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        normalizar();
    }

    public void normalizar() {
        if (cantidadPendiente == null) cantidadPendiente = BigDecimal.ZERO;
        if (cantidadVendidaAcumulada == null) cantidadVendidaAcumulada = BigDecimal.ZERO;
        if (cantidadCompradaAplicada == null) cantidadCompradaAplicada = BigDecimal.ZERO;
        if (montoVentaAcumulado == null) montoVentaAcumulado = BigDecimal.ZERO;
        if (montoReposicionPendiente == null) montoReposicionPendiente = BigDecimal.ZERO;
        if (utilidadBrutaAcumulada == null) utilidadBrutaAcumulada = BigDecimal.ZERO;
        if (costoIndirectoAcumulado == null) costoIndirectoAcumulado = BigDecimal.ZERO;
        if (utilidadNetaEstimadaAcumulada == null) utilidadNetaEstimadaAcumulada = BigDecimal.ZERO;
        if (vecesVendida == null) vecesVendida = 0;
        if (vecesComprada == null) vecesComprada = 0;
        if (estado == null || estado.isBlank()) estado = "PENDIENTE";
        if (prioridad == null || prioridad.isBlank()) prioridad = "MEDIA";
        estado = estado.trim().toUpperCase();
        prioridad = prioridad.trim().toUpperCase();
        if (notas != null) notas = notas.trim();
    }
}
