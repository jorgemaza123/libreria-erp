package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class PosProformaDTO {

    private String clienteDocumento;
    private String clienteNombre;
    private String clienteTelefono;
    private String formaPago = "CONTADO";
    private String metodoPago = "EFECTIVO";
    private BigDecimal descuento = BigDecimal.ZERO;
    private String observaciones;
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private Long productoId;
        private String descripcion;
        private BigDecimal cantidad;
        private BigDecimal precioVenta;
    }
}
