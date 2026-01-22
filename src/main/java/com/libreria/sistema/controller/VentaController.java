package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.ConsultaDocumentoService;
import com.libreria.sistema.service.ReporteService;
import com.libreria.sistema.service.VentaService;
import com.libreria.sistema.util.Constants;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ventas")
@Slf4j
public class VentaController {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final ConfiguracionService configuracionService;
    private final VentaService ventaService;
    private final ReporteService reporteService;
    private final ConsultaDocumentoService consultaDocumentoService;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    public VentaController(ProductoRepository productoRepository,
                           VentaRepository ventaRepository,
                           ConfiguracionService configuracionService,
                           VentaService ventaService,
                           ReporteService reporteService,
                           ConsultaDocumentoService consultaDocumentoService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.configuracionService = configuracionService;
        this.ventaService = ventaService;
        this.reporteService = reporteService;
        this.consultaDocumentoService = consultaDocumentoService;
    }

    @GetMapping("/lista")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public String listaVentas(@RequestParam(defaultValue = "") String buscar,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size,
                              Model model) {
        org.springframework.data.domain.Pageable pageable =
            org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("fechaCreacion").descending());

        org.springframework.data.domain.Page<Venta> ventas;
        if (buscar != null && !buscar.isBlank()) {
            ventas = ventaRepository.buscarPorTermino(buscar.trim(), pageable);
        } else {
            ventas = ventaRepository.findAll(pageable);
        }

        model.addAttribute("ventas", ventas);
        model.addAttribute("buscar", buscar);
        model.addAttribute("currentPage", page);
        return "ventas/lista";
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    public String nuevaVenta(Model model) {
        // Obtener estado de facturación electrónica
        boolean facturaElectronicaActiva = ventaService.isFacturacionElectronicaActiva();

        model.addAttribute("facturaElectronicaActiva", facturaElectronicaActiva);
        model.addAttribute("serieBoletaInfo", facturaElectronicaActiva ? "B001 (Oficial SUNAT)" : "I001 (Interno)");
        model.addAttribute("serieFacturaInfo", facturaElectronicaActiva ? "F001 (Oficial SUNAT)" : "IF001 (Interno)");
        model.addAttribute("modoFacturacion", facturaElectronicaActiva ? "ELECTRÓNICA" : "INTERNA");

        return "ventas/pos";
    }

    @GetMapping("/api/buscar-productos")
    @ResponseBody
    public List<Map<String, Object>> buscarProductos(@RequestParam String term) {
        return productoRepository.buscarInteligente(term).stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("text", p.getCodigoBarra() + " - " + p.getNombre() + " (Stock: " + p.getStockActual() + ")");
            map.put("precio", p.getPrecioVenta());
            map.put("precioMin", p.getPrecioVenta().multiply(Constants.DESCUENTO_MINIMO_VENTA));
            map.put("stock", p.getStockActual());
            map.put("nombre", p.getNombre());
            map.put("marca", p.getMarca());
            map.put("imagen", p.getImagen());
            map.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-" + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Consulta datos de un documento (DNI/RUC) en APISUNAT/RENIEC.
     * Solo disponible para FACTURAS - consulta automática de datos del cliente.
     *
     * @param documento Número de documento (8 dígitos para DNI, 11 para RUC)
     * @return Datos del contribuyente/persona
     */
    @GetMapping("/api/consultar-documento")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> consultarDocumento(@RequestParam String documento) {
        log.info("Consultando documento: {}", documento);

        if (documento == null || documento.isBlank()) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Debe ingresar un número de documento.");
            return ResponseEntity.badRequest().body(error);
        }

        // Limpiar espacios y caracteres no numéricos
        String docLimpio = documento.trim().replaceAll("[^0-9]", "");

        // Validar longitud
        if (docLimpio.length() != 8 && docLimpio.length() != 11) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", "Documento inválido. Ingrese un DNI (8 dígitos) o RUC (11 dígitos).");
            return ResponseEntity.badRequest().body(error);
        }

        // Consultar en APISUNAT
        Map<String, Object> resultado = consultaDocumentoService.consultarDocumento(docLimpio);

        if (Boolean.TRUE.equals(resultado.get("success"))) {
            return ResponseEntity.ok(resultado);
        } else {
            return ResponseEntity.badRequest().body(resultado);
        }
    }

    /**
     * Guarda una nueva venta usando VentaService (con soporte DUAL-MODE)
     * - Si facturaElectronicaActiva = false: usa series internas (I001/IF001)
     * - Si facturaElectronicaActiva = true: usa series oficiales (B001/F001) y envía a SUNAT
     */
    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    public ResponseEntity<?> guardarVenta(@Valid @RequestBody VentaDTO dto, BindingResult bindingResult) {
        Map<String, Object> errorResponse = new HashMap<>();

        // Validar errores de binding
        if (bindingResult.hasErrors()) {
            String errores = bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage())
                .reduce((a, b) -> a + "; " + b)
                .orElse("Datos inválidos");
            log.warn("Validación fallida en guardar venta: {}", errores);
            errorResponse.put("error", errores);
            errorResponse.put("code", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validaciones de negocio antes de procesar
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            errorResponse.put("error", "Debe agregar al menos un producto al carrito.");
            errorResponse.put("code", "EMPTY_CART");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validar que todos los items tengan cantidad y precio válidos
        for (int i = 0; i < dto.getItems().size(); i++) {
            VentaDTO.DetalleDTO item = dto.getItems().get(i);
            if (item.getProductoId() == null) {
                errorResponse.put("error", "El producto #" + (i+1) + " no tiene ID válido.");
                errorResponse.put("code", "INVALID_PRODUCT");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (item.getCantidad() == null || item.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                errorResponse.put("error", "El producto #" + (i+1) + " tiene cantidad inválida.");
                errorResponse.put("code", "INVALID_QUANTITY");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (item.getPrecioVenta() == null || item.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                errorResponse.put("error", "El producto #" + (i+1) + " tiene precio inválido.");
                errorResponse.put("code", "INVALID_PRICE");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        // Validar tipo de comprobante
        String tipoComprobante = dto.getTipoComprobante();
        if (tipoComprobante == null || tipoComprobante.isBlank()) {
            errorResponse.put("error", "Debe seleccionar un tipo de comprobante (Boleta, Factura o Nota de Venta).");
            errorResponse.put("code", "MISSING_DOC_TYPE");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        // Validar RUC para facturas
        if ("FACTURA".equalsIgnoreCase(tipoComprobante)) {
            String doc = dto.getClienteDocumento();
            if (doc == null || doc.length() != 11) {
                errorResponse.put("error", "Para emitir Factura se requiere un RUC válido de 11 dígitos.");
                errorResponse.put("code", "INVALID_RUC");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        // Validar DNI para boletas mayores a S/ 700
        // Solo aplica si la facturacion electronica esta activa
        boolean facturaElectronicaActiva = ventaService.isFacturacionElectronicaActiva();
        if ("BOLETA".equalsIgnoreCase(tipoComprobante) && facturaElectronicaActiva) {
            // Calcular total de la venta
            BigDecimal totalVenta = dto.getItems().stream()
                    .map(item -> item.getPrecioVenta().multiply(item.getCantidad()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal limiteIdentificacion = new BigDecimal("700");
            if (totalVenta.compareTo(limiteIdentificacion) > 0) {
                String doc = dto.getClienteDocumento();
                if (doc == null || doc.length() != 8 || doc.equals("00000000")) {
                    errorResponse.put("error", "Para Boletas mayores a S/ 700.00 es obligatorio el DNI del cliente (8 dígitos).");
                    errorResponse.put("code", "DNI_REQUIRED_OVER_700");
                    return ResponseEntity.badRequest().body(errorResponse);
                }
            }
        }

        // Validar documento para crédito
        if ("CREDITO".equalsIgnoreCase(dto.getFormaPago())) {
            String doc = dto.getClienteDocumento();
            if (doc == null || doc.length() < 8) {
                errorResponse.put("error", "Para ventas a crédito es obligatorio el DNI o RUC del cliente.");
                errorResponse.put("code", "MISSING_DOCUMENT");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        try {
            // Delegar toda la lógica al VentaService
            Map<String, Object> resultado = ventaService.crearVenta(dto);
            return ResponseEntity.ok(resultado);

        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de concurrencia en stock al procesar venta", e);
            errorResponse.put("error", "Otro vendedor actualizó el stock mientras procesaba la venta. Actualice la página e intente nuevamente.");
            errorResponse.put("code", "STOCK_CONFLICT");
            return ResponseEntity.status(409).body(errorResponse);

        } catch (RuntimeException e) {
            log.error("Error de negocio al procesar venta: {}", e.getMessage());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("code", "BUSINESS_ERROR");
            return ResponseEntity.badRequest().body(errorResponse);

        } catch (Exception e) {
            log.error("Error inesperado al procesar venta", e);
            errorResponse.put("error", "Error interno al procesar la venta. Por favor contacte al administrador.");
            errorResponse.put("code", "INTERNAL_ERROR");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    // --- MÉTODOS EXISTENTES SIN CAMBIOS ---
    @GetMapping("/api/producto/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerProducto(@PathVariable Long id) {
        return productoRepository.findById(id).map(p -> {
            Map<String, Object> data = new HashMap<>();
            data.put("id", p.getId());
            data.put("nombre", p.getNombre());
            data.put("marca", p.getMarca());
            data.put("precio", p.getPrecioVenta());
            data.put("precioMinimo", p.getPrecioVenta().multiply(Constants.DESCUENTO_MINIMO_VENTA));
            data.put("stock", p.getStockActual());
            data.put("imagen", p.getImagen());
            data.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-" + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Imprime comprobante usando el formato configurado (A4 o TICKET).
     * El formato se determina según la configuración del sistema.
     */
    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null) return "redirect:/ventas/lista";

        Configuracion config = configuracionService.obtenerConfiguracion();
        model.addAttribute("venta", venta);
        model.addAttribute("config", config);

        // SISTEMA HÍBRIDO: Rutar según formato configurado
        String formato = config.getFormatoImpresion();
        if ("TICKET".equalsIgnoreCase(formato)) {
            return "ventas/ticket";
        }
        return "ventas/impresion"; // A4 por defecto
    }

    /**
     * Fuerza impresión en formato TICKET (80mm térmico).
     * Útil para puntos de venta con impresoras térmicas.
     */
    @GetMapping("/ticket/{id}")
    public String ticket(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null) return "redirect:/ventas/lista";
        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/ticket";
    }

    /**
     * Fuerza impresión en formato A4 (hoja completa).
     * Útil para facturas formales o envío por correo.
     */
    @GetMapping("/impresion-a4/{id}")
    public String impresionA4(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null) return "redirect:/ventas/lista";
        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/impresion";
    }

    /**
     * SISTEMA HÍBRIDO: Descarga PDF del comprobante.
     * Usa el formato configurado (A4 o TICKET).
     */
    @GetMapping("/pdf/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) {
        try {
            Venta venta = ventaRepository.findByIdWithDetalles(id).orElse(null);
            if (venta == null) {
                response.sendError(404, "Venta no encontrada");
                return;
            }

            Configuracion config = configuracionService.obtenerConfiguracion();
            String formato = config.getFormatoImpresion();
            String nombreArchivo = venta.getSerie() + "-" + venta.getNumero() + ".pdf";

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");

            // SISTEMA HÍBRIDO: Rutar según formato configurado
            if ("TICKET".equalsIgnoreCase(formato)) {
                reporteService.generarPdfTicketVenta(venta, config, response.getOutputStream());
            } else {
                reporteService.generarPdfA4Venta(venta, config, response.getOutputStream());
            }
        } catch (Exception e) {
            log.error("Error al generar PDF de venta {}: {}", id, e.getMessage());
            try {
                response.sendError(500, "Error al generar el PDF");
            } catch (Exception ex) {
                log.error("Error al enviar respuesta de error", ex);
            }
        }
    }

    /**
     * Descarga PDF en formato TICKET (forzado).
     */
    @GetMapping("/pdf-ticket/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public void descargarPdfTicket(@PathVariable Long id, HttpServletResponse response) {
        try {
            Venta venta = ventaRepository.findByIdWithDetalles(id).orElse(null);
            if (venta == null) {
                response.sendError(404, "Venta no encontrada");
                return;
            }

            Configuracion config = configuracionService.obtenerConfiguracion();
            String nombreArchivo = venta.getSerie() + "-" + venta.getNumero() + "_ticket.pdf";

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");

            reporteService.generarPdfTicketVenta(venta, config, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF ticket de venta {}: {}", id, e.getMessage());
            try {
                response.sendError(500, "Error al generar el PDF");
            } catch (Exception ex) {
                log.error("Error al enviar respuesta de error", ex);
            }
        }
    }

    /**
     * Descarga PDF en formato A4 (forzado).
     */
    @GetMapping("/pdf-a4/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public void descargarPdfA4(@PathVariable Long id, HttpServletResponse response) {
        try {
            Venta venta = ventaRepository.findByIdWithDetalles(id).orElse(null);
            if (venta == null) {
                response.sendError(404, "Venta no encontrada");
                return;
            }

            Configuracion config = configuracionService.obtenerConfiguracion();
            String nombreArchivo = venta.getSerie() + "-" + venta.getNumero() + "_a4.pdf";

            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + nombreArchivo + "\"");

            reporteService.generarPdfA4Venta(venta, config, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error al generar PDF A4 de venta {}: {}", id, e.getMessage());
            try {
                response.sendError(500, "Error al generar el PDF");
            } catch (Exception ex) {
                log.error("Error al enviar respuesta de error", ex);
            }
        }
    }

    @PostMapping("/api/solicitar-stock")
    @ResponseBody
    public ResponseEntity<?> registrarSolicitud(@RequestParam String producto) {
        String nombreLimpio = producto.trim().toUpperCase();
        SolicitudProducto solicitud = solicitudRepository.findByNombreProductoAndEstado(nombreLimpio, "PENDIENTE")
                .orElse(new SolicitudProducto());
        if (solicitud.getId() == null) {
            solicitud.setNombreProducto(nombreLimpio);
            solicitud.setContador(1);
        } else {
            solicitud.setContador(solicitud.getContador() + 1);
            solicitud.setUltimaSolicitud(LocalDateTime.now());
        }
        solicitudRepository.save(solicitud);
        return ResponseEntity.ok("Solicitud registrada. Total pedidos: " + solicitud.getContador());
    }

    /**
     * Buscar ventas para devolución (por serie-número, cliente, o documento)
     */
    @GetMapping("/api/buscar-devolucion")
    @PreAuthorize("hasPermission(null, 'DEVOLUCIONES_VER')")
    @ResponseBody
    public ResponseEntity<List<Venta>> buscarVentasParaDevolucion(@RequestParam String termino) {
        try {
            List<Venta> ventas = ventaRepository.buscarParaDevolucion(termino);
            return ResponseEntity.ok(ventas);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Obtener detalle completo de una venta (incluyendo detalles)
     */
    @GetMapping("/api/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    @ResponseBody
    public ResponseEntity<Venta> obtenerVentaPorId(@PathVariable Long id) {
        try {
            Venta venta = ventaRepository.findByIdWithDetalles(id)
                    .orElseThrow(() -> new RuntimeException("Venta no encontrada"));
            return ResponseEntity.ok(venta);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Muestra el detalle de venta en el modal.
     * IMPORTANTE: Usa ":: contenido" para devolver solo el fragmento HTML sin el layout.
     */
    @GetMapping("/detalle/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public String verDetalle(@PathVariable Long id, Model model) {
        // Buscamos la venta (asegúrate de que traiga los items/detalles)
        Venta venta = ventaRepository.findById(id).orElse(null);
        
        model.addAttribute("venta", venta);
        
        // "ventas/modal_detalle" es el nombre del archivo HTML (sin .html)
        // ":: contenido" es el nombre del th:fragment que está en tu archivo
        return "ventas/modal_detalle :: contenido"; 
    }
}