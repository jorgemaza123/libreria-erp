package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class VentaDTO {
    // Cabecera
    private String tipoComprobante;
    private String clienteDoc;     
    private String clienteNombre;
    private String clienteTelefono; // <--- NUEVO CAMPO
    private String clienteDireccion;
    private String clienteTipoDoc; 
    
    // Lista de productos
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private Long productoId;
        private BigDecimal cantidad;
        private BigDecimal precio;
        private String nombre;
    }
}