package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.EtiquetaPdfOpcionesDTO;
import com.libreria.sistema.model.dto.ProductoRevisionResultadoDTO;
import com.libreria.sistema.model.dto.ProductoRevisionUpdateDTO;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.CosteoSugerenciaService;
import com.libreria.sistema.service.EtiquetaService;
import com.libreria.sistema.service.ProductoCategorizacionService;
import com.libreria.sistema.service.ProductoExcelService;
import com.libreria.sistema.service.ProductoRevisionService;
import com.libreria.sistema.service.ProductoService;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/productos")
@Slf4j
public class ProductoController {

    private final ProductoService productoService;
    private final ProductoExcelService productoExcelService;
    private final ProductoRepository productoRepository;
    private final EtiquetaService etiquetaService;
    private final ConfiguracionService configuracionService;
    private final CosteoSugerenciaService costeoSugerenciaService;
    private final ProductoCategorizacionService productoCategorizacionService;
    private final ProductoRevisionService productoRevisionService;

    @Value("${app.upload-dir}")
    private String uploadDir;

    public ProductoController(ProductoService productoService, ProductoExcelService productoExcelService,
                              ProductoRepository productoRepository, EtiquetaService etiquetaService,
                              ConfiguracionService configuracionService, CosteoSugerenciaService costeoSugerenciaService,
                              ProductoCategorizacionService productoCategorizacionService,
                              ProductoRevisionService productoRevisionService) {
        this.productoService = productoService;
        this.productoExcelService = productoExcelService;
        this.productoRepository = productoRepository;
        this.etiquetaService = etiquetaService;
        this.configuracionService = configuracionService;
        this.costeoSugerenciaService = costeoSugerenciaService;
        this.productoCategorizacionService = productoCategorizacionService;
        this.productoRevisionService = productoRevisionService;
    }

    /**
     * Genera el siguiente SKU automático con formato SKU-00001
     */
    private String generarSiguienteSku() {
        return productoRepository.findUltimoSku()
            .map(ultimo -> {
                try {
                    int numero = Integer.parseInt(ultimo.replace("SKU-", ""));
                    return String.format("SKU-%05d", numero + 1);
                } catch (NumberFormatException e) {
                    return "SKU-00001";
                }
            })
            .orElse("SKU-00001");
    }

    @GetMapping
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public String listar(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        return "productos/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR')")
    public String nuevo(Model model) {
        Producto p = new Producto();
        p.setActivo(true);
        p.setEsLamina(false);
        p.setClasificacion(Producto.CLASIFICACION_MERCADERIA);
        p.setStockMinimo(Constants.DEFAULT_STOCK_MINIMO);
        p.setUnidadMedida("UNIDAD");
        p.setCodigoInterno(generarSiguienteSku()); // SKU autogenerado pero editable

        model.addAttribute("producto", p);
        model.addAttribute("titulo", "Nuevo Producto");
        agregarContextoCosteo(model);
        return "productos/formulario";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return productoService.obtenerPorId(id).map(producto -> {
            if (producto.esLamina()) {
                attributes.addFlashAttribute("warning", "Las laminas se editan desde su modulo dedicado.");
                return "redirect:/laminas/editar/" + id;
            }
            model.addAttribute("producto", producto);
            model.addAttribute("titulo", "Editar Producto");
            agregarContextoCosteo(model);
            return "productos/formulario";
        }).orElseGet(() -> {
            attributes.addFlashAttribute("error", "Producto no encontrado");
            return "redirect:/productos";
        });
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String guardar(@ModelAttribute Producto producto, 
                          @RequestParam("file") MultipartFile imagen, 
                          RedirectAttributes attributes) {
        try {
            // 0. Si es EDICIÓN, preservar datos de auditoría del producto existente
            if (producto.getId() != null) {
                Producto pDb = productoService.obtenerPorId(producto.getId()).orElse(null);
                if (pDb != null) {
                    // Preservar fecha de creación siempre
                    if (producto.getFechaCreacion() == null) {
                        producto.setFechaCreacion(pDb.getFechaCreacion());
                    }
                    // Si no subió foto nueva, mantener la anterior
                    if (imagen.isEmpty()) {
                        producto.setImagen(pDb.getImagen());
                    }
                }
            }

            // 1. MANEJO DE IMAGEN CON VALIDACIÓN
            if (!imagen.isEmpty()) {
                // Validar tamaño de archivo
                if (imagen.getSize() > Constants.MAX_FILE_SIZE) {
                    throw new IllegalArgumentException("El archivo es demasiado grande. Tamaño máximo: 10MB");
                }

                // Validar tipo MIME
                String contentType = imagen.getContentType();
                if (contentType == null || !Arrays.asList(Constants.ALLOWED_IMAGE_MIME_TYPES).contains(contentType)) {
                    throw new IllegalArgumentException("Tipo de archivo no permitido. Solo se permiten imágenes JPG, PNG y WEBP");
                }

                // Validar extensión
                String nombreOriginal = imagen.getOriginalFilename();
                if (nombreOriginal == null) {
                    throw new IllegalArgumentException("Nombre de archivo inválido");
                }
                String extension = nombreOriginal.substring(nombreOriginal.lastIndexOf(".")).toLowerCase();
                if (!Arrays.asList(Constants.ALLOWED_IMAGE_EXTENSIONS).contains(extension)) {
                    throw new IllegalArgumentException("Extensión de archivo no permitida. Solo: JPG, JPEG, PNG, WEBP");
                }

                // Crear carpeta uploads si no existe
                Path rootPath = Paths.get(uploadDir).toAbsolutePath();
                if (!Files.exists(rootPath)) {
                    Files.createDirectories(rootPath);
                }

                // Generar nombre único (sin usar el nombre original completo para evitar inyección)
                String nombreUnico = UUID.randomUUID().toString() + extension;
                Files.copy(imagen.getInputStream(), rootPath.resolve(nombreUnico));
                producto.setImagen(nombreUnico);
            }

            // 2. VALIDACIONES DE NEGOCIO
            if (producto.getStockMinimo() == null) producto.setStockMinimo(0);
            if (producto.getId() == null) producto.setActivo(true);

            // Autogenerar SKU si está vacío (solo para productos nuevos)
            if (producto.getId() == null && (producto.getCodigoInterno() == null || producto.getCodigoInterno().isBlank())) {
                producto.setCodigoInterno(generarSiguienteSku());
            }

            // Convertir a Mayúsculas para estandarizar
            if(producto.getNombre() != null) producto.setNombre(producto.getNombre().toUpperCase());
            if(producto.getMarca() != null) producto.setMarca(producto.getMarca().toUpperCase());

            // 3. GUARDAR
            boolean esNuevo = producto.getId() == null;
            productoService.guardar(producto);

            String accion = esNuevo ? "agregado" : "actualizado";
            attributes.addFlashAttribute("success",
                "Producto " + accion + " correctamente: " + producto.getNombre()
                + " | Stock: " + producto.getStockActual() + " unidades"
                + " | Precio venta: S/ " + producto.getPrecioVenta());
            return "redirect:/productos";

        } catch (IllegalArgumentException e) {
            // Validación de archivo
            log.warn("Validación de archivo fallida: {}", e.getMessage());
            attributes.addFlashAttribute("error", e.getMessage());
            return producto.getId() != null ? "redirect:/productos/editar/" + producto.getId() : "redirect:/productos/nuevo";

        } catch (DataIntegrityViolationException e) {
            // ERROR DE DUPLICADOS (Código Barras o SKU repetido)
            log.error("Error de integridad de datos al guardar producto", e);
            attributes.addFlashAttribute("error", "Error: El Código de Barras o Código Interno ya existe en otro producto.");
            return producto.getId() != null ? "redirect:/productos/editar/" + producto.getId() : "redirect:/productos/nuevo";

        } catch (IOException e) {
            log.error("Error de I/O al subir imagen", e);
            attributes.addFlashAttribute("error", "Error al subir la imagen. Por favor intente nuevamente.");
            return producto.getId() != null ? "redirect:/productos/editar/" + producto.getId() : "redirect:/productos/nuevo";

        } catch (Exception e) {
            log.error("Error inesperado al guardar producto", e);
            attributes.addFlashAttribute("error", "Error al guardar el producto. Por favor intente nuevamente.");
            return producto.getId() != null ? "redirect:/productos/editar/" + producto.getId() : "redirect:/productos/nuevo";
        }
    }

    @GetMapping("/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_ELIMINAR')")
    public String eliminar(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            productoService.eliminar(id);
            attributes.addFlashAttribute("success", "Producto eliminado/desactivado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "No se puede eliminar: " + e.getMessage());
        }
        return "redirect:/productos";
    }

    // =====================================================
    //     IMPORTACIÓN / EXPORTACIÓN MASIVA EXCEL
    // =====================================================

    /**
     * Descarga la plantilla Excel vacía para importar productos
     */
    @GetMapping("/plantilla-excel")
    public ResponseEntity<byte[]> descargarPlantilla() {
        try {
            byte[] plantilla = productoExcelService.generarPlantilla();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "plantilla_productos.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(plantilla);

        } catch (IOException e) {
            log.error("Error generando plantilla Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Exporta todos los productos actuales a Excel
     */
    @GetMapping("/exportar-excel")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<byte[]> exportarProductos() {
        try {
            byte[] excel = productoExcelService.exportarProductos();

            String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String nombreArchivo = "productos_" + fecha + ".xlsx";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", nombreArchivo);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(excel);

        } catch (IOException e) {
            log.error("Error exportando productos a Excel", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Vista para importar productos desde Excel
     */
    @GetMapping("/importar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String vistaImportar(Model model) {
        return "productos/importar";
    }

    @GetMapping("/categorias-masivo")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String categoriasMasivo(@RequestParam(value = "soloSinCategoria", defaultValue = "true") boolean soloSinCategoria,
                                   Model model) {
        model.addAttribute("productosCategoria", productoCategorizacionService.listarProductos(soloSinCategoria));
        model.addAttribute("soloSinCategoria", soloSinCategoria);
        model.addAttribute("categoriasSugeridas", productoRepository.findDistinctCategorias());
        return "productos/categorias-masivo";
    }

    @PostMapping("/categorias-masivo/aplicar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String aplicarCategoriaMasiva(@RequestParam("categoria") String categoria,
                                         @RequestParam("productoIds") java.util.List<Long> productoIds,
                                         RedirectAttributes attributes) {
        int actualizados = productoCategorizacionService.aplicarCategoria(categoria, productoIds);
        attributes.addFlashAttribute("success", "Categoria aplicada a " + actualizados + " productos.");
        return "redirect:/productos/categorias-masivo";
    }

    @GetMapping("/revision")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String revisionRapida(Model model) {
        model.addAttribute("categoriasRevision", productoRevisionService.obtenerCategorias());
        model.addAttribute("tiposRevision", productoRevisionService.obtenerTipos());
        return "productos/revision";
    }

    @GetMapping("/revision/api")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ProductoRevisionResultadoDTO buscarRevisionRapida(
            @RequestParam(required = false) String termino,
            @RequestParam(required = false) Long id,
            @RequestParam(required = false) String categoria,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String tipo,
            @RequestParam(required = false) String clasificacion,
            @RequestParam(required = false) BigDecimal precioVentaDesde,
            @RequestParam(required = false) BigDecimal precioVentaHasta,
            @RequestParam(required = false) BigDecimal precioCompraDesde,
            @RequestParam(required = false) BigDecimal precioCompraHasta,
            @RequestParam(required = false) Integer stockDesde,
            @RequestParam(required = false) Integer stockHasta,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "nombre") String sort,
            @RequestParam(defaultValue = "asc") String dir) {
        return productoRevisionService.buscar(termino, id, categoria, estado, tipo, clasificacion,
                precioVentaDesde, precioVentaHasta, precioCompraDesde, precioCompraHasta,
                stockDesde, stockHasta, page, size, sort, dir);
    }

    @PostMapping("/revision/api/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> actualizarRevisionRapida(@PathVariable Long id,
                                                      @RequestBody ProductoRevisionUpdateDTO request,
                                                      Authentication authentication) {
        try {
            String usuario = authentication != null ? authentication.getName() : "sistema";
            return ResponseEntity.ok(productoRevisionService.actualizar(id, request, usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", e.getMessage()));
        } catch (Exception e) {
            log.error("Error actualizando producto desde revision rapida", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "No se pudo actualizar el producto."));
        }
    }

    /**
     * Procesa la importación de productos desde Excel
     */
    @PostMapping("/importar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String importarProductos(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "actualizarExistentes", defaultValue = "false") boolean actualizarExistentes,
            RedirectAttributes attributes) {

        if (archivo.isEmpty()) {
            attributes.addFlashAttribute("error", "Por favor seleccione un archivo Excel");
            return "redirect:/productos/importar";
        }

        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null || (!nombreArchivo.endsWith(".xlsx") && !nombreArchivo.endsWith(".xls"))) {
            attributes.addFlashAttribute("error", "El archivo debe ser un Excel (.xlsx o .xls)");
            return "redirect:/productos/importar";
        }

        try {
            Map<String, Object> resultado = productoExcelService.importarProductos(archivo, actualizarExistentes);

            int creados = (int) resultado.get("creados");
            int actualizados = (int) resultado.get("actualizados");
            int omitidos = (int) resultado.get("omitidos");

            StringBuilder mensaje = new StringBuilder();
            mensaje.append("Importación completada: ");
            mensaje.append(creados).append(" productos creados");
            if (actualizados > 0) {
                mensaje.append(", ").append(actualizados).append(" actualizados");
            }
            if (omitidos > 0) {
                mensaje.append(", ").append(omitidos).append(" omitidos");
            }

            if ((boolean) resultado.get("success")) {
                attributes.addFlashAttribute("success", mensaje.toString());
            } else {
                attributes.addFlashAttribute("warning", mensaje.toString());
            }

            // Agregar errores si los hay
            @SuppressWarnings("unchecked")
            java.util.List<String> errores = (java.util.List<String>) resultado.get("errores");
            if (errores != null && !errores.isEmpty()) {
                attributes.addFlashAttribute("erroresImportacion", errores);
            }

            @SuppressWarnings("unchecked")
            java.util.List<String> advertencias = (java.util.List<String>) resultado.get("advertencias");
            if (advertencias != null && !advertencias.isEmpty()) {
                attributes.addFlashAttribute("advertenciasImportacion", advertencias);
            }

            return "redirect:/productos";

        } catch (IOException e) {
            log.error("Error procesando archivo Excel", e);
            attributes.addFlashAttribute("error", "Error al procesar el archivo: " + e.getMessage());
            return "redirect:/productos/importar";
        }
    }

    // =====================================================
    //     GENERACIÓN DE ETIQUETAS CON CÓDIGO DE BARRAS
    // =====================================================

    /**
     * Genera PDF con etiquetas de códigos de barras para los productos seleccionados.
     * El formato (A4 o Ticket) se determina según la configuración del sistema.
     *
     * @param request Lista de IDs de productos y cantidad de etiquetas por producto
     * @return PDF con las etiquetas listas para imprimir, o JSON con error descriptivo
     */
    @PostMapping("/etiquetas/imprimir")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<?> imprimirEtiquetas(@RequestBody EtiquetaPdfOpcionesDTO request) {
        try {
            // Validación de entrada con mensaje claro
            if (request == null) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "error", "Solicitud inválida",
                                "mensaje", "No se recibieron datos de la solicitud"
                        ));
            }

            if (request.getProductoIds() == null || request.getProductoIds().isEmpty()) {
                return ResponseEntity.badRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "error", "Sin productos seleccionados",
                                "mensaje", "Debe seleccionar al menos un producto para generar etiquetas"
                        ));
            }

            int cantidad = request.getCantidad() != null && request.getCantidad() > 0 ? request.getCantidad() : 1;
            if (cantidad > 100) {
                cantidad = 100; // Límite de seguridad
            }
            request.setCantidad(cantidad);

            log.info("Generando etiquetas para {} productos, {} por producto",
                    request.getProductoIds().size(), cantidad);

            byte[] pdf = etiquetaService.generarPdfEtiquetas(request.getProductoIds(), cantidad, request);

            if (pdf == null || pdf.length == 0) {
                return ResponseEntity.status(500)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "error", "PDF vacío",
                                "mensaje", "No se pudo generar el contenido del PDF"
                        ));
            }

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            // IMPORTANTE: Solo UN header Content-Disposition (evita ERR_RESPONSE_HEADERS_MULTIPLE_CONTENT_DISPOSITION)
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"etiquetas.pdf\"");
            headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
            headers.setContentLength(pdf.length);

            log.info("PDF de etiquetas generado exitosamente: {} bytes", pdf.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(pdf);

        } catch (IllegalArgumentException e) {
            // Errores de validación (datos inválidos)
            log.warn("Error de validación generando etiquetas: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Error de validación",
                            "mensaje", e.getMessage()
                    ));

        } catch (Exception e) {
            // Error inesperado - devolver JSON descriptivo en lugar de 500 vacío
            log.error("Error generando etiquetas PDF: {}", e.getMessage(), e);

            String mensajeUsuario = "Error al generar el PDF de etiquetas";
            if (e.getMessage() != null && !e.getMessage().isEmpty()) {
                mensajeUsuario += ": " + e.getMessage();
            }

            return ResponseEntity.status(500)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "error", "Error interno",
                            "mensaje", mensajeUsuario,
                            "detalle", e.getClass().getSimpleName()
                    ));
        }
    }

    private void agregarContextoCosteo(Model model) {
        model.addAttribute("configCosteo", configuracionService.obtenerConfiguracion());
        model.addAttribute("factorIndirectoSugerido", costeoSugerenciaService.obtenerFactorSugeridoGlobal());
    }
}
