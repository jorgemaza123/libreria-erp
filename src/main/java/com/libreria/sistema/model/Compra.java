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
@Table(name = "compras")
public class Compra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Datos del Comprobante del Proveedor
    private String tipoComprobante; // FACTURA, BOLETA, GUIA
    private String serie;
    private String numero;
    
    private LocalDate fechaEmision;
    private LocalDate fechaRecepcion; // Cuándo llegó la mercadería

    // Datos Proveedor
    private String proveedorRuc;
    private String proveedorRazonSocial;

    // Totales
    @Column(precision = 10, scale = 2)
    private BigDecimal totalGravada;

    @Column(precision = 10, scale = 2)
    private BigDecimal totalIgv;

    @Column(precision = 10, scale = 2)
    private BigDecimal total;

    private String estado; // REGISTRADO, ANULADO

    // Auditoría
    private LocalDateTime fechaCreacion;
    
    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @OneToMany(mappedBy = "compra", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<DetalleCompra> items = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if(this.fechaRecepcion == null) this.fechaRecepcion = LocalDate.now();
        if(this.estado == null) this.estado = "REGISTRADO";
    }
}