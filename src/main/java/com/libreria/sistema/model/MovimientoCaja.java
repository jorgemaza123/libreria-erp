package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "movimientos_caja", indexes = {
    @Index(name = "idx_movimiento_tipo_fecha", columnList = "tipo, fecha"),
    @Index(name = "idx_movimiento_fecha", columnList = "fecha"),
    @Index(name = "idx_movimiento_sesion", columnList = "sesion_id")
})
public class MovimientoCaja {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String tipo; // INGRESO, EGRESO

    @Column(nullable = false)
    private String concepto;

    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal monto;

    private LocalDateTime fecha;

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // NUEVO: Vinculación con la sesión de caja
    @ManyToOne
    @JoinColumn(name = "sesion_id")
    private SesionCaja sesion;

    private Long referenciaId;

    @Column(name = "categoria_movimiento", length = 30)
    private String categoriaMovimiento;

    @PrePersist
    protected void onCreate() {
        this.fecha = LocalDateTime.now();
    }
}