package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class DevolucionDTO {

    private Long ventaOriginalId;
    private String motivoDevolucion;
    private String observaciones;
    private String metodoReembolso;
    private List<ItemDevolucion> items;

    @Data
    public static class ItemDevolucion {
        private Long productoId;
        private BigDecimal cantidadDevuelta;
        private BigDecimal precioUnitario;
        private String descripcion;
    }
}
