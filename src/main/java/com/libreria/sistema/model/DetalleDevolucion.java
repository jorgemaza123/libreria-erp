package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

@Entity
@Table(name = "detalle_devoluciones")
@Data
public class DetalleDevolucion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "devolucion_id", nullable = false)
    private DevolucionVenta devolucion;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(nullable = false, length = 255)
    private String descripcion;

    @Column(name = "cantidad_devuelta", precision = 10, scale = 2, nullable = false)
    private BigDecimal cantidadDevuelta;

    @Column(name = "precio_unitario", precision = 10, scale = 2, nullable = false)
    private BigDecimal precioUnitario;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal subtotal;
}
