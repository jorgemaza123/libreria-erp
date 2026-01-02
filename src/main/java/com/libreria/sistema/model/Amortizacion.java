package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "amortizaciones")
public class Amortizacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "venta_id", nullable = false)
    private Venta venta;

    @Column(precision = 10, scale = 2)
    private BigDecimal monto; // Cuánto pagó

    private LocalDateTime fechaPago;
    
    private String metodoPago; // EFECTIVO, YAPE, PLIN, TARJETA
    private String observacion; // Ej: "Adelanto 50%", "Pago final"

    @PrePersist
    protected void onCreate() {
        this.fechaPago = LocalDateTime.now();
    }
}