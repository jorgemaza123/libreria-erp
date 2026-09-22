package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entidad que representa el detalle de cada producto en una toma de inventario.
 * Contiene el snapshot del stock del sistema y el conteo físico realizado.
 */
@Data
@Entity
@Table(name = "detalle_toma_inventario",
       uniqueConstraints = @UniqueConstraint(columnNames = {"toma_inventario_id", "producto_id"}))
public class DetalleTomaInventario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Toma de inventario a la que pertenece este detalle.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "toma_inventario_id", nullable = false)
    private TomaInventario tomaInventario;

    /**
     * Producto que se está contando.
     */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    /**
     * Stock que tenía el sistema al momento de iniciar la toma (SNAPSHOT).
     * Este valor NO cambia una vez creado el registro.
     */
    @Column(nullable = false)
    private Integer stockSistema;

    /**
     * Stock físico contado por el usuario.
     * NULL indica que aún no se ha contado este producto.
     */
    private Integer stockFisico;

    /**
     * Diferencia calculada: stockFisico - stockSistema.
     * Negativo = Faltante, Positivo = Sobrante.
     */
    private Integer diferencia;

    /**
     * Indica si este producto ya fue contado/verificado.
     */
    @Column(nullable = false)
    private Boolean contado = false;

    /**
     * Fecha/hora en que se registró el conteo físico.
     */
    private LocalDateTime fechaConteo;

    /**
     * Usuario que registro el conteo o el ultimo reconteo.
     */
    @Column(length = 80)
    private String usuarioConteo;

    private Integer primerConteoFisico;

    private Integer segundoConteoFisico;

    private LocalDateTime fechaPrimerConteo;

    private LocalDateTime fechaSegundoConteo;

    @Column(length = 80)
    private String usuarioPrimerConteo;

    @Column(length = 80)
    private String usuarioSegundoConteo;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean segundoConteoRequerido = false;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private Boolean segundoConteoConfirmado = false;

    @Column(length = 80)
    private String usuarioDeshacerConteo;

    private LocalDateTime fechaDeshacerConteo;

    /**
     * Observación específica para este producto (opcional).
     * Ej: "Producto dañado", "Encontrado en otra ubicación".
     */
    @Column(length = 255)
    private String observacion;

    /**
     * Zona física donde se contó el producto.
     * Ej: MOSTRADOR, VITRINA, ALMACEN, SUBLIMACION.
     */
    @Column(length = 80)
    private String zonaConteo;

    /**
     * Indica si el ajuste ya fue aplicado al stock (durante el procesamiento).
     */
    @Column(nullable = false)
    private Boolean ajusteAplicado = false;

    @Column(length = 160)
    private String nombreCorregido;

    @Column(length = 120)
    private String categoriaCorregida;

    @Column(length = 80)
    private String marcaCorregida;

    @Column(length = 80)
    private String colorCorregido;

    @Column(length = 80)
    private String modeloCorregido;

    @Column(length = 60)
    private String tipoCorregido;

    @Column(length = 40)
    private String clasificacionCorregida;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoCorregido;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioCorregido;

    @Column(nullable = false)
    private Boolean correccionPendiente = false;

    @Column(nullable = false)
    private Boolean correccionAplicada = false;

    private LocalDateTime fechaCorreccion;

    @Column(length = 80)
    private String usuarioCorreccion;

    private LocalDateTime fechaAplicacionCorreccion;

    @Column(length = 80)
    private String usuarioAplicacionCorreccion;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoAnteriorCorreccion;

    @Column(precision = 12, scale = 4)
    private BigDecimal costoAplicadoCorreccion;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioAnteriorCorreccion;

    @Column(precision = 12, scale = 2)
    private BigDecimal precioAplicadoCorreccion;

    private Integer stockAnteriorAjuste;

    private Integer stockNuevoAjuste;

    // ========== MÉTODOS HELPER ==========

    /**
     * Registra el conteo físico y calcula la diferencia.
     */
    public void registrarConteo(Integer cantidadFisica) {
        this.primerConteoFisico = cantidadFisica;
        this.segundoConteoFisico = null;
        this.fechaPrimerConteo = LocalDateTime.now();
        this.fechaSegundoConteo = null;
        this.usuarioSegundoConteo = null;
        this.segundoConteoRequerido = false;
        this.segundoConteoConfirmado = true;
        this.stockFisico = cantidadFisica;
        this.diferencia = cantidadFisica - this.stockSistema;
        this.contado = true;
        this.fechaConteo = this.fechaPrimerConteo;
    }

    public void registrarPrimerConteo(Integer cantidadFisica, String usuario, boolean requiereSegundoConteo) {
        LocalDateTime ahora = LocalDateTime.now();
        this.primerConteoFisico = cantidadFisica;
        this.segundoConteoFisico = null;
        this.fechaPrimerConteo = ahora;
        this.fechaSegundoConteo = null;
        this.usuarioPrimerConteo = usuario;
        this.usuarioSegundoConteo = null;
        this.segundoConteoRequerido = requiereSegundoConteo;
        this.segundoConteoConfirmado = !requiereSegundoConteo;
        this.stockFisico = cantidadFisica;
        this.diferencia = cantidadFisica - this.stockSistema;
        this.contado = !requiereSegundoConteo;
        this.fechaConteo = requiereSegundoConteo ? null : ahora;
        this.usuarioConteo = requiereSegundoConteo ? null : usuario;
    }

    public void registrarSegundoConteo(Integer cantidadFisica, String usuario) {
        LocalDateTime ahora = LocalDateTime.now();
        this.segundoConteoFisico = cantidadFisica;
        this.fechaSegundoConteo = ahora;
        this.usuarioSegundoConteo = usuario;
        this.segundoConteoRequerido = true;
        this.segundoConteoConfirmado = true;
        this.stockFisico = cantidadFisica;
        this.diferencia = cantidadFisica - this.stockSistema;
        this.contado = true;
        this.fechaConteo = ahora;
        this.usuarioConteo = usuario;
    }

    public void limpiarConteos() {
        this.stockFisico = null;
        this.diferencia = null;
        this.contado = false;
        this.fechaConteo = null;
        this.usuarioConteo = null;
        this.primerConteoFisico = null;
        this.segundoConteoFisico = null;
        this.fechaPrimerConteo = null;
        this.fechaSegundoConteo = null;
        this.usuarioPrimerConteo = null;
        this.usuarioSegundoConteo = null;
        this.segundoConteoRequerido = false;
        this.segundoConteoConfirmado = false;
    }

    /**
     * Verifica si hay diferencia entre el stock físico y el del sistema.
     */
    public boolean tieneDiferencia() {
        return this.diferencia != null && this.diferencia != 0;
    }

    /**
     * Verifica si es un faltante (diferencia negativa).
     */
    public boolean esFaltante() {
        return this.diferencia != null && this.diferencia < 0;
    }

    /**
     * Verifica si es un sobrante (diferencia positiva).
     */
    public boolean esSobrante() {
        return this.diferencia != null && this.diferencia > 0;
    }

    /**
     * Obtiene el valor absoluto de la diferencia.
     */
    public int getDiferenciaAbsoluta() {
        return this.diferencia != null ? Math.abs(this.diferencia) : 0;
    }

    @PrePersist
    protected void onCreate() {
        if (this.contado == null) this.contado = false;
        if (this.ajusteAplicado == null) this.ajusteAplicado = false;
        if (this.segundoConteoRequerido == null) this.segundoConteoRequerido = false;
        if (this.segundoConteoConfirmado == null) this.segundoConteoConfirmado = false;
        if (this.correccionPendiente == null) this.correccionPendiente = false;
        if (this.correccionAplicada == null) this.correccionAplicada = false;
    }
}
