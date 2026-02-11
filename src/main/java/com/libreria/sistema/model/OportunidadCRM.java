package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "oportunidades_crm", indexes = {
    @Index(name = "idx_oport_etapa", columnList = "etapa"),
    @Index(name = "idx_oport_cliente", columnList = "cliente_id"),
    @Index(name = "idx_oport_vendedor", columnList = "vendedor_id")
})
public class OportunidadCRM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String titulo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vendedor_id", nullable = false)
    private Usuario vendedor;

    @Column(nullable = false, length = 30)
    private String etapa; // NUEVO, CONTACTADO, COTIZADO, NEGOCIACION, GANADO, PERDIDO

    @Column(precision = 12, scale = 2)
    private BigDecimal valorEstimado;

    @Column(precision = 12, scale = 2)
    private BigDecimal valorReal;

    @Column(columnDefinition = "TEXT")
    private String notas;

    @Column(length = 300)
    private String motivoPerdida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cotizacion_id")
    private Cotizacion cotizacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    private LocalDate fechaCierreEstimada;
    private LocalDate fechaCierreReal;

    private Integer orden = 0; // Para ordenar en kanban

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaActualizacion = LocalDateTime.now();
        if (this.etapa == null) this.etapa = "NUEVO";
        if (this.orden == null) this.orden = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
    }
}
