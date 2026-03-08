package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.service.EtiquetaService;
import com.libreria.sistema.service.ProductoExcelService;
import com.libreria.sistema.service.ProductoService;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
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

    @Value("${app.upload-dir}")
    private String uploadDir;

    public ProductoController(ProductoService productoService, ProductoExcelService productoExcelService,
                              ProductoRepository productoRepository, EtiquetaService etiquetaService) {
        this.productoService = productoService;
        this.productoExcelService = productoExcelService;
        this.productoRepository = productoRepository;
        this.etiquetaService = etiquetaService;
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
        p.setStockMinimo(Constants.DEFAULT_STOCK_MINIMO);
        p.setUnidadMedida("UNIDAD");
        p.setCodigoInterno(generarSiguienteSku()); // SKU autogenerado pero editable

        model.addAttribute("producto", p);
        model.addAttribute("titulo", "Nuevo Producto");
        return "productos/formulario";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes attributes) {
        return productoService.obtenerPorId(id).map(producto -> {
            model.addAttribute("producto", producto);
            model.addAttribute("titulo", "Editar Producto");
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
     * DTO para recibir la solicitud de impresión de etiquetas.
     */
    public static class EtiquetaRequest {
        private java.util.List<Long> productoIds;
        private int cantidad = 1;

        public java.util.List<Long> getProductoIds() { return productoIds; }
        public void setProductoIds(java.util.List<Long> productoIds) { this.productoIds = productoIds; }
        public int getCantidad() { return cantidad; }
        public void setCantidad(int cantidad) { this.cantidad = cantidad; }
    }

    /**
     * Genera PDF con etiquetas de códigos de barras para los productos seleccionados.
     * El formato (A4 o Ticket) se determina según la configuración del sistema.
     *
     * @param request Lista de IDs de productos y cantidad de etiquetas por producto
     * @return PDF con las etiquetas listas para imprimir, o JSON con error descriptivo
     */
    @PostMapping("/etiquetas/imprimir")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<?> imprimirEtiquetas(@RequestBody EtiquetaRequest request) {
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

            int cantidad = request.getCantidad() > 0 ? request.getCantidad() : 1;
            if (cantidad > 100) {
                cantidad = 100; // Límite de seguridad
            }

            log.info("Generando etiquetas para {} productos, {} por producto",
                    request.getProductoIds().size(), cantidad);

            byte[] pdf = etiquetaService.generarPdfEtiquetas(request.getProductoIds(), cantidad);

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
}