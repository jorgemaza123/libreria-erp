package com.libreria.sistema.model.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String clienteEmail;
    private String clienteDireccion;
    private LocalDate fechaEntrega;
    private LocalDate fechaRecordatorio;
    private Boolean recordatorioActivo;
    private String prioridad = "NORMAL";
    @JsonProperty("aCuenta")
    private BigDecimal aCuenta;
    private String observaciones;
    private String recordatorioNota;
    private String metodoPago = "EFECTIVO";
    private List<ItemDTO> items;

    @Data
    public static class ItemDTO {
        private String descripcion;
        private BigDecimal costo;
    }
}
