package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "adicionales_personalizados", indexes = {
        @Index(name = "idx_adicional_personalizado_codigo", columnList = "codigo"),
        @Index(name = "idx_adicional_personalizado_activo", columnList = "activo")
})
public class AdicionalPersonalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(nullable = false, length = 160)
    private String nombre;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "categoria_adicional_id")
    private CategoriaAdicionalPersonalizado categoriaAdicional;

    @Column(nullable = false, length = 20)
    private String tipoOrigen = "MANUAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_personalizado_id")
    private InsumoPersonalizado insumoPersonalizado;

    @Column(precision = 12, scale = 2)
    private BigDecimal costoManual;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioBase;

    @Column(nullable = false)
    private Boolean editablePrecio = true;

    @Column(nullable = false)
    private Boolean editableCantidad = true;

    @Column(nullable = false)
    private Boolean activo = true;

    @Column(length = 500)
    private String descripcion;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.tipoOrigen == null || this.tipoOrigen.isBlank()) this.tipoOrigen = "MANUAL";
        if (this.editablePrecio == null) this.editablePrecio = true;
        if (this.editableCantidad == null) this.editableCantidad = true;
        if (this.activo == null) this.activo = true;
        if (this.costoManual == null) this.costoManual = BigDecimal.ZERO;
        if (this.precioBase == null) this.precioBase = BigDecimal.ZERO;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.costoManual == null) this.costoManual = BigDecimal.ZERO;
        if (this.precioBase == null) this.precioBase = BigDecimal.ZERO;
    }
}
