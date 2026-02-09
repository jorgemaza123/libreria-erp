package com.libreria.sistema.util;

/**
 * Estados posibles para una Lista Escolar.
 */
public enum ListaEscolarEstado {

    PENDIENTE("Pendiente", "Lista creada, sin procesar"),
    COTIZADA("Cotizada", "Cotizaciones generadas, lista para vender"),
    EN_PROCESO("En Proceso", "Al menos una venta parcial realizada"),
    COMPLETADA("Completada", "Todos los items vendidos"),
    VENCIDA("Vencida", "Pasó fecha de vencimiento sin completar"),
    CANCELADA("Cancelada", "Cliente canceló la lista");

    private final String etiqueta;
    private final String descripcion;

    ListaEscolarEstado(String etiqueta, String descripcion) {
        this.etiqueta = etiqueta;
        this.descripcion = descripcion;
    }

    public String getEtiqueta() {
        return etiqueta;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
