package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "actividades_crm", indexes = {
    @Index(name = "idx_activ_cliente", columnList = "cliente_id"),
    @Index(name = "idx_activ_oportunidad", columnList = "oportunidad_id"),
    @Index(name = "idx_activ_tipo", columnList = "tipo"),
    @Index(name = "idx_activ_fecha_prog", columnList = "fechaProgramada")
})
public class ActividadCRM {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String tipo; // LLAMADA, MENSAJE, REUNION, NOTA, RECLAMO, TAREA, EMAIL, VISITA

    @Column(nullable = false, length = 200)
    private String asunto;

    @Column(columnDefinition = "TEXT")
    private String descripcion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "oportunidad_id")
    private OportunidadCRM oportunidad;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario; // Quien registra

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asignado_id")
    private Usuario asignado; // Para tareas

    private LocalDateTime fechaProgramada;

    private Integer duracionMinutos;

    @Column(nullable = false, length = 20)
    private String estado; // PENDIENTE, COMPLETADA, CANCELADA

    private LocalDateTime fechaCompletada;

    @Column(length = 500)
    private String resultado;

    private LocalDateTime fechaCreacion;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.estado == null) this.estado = "PENDIENTE";
    }
}
