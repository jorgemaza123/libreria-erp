package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "cotizaciones")
public class Cotizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String serie;  // C001
    private Integer numero;

    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;

    // Cliente
    private String clienteDocumento;
    private String clienteNombre;
    private String clienteTelefono;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(columnDefinition = "TEXT")
    private String condiciones;

    // Totales fiscales
    @Column(precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(precision = 10, scale = 2)
    private BigDecimal igv;

    @Column(precision = 10, scale = 2)
    private BigDecimal descuento;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    // Forma de pago
    private String formaPago;    // CONTADO o CREDITO
    private String metodoPago;   // EFECTIVO, YAPE, PLIN, TARJETA, TRANSFERENCIA

    @Column(precision = 10, scale = 2)
    private BigDecimal montoInicial;

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoPendiente;

    private Integer diasCredito;

    // Estado: BORRADOR, EMITIDA, ENVIADA, APROBADA, CONVERTIDA, VENCIDA, ANULADA
    private String estado;

    // Trazabilidad a venta generada
    @Column(name = "venta_id")
    private Long ventaId;

    private LocalDateTime fechaCreacion;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente clienteEntity;

    @OneToMany(mappedBy = "cotizacion", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    private List<DetalleCotizacion> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        this.fechaEmision = LocalDate.now();
        if (this.fechaVencimiento == null) this.fechaVencimiento = LocalDate.now().plusDays(15);
        if (this.estado == null) this.estado = "EMITIDA";
        if (this.formaPago == null) this.formaPago = "CONTADO";
        if (this.descuento == null) this.descuento = BigDecimal.ZERO;
        if (this.montoInicial == null) this.montoInicial = BigDecimal.ZERO;
        if (this.saldoPendiente == null) this.saldoPendiente = BigDecimal.ZERO;
    }
}
