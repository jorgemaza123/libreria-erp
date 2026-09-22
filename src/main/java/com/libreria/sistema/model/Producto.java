package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

@Data
@Entity
@Table(name = "productos", indexes = {
    @Index(name = "idx_producto_activo", columnList = "activo"),
    @Index(name = "idx_producto_activo_stock", columnList = "activo, stockActual"),
    @Index(name = "idx_producto_categoria", columnList = "categoria"),
    @Index(name = "idx_producto_nombre", columnList = "nombre"),
    @Index(name = "idx_producto_es_lamina", columnList = "esLamina"),
    @Index(name = "idx_producto_lamina_numero", columnList = "laminaNumero"),
    @Index(name = "idx_producto_lamina_titulo", columnList = "laminaTitulo"),
    @Index(name = "idx_producto_lamina_marca", columnList = "laminaMarca"),
    @Index(name = "idx_producto_lamina_categoria", columnList = "laminaCategoria")
})
public class Producto {

    public static final String CLASIFICACION_MERCADERIA = "MERCADERIA";
    public static final String CLASIFICACION_INSUMO = "INSUMO";
    public static final String CLASIFICACION_SERVICIO = "SERVICIO";
    public static final String CLASIFICACION_INACTIVO = "INACTIVO";
    public static final String CLASIFICACION_DESCONOCIDO = "DESCONOCIDO";
    public static final List<String> CLASIFICACIONES_INVENTARIO = List.of(
            CLASIFICACION_MERCADERIA,
            CLASIFICACION_INSUMO,
            CLASIFICACION_SERVICIO,
            CLASIFICACION_INACTIVO,
            CLASIFICACION_DESCONOCIDO);
    public static final String ORIGEN_CATALOGO_GENERAL = "GENERAL";
    public static final String ORIGEN_CATALOGO_PERSONALIZADO = "PERSONALIZADO";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- BLOQUEO OPTIMISTA ---
    @Version
    private Long version = 0L;

    @Column(unique = true) 
    private String codigoBarra;

    @Column(unique = true)
    private String codigoInterno;

    @Column(nullable = false)
    private String nombre;

    private String categoria;
    
    @Column(length = 500)
    private String descripcion;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioCompra;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioVenta;

    @Column(precision = 10, scale = 2)
    private BigDecimal precioMayorista;

    @Column(precision = 5, scale = 2)
    private BigDecimal gananciaObjetivoPct;

    @Column(precision = 5, scale = 2)
    private BigDecimal gananciaMinimaPct;

    private Boolean usarReglaManualPrecio;

    @Column(length = 40)
    private String reglaCosteo = "AUTO";

    @Column(precision = 12, scale = 2)
    private BigDecimal unidadesEstimadasMes;

    @Column(precision = 10, scale = 2)
    private BigDecimal minutosTrabajoUnidad;

    @Column(precision = 10, scale = 3)
    private BigDecimal impresionesEquivalentesUnidad;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoIndirectoEstimado;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoTotalEstimado;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioSugeridoEmpresarial;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioMinimoEmpresarial;

    private LocalDateTime fechaUltimoCosteo;

    private Integer stockActual;
    private Integer stockMinimo;
    private Integer stockMaximo;
    private Boolean temporadaActiva = false;
    private Integer stockObjetivoTemporada;
    private Boolean posRapido = false;
    private Integer posRapidoOrden;
    private String unidadMedida;
    
    private String ubicacionFila;
    private String ubicacionColumna;
    private String ubicacionEstante;

    private String tipoAfectacionIgv;
    private boolean activo = true;
    private LocalDateTime fechaCreacion;
    private LocalDateTime fechaActualizacion;

    private String marca;
    private String modelo;
    private String color;
    private String generacion;
    private String tipo;
    private String clasificacion;
    private String origenCatalogo;
    private String imagen;
    private Boolean esLamina = false;
    private String laminaNumero;
    private String laminaTitulo;
    private String laminaMarca;
    private String laminaCategoria;
    private String laminaProveedorRef;
    private String laminaZona;
    private String laminaContenedor;
    private String laminaPosicion;

    private Boolean enLiquidacion = false;
    @Column(precision = 10, scale = 2)
    private BigDecimal precioOriginal;

    /**
     * Tags/Sinónimos para búsqueda inteligente.
     * Almacena palabras clave separadas por comas que ayudan a encontrar el producto.
     * Ejemplo: "diurex, pegamento, scotch, cinta adhesiva"
     */
    @Column(columnDefinition = "TEXT")
    private String tags;

    @PrePersist
    protected void onCreate() {
        this.fechaCreacion = LocalDateTime.now();
        if (this.stockActual == null) this.stockActual = 0;
        if (this.stockMinimo == null) this.stockMinimo = 5;
        if (this.tipoAfectacionIgv == null) this.tipoAfectacionIgv = "GRAVADO";
        if (this.clasificacion == null || this.clasificacion.isBlank()) {
            this.clasificacion = CLASIFICACION_MERCADERIA;
        } else {
            this.clasificacion = normalizarClasificacionInventario(this.clasificacion);
        }
        if (this.origenCatalogo == null || this.origenCatalogo.isBlank()) {
            this.origenCatalogo = ORIGEN_CATALOGO_GENERAL;
        }
        if (this.esLamina == null) {
            this.esLamina = false;
        }
        if (this.usarReglaManualPrecio == null) {
            this.usarReglaManualPrecio = false;
        }
        normalizarCosteo();
        normalizarTemporada();
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.clasificacion == null || this.clasificacion.isBlank()) {
            this.clasificacion = CLASIFICACION_MERCADERIA;
        } else {
            this.clasificacion = normalizarClasificacionInventario(this.clasificacion);
        }
        if (this.origenCatalogo == null || this.origenCatalogo.isBlank()) {
            this.origenCatalogo = ORIGEN_CATALOGO_GENERAL;
        }
        if (this.esLamina == null) {
            this.esLamina = false;
        }
        if (this.usarReglaManualPrecio == null) {
            this.usarReglaManualPrecio = false;
        }
        normalizarCosteo();
        normalizarTemporada();
    }

    private void normalizarCosteo() {
        if (this.reglaCosteo == null || this.reglaCosteo.isBlank()) {
            this.reglaCosteo = "AUTO";
        } else {
            this.reglaCosteo = this.reglaCosteo.trim().toUpperCase();
        }
        if (this.unidadesEstimadasMes != null && this.unidadesEstimadasMes.compareTo(BigDecimal.ZERO) < 0) {
            this.unidadesEstimadasMes = BigDecimal.ZERO;
        }
        if (this.minutosTrabajoUnidad != null && this.minutosTrabajoUnidad.compareTo(BigDecimal.ZERO) < 0) {
            this.minutosTrabajoUnidad = BigDecimal.ZERO;
        }
        if (this.impresionesEquivalentesUnidad != null && this.impresionesEquivalentesUnidad.compareTo(BigDecimal.ZERO) < 0) {
            this.impresionesEquivalentesUnidad = BigDecimal.ZERO;
        }
    }

    private void normalizarTemporada() {
        if (this.temporadaActiva == null) {
            this.temporadaActiva = false;
        }
        if (this.posRapido == null) {
            this.posRapido = false;
        }
        if (this.stockObjetivoTemporada != null && this.stockObjetivoTemporada < 0) {
            this.stockObjetivoTemporada = 0;
        }
        if (this.posRapidoOrden != null && this.posRapidoOrden < 0) {
            this.posRapidoOrden = 0;
        }
        if (this.stockMaximo != null && this.stockMaximo < 0) {
            this.stockMaximo = 0;
        }
    }

    public boolean esInsumo() {
        return CLASIFICACION_INSUMO.equalsIgnoreCase(this.clasificacion);
    }

    public boolean esMercaderiaVendible() {
        return CLASIFICACION_MERCADERIA.equalsIgnoreCase(this.clasificacion)
                && !esCatalogoPersonalizado()
                && this.activo;
    }

    public boolean esServicioInventario() {
        return CLASIFICACION_SERVICIO.equalsIgnoreCase(this.clasificacion)
                || "SERVICIO".equalsIgnoreCase(this.tipo);
    }

    public boolean esInactivoInventario() {
        return CLASIFICACION_INACTIVO.equalsIgnoreCase(this.clasificacion) || !this.activo;
    }

    public boolean esDesconocidoInventario() {
        return CLASIFICACION_DESCONOCIDO.equalsIgnoreCase(this.clasificacion);
    }

    public boolean controlaStockInventario() {
        return !esServicioInventario() && !esInactivoInventario() && !esCatalogoPersonalizado();
    }

    public boolean esVendible() {
        return this.activo && !esInsumo() && !esDesconocidoInventario() && !esInactivoInventario();
    }

    public boolean esCatalogoPersonalizado() {
        return ORIGEN_CATALOGO_PERSONALIZADO.equalsIgnoreCase(this.origenCatalogo);
    }

    public boolean esLamina() {
        return Boolean.TRUE.equals(this.esLamina);
    }

    public String getLaminaUbicacionTexto() {
        StringBuilder ubicacion = new StringBuilder();
        appendUbicacionParte(ubicacion, this.laminaCategoria);
        appendUbicacionParte(ubicacion, this.laminaZona);
        appendUbicacionParte(ubicacion, this.laminaContenedor);
        appendUbicacionParte(ubicacion, this.laminaPosicion);
        return ubicacion.length() > 0 ? ubicacion.toString() : "Sin ubicar";
    }

    public String getUbicacionGeneralTexto() {
        StringBuilder ubicacion = new StringBuilder();
        appendUbicacionParte(ubicacion, this.ubicacionEstante);
        appendUbicacionParte(ubicacion, this.ubicacionFila);
        appendUbicacionParte(ubicacion, this.ubicacionColumna);
        return ubicacion.length() > 0 ? ubicacion.toString() : "-";
    }

    public String getUbicacionResumenTexto() {
        return esLamina() ? getLaminaUbicacionTexto() : getUbicacionGeneralTexto();
    }

    public static String normalizarClasificacionInventario(String valor) {
        if (valor == null || valor.isBlank()) {
            return CLASIFICACION_MERCADERIA;
        }
        String limpio = valor.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        return switch (limpio) {
            case "MERCADERIA", "MERCADERIA_VENDIBLE", "VENDIBLE", "PRODUCTO", "PRODUCTOS" ->
                    CLASIFICACION_MERCADERIA;
            case "INSUMO", "INSUMOS" -> CLASIFICACION_INSUMO;
            case "SERVICIO", "SERVICIOS" -> CLASIFICACION_SERVICIO;
            case "INACTIVO", "INACTIVOS" -> CLASIFICACION_INACTIVO;
            case "DESCONOCIDO", "DESCONOCIDOS", "SIN_CLASIFICAR" -> CLASIFICACION_DESCONOCIDO;
            default -> CLASIFICACION_MERCADERIA;
        };
    }

    public String getLaminaEtiquetaTexto() {
        if (!esLamina()) {
            return null;
        }
        String numero = this.laminaNumero != null && !this.laminaNumero.isBlank()
                ? "#" + this.laminaNumero.trim() + " - "
                : "";
        String titulo = this.laminaTitulo != null && !this.laminaTitulo.isBlank()
                ? this.laminaTitulo.trim()
                : "LAMINA";
        return numero + titulo;
    }

    private void appendUbicacionParte(StringBuilder builder, String valor) {
        if (valor == null || valor.isBlank()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append(" / ");
        }
        builder.append(valor.trim());
    }
}
