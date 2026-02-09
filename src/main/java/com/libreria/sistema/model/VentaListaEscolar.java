package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad de relación entre ListaEscolar y Venta.
 * Permite tracking de múltiples ventas parciales por lista.
 */
@Data
@Entity
@Table(name = "venta_lista_escolar",
       uniqueConstraints = @UniqueConstraint(columnNames = {"lista_escolar_id", "venta_id"}))
public class VentaListaEscolar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- RELACIONES ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_escolar_id", nullable = false)
    private ListaEscolar listaEscolar;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    // --- TIPO DE VENTA ---
    @Column(name = "es_venta_parcial")
    private Boolean esVentaParcial = true;

    @Column(name = "es_venta_final")
    private Boolean esVentaFinal = false;

    // --- DATOS DE LA TRANSACCIÓN ---
    @Column(name = "items_vendidos", nullable = false)
    private Integer itemsVendidos = 0;

    @Column(name = "monto_vendido", precision = 12, scale = 2, nullable = false)
    private BigDecimal montoVendido = BigDecimal.ZERO;

    // --- AUDITORÍA ---
    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(length = 300)
    private String observaciones;

    @PrePersist
    protected void onCreate() {
        this.fechaRegistro = LocalDateTime.now();
    }

    // --- MÉTODOS DE UTILIDAD ---

    /**
     * Obtiene el comprobante asociado.
     */
    public String getComprobante() {
        if (venta == null) return "";
        return String.format("%s-%s", venta.getSerie(), venta.getNumero());
    }
}
