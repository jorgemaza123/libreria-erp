package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad para registrar la facturacion mensual del consumo SUNAT.
 * Guarda el historial de comprobantes emitidos y el estado de pago.
 */
@Entity
@Table(name = "monthly_billing")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyBilling {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Mes y anio en formato "MM-YYYY" (ej: "01-2026")
     */
    @Column(name = "mes_anio", length = 7, nullable = false, unique = true)
    private String mesAnio;

    /**
     * Cantidad total de comprobantes electronicos (Boletas + Facturas)
     */
    @Column(name = "cantidad_comprobantes", nullable = false)
    private Integer cantidadComprobantes = 0;

    /**
     * Cantidad de boletas emitidas
     */
    @Column(name = "cantidad_boletas")
    private Integer cantidadBoletas = 0;

    /**
     * Cantidad de facturas emitidas
     */
    @Column(name = "cantidad_facturas")
    private Integer cantidadFacturas = 0;

    /**
     * Monto calculado segun la tabla de tarifas
     */
    @Column(name = "monto_calculado", precision = 10, scale = 2, nullable = false)
    private BigDecimal montoCalculado = BigDecimal.ZERO;

    /**
     * Estado del pago
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "estado_pago", length = 20, nullable = false)
    private EstadoPago estadoPago = EstadoPago.PENDIENTE;

    /**
     * Fecha en que se realizo el pago
     */
    @Column(name = "fecha_pago")
    private LocalDateTime fechaPago;

    /**
     * Fecha en que se genero este registro
     */
    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    /**
     * Ultima actualizacion del registro
     */
    @Column(name = "fecha_actualizacion")
    private LocalDateTime fechaActualizacion;

    /**
     * Observaciones o notas del pago
     */
    @Column(name = "observaciones", length = 500)
    private String observaciones;

    /**
     * Enum para el estado de pago
     */
    public enum EstadoPago {
        PENDIENTE,  // Aun no se ha pagado
        PAGADO,     // Ya fue pagado
        GRATIS      // No requiere pago (<=20 comprobantes)
    }

    @PrePersist
    protected void onCreate() {
        fechaCreacion = LocalDateTime.now();
        fechaActualizacion = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        fechaActualizacion = LocalDateTime.now();
    }

    /**
     * Constructor para crear un nuevo registro de mes
     */
    public MonthlyBilling(String mesAnio) {
        this.mesAnio = mesAnio;
        this.cantidadComprobantes = 0;
        this.cantidadBoletas = 0;
        this.cantidadFacturas = 0;
        this.montoCalculado = BigDecimal.ZERO;
        this.estadoPago = EstadoPago.PENDIENTE;
    }

    /**
     * Verifica si el mes tiene deuda pendiente
     */
    public boolean tieneDeudaPendiente() {
        return estadoPago == EstadoPago.PENDIENTE &&
               montoCalculado.compareTo(BigDecimal.ZERO) > 0;
    }
}
