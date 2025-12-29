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

    // Datos SUNAT Cabecera
    private String tipoComprobante; // BOLETA, FACTURA, NOTA_VENTA
    private String serie;           // B001, F001
    private Integer numero;         // Correlativo

    private LocalDate fechaEmision;
    private LocalDate fechaVencimiento;
    private String moneda = "PEN";  // Por defecto Soles
    private String tipoOperacion = "0101"; // Venta Interna

    // Datos Cliente
    private String clienteTipoDocumento; // 1=DNI, 6=RUC, 0=S/D
    private String clienteNumeroDocumento;
    private String clienteDenominacion;
    private String clienteDireccion;

    // Totales (Cálculos Fiscales)
    @Column(precision = 10, scale = 2)
    private BigDecimal totalGravada; // Base imponible

    @Column(precision = 10, scale = 2)
    private BigDecimal totalIgv;     // El impuesto

    @Column(precision = 10, scale = 2)
    private BigDecimal total;        // Lo que paga el cliente

    private String estado; // EMITIDO, ANULADO

    // Auditoría
    private LocalDateTime fechaCreacion;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario; // Quién vendió

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetalleVenta> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if(this.fechaEmision == null) this.fechaEmision = LocalDate.now();
        if(this.fechaVencimiento == null) this.fechaVencimiento = LocalDate.now();
        if(this.estado == null) this.estado = "EMITIDO";
    }
}