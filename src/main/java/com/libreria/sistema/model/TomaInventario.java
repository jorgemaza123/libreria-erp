package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad que representa una sesión de Toma de Inventario Físico.
 * Permite auditar y corregir el stock del sistema mediante conteo físico.
 */
@Data
@Entity
@Table(name = "toma_inventario")
public class TomaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Fecha y hora de inicio de la toma de inventario.
     */
    @Column(nullable = false)
    private LocalDateTime fechaInicio;

    /**
     * Fecha y hora de cierre/procesamiento de la toma.
     */
    private LocalDateTime fechaCierre;

    /**
     * Estado de la toma de inventario.
     * ABIERTO: En proceso de conteo, editable.
     * PROCESADO: Cerrado, ajustes aplicados al stock.
     * CANCELADO: Anulado sin aplicar cambios.
     */
    @Column(length = 20, nullable = false)
    private String estado = "ABIERTO";

    /**
     * Usuario que inició la toma de inventario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /**
     * Usuario que procesó/cerró la toma de inventario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_cierre_id")
    private Usuario usuarioCierre;

    /**
     * Observaciones generales de la toma.
     */
    @Column(columnDefinition = "TEXT")
    private String observaciones;

    /**
     * Código único de la toma para referencia (ej: TI-2024-0001).
     */
    @Column(length = 20, unique = true)
    private String codigo;

    /**
     * Detalles de los productos en esta toma de inventario.
     */
    @OneToMany(mappedBy = "tomaInventario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DetalleTomaInventario> detalles = new ArrayList<>();

    // ========== CAMPOS CALCULADOS (Transient) ==========

    /**
     * Total de productos en la toma.
     */
    @Transient
    public int getTotalProductos() {
        return detalles != null ? detalles.size() : 0;
    }

    /**
     * Productos con diferencia (faltantes o sobrantes).
     */
    @Transient
    public long getProductosConDiferencia() {
        if (detalles == null) return 0;
        return detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() != 0)
                .count();
    }

    /**
     * Total de faltantes (diferencias negativas).
     */
    @Transient
    public int getTotalFaltantes() {
        if (detalles == null) return 0;
        return detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() < 0)
                .mapToInt(d -> Math.abs(d.getDiferencia()))
                .sum();
    }

    /**
     * Total de sobrantes (diferencias positivas).
     */
    @Transient
    public int getTotalSobrantes() {
        if (detalles == null) return 0;
        return detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() > 0)
                .mapToInt(DetalleTomaInventario::getDiferencia)
                .sum();
    }

    @PrePersist
    protected void onCreate() {
        this.fechaInicio = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "ABIERTO";
        }
    }

    // ========== CONSTANTES DE ESTADO ==========
    public static final String ESTADO_ABIERTO = "ABIERTO";
    public static final String ESTADO_PROCESADO = "PROCESADO";
    public static final String ESTADO_CANCELADO = "CANCELADO";
}
