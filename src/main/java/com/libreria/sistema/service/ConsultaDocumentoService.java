package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.dto.ConsultaDocumentoDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

/**
 * Servicio para consultar datos de DNI/RUC desde APISUNAT (PSE)
 *
 * Endpoints del PSE:
 * - Consulta RUC: https://dev.apisunat.pe/api/v1/business/ruc/{ruc}
 * - Consulta DNI: https://dev.apisunat.pe/api/v1/person/dni/{dni}
 *
 * Requiere token de APISUNAT configurado en Configuracion.facturacionToken
 */
@Service
@Slf4j
public class ConsultaDocumentoService {

    private final ConfiguracionService configuracionService;
    private final RestTemplate restTemplate;

    // URLs base de APISUNAT
    private static final String URL_BASE_PROD = "https://app.apisunat.pe";
    private static final String URL_BASE_DEV = "https://dev.apisunat.pe";
    private static final String ENDPOINT_RUC = "/api/v1/business/ruc/";
    private static final String ENDPOINT_DNI = "/api/v1/person/dni/";

    public ConsultaDocumentoService(ConfiguracionService configuracionService, RestTemplate restTemplate) {
        this.configuracionService = configuracionService;
        this.restTemplate = restTemplate;
    }

    /**
     * Consulta datos de un RUC en SUNAT
     *
     * @param ruc Número de RUC (11 dígitos)
     * @return Map con los datos del contribuyente o error
     */
    public Map<String, Object> consultarRuc(String ruc) {
        Map<String, Object> resultado = new HashMap<>();

        // Validar formato de RUC
        if (ruc == null || !ruc.matches("\\d{11}")) {
            resultado.put("success", false);
            resultado.put("error", "El RUC debe tener 11 dígitos numéricos.");
            return resultado;
        }

        try {
            Configuracion config = configuracionService.obtenerConfiguracion();
            String token = config.getFacturacionToken();

            if (token == null || token.isBlank()) {
                resultado.put("success", false);
                resultado.put("error", "Token de facturación no configurado. Configure el token en Configuración > General > Facturación.");
                return resultado;
            }

            // Determinar URL base según modo
            String urlBase = Boolean.TRUE.equals(config.getModoProduccion()) ? URL_BASE_PROD : URL_BASE_DEV;
            String url = urlBase + ENDPOINT_RUC + ruc;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Consultando RUC {} en APISUNAT", ruc);
            ResponseEntity<ConsultaDocumentoDTO> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, ConsultaDocumentoDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ConsultaDocumentoDTO dto = response.getBody();
                if (dto.isSuccess() && dto.getPayload() != null) {
                    ConsultaDocumentoDTO.Payload payload = dto.getPayload();

                    resultado.put("success", true);
                    resultado.put("tipoDocumento", "6"); // RUC
                    resultado.put("numeroDocumento", ruc);
                    resultado.put("razonSocial", payload.getNombreRazonSocial());
                    resultado.put("direccion", payload.getDireccionCompleta());
                    resultado.put("estado", payload.getEstado());
                    resultado.put("condicion", payload.getCondicion());
                    resultado.put("activo", payload.esContribuyenteActivo());

                    // Advertencia si no está activo/habido
                    if (!payload.esContribuyenteActivo()) {
                        resultado.put("advertencia", "El contribuyente no está ACTIVO o no está HABIDO. Estado: " +
                            payload.getEstado() + ", Condición: " + payload.getCondicion());
                    }

                    log.info("RUC {} consultado exitosamente: {}", ruc, payload.getNombreRazonSocial());
                } else {
                    resultado.put("success", false);
                    resultado.put("error", dto.getMessage() != null ? dto.getMessage() : "No se encontraron datos para el RUC.");
                }
            } else {
                resultado.put("success", false);
                resultado.put("error", "Error al consultar el RUC. Intente nuevamente.");
            }

        } catch (RestClientException e) {
            log.error("Error de conexión al consultar RUC {}: {}", ruc, e.getMessage());
            resultado.put("success", false);
            resultado.put("error", "Error de conexión con APISUNAT. Verifique su conexión a internet.");
        } catch (Exception e) {
            log.error("Error inesperado al consultar RUC {}: {}", ruc, e.getMessage(), e);
            resultado.put("success", false);
            resultado.put("error", "Error inesperado al consultar el RUC.");
        }

        return resultado;
    }

    /**
     * Consulta datos de un DNI en RENIEC
     *
     * @param dni Número de DNI (8 dígitos)
     * @return Map con los datos de la persona o error
     */
    public Map<String, Object> consultarDni(String dni) {
        Map<String, Object> resultado = new HashMap<>();

        // Validar formato de DNI
        if (dni == null || !dni.matches("\\d{8}")) {
            resultado.put("success", false);
            resultado.put("error", "El DNI debe tener 8 dígitos numéricos.");
            return resultado;
        }

        try {
            Configuracion config = configuracionService.obtenerConfiguracion();
            String token = config.getFacturacionToken();

            if (token == null || token.isBlank()) {
                resultado.put("success", false);
                resultado.put("error", "Token de facturación no configurado. Configure el token en Configuración > General > Facturación.");
                return resultado;
            }

            // Determinar URL base según modo
            String urlBase = Boolean.TRUE.equals(config.getModoProduccion()) ? URL_BASE_PROD : URL_BASE_DEV;
            String url = urlBase + ENDPOINT_DNI + dni;

            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(token);
            headers.setContentType(MediaType.APPLICATION_JSON);

            HttpEntity<String> entity = new HttpEntity<>(headers);

            log.info("Consultando DNI {} en RENIEC", dni);
            ResponseEntity<ConsultaDocumentoDTO> response = restTemplate.exchange(
                url, HttpMethod.GET, entity, ConsultaDocumentoDTO.class
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                ConsultaDocumentoDTO dto = response.getBody();
                if (dto.isSuccess() && dto.getPayload() != null) {
                    ConsultaDocumentoDTO.Payload payload = dto.getPayload();

                    resultado.put("success", true);
                    resultado.put("tipoDocumento", "1"); // DNI
                    resultado.put("numeroDocumento", dni);
                    resultado.put("nombreCompleto", payload.getNombreRazonSocial());
                    resultado.put("direccion", ""); // RENIEC no devuelve dirección

                    log.info("DNI {} consultado exitosamente: {}", dni, payload.getNombreRazonSocial());
                } else {
                    resultado.put("success", false);
                    resultado.put("error", dto.getMessage() != null ? dto.getMessage() : "No se encontraron datos para el DNI.");
                }
            } else {
                resultado.put("success", false);
                resultado.put("error", "Error al consultar el DNI. Intente nuevamente.");
            }

        } catch (RestClientException e) {
            log.error("Error de conexión al consultar DNI {}: {}", dni, e.getMessage());
            resultado.put("success", false);
            resultado.put("error", "Error de conexión con APISUNAT. Verifique su conexión a internet.");
        } catch (Exception e) {
            log.error("Error inesperado al consultar DNI {}: {}", dni, e.getMessage(), e);
            resultado.put("success", false);
            resultado.put("error", "Error inesperado al consultar el DNI.");
        }

        return resultado;
    }

    /**
     * Consulta un documento automáticamente según su longitud
     * - 8 dígitos: DNI
     * - 11 dígitos: RUC
     *
     * @param documento Número de documento
     * @return Map con los datos o error
     */
    public Map<String, Object> consultarDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", false);
            resultado.put("error", "Debe ingresar un número de documento.");
            return resultado;
        }

        // Limpiar espacios
        documento = documento.trim();

        if (documento.length() == 11) {
            return consultarRuc(documento);
        } else if (documento.length() == 8) {
            return consultarDni(documento);
        } else {
            Map<String, Object> resultado = new HashMap<>();
            resultado.put("success", false);
            resultado.put("error", "Documento inválido. Ingrese un DNI (8 dígitos) o RUC (11 dígitos).");
            return resultado;
        }
    }

    /**
     * Verifica si el servicio de consulta está disponible
     */
    public boolean isServicioDisponible() {
        Configuracion config = configuracionService.obtenerConfiguracion();
        String token = config.getFacturacionToken();
        return token != null && !token.isBlank();
    }
}
