package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "pedido_personalizado_componentes")
public class PedidoPersonalizadoComponente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_item_id", nullable = false)
    private PedidoPersonalizadoItem pedidoItem;

    @Column(length = 120)
    private String categoria;

    @Column(nullable = false, length = 180)
    private String nombre;

    @Column(nullable = false, length = 20)
    private String tipoOrigen = "MANUAL";

    private Long insumoPersonalizadoId;
    private Long adicionalPersonalizadoId;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoUnitario = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal costoTotal = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean incluido = true;

    @Column(nullable = false)
    private Boolean eliminado = false;

    @Column(nullable = false)
    private Integer orden = 0;

    @PrePersist
    protected void onCreate() {
        if (this.tipoOrigen == null || this.tipoOrigen.isBlank()) this.tipoOrigen = "MANUAL";
        if (this.cantidad == null || this.cantidad.compareTo(BigDecimal.ZERO) <= 0) this.cantidad = BigDecimal.ONE;
        if (this.costoUnitario == null) this.costoUnitario = BigDecimal.ZERO;
        if (this.costoTotal == null) this.costoTotal = BigDecimal.ZERO;
        if (this.incluido == null) this.incluido = true;
        if (this.eliminado == null) this.eliminado = false;
        if (this.orden == null) this.orden = 0;
    }
}
