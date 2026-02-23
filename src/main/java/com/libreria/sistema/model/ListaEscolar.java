package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entidad principal para gestión de Listas Escolares.
 * Representa una lista de útiles de un alumno con sus cotizaciones y ventas parciales.
 */
@Data
@Entity
@Table(name = "listas_escolares",
       uniqueConstraints = @UniqueConstraint(columnNames = {"serie", "numero"}))
public class ListaEscolar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- CORRELATIVO ---
    @Column(nullable = false, length = 10)
    private String serie = "LE001";

    @Column(nullable = false)
    private Integer numero;

    // --- DATOS DEL ALUMNO ---
    @Column(name = "nombre_alumno", nullable = false, length = 200)
    private String nombreAlumno;

    @Column(nullable = false, length = 50)
    private String grado;

    @Column(length = 20)
    private String seccion;

    @Column(name = "colegio", length = 200, columnDefinition = "VARCHAR(200)")
    private String colegio;

    @Column(name = "anio_escolar", nullable = false)
    private Integer anioEscolar;

    // --- DATOS DE CONTACTO (Padre/Apoderado) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id")
    private Cliente cliente;

    @Column(name = "contacto_nombre", length = 200)
    private String contactoNombre;

    @Column(name = "contacto_telefono", length = 20)
    private String contactoTelefono;

    @Column(name = "contacto_email", length = 100)
    private String contactoEmail;

    // --- TEXTO ORIGINAL (OCR) ---
    @Column(name = "texto_original", columnDefinition = "TEXT", nullable = false)
    private String textoOriginal;

    @Column(name = "fuente_texto", length = 50)
    private String fuenteTexto = "OCR_WHATSAPP";

    // --- ESTADO Y FECHAS ---
    @Column(nullable = false, length = 30)
    private String estado = "PENDIENTE";

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    @Column(name = "fecha_vencimiento")
    private LocalDateTime fechaVencimiento;

    @Column(name = "fecha_ultima_venta")
    private LocalDateTime fechaUltimaVenta;

    // --- TOTALES POR NIVEL DE COTIZACIÓN ---
    @Column(name = "total_economico", precision = 12, scale = 2)
    private BigDecimal totalEconomico = BigDecimal.ZERO;

    @Column(name = "total_medio", precision = 12, scale = 2)
    private BigDecimal totalMedio = BigDecimal.ZERO;

    @Column(name = "total_premium", precision = 12, scale = 2)
    private BigDecimal totalPremium = BigDecimal.ZERO;

    // --- PROGRESO ---
    @Column(name = "items_total")
    private Integer itemsTotal = 0;

    @Column(name = "items_vendidos")
    private Integer itemsVendidos = 0;

    @Column(name = "items_pendientes")
    private Integer itemsPendientes = 0;

    // --- MONTOS ACUMULADOS ---
    @Column(name = "monto_vendido", precision = 12, scale = 2)
    private BigDecimal montoVendido = BigDecimal.ZERO;

    @Column(name = "monto_pendiente", precision = 12, scale = 2)
    private BigDecimal montoPendiente = BigDecimal.ZERO;

    // --- COMENTARIOS ---
    @Column(columnDefinition = "TEXT")
    private String comentarios;

    // --- AUDITORÍA ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    // --- RELACIONES ---
    @OneToMany(mappedBy = "listaEscolar", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orden ASC")
    private List<DetalleListaEscolar> detalles = new ArrayList<>();

    @OneToMany(mappedBy = "listaEscolar", cascade = CascadeType.ALL)
    private List<VentaListaEscolar> ventas = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.fechaVencimiento == null) {
            this.fechaVencimiento = this.fechaCreacion.plusDays(15);
        }
        if (this.anioEscolar == null) {
            this.anioEscolar = java.time.Year.now().getValue();
        }
    }

    // --- MÉTODOS DE UTILIDAD ---

    /**
     * Retorna el código completo de la lista (serie-número).
     */
    public String getCodigoCompleto() {
        return String.format("%s-%06d", serie, numero);
    }

    /**
     * Calcula el porcentaje de avance de la lista.
     */
    public int getPorcentajeAvance() {
        if (itemsTotal == null || itemsTotal == 0) return 0;
        return (int) Math.round((double) itemsVendidos / itemsTotal * 100);
    }

    /**
     * Verifica si la lista está vencida.
     */
    public boolean estaVencida() {
        return fechaVencimiento != null && LocalDateTime.now().isAfter(fechaVencimiento);
    }

    /**
     * Verifica si se pueden realizar más ventas.
     */
    public boolean permiteVentas() {
        return "PENDIENTE".equals(estado) ||
               "COTIZADA".equals(estado) ||
               "EN_PROCESO".equals(estado);
    }

    /**
     * Agrega un detalle a la lista.
     */
    public void agregarDetalle(DetalleListaEscolar detalle) {
        detalles.add(detalle);
        detalle.setListaEscolar(this);
        detalle.setOrden(detalles.size());
        this.itemsTotal = detalles.size();
        recalcularPendientes();
    }

    /**
     * Recalcula los contadores de items pendientes.
     */
    public void recalcularPendientes() {
        int vendidos = 0;
        int pendientes = 0;
        for (DetalleListaEscolar d : detalles) {
            if ("VENDIDO".equals(d.getEstado())) {
                vendidos++;
            } else if (!"OMITIDO".equals(d.getEstado()) && !"NO_DISPONIBLE".equals(d.getEstado())) {
                pendientes++;
            }
        }
        this.itemsVendidos = vendidos;
        this.itemsPendientes = pendientes;
    }
}
