package com.libreria.sistema.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * DTO para Request a APISUNAT (PSE)
 * Mapea al JSON requerido por https://apisunat.pe/api/v3/documents
 */
@Data
public class SunatRequestDTO {

    @JsonProperty("documento")
    private String documento; // "boleta" o "factura"

    @JsonProperty("serie")
    private String serie; // "B001", "F001"

    @JsonProperty("numero")
    private Integer numero; // Correlativo

    @JsonProperty("fecha_de_emision")
    private String fechaDeEmision; // "2025-01-03"

    @JsonProperty("fecha_de_vencimiento")
    private String fechaDeVencimiento; // "2025-01-15" (solo crédito)

    @JsonProperty("moneda")
    private String moneda; // "PEN", "USD"

    @JsonProperty("tipo_operacion")
    private String tipoOperacion; // "0101"

    @JsonProperty("cliente_tipo_de_documento")
    private String clienteTipoDeDocumento; // "1" (DNI), "6" (RUC)

    @JsonProperty("cliente_numero_de_documento")
    private String clienteNumeroDeDocumento;

    @JsonProperty("cliente_denominacion")
    private String clienteDenominacion;

    @JsonProperty("cliente_direccion")
    private String clienteDireccion;

    @JsonProperty("items")
    private List<ItemDTO> items;

    @JsonProperty("cuotas")
    private List<CuotaDTO> cuotas; // Opcional, solo para crédito

    @JsonProperty("total")
    private String total; // "276.27"

    // --- CAMPOS PARA NOTA DE CRÉDITO ---
    @JsonProperty("documento_afectado")
    private DocumentoAfectadoDTO documentoAfectado;

    @JsonProperty("nota_credito_codigo_tipo")
    private String notaCreditoCodigoTipo; // "01" = Anulación de operación

    @JsonProperty("nota_credito_motivo")
    private String notaCreditoMotivo;

    /**
     * DTO para documento afectado (nota de crédito)
     */
    @Data
    public static class DocumentoAfectadoDTO {
        @JsonProperty("documento")
        private String documento; // "boleta" o "factura"

        @JsonProperty("serie")
        private String serie;

        @JsonProperty("numero")
        private Integer numero;
    }

    /**
     * DTO para cada item del comprobante
     */
    @Data
    public static class ItemDTO {

        @JsonProperty("unidad_de_medida")
        private String unidadDeMedida; // "NIU", "ZZ"

        @JsonProperty("descripcion")
        private String descripcion;

        @JsonProperty("cantidad")
        private String cantidad;

        @JsonProperty("valor_unitario")
        private String valorUnitario; // Precio SIN IGV

        @JsonProperty("porcentaje_igv")
        private String porcentajeIgv; // "18"

        @JsonProperty("codigo_tipo_afectacion_igv")
        private String codigoTipoAfectacionIgv; // "10", "20", "30"

        @JsonProperty("nombre_tributo")
        private String nombreTributo; // "IGV"
    }

    /**
     * DTO para cuotas en ventas a crédito
     */
    @Data
    public static class CuotaDTO {

        @JsonProperty("importe")
        private String importe; // "100.00"

        @JsonProperty("fecha_de_pago")
        private String fechaDePago; // "2025-01-10"
    }
}
