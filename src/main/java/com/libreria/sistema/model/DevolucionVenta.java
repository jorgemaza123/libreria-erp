package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;

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

    @ManyToOne
    @JoinColumn(name = "venta_original_id", nullable = false)
    private Venta ventaOriginal;

    private String tipoComprobante = "NOTA_CREDITO";

    private String serie; // NC01 (interno) o C001 (SUNAT)

    private Integer numero;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDate fechaEmision;

    // PRODUCTO_DEFECTUOSO, ERROR_FACTURACION, CLIENTE_DESISTE, OTRO
    @Column(name = "motivo_devolucion", nullable = false, length = 100)
    private String motivoDevolucion;

    @Column(columnDefinition = "TEXT")
    private String observaciones;

    @Column(name = "total_devuelto", precision = 10, scale = 2, nullable = false)
    private BigDecimal totalDevuelto;

    // EFECTIVO, CREDITO_FAVOR, DEVOLUCION_INVENTARIO
    @Column(name = "metodo_reembolso", nullable = false, length = 50)
    private String metodoReembolso;

    // PROCESADA, ANULADA
    @Column(nullable = false, length = 20)
    private String estado = "PROCESADA";

    @ManyToOne
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(name = "fecha_creacion")
    private LocalDateTime fechaCreacion;

    // --- CAMPOS SUNAT ---
    @Column(name = "sunat_estado", length = 50)
    private String sunatEstado; // NULL, PENDIENTE, ACEPTADO, RECHAZADO

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
