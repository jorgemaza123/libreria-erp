package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class CompraDTO {
    private Long proveedorId;
    private String tipoComprobante;
    private String numeroComprobante;
    private String observaciones;
    private List<DetalleDTO> items;

    @Data
    public static class DetalleDTO {
        private Long productoId;
        private Integer cantidad;
        private BigDecimal costo;
    }
}