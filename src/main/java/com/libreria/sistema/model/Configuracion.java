package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "configuracion")
public class Configuracion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // ========== DATOS DE EMPRESA ==========
    private String nombreEmpresa;
    private String ruc;
    private String direccion;
    private String telefono;
    private String email;

    @Column(columnDefinition = "TEXT")
    private String logoBase64;
    @Column(columnDefinition = "TEXT")
    private String logoUrl;

    private String slogan; // "Tu librería de confianza"
    private String webSite;
    private String facebook;
    private String instagram;
    private String whatsapp;
    private String horarioAtencion; // "Lun-Vie: 9am-7pm, Sáb: 9am-2pm"

    // ========== PERSONALIZACIÓN VISUAL ==========
    @Column(length = 7)
    private String colorPrimario; // "#007bff"
    @Column(length = 7)
    private String colorSecundario; // "#6c757d"
    @Column(length = 7)
    private String colorExito; // "#28a745"
    @Column(length = 7)
    private String colorPeligro; // "#dc3545"
    @Column(length = 7)
    private String colorAdvertencia; // "#ffc107"
    @Column(length = 7)
    private String colorInfo; // "#17a2b8"
    @Column(length = 7)
    private String colorOscuro; // "#343a40"
    @Column(length = 7)
    private String colorClaro; // "#f8f9fa"
    @Column(length = 7)
    private String colorBronce; // "#cd7f32" - Para medallas 3er lugar

    // ========== CONFIGURACIÓN DE REPORTES ==========
    private Boolean mostrarLogoEnReportes; // default: true

    @Column(columnDefinition = "TEXT")
    private String piePaginaReportes;
    @Column(columnDefinition = "TEXT")
    private String encabezadoReportes;

    private String formatoFechaReportes; // "dd/MM/yyyy"
    private String formatoMoneda; // "S/"

    // ========== CONFIGURACIÓN DE SISTEMA ==========
    private Integer itemsPorPagina; // 10
    private Integer stockMinimo; // 5
    private Integer diasVencimientoCredito; // 30
    private Integer diasDevolucion; // 30

    @Column(precision = 5, scale = 2)
    private BigDecimal igvPorcentaje; // 18.00

    // ========== CONTROL FINANCIERO Y DE CAJA ==========
    // Bloquea ventas si no hay caja abierta (seguridad financiera)
    private Boolean aperturaCajaObligatoria;

    // Oculta el monto esperado al cajero al cerrar (evita ajustes fraudulentos)
    private Boolean cierreCajaCiego;

    // Alerta si hay mucho dinero en caja (riesgo de robo)
    @Column(precision = 10, scale = 2)
    private BigDecimal limiteEfectivoCaja;

    // ========== REGLAS DE NEGOCIO ==========
    // CRÍTICO: Permite vender sin stock (Ferreterías a veces SÍ, Farmacias NUNCA)
    private Boolean permitirStockNegativo;

    // Permite vender fracciones: 1.5 metros de cable, 0.250 kg de clavos
    // false = Solo enteros (Librería), true = Decimales (Ferretería/Cable/Peso)
    private Boolean permitirVentaFraccionada;

    // Límite de descuento que puede aplicar un cajero (evita que regale la tienda)
    @Column(precision = 5, scale = 2)
    private BigDecimal porcentajeDescuentoMaximo;

    // true = Precio incluye IGV (B2C), false = Se suma IGV al final (B2B)
    private Boolean preciosIncluyenImpuesto;

    // ========== FACTURACIÓN ELECTRÓNICA ==========
    // URL del API de facturación (Nubefact, Facturador SUNAT, etc.)
    @Column(columnDefinition = "TEXT")
    private String facturacionEndpoint;

    // Token de seguridad para el servicio de facturación
    @Column(columnDefinition = "TEXT")
    private String facturacionToken;

    // Ruta al certificado digital .pfx (si facturas directo a SUNAT)
    private String certificadoDigitalRuta;

    // Clave del certificado digital
    private String claveCertificado;

    // false = Modo pruebas (Beta SUNAT), true = Producción (Facturas reales)
    private Boolean modoProduccion;

    /**
     * Constructor con valores por defecto
     */
    public Configuracion() {
        // Datos de empresa
        this.nombreEmpresa = "MI EMPRESA S.A.C.";
        this.ruc = "20000000001";
        this.direccion = "Dirección por configurar";
        this.telefono = "(01) 000-0000";
        this.email = "contacto@miempresa.com";
        this.slogan = "Tu empresa de confianza";
        this.horarioAtencion = "Lun-Vie: 9am-7pm, Sáb: 9am-2pm";

        // Personalización visual - Colores por defecto
        this.colorPrimario = "#007bff";
        this.colorSecundario = "#6c757d";
        this.colorExito = "#28a745";
        this.colorPeligro = "#dc3545";
        this.colorAdvertencia = "#ffc107";
        this.colorInfo = "#17a2b8";
        this.colorOscuro = "#343a40";
        this.colorClaro = "#f8f9fa";
        this.colorBronce = "#cd7f32";

        // Configuración de reportes
        this.mostrarLogoEnReportes = true;
        this.piePaginaReportes = "Gracias por su preferencia";
        this.encabezadoReportes = "Documento generado automáticamente";
        this.formatoFechaReportes = "dd/MM/yyyy";
        this.formatoMoneda = "S/";

        // Configuración de sistema
        this.itemsPorPagina = 10;
        this.stockMinimo = 5;
        this.diasVencimientoCredito = 30;
        this.diasDevolucion = 30;
        this.igvPorcentaje = new BigDecimal("18.00");

        // Control financiero y de caja
        this.aperturaCajaObligatoria = true;
        this.cierreCajaCiego = true;
        this.limiteEfectivoCaja = new BigDecimal("2000.00");

        // Reglas de negocio
        this.permitirStockNegativo = false;
        this.permitirVentaFraccionada = false;
        this.porcentajeDescuentoMaximo = new BigDecimal("10.00");
        this.preciosIncluyenImpuesto = true;

        // Facturación electrónica
        this.facturacionEndpoint = "";
        this.facturacionToken = "";
        this.certificadoDigitalRuta = "";
        this.claveCertificado = "";
        this.modoProduccion = false;
    }
}