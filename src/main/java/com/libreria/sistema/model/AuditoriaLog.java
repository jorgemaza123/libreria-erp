package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Table(name = "auditoria_logs")
@Data
public class AuditoriaLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String usuario;

    @Column(nullable = false, length = 50)
    private String accion; // CREAR, MODIFICAR, ELIMINAR, ANULAR

    @Column(nullable = false, length = 100)
    private String modulo; // PRODUCTOS, VENTAS, COMPRAS, CAJA, INVENTARIO, CONFIGURACION

    @Column(nullable = false, length = 100)
    private String entidad; // Nombre de la entidad afectada

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(name = "valor_anterior", columnDefinition = "TEXT")
    private String valorAnterior; // JSON con valores antes del cambio

    @Column(name = "valor_nuevo", columnDefinition = "TEXT")
    private String valorNuevo; // JSON con valores después del cambio

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "fecha_hora", nullable = false)
    private LocalDateTime fechaHora;

    @Column(columnDefinition = "TEXT")
    private String detalles; // Información adicional

    @PrePersist
    protected void onCreate() {
        if (fechaHora == null) {
            fechaHora = LocalDateTime.now();
        }
    }
}
