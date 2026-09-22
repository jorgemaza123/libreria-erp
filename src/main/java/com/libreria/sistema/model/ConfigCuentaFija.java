package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Entity
@Table(name = "config_cuentas_fijas", indexes = {
        @Index(name = "idx_cuenta_fija_activa", columnList = "activa, orden")
})
public class ConfigCuentaFija {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Column(precision = 12, scale = 2, nullable = false)
    private BigDecimal montoMensual = BigDecimal.ZERO;

    @Column(length = 80)
    private String categoria;

    @Column(length = 40)
    private String tipoCosto = "FIJO";

    @Column(length = 40)
    private String reglaReparto = "UNIDADES";

    @Column(length = 80)
    private String categoriaObjetivo;

    @Column(precision = 12, scale = 2)
    private BigDecimal baseMensual = BigDecimal.ZERO;

    @Column(precision = 5, scale = 2)
    private BigDecimal porcentajeUsoCosteo = new BigDecimal("100.00");

    private Boolean incluirEnCosteo = true;

    @Column(length = 500)
    private String notas;

    private Boolean activa = true;

    private Integer orden = 0;

    @PrePersist
    @PreUpdate
    protected void normalizar() {
        if (this.nombre != null) this.nombre = this.nombre.trim();
        if (this.categoria != null) this.categoria = this.categoria.trim().toUpperCase();
        if (this.tipoCosto == null || this.tipoCosto.isBlank()) this.tipoCosto = "FIJO";
        this.tipoCosto = this.tipoCosto.trim().toUpperCase();
        if (this.reglaReparto == null || this.reglaReparto.isBlank()) this.reglaReparto = "UNIDADES";
        this.reglaReparto = this.reglaReparto.trim().toUpperCase();
        if (this.categoriaObjetivo != null) this.categoriaObjetivo = this.categoriaObjetivo.trim().toUpperCase();
        if (this.montoMensual == null) this.montoMensual = BigDecimal.ZERO;
        if (this.baseMensual == null) this.baseMensual = BigDecimal.ZERO;
        if (this.porcentajeUsoCosteo == null) this.porcentajeUsoCosteo = new BigDecimal("100.00");
        if (this.incluirEnCosteo == null) this.incluirEnCosteo = true;
        if (this.activa == null) this.activa = true;
        if (this.orden == null) this.orden = 0;
        if (this.notas != null) this.notas = this.notas.trim();
    }
}
