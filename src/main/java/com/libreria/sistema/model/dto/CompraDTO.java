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
    private BigDecimal subtotalDirecto;
    private BigDecimal gastosIndirectosMonto;
    private BigDecimal factorIndirectoAplicadoPct;
    private BigDecimal factorIndirectoSugeridoPct;
    private String origenFactorIndirecto;
    private String detalleGastosIndirectos;
    private BigDecimal totalCostoReal;
    private List<DetalleDTO> items;

    @Data
    public static class DetalleDTO {
        private Long productoId;
        private Integer cantidad;
        private BigDecimal costo;
        private BigDecimal totalPagado;
        private String tipoCatalogo;
        private String presentacionNombre;
        private BigDecimal cantidadPresentacion;
        private BigDecimal factorPresentacion;
        private BigDecimal precioPorPresentacion;
    }
}
