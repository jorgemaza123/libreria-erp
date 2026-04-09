package com.libreria.sistema.controller;

import com.libreria.sistema.aspect.RequerirCajaAbierta;
import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.PosProformaDTO;
import com.libreria.sistema.model.dto.ServicioRapidoDTO;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.ConsultaDocumentoService;
import com.libreria.sistema.service.CotizacionPdfService;
import com.libreria.sistema.service.DevolucionService;
import com.libreria.sistema.service.LaminaBusquedaService;
import com.libreria.sistema.service.LaminaService;
import com.libreria.sistema.service.ProductoBusquedaService;
import com.libreria.sistema.service.ReporteService;
import com.libreria.sistema.service.VentaService;
import com.libreria.sistema.util.Constants;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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
    private final ClienteRepository clienteRepository;
    private final ConfiguracionService configuracionService;
    private final CotizacionPdfService cotizacionPdfService;
    private final VentaService ventaService;
    private final ReporteService reporteService;
    private final ConsultaDocumentoService consultaDocumentoService;
    private final ProductoBusquedaService productoBusquedaService;
    private final LaminaBusquedaService laminaBusquedaService;
    private final LaminaService laminaService;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    @Autowired
    private DevolucionService devolucionService;

    public VentaController(ProductoRepository productoRepository,
            VentaRepository ventaRepository,
            ClienteRepository clienteRepository,
            ConfiguracionService configuracionService,
            CotizacionPdfService cotizacionPdfService,
            VentaService ventaService,
            ReporteService reporteService,
            ConsultaDocumentoService consultaDocumentoService,
            ProductoBusquedaService productoBusquedaService,
            LaminaBusquedaService laminaBusquedaService,
            LaminaService laminaService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.clienteRepository = clienteRepository;
        this.configuracionService = configuracionService;
        this.cotizacionPdfService = cotizacionPdfService;
        this.ventaService = ventaService;
        this.reporteService = reporteService;
        this.consultaDocumentoService = consultaDocumentoService;
        this.productoBusquedaService = productoBusquedaService;
        this.laminaBusquedaService = laminaBusquedaService;
        this.laminaService = laminaService;
    }

    /** Redirige /ventas → /ventas/lista para evitar Error 500 en URL raíz. */
    @GetMapping
    public String indice() {
        return "redirect:/ventas/lista";
    }

    @GetMapping("/lista")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public String listaVentas(@RequestParam(defaultValue = "") String buscar,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("fechaCreacion").descending());

        org.springframework.data.domain.Page<Venta> ventas;
        if (buscar != null && !buscar.isBlank()) {
            ventas = ventaRepository.buscarPorTermino(buscar.trim(), pageable);
        } else {
            ventas = ventaRepository.findAll(pageable);
        }

        java.util.List<Long> ventaIds = ventas.getContent().stream()
                .map(Venta::getId).collect(java.util.stream.Collectors.toList());
        java.util.Map<Long, java.math.BigDecimal> totalesDevueltos = devolucionService
                .obtenerMapaTotalesDevueltos(ventaIds);

        model.addAttribute("ventas", ventas);
        model.addAttribute("buscar", buscar);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalesDevueltos", totalesDevueltos);
        return "ventas/lista";
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    @RequerirCajaAbierta(mensaje = "Debe abrir caja antes de realizar ventas.")
    public String nuevaVenta(Model model) {
        // Obtener estado de facturación electrónica
        boolean facturaElectronicaActiva = ventaService.isFacturacionElectronicaActiva();

        model.addAttribute("facturaElectronicaActiva", facturaElectronicaActiva);
        model.addAttribute("serieBoletaInfo", facturaElectronicaActiva ? "B001 (Oficial SUNAT)" : "I001 (Interno)");
        model.addAttribute("serieFacturaInfo", facturaElectronicaActiva ? "F001 (Oficial SUNAT)" : "IF001 (Interno)");
        model.addAttribute("modoFacturacion", facturaElectronicaActiva ? "ELECTRÓNICA" : "INTERNA");

        model.addAttribute("laminaCategoriasPos", laminaService.listarCategoriasActivas());
        model.addAttribute("laminaPrecioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "ventas/pos";
    }

    /**
     * OMNIBUSCADOR V3: Búsqueda avanzada de productos con soporte visual.
     *
     * Características:
     * - Búsqueda multi-campo: nombre, marca, categoría, códigos, descripción, TAGS
     * - Tokenizada: "cuaderno loro" encuentra "LORO CUADERNO ARIMANY"
     * - Búsqueda por sinónimos: "pegamento" encuentra "CINTA SCOTCH" si tiene tag
     * - Case-insensitive con normalización de acentos
     * - Ordenada por relevancia: stock > 0 primero, códigos exactos priorizados
     * - Incluye datos para panel visual (imagen, ubicación, stock mínimo)
     *
     * @param term Término de búsqueda
     * @return Lista de productos formateados para Select2 con datos visuales
     */
    @GetMapping("/api/buscar-productos")
    @ResponseBody
    public List<Map<String, Object>> buscarProductos(@RequestParam String term) {
        BigDecimal margenMinimoAlerta = configuracionService.obtenerConfiguracion().getMargenMinimoAlerta();
        if (margenMinimoAlerta == null)
            margenMinimoAlerta = new BigDecimal("15.00");
        final BigDecimal margenMinConfig = margenMinimoAlerta;

        return productoBusquedaService.buscar(term, 20).stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());

            // Texto enriquecido con marca y stock para mejor identificación
            StringBuilder textBuilder = new StringBuilder();
            if (p.getCodigoBarra() != null && !p.getCodigoBarra().isEmpty()) {
                textBuilder.append(p.getCodigoBarra()).append(" - ");
            }
            textBuilder.append(p.getNombre());
            if (p.getMarca() != null && !p.getMarca().isEmpty()) {
                textBuilder.append(" [").append(p.getMarca()).append("]");
            }
            textBuilder.append(" (Stock: ").append(p.getStockActual()).append(")");

            map.put("text", textBuilder.toString());
            map.put("precio", p.getPrecioVenta());
            map.put("precioMin", p.getPrecioVenta() != null
                    ? p.getPrecioVenta().multiply(Constants.DESCUENTO_MINIMO_VENTA)
                    : null);
            map.put("stock", p.getStockActual());
            map.put("stockMinimo", p.getStockMinimo());
            map.put("nombre", p.getNombre());
            map.put("marca", p.getMarca());
            map.put("categoria", p.getCategoria());
            map.put("descripcion", p.getDescripcion());
            map.put("imagen", p.getImagen());
            map.put("codigoBarra", p.getCodigoBarra());
            map.put("codigoInterno", p.getCodigoInterno());
            map.put("tags", p.getTags());

            // Ubicación detallada
            String ubicacionEstante = p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "";
            String ubicacionFila = p.getUbicacionFila() != null ? p.getUbicacionFila() : "";
            String ubicacionColumna = p.getUbicacionColumna() != null ? p.getUbicacionColumna() : "";
            map.put("ubicacion", ubicacionEstante + "-" + ubicacionFila);
            map.put("ubicacionEstante", ubicacionEstante);
            map.put("ubicacionFila", ubicacionFila);
            map.put("ubicacionColumna", ubicacionColumna);

            // Flags de estado
            map.put("tieneStock", p.getStockActual() != null && p.getStockActual() > 0);
            map.put("stockBajo", p.getStockActual() != null && p.getStockMinimo() != null
                    && p.getStockActual() <= p.getStockMinimo());

            // Alerta de margen bajo (calculado en backend — no expone precioCompra)
            BigDecimal precioVenta = p.getPrecioVenta();
            BigDecimal precioCompra = p.getPrecioCompra();
            if (precioVenta != null && precioCompra != null && precioVenta.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal margenActual = precioVenta.subtract(precioCompra)
                        .divide(precioVenta, 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP);
                map.put("margenActual", margenActual);
                // Precio mínimo = costoCompra / (1 - margenMinimo/100)
                BigDecimal divisor = BigDecimal.ONE
                        .subtract(margenMinConfig.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                if (divisor.compareTo(BigDecimal.ZERO) > 0) {
                    map.put("precioMinimoRecomendado", precioCompra.divide(divisor, 2, RoundingMode.HALF_UP));
                }
                map.put("margenBajo", margenActual.compareTo(margenMinConfig) < 0);
            } else {
                map.put("margenBajo", false);
            }
            map.put("margenMinimoAlerta", margenMinConfig);

            return map;
        }).collect(Collectors.toList());
    }

    @GetMapping("/api/buscar-laminas")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    @ResponseBody
    public List<Map<String, Object>> buscarLaminas(@RequestParam String term) {
        return laminaBusquedaService.buscar(term, 25).stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            map.put("text", construirTextoLamina(p));
            map.put("precio", p.getPrecioVenta());
            map.put("stock", p.getStockActual());
            map.put("stockMinimo", p.getStockMinimo());
            map.put("nombre", p.getNombre());
            map.put("marca", p.getLaminaMarca() != null ? p.getLaminaMarca() : p.getMarca());
            map.put("categoria", p.getCategoria());
            map.put("descripcion", p.getDescripcion());
            map.put("imagen", p.getImagen());
            map.put("codigoBarra", p.getCodigoBarra());
            map.put("codigoInterno", p.getCodigoInterno());
            map.put("esLamina", true);
            map.put("laminaNumero", p.getLaminaNumero());
            map.put("laminaTitulo", p.getLaminaTitulo());
            map.put("laminaMarca", p.getLaminaMarca());
            map.put("laminaCategoria", p.getLaminaCategoria());
            map.put("laminaProveedorRef", p.getLaminaProveedorRef());
            map.put("laminaZona", p.getLaminaZona());
            map.put("laminaContenedor", p.getLaminaContenedor());
            map.put("laminaPosicion", p.getLaminaPosicion());
            map.put("ubicacionTexto", p.getLaminaUbicacionTexto());
            map.put("ubicacion", p.getLaminaUbicacionTexto());
            map.put("ubicacionEstante", p.getLaminaZona());
            map.put("ubicacionFila", p.getLaminaContenedor());
            map.put("ubicacionColumna", p.getLaminaPosicion());
            map.put("tieneStock", p.getStockActual() != null && p.getStockActual() > 0);
            map.put("stockBajo", p.getStockActual() != null && p.getStockMinimo() != null
                    && p.getStockActual() <= p.getStockMinimo());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * AUTOCOMPLETE: Sugerencias rápidas en tiempo real.
     * Optimizado para velocidad, retorna máximo 10 resultados.
     *
     * @param q Query de búsqueda (mínimo 1 carácter)
     * @return Top 10 productos sugeridos
     */
    @GetMapping("/api/autocomplete-productos")
    @ResponseBody
    public List<Map<String, Object>> autocompleteProductos(@RequestParam String q) {
        return productoBusquedaService.autocompleteParaSelect2(q);
    }

    /**
     * PRODUCTOS RELACIONADOS: Busca productos similares por categoría y tags.
     *
     * @param id ID del producto actual
     * @return Lista de hasta 6 productos relacionados
     */
    @GetMapping("/api/productos-relacionados/{id}")
    @ResponseBody
    public List<Map<String, Object>> productosRelacionados(@PathVariable Long id) {
        return productoRepository.findById(id).map(producto -> {
            return productoBusquedaService.buscarRelacionados(
                    producto.getId(),
                    producto.getCategoria(),
                    producto.getTags(),
                    6).stream().map(p -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("id", p.getId());
                        map.put("nombre", p.getNombre());
                        map.put("precio", p.getPrecioVenta());
                        map.put("stock", p.getStockActual());
                        map.put("imagen", p.getImagen());
                        map.put("marca", p.getMarca());
                        return map;
                    }).collect(Collectors.toList());
        }).orElse(List.of());
    }

    private String construirTextoLamina(Producto producto) {
        StringBuilder texto = new StringBuilder("LAMINA");
        if (producto.getLaminaNumero() != null && !producto.getLaminaNumero().isBlank()) {
            texto.append(" ").append(producto.getLaminaNumero());
        }
        if (producto.getLaminaTitulo() != null && !producto.getLaminaTitulo().isBlank()) {
            texto.append(" - ").append(producto.getLaminaTitulo());
        } else if (producto.getNombre() != null && !producto.getNombre().isBlank()) {
            texto.append(" - ").append(producto.getNombre());
        }
        if (producto.getLaminaMarca() != null && !producto.getLaminaMarca().isBlank()) {
            texto.append(" [").append(producto.getLaminaMarca()).append("]");
        }
        texto.append(" (Stock: ").append(producto.getStockActual() != null ? producto.getStockActual() : 0).append(")");
        return texto.toString();
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

        // Buscar si el cliente ya existe localmente para agregar su teléfono
        clienteRepository.findByNumeroDocumento(docLimpio).ifPresent(clienteLocal -> {
            if (clienteLocal.getTelefono() != null && !clienteLocal.getTelefono().isBlank()) {
                resultado.put("telefono", clienteLocal.getTelefono());
            }
            // Si la API no devolvió dirección pero el cliente local la tiene
            if (resultado.get("direccion") == null || resultado.get("direccion").toString().isBlank()) {
                if (clienteLocal.getDireccion() != null && !clienteLocal.getDireccion().isBlank()) {
                    resultado.put("direccion", clienteLocal.getDireccion());
                }
            }
        });

        if (Boolean.TRUE.equals(resultado.get("success"))) {
            return ResponseEntity.ok(resultado);
        } else {
            return ResponseEntity.badRequest().body(resultado);
        }
    }

    /**
     * Guarda una nueva venta usando VentaService (con soporte DUAL-MODE)
     * - Si facturaElectronicaActiva = false: usa series internas (I001/IF001)
     * - Si facturaElectronicaActiva = true: usa series oficiales (B001/F001) y
     * envía a SUNAT
     */
    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    @RequerirCajaAbierta(mensaje = "CAJA CERRADA: Debe abrir caja antes de registrar ventas.")
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
                errorResponse.put("error", "El producto #" + (i + 1) + " no tiene ID válido.");
                errorResponse.put("code", "INVALID_PRODUCT");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (item.getCantidad() == null || item.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                errorResponse.put("error", "El producto #" + (i + 1) + " tiene cantidad inválida.");
                errorResponse.put("code", "INVALID_QUANTITY");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (item.getPrecioVenta() == null || item.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                errorResponse.put("error", "El producto #" + (i + 1) + " tiene precio inválido.");
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
                    errorResponse.put("error",
                            "Para Boletas mayores a S/ 700.00 es obligatorio el DNI del cliente (8 dígitos).");
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

        if (dto.isEntregaAlFinal()) {
            if (!"CREDITO".equalsIgnoreCase(dto.getFormaPago())) {
                errorResponse.put("error", "El apartado debe registrarse como crédito para permitir pagos parciales.");
                errorResponse.put("code", "INVALID_LAYAWAY_MODE");
                return ResponseEntity.badRequest().body(errorResponse);
            }
            if (!"NOTA_VENTA".equalsIgnoreCase(tipoComprobante)) {
                errorResponse.put("error", "Los apartados deben registrarse como Nota de Venta hasta la entrega final.");
                errorResponse.put("code", "LAYAWAY_REQUIRES_NOTA");
                return ResponseEntity.badRequest().body(errorResponse);
            }
        }

        try {
            // Delegar toda la lógica al VentaService
            Map<String, Object> resultado = ventaService.crearVenta(dto);
            return ResponseEntity.ok(resultado);

        } catch (OptimisticLockingFailureException e) {
            log.warn("Conflicto de concurrencia en stock al procesar venta", e);
            errorResponse.put("error",
                    "Otro vendedor actualizó el stock mientras procesaba la venta. Actualice la página e intente nuevamente.");
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

    @PostMapping("/api/proforma-pdf")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    public ResponseEntity<?> exportarProformaPdf(@RequestBody PosProformaDTO dto) {
        Map<String, Object> errorResponse = new HashMap<>();

        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            errorResponse.put("error", "Agregue al menos un producto al carrito antes de exportar la cotizacion.");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        try {
            Cotizacion proforma = construirProformaDesdePos(dto);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            cotizacionPdfService.generarPdf(proforma, outputStream);

            String archivo = "Cotizacion-POS-" + LocalDate.now() + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + archivo + "\"")
                    .body(outputStream.toByteArray());
        } catch (RuntimeException e) {
            log.warn("Error validando cotizacion temporal desde POS: {}", e.getMessage());
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error inesperado al exportar cotizacion temporal desde POS", e);
            errorResponse.put("error", "No se pudo generar la cotizacion PDF.");
            return ResponseEntity.status(500).body(errorResponse);
        }
    }

    @PostMapping("/anular/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_ELIMINAR')")
    public String anularVenta(@PathVariable Long id,
                              @RequestParam(defaultValue = "Anulación desde historial de ventas") String motivo,
                              RedirectAttributes redirectAttributes) {
        try {
            Map<String, Object> resultado = ventaService.anularVenta(id, motivo);
            String mensaje = resultado.get("advertencia") != null
                    ? resultado.get("advertencia").toString()
                    : "Venta anulada correctamente.";
            redirectAttributes.addFlashAttribute("success", mensaje);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al anular venta {}", id, e);
            redirectAttributes.addFlashAttribute("error", "No se pudo anular la venta.");
        }
        return "redirect:/ventas/lista";
    }

    @PostMapping("/entregar/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_EDITAR')")
    public String entregarApartado(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            ventaService.entregarApartado(id);
            redirectAttributes.addFlashAttribute("success", "Apartado entregado y cerrado correctamente.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Error inesperado al entregar apartado {}", id, e);
            redirectAttributes.addFlashAttribute("error", "No se pudo completar la entrega del apartado.");
        }
        return "redirect:/ventas/lista";
    }

    @PostMapping("/api/servicio-rapido")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    @RequerirCajaAbierta(mensaje = "CAJA CERRADA: Debe abrir caja antes de registrar ventas.")
    @ResponseBody
    public ResponseEntity<?> registrarServicioRapido(@Valid @RequestBody ServicioRapidoDTO dto,
                                                     BindingResult bindingResult) {
        Map<String, Object> errorResponse = new HashMap<>();

        if (bindingResult.hasErrors()) {
            String errores = bindingResult.getAllErrors().stream()
                    .map(error -> error.getDefaultMessage())
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("Datos inválidos");
            errorResponse.put("error", errores);
            errorResponse.put("code", "VALIDATION_ERROR");
            return ResponseEntity.badRequest().body(errorResponse);
        }

        Producto servicioGeneral = productoRepository.findByCodigoInterno("SERV-001")
                .orElseThrow(() -> new RuntimeException("No se encontró el servicio base SERV-001"));

        String descripcion = dto.getDescripcion() != null && !dto.getDescripcion().isBlank()
                ? dto.getDescripcion().trim()
                : "Servicio personalizado";

        VentaDTO ventaRapida = new VentaDTO();
        ventaRapida.setClienteNombre("PUBLICO GENERAL");
        ventaRapida.setClienteDocumento("00000000");
        ventaRapida.setClienteDireccion("");
        ventaRapida.setClienteTelefono("");
        ventaRapida.setTipoComprobante("NOTA_VENTA");
        ventaRapida.setFormaPago("CONTADO");
        ventaRapida.setMetodoPago("EFECTIVO");

        VentaDTO.DetalleDTO detalle = new VentaDTO.DetalleDTO();
        detalle.setProductoId(servicioGeneral.getId());
        detalle.setCantidad(BigDecimal.ONE);
        detalle.setPrecioVenta(dto.getMonto());
        detalle.setDescripcion(descripcion);
        ventaRapida.setItems(List.of(detalle));

        try {
            Map<String, Object> resultado = new HashMap<>(ventaService.crearVenta(ventaRapida));
            resultado.put("descripcion", descripcion);
            resultado.put("monto", dto.getMonto());
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            log.error("Error de negocio al registrar servicio rápido: {}", e.getMessage());
            errorResponse.put("error", e.getMessage());
            errorResponse.put("code", "BUSINESS_ERROR");
            return ResponseEntity.badRequest().body(errorResponse);
        } catch (Exception e) {
            log.error("Error inesperado al registrar servicio rápido", e);
            errorResponse.put("error", "Error interno al registrar el servicio rápido.");
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
            data.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-"
                    + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
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
        if (venta == null)
            return "redirect:/ventas/lista";

        Configuracion config = configuracionService.obtenerConfiguracion();
        model.addAttribute("venta", venta);
        model.addAttribute("config", config);
        model.addAttribute("cantidadesDevueltas", devolucionService.obtenerCantidadesDevueltasPorVenta(id));
        model.addAttribute("totalDevuelto", devolucionService.obtenerTotalDevueltoPorVenta(id));

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
        if (venta == null)
            return "redirect:/ventas/lista";
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
        if (venta == null)
            return "redirect:/ventas/lista";
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
            response.setHeader("Content-Disposition", "inline; filename=\"" + nombreArchivo + "\"");

            java.util.Map<Long, java.math.BigDecimal> cantDev = devolucionService
                    .obtenerCantidadesDevueltasPorVenta(id);
            java.math.BigDecimal totalDev = devolucionService.obtenerTotalDevueltoPorVenta(id);
            // SISTEMA HÍBRIDO: Rutar según formato configurado
            if ("TICKET".equalsIgnoreCase(formato)) {
                reporteService.generarPdfTicketVenta(venta, config, response.getOutputStream(), cantDev, totalDev);
            } else {
                reporteService.generarPdfA4Venta(venta, config, response.getOutputStream(), cantDev, totalDev);
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

            java.util.Map<Long, java.math.BigDecimal> cantDev = devolucionService
                    .obtenerCantidadesDevueltasPorVenta(id);
            java.math.BigDecimal totalDev = devolucionService.obtenerTotalDevueltoPorVenta(id);
            reporteService.generarPdfTicketVenta(venta, config, response.getOutputStream(), cantDev, totalDev);
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

            java.util.Map<Long, java.math.BigDecimal> cantDev = devolucionService
                    .obtenerCantidadesDevueltasPorVenta(id);
            java.math.BigDecimal totalDev = devolucionService.obtenerTotalDevueltoPorVenta(id);
            reporteService.generarPdfA4Venta(venta, config, response.getOutputStream(), cantDev, totalDev);
        } catch (Exception e) {
            log.error("Error al generar PDF A4 de venta {}: {}", id, e.getMessage());
            try {
                response.sendError(500, "Error al generar el PDF");
            } catch (Exception ex) {
                log.error("Error al enviar respuesta de error", ex);
            }
        }
    }

    private Cotizacion construirProformaDesdePos(PosProformaDTO dto) {
        Cotizacion cotizacion = new Cotizacion();
        cotizacion.setSerie("P001");
        cotizacion.setNumero(LocalDateTime.now().toLocalTime().toSecondOfDay());
        cotizacion.setFechaEmision(LocalDate.now());
        cotizacion.setFechaVencimiento(LocalDate.now().plusDays(7));
        cotizacion.setClienteNombre(dto.getClienteNombre() != null && !dto.getClienteNombre().isBlank()
                ? dto.getClienteNombre().trim()
                : "CLIENTE VARIOS");
        cotizacion.setClienteDocumento(dto.getClienteDocumento() != null && !dto.getClienteDocumento().isBlank()
                ? dto.getClienteDocumento().trim()
                : "00000000");
        cotizacion.setClienteTelefono(dto.getClienteTelefono() != null ? dto.getClienteTelefono().trim() : "");
        cotizacion.setFormaPago(dto.getFormaPago() != null && !dto.getFormaPago().isBlank()
                ? dto.getFormaPago().trim()
                : "CONTADO");
        cotizacion.setMetodoPago(dto.getMetodoPago() != null && !dto.getMetodoPago().isBlank()
                ? dto.getMetodoPago().trim()
                : "EFECTIVO");
        cotizacion.setEstado("EMITIDA");
        cotizacion.setObservaciones(dto.getObservaciones() != null && !dto.getObservaciones().isBlank()
                ? dto.getObservaciones().trim()
                : "Cotizacion generada desde POS. Sujeta a disponibilidad de stock.");
        cotizacion.setCondiciones("Precios sujetos a variacion y stock disponible al momento de la venta.");

        BigDecimal totalBruto = BigDecimal.ZERO;
        BigDecimal descuento = dto.getDescuento() != null ? dto.getDescuento() : BigDecimal.ZERO;
        BigDecimal igvFactor = configuracionService.getIgvFactor();

        for (PosProformaDTO.ItemDTO itemDto : dto.getItems()) {
            if (itemDto.getProductoId() == null) {
                throw new RuntimeException("Todos los productos de la cotizacion deben tener ID valido.");
            }
            if (itemDto.getCantidad() == null || itemDto.getCantidad().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Todas las cantidades de la cotizacion deben ser mayores a cero.");
            }
            if (itemDto.getPrecioVenta() == null || itemDto.getPrecioVenta().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("Todos los precios de la cotizacion deben ser mayores a cero.");
            }

            Producto producto = productoRepository.findById(itemDto.getProductoId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: ID " + itemDto.getProductoId()));

            DetalleCotizacion detalle = new DetalleCotizacion();
            detalle.setCotizacion(cotizacion);
            detalle.setProducto(producto);
            detalle.setCantidad(itemDto.getCantidad());
            detalle.setPrecioUnitario(itemDto.getPrecioVenta().setScale(2, RoundingMode.HALF_UP));
            detalle.setSubtotal(detalle.getCantidad().multiply(detalle.getPrecioUnitario()).setScale(2, RoundingMode.HALF_UP));
            detalle.setTipoItem("SERVICIO".equalsIgnoreCase(producto.getTipo()) ? "SERVICIO" : "PRODUCTO");
            detalle.setCategoriaServicio("SERVICIO".equalsIgnoreCase(detalle.getTipoItem()) ? producto.getCategoria() : null);
            detalle.setDescripcion(itemDto.getDescripcion() != null && !itemDto.getDescripcion().isBlank()
                    ? itemDto.getDescripcion().trim()
                    : producto.getNombre());
            cotizacion.getItems().add(detalle);
            totalBruto = totalBruto.add(detalle.getSubtotal());
        }

        BigDecimal totalConDescuento = totalBruto.subtract(descuento).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
        BigDecimal subtotal = totalConDescuento.divide(igvFactor, 2, RoundingMode.HALF_UP);
        BigDecimal igv = totalConDescuento.subtract(subtotal).setScale(2, RoundingMode.HALF_UP);

        cotizacion.setDescuento(descuento.setScale(2, RoundingMode.HALF_UP));
        cotizacion.setSubtotal(subtotal);
        cotizacion.setIgv(igv);
        cotizacion.setTotal(totalConDescuento);
        cotizacion.setMontoInicial(BigDecimal.ZERO);
        cotizacion.setSaldoPendiente(BigDecimal.ZERO);
        cotizacion.setDiasCredito(null);
        return cotizacion;
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
     * IMPORTANTE: Usa ":: contenido" para devolver solo el fragmento HTML sin el
     * layout.
     */
    @GetMapping("/detalle/{id}")
    @PreAuthorize("hasPermission(null, 'VENTAS_VER')")
    public String verDetalle(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findByIdWithDetalles(id).orElse(null);
        model.addAttribute("venta", venta);
        if (venta != null) {
            model.addAttribute("cantidadesDevueltas", devolucionService.obtenerCantidadesDevueltasPorVenta(id));
            model.addAttribute("totalDevuelto", devolucionService.obtenerTotalDevueltoPorVenta(id));

            // Total de unidades vendidas por productoId en esta venta (para comparar
            // correctamente
            // cuando hay varias líneas con el mismo producto, ej: cuadernos por curso)
            java.util.Map<Long, java.math.BigDecimal> cantidadesTotalPorProducto = new java.util.HashMap<>();
            if (venta.getItems() != null) {
                for (DetalleVenta item : venta.getItems()) {
                    if (item.getProducto() != null) {
                        cantidadesTotalPorProducto.merge(
                                item.getProducto().getId(),
                                item.getCantidad(),
                                java.math.BigDecimal::add);
                    }
                }
            }
            model.addAttribute("cantidadesTotalPorProducto", cantidadesTotalPorProducto);
        }
        // C-1: exponer config para que el modal use datos reales de la empresa
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/modal_detalle :: contenido";
    }
}
