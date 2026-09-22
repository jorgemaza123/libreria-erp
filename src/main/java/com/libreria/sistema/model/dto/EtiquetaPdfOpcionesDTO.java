package com.libreria.sistema.model.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Data
public class EtiquetaPdfOpcionesDTO {

    private List<Long> productoIds = new ArrayList<>();
    private List<ProductoEtiquetaOverrideDTO> productos = new ArrayList<>();

    private Integer cantidad = 1;
    private String plantilla = "UTIL_CHICO";
    private String formato;
    private Integer anchoTicketMm;

    private Boolean mostrarNombre = true;
    private Boolean mostrarPrecio = true;
    private Boolean mostrarCodigoTexto = true;
    private Boolean mostrarCategoria = false;
    private Boolean mostrarCodigoInterno = false;
    private Boolean mostrarBorde = true;

    private String fuente = "HELVETICA";
    private String colorTexto = "#111827";
    private String colorPrecio = "#111827";
    private String colorCodigo = "#374151";
    private String colorBorde = "#D1D5DB";

    private BigDecimal nombreFontSize;
    private BigDecimal precioFontSize;
    private BigDecimal codigoFontSize;
    private BigDecimal categoriaFontSize;
    private BigDecimal codigoAnchoMm;
    private BigDecimal codigoAltoMm;
    private BigDecimal etiquetaAnchoMm;
    private BigDecimal etiquetaAltoMm;
    private BigDecimal margenHorizontalMm;
    private BigDecimal margenVerticalMm;

    private Integer columnasA4;
    private Integer filasA4;
    private Integer saltarEtiquetasA4 = 0;

    @Data
    public static class ProductoEtiquetaOverrideDTO {
        private Long productoId;
        private Integer cantidad;
        private String plantilla;
        private String nombrePersonalizado;
        private BigDecimal precioPersonalizado;

        private Boolean mostrarNombre;
        private Boolean mostrarPrecio;
        private Boolean mostrarCategoria;
        private Boolean mostrarCodigoInterno;
        private Boolean mostrarCodigoTexto;
        private Boolean mostrarBorde;

        private String fuente;
        private BigDecimal nombreFontSize;
        private BigDecimal precioFontSize;
        private BigDecimal codigoFontSize;
        private BigDecimal categoriaFontSize;
        private BigDecimal codigoAnchoMm;
        private BigDecimal codigoAltoMm;
        private BigDecimal etiquetaAltoMm;

        private String colorPrecio;
        private String colorTexto;
        private String colorCodigo;
        private String colorBorde;
    }
}
