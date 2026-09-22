package com.libreria.sistema.model;

/**
 * Constantes para categorias de movimientos de caja.
 * Permite control financiero estructurado: distinguir ventas, compras, gastos, retiros, etc.
 */
public final class CategoriaMovimiento {

    private CategoriaMovimiento() {}

    // --- INGRESOS ---
    public static final String VENTA = "VENTA";
    public static final String COBRANZA = "COBRANZA";
    public static final String ANTICIPO_SERVICIO = "ANTICIPO_SERVICIO";
    public static final String APORTE_DUENO = "APORTE_DUENO";
    public static final String OTRO_INGRESO = "OTRO_INGRESO";

    // --- EGRESOS ---
    public static final String COMPRA_MERCADERIA = "COMPRA_MERCADERIA";
    public static final String GASTO_OPERATIVO = "GASTO_OPERATIVO";
    public static final String RETIRO_DUENO = "RETIRO_DUENO";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String OTRO_EGRESO = "OTRO_EGRESO";

    public static String etiqueta(String categoria) {
        if (categoria == null) return "Sin categoria";
        return switch (categoria) {
            case VENTA -> "Venta";
            case COBRANZA -> "Cobranza";
            case ANTICIPO_SERVICIO -> "Anticipo Servicio";
            case APORTE_DUENO -> "Aporte Dueno";
            case OTRO_INGRESO -> "Otro Ingreso";
            case COMPRA_MERCADERIA -> "Compra Mercaderia";
            case GASTO_OPERATIVO -> "Gasto Operativo";
            case RETIRO_DUENO -> "Retiro Dueno";
            case DEVOLUCION -> "Devolucion";
            case OTRO_EGRESO -> "Otro Egreso";
            default -> categoria;
        };
    }

    public static String badgeClass(String categoria) {
        if (categoria == null) return "badge-secondary";
        return switch (categoria) {
            case VENTA -> "badge-success";
            case COBRANZA -> "badge-info";
            case ANTICIPO_SERVICIO -> "badge-primary";
            case APORTE_DUENO -> "badge-primary";
            case OTRO_INGRESO -> "badge-secondary";
            case COMPRA_MERCADERIA -> "badge-warning";
            case GASTO_OPERATIVO -> "badge-danger";
            case RETIRO_DUENO -> "badge-dark";
            case DEVOLUCION -> "badge-danger";
            case OTRO_EGRESO -> "badge-secondary";
            default -> "badge-secondary";
        };
    }
}
