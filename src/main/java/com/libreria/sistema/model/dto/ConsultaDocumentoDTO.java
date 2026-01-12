package com.libreria.sistema.model.dto;

import lombok.Data;
import java.util.List;

/**
 * DTO para respuestas de consulta DNI/RUC desde APISUNAT
 */
@Data
public class ConsultaDocumentoDTO {

    private boolean success;
    private String message;
    private Payload payload;

    @Data
    public static class Payload {
        // Campos comunes
        private String tipoDocumento; // "DNI" o "RUC"
        private String numeroDocumento;
        private String nombreCompleto; // Para personas naturales
        private String razonSocial;    // Para empresas
        private String direccion;

        // Campos específicos de RUC
        private String ruc;
        private String razon_social;
        private String condicion;       // HABIDO, NO HABIDO
        private String nombre_comercial;
        private String tipo;            // SOCIEDAD ANONIMA, etc.
        private String estado;          // ACTIVO, BAJA
        private String direccion_fiscal;
        private String departamento;
        private String provincia;
        private String distrito;
        private List<String> actividades_economicas;

        // Campos específicos de DNI
        private String nombres;
        private String apellido_paterno;
        private String apellido_materno;

        /**
         * Obtiene el nombre/razón social según el tipo de documento
         */
        public String getNombreRazonSocial() {
            if (razon_social != null && !razon_social.isEmpty()) {
                return razon_social;
            }
            if (razonSocial != null && !razonSocial.isEmpty()) {
                return razonSocial;
            }
            if (nombreCompleto != null && !nombreCompleto.isEmpty()) {
                return nombreCompleto;
            }
            // Construir nombre completo desde partes
            StringBuilder sb = new StringBuilder();
            if (apellido_paterno != null) sb.append(apellido_paterno).append(" ");
            if (apellido_materno != null) sb.append(apellido_materno).append(" ");
            if (nombres != null) sb.append(nombres);
            return sb.toString().trim();
        }

        /**
         * Obtiene la dirección según el tipo de documento
         */
        public String getDireccionCompleta() {
            if (direccion_fiscal != null && !direccion_fiscal.isEmpty()) {
                StringBuilder sb = new StringBuilder(direccion_fiscal.trim());
                if (distrito != null && !distrito.isEmpty()) {
                    sb.append(", ").append(distrito);
                }
                if (provincia != null && !provincia.isEmpty()) {
                    sb.append(" - ").append(provincia);
                }
                if (departamento != null && !departamento.isEmpty()) {
                    sb.append(" - ").append(departamento);
                }
                return sb.toString();
            }
            return direccion != null ? direccion : "";
        }

        /**
         * Verifica si el contribuyente está activo y habido
         */
        public boolean esContribuyenteActivo() {
            return "ACTIVO".equalsIgnoreCase(estado) && "HABIDO".equalsIgnoreCase(condicion);
        }
    }
}
