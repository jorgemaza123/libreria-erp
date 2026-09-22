package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductoRevisionResultadoDTO {
    private List<ProductoRevisionItemDTO> productos;
    private int page;
    private int size;
    private int totalPages;
    private long total;
    private long filtered;
    private boolean first;
    private boolean last;
    private Map<String, Object> resumen;
}
