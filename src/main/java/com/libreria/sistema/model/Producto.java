package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

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

    private Integer stockActual;
    private Integer stockMinimo;
    private Integer stockMaximo;
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
        }
        if (this.origenCatalogo == null || this.origenCatalogo.isBlank()) {
            this.origenCatalogo = ORIGEN_CATALOGO_GENERAL;
        }
        if (this.esLamina == null) {
            this.esLamina = false;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.fechaActualizacion = LocalDateTime.now();
        if (this.clasificacion == null || this.clasificacion.isBlank()) {
            this.clasificacion = CLASIFICACION_MERCADERIA;
        }
        if (this.origenCatalogo == null || this.origenCatalogo.isBlank()) {
            this.origenCatalogo = ORIGEN_CATALOGO_GENERAL;
        }
        if (this.esLamina == null) {
            this.esLamina = false;
        }
    }

    public boolean esInsumo() {
        return CLASIFICACION_INSUMO.equalsIgnoreCase(this.clasificacion);
    }

    public boolean esVendible() {
        return !esInsumo();
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
