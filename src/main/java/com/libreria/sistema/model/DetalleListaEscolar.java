package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle de cada item de una Lista Escolar.
 * Contiene el texto original, los productos cotizados en 3 niveles de precio,
 * y el seguimiento del estado de venta.
 */
@Data
@Entity
@Table(name = "detalle_lista_escolar")
public class DetalleListaEscolar {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // --- RELACIÓN CON LISTA PADRE ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lista_escolar_id", nullable = false)
    private ListaEscolar listaEscolar;

    // --- TEXTO ORIGINAL DEL ITEM ---
    @Column(name = "texto_original", nullable = false, length = 500)
    private String textoOriginal;

    @Column(name = "cantidad_solicitada", nullable = false)
    private Integer cantidadSolicitada = 1;

    // --- MATCH AUTOMÁTICO ---
    @Column(name = "confianza_match", precision = 5, scale = 2)
    private BigDecimal confianzaMatch;

    @Column(name = "match_automatico")
    private Boolean matchAutomatico = false;

    // --- PRODUCTO PRINCIPAL ASIGNADO (puede ser NULL si no hay match) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id")
    private Producto producto;

    @Column(name = "producto_nombre_snapshot", length = 200)
    private String productoNombreSnapshot;

    // --- NIVEL ECONÓMICO ---
    @Column(name = "precio_economico", precision = 10, scale = 2)
    private BigDecimal precioEconomico;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_economico_id")
    private Producto productoEconomico;

    // --- NIVEL MEDIO ---
    @Column(name = "precio_medio", precision = 10, scale = 2)
    private BigDecimal precioMedio;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_medio_id")
    private Producto productoMedio;

    // --- NIVEL PREMIUM ---
    @Column(name = "precio_premium", precision = 10, scale = 2)
    private BigDecimal precioPremium;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_premium_id")
    private Producto productoPremium;

    // --- NIVEL SELECCIONADO PARA VENTA ---
    @Column(name = "nivel_seleccionado", length = 20)
    private String nivelSeleccionado; // ECONOMICO, MEDIO, PREMIUM

    @Column(name = "precio_final", precision = 10, scale = 2)
    private BigDecimal precioFinal;

    // --- ESTADO DEL ITEM ---
    @Column(nullable = false, length = 30)
    private String estado = "NO_COTIZADO";

    // --- PRODUCTO REEMPLAZO ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_reemplazo_id")
    private Producto productoReemplazo;

    @Column(name = "motivo_reemplazo", length = 200)
    private String motivoReemplazo;

    // --- VENTA ASOCIADA (si fue vendido) ---
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venta_id")
    private Venta venta;

    @Column(name = "detalle_venta_id")
    private Long detalleVentaId;

    @Column(name = "fecha_venta")
    private LocalDateTime fechaVenta;

    // --- ORDEN DE VISUALIZACIÓN ---
    @Column(nullable = false)
    private Integer orden = 0;

    // --- OBSERVACIONES ---
    @Column(length = 300)
    private String observaciones;

    // --- COTIZACIÓN MANUAL (PROVEEDOR EXTERNO) ---
    @Column(name = "cotizado_proveedor_economico")
    private Boolean cotizadoProveedorEconomico = false;

    @Column(name = "cotizado_proveedor_medio")
    private Boolean cotizadoProveedorMedio = false;

    @Column(name = "cotizado_proveedor_premium")
    private Boolean cotizadoProveedorPremium = false;

    @Column(name = "nombre_proveedor", length = 100)
    private String nombreProveedor;

    @Column(name = "descripcion_manual_economico", length = 200)
    private String descripcionManualEconomico;

    @Column(name = "descripcion_manual_medio", length = 200)
    private String descripcionManualMedio;

    @Column(name = "descripcion_manual_premium", length = 200)
    private String descripcionManualPremium;

    @Column(name = "producto_comprado")
    private Boolean productoComprado = false;

    @Column(name = "fecha_cotizacion_proveedor")
    private LocalDateTime fechaCotizacionProveedor;

    // --- REGALOS/OFERTAS ---
    @Column(name = "es_regalo")
    private Boolean esRegalo = false;

    @Column(name = "texto_regalo", length = 200)
    private String textoRegalo;

    @Column(name = "nivel_regalo", length = 20)
    private String nivelRegalo; // ECONOMICO, MEDIO, PREMIUM - indica a qué nivel aplica el regalo

    // --- MÉTODOS DE UTILIDAD ---

    /**
     * Obtiene el producto final a usar según el nivel seleccionado.
     */
    public Producto getProductoFinal() {
        if (productoReemplazo != null) {
            return productoReemplazo;
        }
        if (nivelSeleccionado == null) {
            return producto;
        }
        return switch (nivelSeleccionado) {
            case "ECONOMICO" -> productoEconomico != null ? productoEconomico : producto;
            case "MEDIO" -> productoMedio != null ? productoMedio : producto;
            case "PREMIUM" -> productoPremium != null ? productoPremium : producto;
            default -> producto;
        };
    }

    /**
     * Obtiene el ID del producto final.
     */
    public Long getProductoFinalId() {
        Producto p = getProductoFinal();
        return p != null ? p.getId() : null;
    }

    /**
     * Obtiene el precio según el nivel seleccionado.
     */
    public BigDecimal getPrecioSegunNivel() {
        if (precioFinal != null) {
            return precioFinal;
        }
        if (nivelSeleccionado == null) {
            return precioMedio != null ? precioMedio : BigDecimal.ZERO;
        }
        return switch (nivelSeleccionado) {
            case "ECONOMICO" -> precioEconomico != null ? precioEconomico : BigDecimal.ZERO;
            case "MEDIO" -> precioMedio != null ? precioMedio : BigDecimal.ZERO;
            case "PREMIUM" -> precioPremium != null ? precioPremium : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Calcula el subtotal (cantidad * precio).
     */
    public BigDecimal getSubtotal() {
        BigDecimal precio = getPrecioSegunNivel();
        return precio.multiply(BigDecimal.valueOf(cantidadSolicitada));
    }

    /**
     * Verifica si el item permite ser vendido.
     */
    public boolean permiteVenta() {
        return "COTIZADO".equals(estado) ||
               "PENDIENTE".equals(estado) ||
               "REEMPLAZADO".equals(estado);
    }

    /**
     * Verifica si el item ya fue vendido.
     */
    public boolean estaVendido() {
        return "VENDIDO".equals(estado);
    }

    /**
     * Verifica si tiene producto asignado.
     */
    public boolean tieneProducto() {
        return producto != null || productoEconomico != null ||
               productoMedio != null || productoPremium != null ||
               productoReemplazo != null;
    }

    /**
     * Verifica si tiene stock disponible.
     */
    public boolean tieneStockDisponible() {
        Producto p = getProductoFinal();
        if (p == null) return false;
        if ("SERVICIO".equals(p.getTipo())) return true;
        return p.getStockActual() != null && p.getStockActual() >= cantidadSolicitada;
    }

    /**
     * Marca el item como vendido.
     */
    public void marcarVendido(Venta venta, Long detalleVentaId) {
        this.estado = "VENDIDO";
        this.venta = venta;
        this.detalleVentaId = detalleVentaId;
        this.fechaVenta = LocalDateTime.now();
    }

    /**
     * Marca el item como pendiente.
     */
    public void marcarPendiente() {
        if (!estaVendido()) {
            this.estado = "PENDIENTE";
        }
    }

    /**
     * Reemplaza el producto por otro.
     */
    public void reemplazarProducto(Producto nuevoProducto, String motivo) {
        this.productoReemplazo = nuevoProducto;
        this.motivoReemplazo = motivo;
        this.precioFinal = nuevoProducto.getPrecioVenta();
        this.estado = "REEMPLAZADO";
    }

    /**
     * Verifica si el item es un regalo/oferta.
     */
    public boolean esItemRegalo() {
        return Boolean.TRUE.equals(esRegalo);
    }

    /**
     * Crea un item como regalo.
     */
    public static DetalleListaEscolar crearRegalo(ListaEscolar lista, String texto, String nivel, int orden) {
        DetalleListaEscolar regalo = new DetalleListaEscolar();
        regalo.setListaEscolar(lista);
        regalo.setTextoOriginal(texto);
        regalo.setTextoRegalo(texto);
        regalo.setCantidadSolicitada(1);
        regalo.setEsRegalo(true);
        regalo.setNivelRegalo(nivel);
        regalo.setEstado("COTIZADO");
        regalo.setOrden(orden);
        regalo.setPrecioEconomico(BigDecimal.ZERO);
        regalo.setPrecioMedio(BigDecimal.ZERO);
        regalo.setPrecioPremium(BigDecimal.ZERO);
        regalo.setPrecioFinal(BigDecimal.ZERO);
        return regalo;
    }

    /**
     * Obtiene el nombre para mostrar del producto por nivel.
     */
    public String getNombreProductoPorNivel(String nivel) {
        if (esItemRegalo()) {
            return textoRegalo != null ? textoRegalo : textoOriginal;
        }
        Producto p = switch (nivel) {
            case "ECONOMICO" -> productoEconomico;
            case "MEDIO" -> productoMedio;
            case "PREMIUM" -> productoPremium;
            default -> producto;
        };
        return p != null ? p.getNombre() : null;
    }

    /**
     * Obtiene el precio por nivel específico.
     */
    public BigDecimal getPrecioPorNivel(String nivel) {
        return switch (nivel) {
            case "ECONOMICO" -> precioEconomico != null ? precioEconomico : BigDecimal.ZERO;
            case "MEDIO" -> precioMedio != null ? precioMedio : BigDecimal.ZERO;
            case "PREMIUM" -> precioPremium != null ? precioPremium : BigDecimal.ZERO;
            default -> BigDecimal.ZERO;
        };
    }

    /**
     * Obtiene el ID del producto por nivel.
     */
    public Long getProductoIdPorNivel(String nivel) {
        Producto p = switch (nivel) {
            case "ECONOMICO" -> productoEconomico;
            case "MEDIO" -> productoMedio;
            case "PREMIUM" -> productoPremium;
            default -> producto;
        };
        return p != null ? p.getId() : null;
    }

    /**
     * Verifica si este item tiene cotización manual de proveedor para un nivel.
     */
    public boolean esCotizacionProveedorPorNivel(String nivel) {
        return switch (nivel) {
            case "ECONOMICO" -> Boolean.TRUE.equals(cotizadoProveedorEconomico);
            case "MEDIO" -> Boolean.TRUE.equals(cotizadoProveedorMedio);
            case "PREMIUM" -> Boolean.TRUE.equals(cotizadoProveedorPremium);
            default -> false;
        };
    }

    /**
     * Verifica si tiene alguna cotización de proveedor.
     */
    public boolean tieneCotizacionProveedor() {
        return Boolean.TRUE.equals(cotizadoProveedorEconomico) ||
               Boolean.TRUE.equals(cotizadoProveedorMedio) ||
               Boolean.TRUE.equals(cotizadoProveedorPremium);
    }

    /**
     * Obtiene la descripción manual por nivel.
     */
    public String getDescripcionManualPorNivel(String nivel) {
        return switch (nivel) {
            case "ECONOMICO" -> descripcionManualEconomico;
            case "MEDIO" -> descripcionManualMedio;
            case "PREMIUM" -> descripcionManualPremium;
            default -> null;
        };
    }

    /**
     * Obtiene el nombre a mostrar por nivel (producto real o descripción manual).
     */
    public String getNombreMostrarPorNivel(String nivel) {
        // Si es regalo
        if (esItemRegalo()) {
            return textoRegalo != null ? textoRegalo : textoOriginal;
        }
        // Si tiene producto de la BD
        String nombreProducto = getNombreProductoPorNivel(nivel);
        if (nombreProducto != null) {
            return nombreProducto;
        }
        // Si tiene descripción manual
        String descManual = getDescripcionManualPorNivel(nivel);
        if (descManual != null && !descManual.isBlank()) {
            return descManual;
        }
        // Fallback al texto original
        return textoOriginal;
    }

    /**
     * Verifica si el item necesita producto (no tiene producto ni cotización manual).
     */
    public boolean necesitaProductoPorNivel(String nivel) {
        Long productoId = getProductoIdPorNivel(nivel);
        boolean esCotizacionManual = esCotizacionProveedorPorNivel(nivel);
        BigDecimal precio = getPrecioPorNivel(nivel);

        // Necesita producto si: no tiene producto, no es cotización manual, y no tiene precio
        return productoId == null && !esCotizacionManual &&
               (precio == null || precio.compareTo(BigDecimal.ZERO) == 0);
    }

    /**
     * Establece cotización manual de proveedor para un nivel.
     */
    public void setCotizacionProveedor(String nivel, BigDecimal precio, String descripcion, String proveedor) {
        this.nombreProveedor = proveedor;
        this.fechaCotizacionProveedor = LocalDateTime.now();

        switch (nivel) {
            case "ECONOMICO" -> {
                this.cotizadoProveedorEconomico = true;
                this.precioEconomico = precio;
                this.descripcionManualEconomico = descripcion;
            }
            case "MEDIO" -> {
                this.cotizadoProveedorMedio = true;
                this.precioMedio = precio;
                this.descripcionManualMedio = descripcion;
            }
            case "PREMIUM" -> {
                this.cotizadoProveedorPremium = true;
                this.precioPremium = precio;
                this.descripcionManualPremium = descripcion;
            }
        }

        if ("NO_COTIZADO".equals(this.estado)) {
            this.estado = "COTIZADO";
        }
    }

    /**
     * Vincula un producto real a un item que tenía cotización manual.
     */
    public void vincularProducto(String nivel, Producto productoReal) {
        switch (nivel) {
            case "ECONOMICO" -> {
                this.productoEconomico = productoReal;
                this.precioEconomico = productoReal.getPrecioVenta();
                this.cotizadoProveedorEconomico = false;
            }
            case "MEDIO" -> {
                this.productoMedio = productoReal;
                this.precioMedio = productoReal.getPrecioVenta();
                this.cotizadoProveedorMedio = false;
            }
            case "PREMIUM" -> {
                this.productoPremium = productoReal;
                this.precioPremium = productoReal.getPrecioVenta();
                this.cotizadoProveedorPremium = false;
            }
        }
        this.productoComprado = true;
    }
}
