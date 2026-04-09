package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "pedido_personalizado_items")
public class PedidoPersonalizadoItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "pedido_id", nullable = false)
    private PedidoPersonalizado pedido;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id")
    private PlantillaPersonalizada plantilla;

    @Column(length = 80)
    private String codigoModeloSnapshot;

    @Column(length = 180)
    private String nombreComercialSnapshot;

    private String fotoSnapshot;

    @Column(length = 60)
    private String categoriaSnapshot;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cantidad = BigDecimal.ONE;

    @Column(precision = 12, scale = 2)
    private BigDecimal costoSnapshot = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioMinimoSnapshot = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioSugeridoSnapshot = BigDecimal.ZERO;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioFinal = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String configuracionJson;

    @OneToMany(mappedBy = "pedidoItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PedidoPersonalizadoComponente> componentes = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (this.cantidad == null || this.cantidad.compareTo(BigDecimal.ZERO) <= 0) this.cantidad = BigDecimal.ONE;
        if (this.costoSnapshot == null) this.costoSnapshot = BigDecimal.ZERO;
        if (this.precioMinimoSnapshot == null) this.precioMinimoSnapshot = BigDecimal.ZERO;
        if (this.precioSugeridoSnapshot == null) this.precioSugeridoSnapshot = BigDecimal.ZERO;
        if (this.precioFinal == null) this.precioFinal = BigDecimal.ZERO;
    }
}
