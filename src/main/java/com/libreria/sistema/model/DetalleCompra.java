package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "detalle_compras")
public class DetalleCompra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "compra_id", nullable = false)
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    private BigDecimal cantidad;

    // Costos
    @Column(precision = 10, scale = 2)
    private BigDecimal costoUnitario; // Sin IGV

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal; // cantidad * costoUnitario
}