package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * Entidad para el sistema de notificaciones.
 * Almacena alertas del sistema como stock bajo, incidencias, créditos vencidos, etc.
 */
@Data
@Entity
@Table(name = "notificaciones")
public class Notificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Tipo de notificación para clasificación e iconos
     */
    @Column(nullable = false, length = 50)
    private String tipo; // STOCK_BAJO, STOCK_AGOTADO, CREDITO_VENCIDO, INCIDENCIA, SISTEMA, INFO, ALERTA

    /**
     * Título corto de la notificación
     */
    @Column(nullable = false, length = 150)
    private String titulo;

    /**
     * Mensaje detallado
     */
    @Column(columnDefinition = "TEXT")
    private String mensaje;

    /**
     * Icono FontAwesome (ej: "fa-box", "fa-exclamation-triangle")
     */
    @Column(length = 50)
    private String icono;

    /**
     * Color del icono (ej: "text-warning", "text-danger")
     */
    @Column(length = 30)
    private String colorIcono;

    /**
     * URL a la que redirige al hacer click (opcional)
     */
    @Column(length = 255)
    private String urlAccion;

    /**
     * Si la notificación ha sido leída
     */
    @Column(nullable = false)
    private Boolean leida = false;

    /**
     * Si la notificación ha sido resuelta/atendida
     */
    @Column(nullable = false)
    private Boolean resuelta = false;

    /**
     * Fecha de creación
     */
    @Column(nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Fecha de lectura
     */
    private LocalDateTime fechaLectura;

    /**
     * Fecha de resolución
     */
    private LocalDateTime fechaResolucion;

    /**
     * Usuario destinatario (null = todos los admins)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    /**
     * Entidad relacionada (para evitar duplicados)
     * Formato: "TIPO:ID" ej: "PRODUCTO:123", "VENTA:456"
     */
    @Column(length = 100)
    private String entidadRelacionada;

    /**
     * Prioridad: BAJA, NORMAL, ALTA, URGENTE
     */
    @Column(length = 20)
    private String prioridad;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.leida == null) this.leida = false;
        if (this.resuelta == null) this.resuelta = false;
        if (this.prioridad == null) this.prioridad = "NORMAL";
    }

    /**
     * Constructor vacío
     */
    public Notificacion() {}

    /**
     * Constructor conveniente
     */
    public Notificacion(String tipo, String titulo, String mensaje, String icono, String colorIcono) {
        this.tipo = tipo;
        this.titulo = titulo;
        this.mensaje = mensaje;
        this.icono = icono;
        this.colorIcono = colorIcono;
    }

    /**
     * Builder pattern para crear notificaciones
     */
    public static NotificacionBuilder builder() {
        return new NotificacionBuilder();
    }

    public static class NotificacionBuilder {
        private final Notificacion notificacion = new Notificacion();

        public NotificacionBuilder tipo(String tipo) {
            notificacion.setTipo(tipo);
            return this;
        }

        public NotificacionBuilder titulo(String titulo) {
            notificacion.setTitulo(titulo);
            return this;
        }

        public NotificacionBuilder mensaje(String mensaje) {
            notificacion.setMensaje(mensaje);
            return this;
        }

        public NotificacionBuilder icono(String icono) {
            notificacion.setIcono(icono);
            return this;
        }

        public NotificacionBuilder color(String color) {
            notificacion.setColorIcono(color);
            return this;
        }

        public NotificacionBuilder url(String url) {
            notificacion.setUrlAccion(url);
            return this;
        }

        public NotificacionBuilder usuario(Usuario usuario) {
            notificacion.setUsuario(usuario);
            return this;
        }

        public NotificacionBuilder entidadRelacionada(String entidad) {
            notificacion.setEntidadRelacionada(entidad);
            return this;
        }

        public NotificacionBuilder prioridad(String prioridad) {
            notificacion.setPrioridad(prioridad);
            return this;
        }

        public Notificacion build() {
            return notificacion;
        }
    }
}
