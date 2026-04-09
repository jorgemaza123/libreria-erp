package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class CompraPersonalizadaDTO {
    private Long proveedorId;
    private String tipoComprobante;
    private String numeroComprobante;
    private String observaciones;
    private List<ItemDTO> items = new ArrayList<>();

    @Data
    public static class ItemDTO {
        private Long insumoId;
        private Long presentacionId;
        private BigDecimal cantidadPresentacion;
        private BigDecimal totalPagado;
    }
}
