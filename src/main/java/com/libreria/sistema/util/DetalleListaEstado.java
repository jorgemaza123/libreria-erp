package com.libreria.sistema.util;

/**
 * Estados posibles para un item de Lista Escolar.
 */
public enum DetalleListaEstado {

    NO_COTIZADO("No Cotizado", "Item parseado pero sin producto asignado", "secondary"),
    COTIZADO("Cotizado", "Producto asignado, pendiente de venta", "info"),
    PENDIENTE("Pendiente", "Cliente decidió no comprar ahora", "warning"),
    VENDIDO("Vendido", "Item vendido, afectó inventario", "success"),
    REEMPLAZADO("Reemplazado", "Producto original cambiado por otro", "primary"),
    NO_DISPONIBLE("No Disponible", "Sin stock ni alternativa", "danger"),
    OMITIDO("Omitido", "Cliente no quiere este item", "dark");

    private final String etiqueta;
    private final String descripcion;
    private final String badgeClass; // Bootstrap badge color

    DetalleListaEstado(String etiqueta, String descripcion, String badgeClass) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
        this.badgeClass = badgeClass;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public String getBadgeClass() {
        return badgeClass;
    }

    /**
     * Verifica si el estado permite realizar venta.
     */
    public boolean permiteVenta() {
        return this == COTIZADO || this == PENDIENTE || this == REEMPLAZADO;
    }

    /**
     * Verifica si el estado indica que ya fue vendido.
     */
    public boolean estaVendido() {
        return this == VENDIDO;
    }
}
