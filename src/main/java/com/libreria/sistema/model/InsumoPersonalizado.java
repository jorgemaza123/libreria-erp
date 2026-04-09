package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "insumos_personalizados", indexes = {
        @Index(name = "idx_insumo_personalizado_codigo", columnList = "codigo"),
        @Index(name = "idx_insumo_personalizado_activo", columnList = "activo"),
        @Index(name = "idx_insumo_personalizado_categoria", columnList = "categoria")
})
public class InsumoPersonalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 60)
    private String codigo;

    @Column(nullable = false, length = 200)
    private String nombre;

    @Column(nullable = false, unique = true, length = 220)
    private String slugBusqueda;

    @Column(length = 120)
    private String categoria;

    @Column(length = 120)
    private String subcategoria;

    @Column(nullable = false, length = 30)
    private String unidadBase = "UNIDAD";

    @Column(nullable = false)
    private Boolean controlaStock = true;

    @Column(nullable = false)
    private Boolean activo = true;

    private String foto;

    @Column(length = 1000)
    private String descripcion;

    @Column(columnDefinition = "TEXT")
    private String tags;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false, unique = true)
    private Producto producto;

    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.activo == null) this.activo = true;
        if (this.controlaStock == null) this.controlaStock = true;
        if (this.unidadBase == null || this.unidadBase.isBlank()) this.unidadBase = "UNIDAD";
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.unidadBase == null || this.unidadBase.isBlank()) this.unidadBase = "UNIDAD";
    }
}
