package com.libreria.sistema.model.enums;

/**
 * Enumeración de métodos de pago soportados por el sistema.
 * Permite extensibilidad para nuevos métodos como Yape, Plin, etc.
 */
public enum MetodoPago {
    EFECTIVO("Efectivo", "EFE"),
    YAPE("Yape", "YAP"),
    PLIN("Plin", "PLI"),
    TARJETA("Tarjeta", "TAR"),
    TRANSFERENCIA("Transferencia", "TRA"),
    MIXTO("Pago Mixto", "MIX");

    private final String descripcion;
    private final String codigoCorto;

    MetodoPago(String descripcion, String codigoCorto) {
        this.descripcion = descripcion;
        this.codigoCorto = codigoCorto;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getCodigoCorto() {
        return codigoCorto;
    }

    /**
     * Obtiene el método de pago desde un string (case-insensitive)
     * @param valor String del método de pago
     * @return MetodoPago correspondiente o EFECTIVO por defecto
     */
    public static MetodoPago fromString(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return EFECTIVO;
        }
        try {
            return MetodoPago.valueOf(valor.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return EFECTIVO;
        }
    }

    /**
     * Verifica si el valor es un método de pago válido
     */
    public static boolean isValid(String valor) {
        if (valor == null || valor.trim().isEmpty()) {
            return false;
        }
        try {
            MetodoPago.valueOf(valor.toUpperCase().trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
