package com.libreria.sistema.model.dto;

import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class OrdenDTO {
    private Long id;
    private String tipoServicio;
    private String tituloTrabajo;
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDocumento;
    private LocalDate fechaEntrega;
    private BigDecimal aCuenta;
    private String observaciones;
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private String descripcion;
        private BigDecimal costo;
    }
}