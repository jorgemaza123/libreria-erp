package com.libreria.sistema.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * DTO para Response de APISUNAT (PSE)
 * Mapea el JSON de respuesta de https://apisunat.pe/api/v3/documents
 */
@Data
public class SunatResponseDTO {

    @JsonProperty("success")
    private Boolean success;

    @JsonProperty("message")
    private String message;

    @JsonProperty("payload")
    private PayloadDTO payload;

    /**
     * DTO para el payload de respuesta exitosa
     */
    @Data
    public static class PayloadDTO {

        @JsonProperty("estado")
        private String estado; // "ACEPTADO", "PENDIENTE", "RECHAZADO"

        @JsonProperty("hash")
        private String hash; // Hash del comprobante

        @JsonProperty("xml")
        private String xml; // URL del XML firmado

        @JsonProperty("cdr")
        private String cdr; // URL del CDR (Constancia de Recepción)

        @JsonProperty("pdf")
        private PdfDTO pdf; // URLs del PDF
    }

    /**
     * DTO para las URLs del PDF
     */
    @Data
    public static class PdfDTO {

        @JsonProperty("ticket")
        private String ticket; // URL del PDF formato ticket

        @JsonProperty("a4")
        private String a4; // URL del PDF formato A4 (opcional, algunos PSE lo incluyen)
    }
}
