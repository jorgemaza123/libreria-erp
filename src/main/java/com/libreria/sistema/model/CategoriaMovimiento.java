package com.libreria.sistema.model;

/**
 * Constantes para categorías de movimientos de caja.
 * Permite control financiero estructurado: distinguir ventas, compras, gastos, retiros, etc.
 */
public final class CategoriaMovimiento {

    private CategoriaMovimiento() {} // No instanciable

    // --- INGRESOS ---
    public static final String VENTA = "VENTA";
    public static final String COBRANZA = "COBRANZA";
    public static final String APORTE_DUENO = "APORTE_DUENO";
    public static final String OTRO_INGRESO = "OTRO_INGRESO";

    // --- EGRESOS ---
    public static final String COMPRA_MERCADERIA = "COMPRA_MERCADERIA";
    public static final String GASTO_OPERATIVO = "GASTO_OPERATIVO";
    public static final String RETIRO_DUENO = "RETIRO_DUENO";
    public static final String DEVOLUCION = "DEVOLUCION";
    public static final String OTRO_EGRESO = "OTRO_EGRESO";

    /**
     * Obtiene etiqueta legible para mostrar en UI.
     */
    public static String etiqueta(String categoria) {
        if (categoria == null) return "Sin categoría";
        return switch (categoria) {
            case VENTA -> "Venta";
            case COBRANZA -> "Cobranza";
            case APORTE_DUENO -> "Aporte Dueño";
            case OTRO_INGRESO -> "Otro Ingreso";
            case COMPRA_MERCADERIA -> "Compra Mercadería";
            case GASTO_OPERATIVO -> "Gasto Operativo";
            case RETIRO_DUENO -> "Retiro Dueño";
            case DEVOLUCION -> "Devolución";
            case OTRO_EGRESO -> "Otro Egreso";
            default -> categoria;
        };
    }

    /**
     * Obtiene clase CSS de badge para mostrar en UI.
     */
    public static String badgeClass(String categoria) {
        if (categoria == null) return "badge-secondary";
        return switch (categoria) {
            case VENTA -> "badge-success";
            case COBRANZA -> "badge-info";
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
