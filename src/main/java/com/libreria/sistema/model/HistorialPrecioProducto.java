package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "historial_precios_productos", indexes = {
        @Index(name = "idx_hist_precio_producto_fecha", columnList = "producto_id, fecha"),
        @Index(name = "idx_hist_precio_origen", columnList = "origen")
})
public class HistorialPrecioProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    private LocalDateTime fecha;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioNuevo;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoAnterior;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoNuevo;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioMinimo;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioSugerido;

    @Column(length = 40)
    private String origen;

    @Column(length = 255)
    private String motivo;

    @Column(length = 80)
    private String usuario;

    @PrePersist
    protected void onCreate() {
        if (fecha == null) {
            fecha = LocalDateTime.now();
        }
        if (origen != null) {
            origen = origen.trim().toUpperCase();
        }
    }
}
