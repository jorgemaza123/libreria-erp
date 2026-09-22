package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class PosConfiguracionRapidaDTO {

    private List<Long> productoIds = new ArrayList<>();
    private List<ImpresionPresetDTO> impresiones = new ArrayList<>();

    @Data
    public static class ImpresionPresetDTO {
        private String tipo;
        private String etiqueta;
        private BigDecimal precio;
    }
}
