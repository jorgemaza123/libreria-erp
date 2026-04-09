package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "plantilla_componentes_personalizado")
public class PlantillaComponentePersonalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plantilla_id", nullable = false)
    private PlantillaPersonalizada plantilla;

    @Column(nullable = false, length = 20)
    private String tipoOrigen = "MANUAL";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "insumo_personalizado_id")
    private InsumoPersonalizado insumoPersonalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "adicional_personalizado_id")
    private AdicionalPersonalizado adicionalPersonalizado;

    @Column(length = 300)
    private String descripcionManual;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal cantidadBase = BigDecimal.ONE;

    @Column(nullable = false, length = 20)
    private String tipoComponente = "BASE";

    @Column(nullable = false)
    private Boolean incluidoPorDefecto = true;

    @Column(nullable = false)
    private Boolean editableCantidad = true;

    @Column(nullable = false)
    private Boolean puedeEliminarse = false;

    @Column(nullable = false)
    private Integer orden = 0;

    @PrePersist
    protected void onCreate() {
        if (this.tipoOrigen == null || this.tipoOrigen.isBlank()) this.tipoOrigen = "MANUAL";
        if (this.tipoComponente == null || this.tipoComponente.isBlank()) this.tipoComponente = "BASE";
        if (this.cantidadBase == null || this.cantidadBase.compareTo(BigDecimal.ZERO) <= 0) this.cantidadBase = BigDecimal.ONE;
        if (this.incluidoPorDefecto == null) this.incluidoPorDefecto = true;
        if (this.editableCantidad == null) this.editableCantidad = true;
        if (this.puedeEliminarse == null) this.puedeEliminarse = false;
        if (this.orden == null) this.orden = 0;
    }
}
