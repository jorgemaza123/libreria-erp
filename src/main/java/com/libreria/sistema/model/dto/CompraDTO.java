package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CompraDTO {
    // Cabecera
    private String tipoComprobante;
    private String serie;
    private String numero;
    private LocalDate fechaEmision;
    
    private String proveedorRuc;
    private String proveedorRazon;
    
    // Items
    private List<ItemCompraDTO> items;

    @Data
    public static class ItemCompraDTO {
        private Long productoId;
        private BigDecimal cantidad;
        private BigDecimal costoUnitario; // El usuario ingresa el costo unitario
    }
}