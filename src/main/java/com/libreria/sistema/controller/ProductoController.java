package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.service.ProductoExcelService;
import com.libreria.sistema.service.ProductoService;
import com.libreria.sistema.util.Constants;
import lombok.extern.slf4j.Slf4j;
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

    public ProductoController(ProductoService productoService, ProductoExcelService productoExcelService, ProductoRepository productoRepository) {
        this.productoService = productoService;
        this.productoExcelService = productoExcelService;
        this.productoRepository = productoRepository;
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
                Path rootPath = Paths.get("uploads").toAbsolutePath();
                if (!Files.exists(rootPath)) {
                    Files.createDirectories(rootPath);
                }

                // Generar nombre único (sin usar el nombre original completo para evitar inyección)
                String nombreUnico = UUID.randomUUID().toString() + extension;
                Files.copy(imagen.getInputStream(), rootPath.resolve(nombreUnico));
                producto.setImagen(nombreUnico);
            } else {
                // Si es EDICIÓN y no subió foto nueva, mantener la anterior
                if (producto.getId() != null) {
                    Producto pDb = productoService.obtenerPorId(producto.getId()).orElse(null);
                    if (pDb != null) {
                        producto.setImagen(pDb.getImagen());
                        // Mantener fechas de auditoría si el formulario no las envía
                        if(producto.getFechaCreacion() == null) {
                            producto.setFechaCreacion(pDb.getFechaCreacion());
                        }
                    }
                }
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
            productoService.guardar(producto);
            
            attributes.addFlashAttribute("success", "Producto guardado correctamente");
            return "redirect:/productos";

        } catch (IllegalArgumentException e) {
            // Validación de archivo
            log.warn("Validación de archivo fallida: {}", e.getMessage());
            attributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/productos/nuevo";

        } catch (DataIntegrityViolationException e) {
            // ERROR DE DUPLICADOS (Código Barras o SKU repetido)
            log.error("Error de integridad de datos al guardar producto", e);
            attributes.addFlashAttribute("error", "Error: El Código de Barras o Código Interno ya existe en otro producto.");
            return "redirect:/productos/nuevo";

        } catch (IOException e) {
            log.error("Error de I/O al subir imagen", e);
            attributes.addFlashAttribute("error", "Error al subir la imagen. Por favor intente nuevamente.");
            return "redirect:/productos/nuevo";

        } catch (Exception e) {
            log.error("Error inesperado al guardar producto", e);
            attributes.addFlashAttribute("error", "Error al guardar el producto. Por favor intente nuevamente.");
            return "redirect:/productos/nuevo";
        }
    }

    @GetMapping("/eliminar/{id}")
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
}