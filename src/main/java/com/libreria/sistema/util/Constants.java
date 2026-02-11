package com.libreria.sistema.util;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

/**
 * Constantes globales del sistema
 */
public final class Constants {

    private Constants() {
        // Clase de utilidad - no instanciable
    }

    // === IMPUESTOS Y CÁLCULOS ===
    public static final BigDecimal IGV_RATE = new BigDecimal("1.18"); // 18% IGV
    public static final BigDecimal DESCUENTO_MINIMO_VENTA = new BigDecimal("0.90"); // 10% descuento máximo

    // === CRÉDITOS ===
    public static final int DEFAULT_CREDIT_DAYS = 7;
    public static final int MIN_CREDIT_DAYS = 1;
    public static final int MAX_CREDIT_DAYS = 90;

    // === STOCK ===
    public static final int DEFAULT_STOCK_MINIMO = 5;

    // === VALIDACIÓN DE ARCHIVOS ===
    public static final long MAX_FILE_SIZE = 10 * 1024 * 1024; // 10 MB
    public static final String[] ALLOWED_IMAGE_EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};
    public static final String[] ALLOWED_IMAGE_MIME_TYPES = {
        "image/jpeg",
        "image/png",
        "image/webp"
    };

    // === SEGURIDAD ===
    public static final int MAX_LOGIN_ATTEMPTS = 5;
    public static final int ACCOUNT_LOCK_DURATION_MINUTES = 15;
    public static final int MIN_PASSWORD_LENGTH = 8;

    // === SESIONES ===
    public static final int SESSION_TIMEOUT_HOURS = 2;
    // -1 = sesiones ilimitadas (permite PC + móvil simultáneamente)
    public static final int MAX_SESSIONS_PER_USER = -1;

    // === PAGINACIÓN ===
    public static final int DEFAULT_PAGE_SIZE = 50;
    public static final int MAX_PAGE_SIZE = 500;

    // === FORMATOS ===
    public static final String DATE_FORMAT = "dd/MM/yyyy";
    public static final String DATETIME_FORMAT = "dd/MM/yyyy HH:mm:ss";
    public static final String CURRENCY_SYMBOL = "S/ ";

    // === TIPOS DE DOCUMENTO SUNAT ===
    public static final String TIPO_DOC_DNI = "1";
    public static final String TIPO_DOC_RUC = "6";
    public static final int DNI_LENGTH = 8;
    public static final int RUC_LENGTH = 11;

    // === TIPOS DE AFECTACIÓN IGV ===
    public static final String AFECTACION_GRAVADO = "10";
    public static final String AFECTACION_EXONERADO = "20";
    public static final String AFECTACION_INAFECTO = "30";

    // === ESTADOS DE VENTA ===
    /** Estados que representan dinero real para KPIs financieros */
    public static final Set<String> ESTADOS_VALIDOS_KPI = Set.of("EMITIDO", "PAGADO_TOTAL", "DEVUELTO_PARCIAL");
    /** Estados que representan dinero real - como lista para queries JPQL IN */
    public static final List<String> ESTADOS_VALIDOS_KPI_LIST = List.of("EMITIDO", "PAGADO_TOTAL", "DEVUELTO_PARCIAL");
    public static final String ESTADO_ANULADO = "ANULADO";
    public static final String ESTADO_DEVUELTO_TOTAL = "DEVUELTO_TOTAL";

    // === CANALES DE VENTA ===
    public static final String CANAL_LOCAL = "LOCAL";
    public static final String CANAL_WHATSAPP = "WHATSAPP";
    public static final String CANAL_DELIVERY = "DELIVERY";
    public static final String CANAL_TELEFONO = "TELEFONO";
    public static final String CANAL_WEB = "WEB";

    // === SERIES DE COMPROBANTES ===
    public static final String SERIE_BOLETA_INTERNA = "I001";
    public static final String SERIE_FACTURA_INTERNA = "IF001";
    public static final String SERIE_BOLETA_ELECTRONICA = "B001";
    public static final String SERIE_FACTURA_ELECTRONICA = "F001";
    public static final String SERIE_COTIZACION = "C001";
}
