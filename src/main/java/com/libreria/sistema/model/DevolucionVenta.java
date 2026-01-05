package com.libreria.sistema.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Data;
import lombok.ToString;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "devoluciones")
@Data
public class DevolucionVenta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER) // Aseguramos carga para evitar LazyInit en serialización simple
    @JoinColumn(name = "venta_original_id", nullable = false)
    @ToString.Exclude
    @JsonIgnoreProperties({"devoluciones", "hibernateLazyInitializer", "handler"}) // Evita bucles si Venta tiene lista de devoluciones
    private Venta ventaOriginal;

    private String tipoComprobante = "NOTA_CREDITO";

    private String serie; 

    private Integer numero;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    @Column(name = "motivo_devolucion", nullable = false, length = 100)
    private String motivoDevolucion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "total_devuelto", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalDevuelto;

    @Column(name = "metodo_reembolso", nullable = false, length = 50)
    private String metodoReembolso;

    @Column(nullable = false, length = 20)
    private String estado = "PROCESADA";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @ToString.Exclude
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password", "roles"}) // No enviar datos sensibles ni proxy
    private Usuario usuario;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    // --- CAMPOS SUNAT ---
    @Column(name = "sunat_estado", length = 50)
    private String sunatEstado;

    @Column(name = "sunat_hash", length = 255)
    private String sunatHash;

    @Column(name = "sunat_xml_url", length = 500)
    private String sunatXmlUrl;

    @Column(name = "sunat_cdr_url", length = 500)
    private String sunatCdrUrl;

    @Column(name = "sunat_pdf_url", length = 500)
    private String sunatPdfUrl;

    @Column(name = "sunat_fecha_envio")
    private LocalDateTime sunatFechaEnvio;

    @Column(name = "sunat_mensaje_error", columnDefinition = "TEXT")
    private String sunatMensajeError;

    @OneToMany(mappedBy = "devolucion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ToString.Exclude
    @JsonIgnoreProperties({"devolucion", "hibernateLazyInitializer", "handler"}) // CRÍTICO: Rompe el bucle con el hijo
    private List<DetalleDevolucion> detalles = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now();
        }
        if (fechaEmision == null) {
            fechaEmision = LocalDate.now();
        }
        if (estado == null) {
            estado = "PROCESADA";
        }
    }
}