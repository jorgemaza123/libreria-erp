package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VentaDTO {
    // Datos básicos
    private String clienteNombre;
    private String clienteDocumento; 
    private String tipoComprobante;  
    
    // Datos Cliente Extra (Para crear el cliente si es nuevo)
    private String clienteDireccion;
    private String clienteTelefono; 
    
    // --- NUEVO: DATOS DE PAGO ---
    private String formaPago; // "CONTADO" o "CREDITO"
    private BigDecimal montoInicial; // Si es crédito, ¿deja adelanto?
    private Integer diasCredito; // 7, 15, 30 días
    
    private List<DetalleDTO> items;

    @Data
    public static class DetalleDTO {
        private Long productoId;
        private BigDecimal cantidad;
        private BigDecimal precioVenta; 
    }
}