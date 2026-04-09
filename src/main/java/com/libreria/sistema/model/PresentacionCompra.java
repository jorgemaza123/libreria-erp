package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "presentaciones_compra", indexes = {
        @Index(name = "idx_presentacion_tipo_catalogo", columnList = "tipoCatalogo"),
        @Index(name = "idx_presentacion_activa", columnList = "activa")
})
public class PresentacionCompra {

    public static final String TIPO_PRODUCTO_GENERAL = "PRODUCTO_GENERAL";
    public static final String TIPO_INSUMO_PERSONALIZADO = "INSUMO_PERSONALIZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String tipoCatalogo = TIPO_PRODUCTO_GENERAL;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_personalizado_id")
    private InsumoPersonalizado insumoPersonalizado;

    @Column(nullable = false, length = 120)
    private String nombrePresentacion;

    @Column(nullable = false, length = 30)
    private String unidadMedidaPresentacion = "UNIDAD";

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal factorBase = BigDecimal.ONE;

    @Column(nullable = false)
    private Boolean permiteDecimal = false;

    @Column(nullable = false)
    private Boolean predeterminada = false;

    @Column(nullable = false)
    private Boolean activa = true;

    @Column(nullable = false)
    private Integer orden = 0;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.tipoCatalogo == null || this.tipoCatalogo.isBlank()) this.tipoCatalogo = TIPO_PRODUCTO_GENERAL;
        if (this.unidadMedidaPresentacion == null || this.unidadMedidaPresentacion.isBlank()) this.unidadMedidaPresentacion = "UNIDAD";
        if (this.factorBase == null || this.factorBase.compareTo(BigDecimal.ZERO) <= 0) this.factorBase = BigDecimal.ONE;
        if (this.permiteDecimal == null) this.permiteDecimal = false;
        if (this.predeterminada == null) this.predeterminada = false;
        if (this.activa == null) this.activa = true;
        if (this.orden == null) this.orden = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.factorBase == null || this.factorBase.compareTo(BigDecimal.ZERO) <= 0) this.factorBase = BigDecimal.ONE;
    }
}
