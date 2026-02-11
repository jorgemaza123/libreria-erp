package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "detalle_cotizaciones")
public class DetalleCotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "cotizacion_id", nullable = false)
    private Cotizacion cotizacion;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private String tipoItem = "PRODUCTO"; // PRODUCTO o SERVICIO

    private String categoriaServicio; // SUBLIMACION, COPIAS, IMPRESION, etc.

    private BigDecimal cantidad;
    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioUnitario;

    @Column(precision = 10, scale = 2)
    private BigDecimal costoEstimado; // Costo estimado para calcular margen

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(columnDefinition = "TEXT")
    private String parametrosJson; // JSON libre para parámetros personalizados del servicio
}
