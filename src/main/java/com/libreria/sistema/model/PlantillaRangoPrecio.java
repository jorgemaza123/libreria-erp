package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "plantilla_rangos_precio")
public class PlantillaRangoPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaPersonalizada plantilla;

    @Column(nullable = false)
    private Integer cantidadMin = 1;

    private Integer cantidadMax;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal margenMinimoPct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 8, scale = 2)
    private BigDecimal margenObjetivoPct = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal cargoFijo = BigDecimal.ZERO;

    @Column(precision = 8, scale = 2)
    private BigDecimal descuentoMayorPct;

    @Column(nullable = false)
    private Boolean activo = true;

    @PrePersist
    protected void onCreate() {
        if (this.cantidadMin == null || this.cantidadMin <= 0) this.cantidadMin = 1;
        if (this.margenMinimoPct == null) this.margenMinimoPct = BigDecimal.ZERO;
        if (this.margenObjetivoPct == null) this.margenObjetivoPct = BigDecimal.ZERO;
        if (this.cargoFijo == null) this.cargoFijo = BigDecimal.ZERO;
        if (this.activo == null) this.activo = true;
    }
}
