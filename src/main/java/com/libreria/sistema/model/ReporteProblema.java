package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entidad para registrar problemas/incidencias encontradas en la tienda.
 * Permite a los vendedores reportar productos dañados, defectuosos,
 * problemas de infraestructura, etc.
 */
@Data
@Entity
@Table(name = "reportes_problemas")
public class ReporteProblema {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de problema reportado
     */
    @Column(nullable = false, length = 50)
    private String tipoProblema; // PRODUCTO_DANADO, PRODUCTO_DEFECTUOSO, FALTANTE, INFRAESTRUCTURA, OTRO

    /**
     * Título breve del problema
     */
    @Column(nullable = false, length = 150)
    private String titulo;

    /**
     * Descripción detallada del problema
     */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String descripcion;

    /**
     * Producto relacionado (opcional, si el problema es de un producto específico)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    /**
     * Ubicación donde se encontró el problema
     */
    @Column(length = 100)
    private String ubicacion;

    /**
     * Nivel de urgencia: BAJA, MEDIA, ALTA, CRITICA
     */
    @Column(nullable = false, length = 20)
    private String urgencia;

    /**
     * Estado del reporte: PENDIENTE, EN_REVISION, RESUELTO, DESCARTADO
     */
    @Column(nullable = false, length = 20)
    private String estado;

    /**
     * Usuario que reportó el problema
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuarioReporta;

    /**
     * Usuario que resolvió/atendió el problema (opcional)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_atiende_id")
    private Usuario usuarioAtiende;

    /**
     * Fecha y hora del reporte
     */
    @Column(nullable = false)
    private LocalDateTime fechaReporte;

    /**
     * Fecha y hora de resolución (si aplica)
     */
    private LocalDateTime fechaResolucion;

    /**
     * Comentario de resolución o seguimiento
     */
    @Column(columnDefinition = "TEXT")
    private String comentarioResolucion;

    /**
     * Imagen del problema (base64 o ruta)
     */
    @Column(columnDefinition = "TEXT")
    private String imagenBase64;

    /**
     * Cantidad afectada (para productos)
     */
    private Integer cantidadAfectada;

    /**
     * Acción tomada: DESCUENTO, DEVOLUCION_PROVEEDOR, BAJA_INVENTARIO, REPARACION, OTRO
     */
    @Column(length = 50)
    private String accionTomada;

    @PrePersist
    protected void onCreate() {
        this.fechaReporte = LocalDateTime.now();
        if (this.estado == null) {
            this.estado = "PENDIENTE";
        }
        if (this.urgencia == null) {
            this.urgencia = "MEDIA";
        }
    }
}
