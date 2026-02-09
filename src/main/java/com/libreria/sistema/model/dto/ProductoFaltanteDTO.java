package com.libreria.sistema.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * DTO para representar productos solicitados que no están en inventario.
 * Agrupa items similares de diferentes listas escolares.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductoFaltanteDTO {

    private String textoNormalizado;
    private String textoOriginal;
    private Integer vecessolicitado;
    private Integer cantidadTotal;
    private BigDecimal precioPromedio;
    private String proveedorSugerido;
    private LocalDateTime ultimaSolicitud;
    private List<DetalleFaltante> detalles = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetalleFaltante {
        private Long detalleId;
        private Long listaId;
        private String codigoLista;
        private String nombreAlumno;
        private String colegio;
        private String grado;
        private Integer cantidad;
        private String nivel;
        private BigDecimal precioSugerido;
        private String proveedor;
        private LocalDateTime fechaSolicitud;
        private Boolean cotizadoProveedor;
    }

    /**
     * Constructor para usar en queries JPQL.
     */
    public ProductoFaltanteDTO(String textoOriginal, Long vecesSolicitado, Long cantidadTotal) {
        this.textoOriginal = textoOriginal;
        this.textoNormalizado = normalizarTexto(textoOriginal);
        this.vecessolicitado = vecesSolicitado != null ? vecesSolicitado.intValue() : 0;
        this.cantidadTotal = cantidadTotal != null ? cantidadTotal.intValue() : 0;
    }

    /**
     * Normaliza el texto para agrupar items similares.
     */
    public static String normalizarTexto(String texto) {
        if (texto == null) return "";
        return texto.toLowerCase()
                .replaceAll("[^a-záéíóúñ0-9\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public void agregarDetalle(DetalleFaltante detalle) {
        if (this.detalles == null) {
            this.detalles = new ArrayList<>();
        }
        this.detalles.add(detalle);
    }
}
