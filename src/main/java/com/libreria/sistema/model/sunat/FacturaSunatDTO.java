package com.libreria.sistema.model.sunat;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

@Data
public class FacturaSunatDTO {
    private String documento; // "factura" o "boleta"
    private String serie;
    private Integer numero;
    
    @JsonProperty("fecha_de_emision")
    private String fechaEmision;
    
    private String moneda; // "PEN"
    
    @JsonProperty("tipo_operacion")
    private String tipoOperacion; // "0101"
    
    @JsonProperty("cliente_tipo_de_documento")
    private String clienteTipoDocumento; // "1" DNI, "6" RUC
    
    @JsonProperty("cliente_numero_de_documento")
    private String clienteNumeroDocumento;
    
    @JsonProperty("cliente_denominacion")
    private String clienteDenominacion;
    
    @JsonProperty("total_gravada")
    private String totalGravada;
    
    @JsonProperty("total_igv")
    private String totalIgv;
    
    private String total;
    
    private List<ItemSunatDTO> items;

    @Data
    public static class ItemSunatDTO {
        @JsonProperty("unidad_de_medida")
        private String unidadMedida; // "NIU"
        
        private String descripcion;
        private String cantidad;
        
        @JsonProperty("valor_unitario")
        private String valorUnitario; // Sin IGV
        
        @JsonProperty("precio_unitario")
        private String precioUnitario; // Con IGV
        
        @JsonProperty("porcentaje_igv")
        private String porcentajeIgv; // "18"
        
        @JsonProperty("codigo_tipo_afectacion_igv")
        private String codigoAfectacion; // "10"
    }
}