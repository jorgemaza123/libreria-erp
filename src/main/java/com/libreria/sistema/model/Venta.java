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
@Table(name = "ventas")
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- DATOS SUNAT CABECERA ---
    private String tipoComprobante; // BOLETA, FACTURA, NOTA_VENTA
    private String serie;           // B001, F001
    private Integer numero;         // Correlativo

    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento; // Importante para Crédito
    private String moneda = "PEN";  
    private String tipoOperacion = "0101"; 

    // --- NUEVO: CONDICIÓN DE PAGO (SUNAT) ---
    private String formaPago; // "Contado" o "Crédito"

    // --- DATOS CLIENTE (SNAPSHOT) ---
    // Guardamos los datos en texto por si el cliente cambia de dirección en el futuro,
    // la factura histórica no se altere.
    private String clienteTipoDocumento; 
    private String clienteNumeroDocumento;
    private String clienteDenominacion;
    private String clienteDireccion;

    // --- NUEVO: RELACIÓN CON CLIENTE ---
    // Para poder buscar "Todas las ventas de Juan" o "Deudas de Juan"
    @ManyToOne
    @JoinColumn(name = "cliente_id")
    private Cliente clienteEntity; 

    // --- TOTALES ---
    @Column(precision = 10, scale = 2)
    private BigDecimal totalGravada; 

    @Column(precision = 10, scale = 2)
    private BigDecimal totalIgv;    

    @Column(precision = 10, scale = 2)
    private BigDecimal total;       

    // --- NUEVO: CONTROL DE DEUDA ---
    @Column(precision = 10, scale = 2)
    private BigDecimal montoPagado; // Suma de amortizaciones

    @Column(precision = 10, scale = 2)
    private BigDecimal saldoPendiente; // total - montoPagado

    private String estado; // EMITIDO, ANULADO, PAGADO_TOTAL

    // --- AUDITORÍA ---
    private LocalDateTime fechaCreacion;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario; 

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetalleVenta> items = new ArrayList<>();

    // --- NUEVO: LISTA DE PAGOS ---
    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Amortizacion> amortizaciones = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if(this.fechaEmision == null) this.fechaEmision = LocalDate.now();
        if(this.fechaVencimiento == null) this.fechaVencimiento = LocalDate.now();
        if(this.estado == null) this.estado = "EMITIDO";
        if(this.montoPagado == null) this.montoPagado = BigDecimal.ZERO;
        if(this.saldoPendiente == null) this.saldoPendiente = BigDecimal.ZERO;
    }
}