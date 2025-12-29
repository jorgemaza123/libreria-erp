package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "movimientos_caja")
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // INGRESO, EGRESO

    @Column(nullable = false)
    private String concepto; // Ej: "Venta del día", "Pago de Luz", "Taxi"

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal monto;

    private LocalDateTime fecha;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // Opcional: Para vincular con una venta específica si se desea en el futuro
    private Long referenciaId; // ID de Venta o Compra

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}