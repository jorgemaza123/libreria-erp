package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.SunatRequestDTO;
import com.libreria.sistema.model.dto.SunatResponseDTO;
import com.libreria.sistema.repository.ConfiguracionSunatRepository;
import com.libreria.sistema.repository.CorrelativoRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Map;
import java.util.List;

@Service
public class FacturacionElectronicaService {

    private final ConfiguracionSunatRepository configuracionRepo;
    private final VentaRepository ventaRepo;
    private final CorrelativoRepository correlativoRepo;
    private final RestTemplate restTemplate;
    private final ConfiguracionService configuracionService;

    public FacturacionElectronicaService(
            ConfiguracionSunatRepository configuracionRepo,
            VentaRepository ventaRepo,
            CorrelativoRepository correlativoRepo,
            RestTemplate restTemplate,
            ConfiguracionService configuracionService) {
        this.configuracionRepo = configuracionRepo;
        this.ventaRepo = ventaRepo;
        this.correlativoRepo = correlativoRepo;
        this.restTemplate = restTemplate;
        this.configuracionService = configuracionService;
    }

    /**
     * Envía un comprobante (Boleta/Factura) a SUNAT mediante el PSE APISUNAT
     */
    @Transactional
    public SunatResponseDTO enviarComprobanteSunat(Long ventaId) {
        // 1. Obtener la venta
        Venta venta = ventaRepo.findById(ventaId)
                .orElseThrow(() -> new RuntimeException("Venta no encontrada: " + ventaId));

        // 2. Validar configuración SUNAT
        ConfiguracionSunat config = validarConfiguracion();

        // 3. Mapear Venta → SunatRequestDTO
        SunatRequestDTO request = mapearVentaToSunatRequest(venta);

        try {
            // 4. Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getTokenApiSunat());

            // 5. Crear entidad HTTP
            HttpEntity<SunatRequestDTO> entity = new HttpEntity<>(request, headers);

            // 6. Enviar POST a APISUNAT (URL ya incluye el path completo)
            String url = config.getUrlApiSunat();
            ResponseEntity<SunatResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    SunatResponseDTO.class
            );

            SunatResponseDTO responseBody = response.getBody();

            // 7. Procesar respuesta y actualizar venta
            if (responseBody != null) {
                procesarRespuestaSunat(venta, responseBody);
            }

            return responseBody;

        } catch (Exception e) {
            // 8. Registrar error en la venta
            venta.setSunatEstado("ERROR");
            venta.setSunatMensajeError("Error al enviar: " + e.getMessage());
            venta.setSunatFechaEnvio(LocalDateTime.now());
            ventaRepo.save(venta);

            throw new RuntimeException("Error al enviar comprobante a SUNAT: " + e.getMessage(), e);
        }
    }

    /**
     * Mapea una Venta a SunatRequestDTO según especificación APISUNAT
     */
    private SunatRequestDTO mapearVentaToSunatRequest(Venta venta) {
        SunatRequestDTO request = new SunatRequestDTO();

        // Datos del comprobante
        request.setDocumento(venta.getTipoComprobante().toLowerCase()); // "boleta" o "factura"
        request.setSerie(venta.getSerie());
        request.setNumero(venta.getNumero());
        request.setFechaDeEmision(venta.getFechaEmision().toString()); // "2025-01-03"
        request.setMoneda(venta.getMoneda());
        request.setTipoOperacion(venta.getTipoOperacion());

        // Fecha de vencimiento (solo crédito)
        if ("Crédito".equalsIgnoreCase(venta.getFormaPago())) {
            request.setFechaDeVencimiento(venta.getFechaVencimiento().toString());
        }

        // Datos del cliente
        request.setClienteTipoDeDocumento(venta.getClienteTipoDocumento());
        request.setClienteNumeroDeDocumento(venta.getClienteNumeroDocumento());
        request.setClienteDenominacion(venta.getClienteDenominacion());
        request.setClienteDireccion(venta.getClienteDireccion());

        // Items
        List<SunatRequestDTO.ItemDTO> items = new ArrayList<>();
        for (DetalleVenta detalle : venta.getItems()) {
            SunatRequestDTO.ItemDTO item = new SunatRequestDTO.ItemDTO();
            item.setUnidadDeMedida(detalle.getUnidadMedida());
            item.setDescripcion(detalle.getDescripcion());
            item.setCantidad(detalle.getCantidad().toString());
            item.setValorUnitario(detalle.getValorUnitario().setScale(6, RoundingMode.HALF_UP).toString());
            item.setPorcentajeIgv(detalle.getPorcentajeIgv().toString());
            item.setCodigoTipoAfectacionIgv(detalle.getCodigoTipoAfectacionIgv());
            item.setNombreTributo("IGV");
            items.add(item);
        }
        request.setItems(items);

        // Cuotas (solo para crédito)
        if ("Crédito".equalsIgnoreCase(venta.getFormaPago())) {
            List<SunatRequestDTO.CuotaDTO> cuotas = new ArrayList<>();
            SunatRequestDTO.CuotaDTO cuota = new SunatRequestDTO.CuotaDTO();
            cuota.setImporte(venta.getTotal().toString());
            cuota.setFechaDePago(venta.getFechaVencimiento().toString());
            cuotas.add(cuota);
            request.setCuotas(cuotas);
        }

        // Total
        request.setTotal(venta.getTotal().toString());

        return request;
    }

    /**
     * Procesa la respuesta de SUNAT y actualiza la venta
     */
    @Transactional
    public void procesarRespuestaSunat(Venta venta, SunatResponseDTO response) {
        venta.setSunatFechaEnvio(LocalDateTime.now());

        if (Boolean.TRUE.equals(response.getSuccess()) && response.getPayload() != null) {
            // Respuesta exitosa
            SunatResponseDTO.PayloadDTO payload = response.getPayload();

            venta.setSunatEstado(payload.getEstado()); // "ACEPTADO", "PENDIENTE", "RECHAZADO"
            venta.setSunatHash(payload.getHash());
            venta.setSunatXmlUrl(payload.getXml());
            venta.setSunatCdrUrl(payload.getCdr());

            if (payload.getPdf() != null) {
                venta.setSunatPdfUrl(payload.getPdf().getTicket());
            }

            venta.setSunatMensajeError(null); // Limpiar errores previos

        } else {
            // Respuesta con error
            venta.setSunatEstado("RECHAZADO");
            venta.setSunatMensajeError(response.getMessage());
        }

        ventaRepo.save(venta);
    }

    /**
     * Valida que la configuración SUNAT esté completa y activa
     */
    private ConfiguracionSunat validarConfiguracion() {
        ConfiguracionSunat config = configuracionRepo.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new RuntimeException(
                        "No existe configuración de SUNAT. Configure primero en el módulo de Configuración."));

        if (!Boolean.TRUE.equals(config.getFacturaElectronicaActiva())) {
            throw new RuntimeException("La facturación electrónica está desactivada en la configuración.");
        }

        if (config.getTokenApiSunat() == null || config.getTokenApiSunat().isEmpty()) {
            throw new RuntimeException("Token de APISUNAT no configurado.");
        }

        if (config.getUrlApiSunat() == null || config.getUrlApiSunat().isEmpty()) {
            throw new RuntimeException("URL de APISUNAT no configurada.");
        }

        return config;
    }

    /**
     * Calcula el valor unitario SIN IGV a partir del precio con IGV
     * Fórmula: valorUnitario = precioConIgv / igvFactor (configurable)
     */
    public BigDecimal calcularValorUnitarioSinIgv(BigDecimal precioConIgv) {
        BigDecimal igvFactor = configuracionService.getIgvFactor();
        return precioConIgv.divide(igvFactor, 6, RoundingMode.HALF_UP);
    }

    /**
     * Verifica si la facturación electrónica está disponible
     */
    public boolean isFacturacionElectronicaActiva() {
        return configuracionRepo.findFirstByOrderByIdDesc()
                .map(ConfiguracionSunat::getFacturaElectronicaActiva)
                .orElse(false);
    }

    /**
     * Obtiene la configuración actual de SUNAT
     */
    public ConfiguracionSunat obtenerConfiguracionActual() {
        return configuracionRepo.findFirstByOrderByIdDesc()
                .orElse(null);
    }

    /**
     * Sincroniza los correlativos locales con SUNAT al activar facturación electrónica.
     * Consulta al PSE el último número usado de cada serie oficial (B001, F001).
     * Si no hay comprobantes en SUNAT, empezará en 1.
     *
     * @return Mensaje de resultado de la sincronización
     */
    @Transactional
    public String sincronizarConSunat() {
        try {
            // Validar configuración
            ConfiguracionSunat config = validarConfiguracion();

            // Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getTokenApiSunat());

            StringBuilder resultado = new StringBuilder("Sincronización completada:\n");

            // Sincronizar BOLETA B001
            sincronizarSerie(config, headers, "BOLETA", "B001", resultado);

            // Sincronizar FACTURA F001
            sincronizarSerie(config, headers, "FACTURA", "F001", resultado);

            return resultado.toString();

        } catch (Exception e) {
            throw new RuntimeException("Error al sincronizar con SUNAT: " + e.getMessage(), e);
        }
    }

    /**
     * Sincroniza una serie específica consultando el último número en SUNAT
     */
    private void sincronizarSerie(ConfiguracionSunat config, HttpHeaders headers,
                                   String tipo, String serie, StringBuilder resultado) {
        try {
            // Consultar último número usado en SUNAT para esta serie
            // Construir URL para sincronización: reemplazar "/documents" por "/documents/last"
            String baseUrl = config.getUrlApiSunat();
            String url = baseUrl.replace("/documents", "/documents/last") + "?serie=" + serie;

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    Map.class
            );

            Integer ultimoNumeroSunat = 0;

            if (response.getBody() != null && response.getBody().containsKey("numero")) {
                Object numeroObj = response.getBody().get("numero");
                if (numeroObj != null) {
                    ultimoNumeroSunat = Integer.parseInt(numeroObj.toString());
                }
            }

            // Actualizar correlativo local
            Correlativo correlativo = correlativoRepo.findByCodigoAndSerie(tipo, serie)
                    .orElse(new Correlativo(tipo, serie, 0));

            correlativo.setUltimoNumero(ultimoNumeroSunat);
            correlativoRepo.save(correlativo);

            resultado.append(String.format("- %s %s: último número = %d\n",
                    tipo, serie, ultimoNumeroSunat));

        } catch (Exception e) {
            // Si el endpoint no existe o falla, inicializamos en 0
            Correlativo correlativo = correlativoRepo.findByCodigoAndSerie(tipo, serie)
                    .orElse(new Correlativo(tipo, serie, 0));

            if (correlativo.getId() == null) {
                correlativoRepo.save(correlativo);
            }

            resultado.append(String.format("- %s %s: inicializado en 0 (sin datos previos en SUNAT)\n",
                    tipo, serie));
        }
    }

    /**
     * Obtiene las series a usar según el modo de facturación
     * @param tipoComprobante BOLETA, FACTURA, NOTA_VENTA
     * @param facturaElectronicaActiva true = usar series oficiales, false = usar series internas
     * @return Serie correspondiente
     */
    public String obtenerSerie(String tipoComprobante, boolean facturaElectronicaActiva) {
        if (facturaElectronicaActiva) {
            // Modo Electrónico: Series oficiales SUNAT
            return switch (tipoComprobante) {
                case "BOLETA" -> "B001";
                case "FACTURA" -> "F001";
                case "NOTA_CREDITO" -> "C001";
                default -> "N001";
            };
        } else {
            // Modo Interno: Series internas
            return switch (tipoComprobante) {
                case "BOLETA" -> "I001";
                case "FACTURA" -> "IF001";
                case "NOTA_CREDITO" -> "NC01";
                default -> "NI001";
            };
        }
    }

    /**
     * Envía una Nota de Crédito a SUNAT
     */
    @Transactional
    public SunatResponseDTO enviarNotaCreditoSunat(DevolucionVenta devolucion) {
        // 1. Validar configuración SUNAT
        ConfiguracionSunat config = validarConfiguracion();

        // 2. Mapear DevolucionVenta → SunatRequestDTO
        SunatRequestDTO request = mapearDevolucionToSunatRequest(devolucion);

        try {
            // 3. Preparar headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(config.getTokenApiSunat());

            // 4. Crear entidad HTTP
            HttpEntity<SunatRequestDTO> entity = new HttpEntity<>(request, headers);

            // 5. Enviar POST a APISUNAT (URL ya incluye el path completo)
            String url = config.getUrlApiSunat();
            ResponseEntity<SunatResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    SunatResponseDTO.class
            );

            return response.getBody();

        } catch (Exception e) {
            throw new RuntimeException("Error al enviar nota de crédito a SUNAT: " + e.getMessage(), e);
        }
    }

    /**
     * Mapea una DevolucionVenta a SunatRequestDTO para nota de crédito
     */
    private SunatRequestDTO mapearDevolucionToSunatRequest(DevolucionVenta devolucion) {
        SunatRequestDTO request = new SunatRequestDTO();
        Venta ventaOriginal = devolucion.getVentaOriginal();

        // Datos de la nota de crédito
        request.setDocumento("nota_credito");
        request.setSerie(devolucion.getSerie());
        request.setNumero(devolucion.getNumero());
        request.setFechaDeEmision(devolucion.getFechaEmision().toString());
        request.setMoneda("PEN");
        request.setTipoOperacion("0101");

        // Documento afectado (venta original)
        SunatRequestDTO.DocumentoAfectadoDTO docAfectado = new SunatRequestDTO.DocumentoAfectadoDTO();
        docAfectado.setDocumento(ventaOriginal.getTipoComprobante().toLowerCase());
        docAfectado.setSerie(ventaOriginal.getSerie());
        docAfectado.setNumero(ventaOriginal.getNumero());
        request.setDocumentoAfectado(docAfectado);

        // Tipo y motivo de nota de crédito
        request.setNotaCreditoCodigoTipo("01"); // 01 = Anulación de operación
        request.setNotaCreditoMotivo(obtenerMotivoDescripcion(devolucion.getMotivoDevolucion()));

        // Datos del cliente (de la venta original)
        request.setClienteTipoDeDocumento(ventaOriginal.getClienteTipoDocumento());
        request.setClienteNumeroDeDocumento(ventaOriginal.getClienteNumeroDocumento());
        request.setClienteDenominacion(ventaOriginal.getClienteDenominacion());
        request.setClienteDireccion(ventaOriginal.getClienteDireccion());

        // Items de la devolución
        List<SunatRequestDTO.ItemDTO> items = new ArrayList<>();
        BigDecimal igvFactor = configuracionService.getIgvFactor();
        String igvPorcentajeStr = configuracionService.getIgvPorcentajeString();

        for (DetalleDevolucion detalle : devolucion.getDetalles()) {
            SunatRequestDTO.ItemDTO item = new SunatRequestDTO.ItemDTO();
            item.setUnidadDeMedida("NIU");
            item.setDescripcion(detalle.getDescripcion());
            item.setCantidad(detalle.getCantidadDevuelta().toString());

            // Calcular valor unitario sin IGV (configurable)
            BigDecimal valorUnitario = detalle.getPrecioUnitario().divide(igvFactor, 6, RoundingMode.HALF_UP);
            item.setValorUnitario(valorUnitario.toString());
            item.setPorcentajeIgv(igvPorcentajeStr);
            item.setCodigoTipoAfectacionIgv("10"); // Gravado
            item.setNombreTributo("IGV");
            items.add(item);
        }
        request.setItems(items);

        // Total
        request.setTotal(devolucion.getTotalDevuelto().toString());

        return request;
    }

    /**
     * Convierte el código de motivo a descripción legible
     */
    private String obtenerMotivoDescripcion(String motivoCodigo) {
        return switch (motivoCodigo) {
            case "PRODUCTO_DEFECTUOSO" -> "Producto defectuoso o en mal estado";
            case "ERROR_FACTURACION" -> "Error en la facturación";
            case "CLIENTE_DESISTE" -> "Cliente desiste de la compra";
            case "OTRO" -> "Otros motivos";
            default -> motivoCodigo;
        };
    }
}
