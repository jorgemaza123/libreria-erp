package com.libreria.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
    @JoinColumn(name = "compra_id")
    @JsonIgnore
    private Compra compra;

    @ManyToOne
    @JoinColumn(name = "producto_id")
    private Producto producto;

    private Integer cantidad;
    private BigDecimal precioUnitario; // Costo al que compramos
    private BigDecimal subtotal;

    @Column(length = 40)
    private String tipoCatalogo;

    @Column(length = 80)
    private String presentacionNombre;

    @Column(precision = 12, scale = 3)
    private BigDecimal cantidadPresentacion;

    @Column(precision = 12, scale = 3)
    private BigDecimal factorPresentacion;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioPorPresentacion;
}
