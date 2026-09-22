package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class CotizacionItemsResumenDTO {
    private long total;
    private List<String> primerosTipos;
}
