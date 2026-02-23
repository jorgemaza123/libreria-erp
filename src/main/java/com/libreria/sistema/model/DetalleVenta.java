package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "detalle_ventas")
public class DetalleVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    // Datos Item SUNAT
    private String unidadMedida; // NIU, ZZ, etc.
    private String descripcion;  // Guardamos el nombre al momento de la venta
    private BigDecimal cantidad;

    // Precios
    @Column(precision = 10, scale = 2)
    private BigDecimal valorUnitario; // Precio SIN IGV (Para SUNAT)

    @Column(precision = 10, scale = 2)
    private BigDecimal precioUnitario; // Precio CON IGV (El que ve el cliente)

    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal; // cantidad * precioUnitario

    // Costo congelado al momento de la venta (para rentabilidad real)
    @Column(precision = 10, scale = 2)
    private BigDecimal costoUnitario; // precioCompra del producto al momento de la venta

    @Column(precision = 10, scale = 2)
    private BigDecimal utilidadUnitaria; // precioUnitario - costoUnitario

    @Column(precision = 10, scale = 2)
    private BigDecimal utilidadTotal; // utilidadUnitaria * cantidad

    // Tipo de item y categoría (propagados desde cotización para rentabilidad)
    private String tipoItem; // PRODUCTO o SERVICIO

    private String categoriaServicio; // SUBLIMACION, COPIAS, etc. (para tracking post-conversión)

    // Impuestos Item
    private BigDecimal porcentajeIgv = new BigDecimal("18.00");
    private String codigoTipoAfectacionIgv = "10"; // 10 = Gravado - Operación Onerosa
}