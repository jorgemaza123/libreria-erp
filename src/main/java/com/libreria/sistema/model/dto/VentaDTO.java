package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VentaDTO {
    // Datos básicos que vienen del POS
    private String clienteNombre;
    private String clienteDocumento; // DNI o RUC
    private String tipoComprobante;  // BOLETA, FACTURA
    
    // FALTABA ESTE CAMPO (Causa del error 1)
    private String clienteTelefono; 
    
    // Lista de productos del carrito
    private List<DetalleDTO> items;

    @Data
    public static class DetalleDTO {
        private Long productoId;
        private BigDecimal cantidad;
        private BigDecimal precioVenta; // Precio Final (Con IGV)
    }
}