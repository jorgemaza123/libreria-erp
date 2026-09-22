package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CotizacionServicioDTO {
    private Long id;
    private String tipoServicio;
    private String tituloTrabajo;
    private String clienteNombre;
    private String clienteTelefono;
    private String clienteDocumento;
    private String clienteEmail;
    private String clienteDireccion;
    private LocalDate fechaEntrega;
    private LocalDate fechaValidez;
    private String observaciones;
    private List<SeccionDTO> secciones;

    @Data
    public static class SeccionDTO {
        private String titulo;
        private List<ItemDTO> items;
    }

    @Data
    public static class ItemDTO {
        private String descripcion;
        private BigDecimal costo;
    }
}
