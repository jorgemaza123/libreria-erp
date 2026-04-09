package com.libreria.sistema.controller;

import com.libreria.sistema.model.LaminaCategoria;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.LaminaCargaMasivaDTO;
import com.libreria.sistema.model.dto.LaminaFormDTO;
import com.libreria.sistema.service.LaminaExcelService;
import com.libreria.sistema.service.LaminaService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/laminas")
@PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
@Slf4j
public class LaminaController {

    private final LaminaService laminaService;
    private final LaminaExcelService laminaExcelService;

    public LaminaController(LaminaService laminaService, LaminaExcelService laminaExcelService) {
        this.laminaService = laminaService;
        this.laminaExcelService = laminaExcelService;
    }

    @ModelAttribute("laminaCategorias")
    public List<LaminaCategoria> cargarCategoriasActivas() {
        return laminaService.listarCategoriasActivas();
    }

    @GetMapping
    public String listar(Model model) {
        model.addAttribute("laminas", laminaService.listarActivas());
        model.addAttribute("precioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "laminas/lista";
    }

    @GetMapping("/nuevo")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR')")
    public String nuevo(@RequestParam(value = "laminaCategoria", required = false) String laminaCategoria,
                        @RequestParam(value = "laminaMarca", required = false) String laminaMarca,
                        @RequestParam(value = "laminaContenedor", required = false) String laminaContenedor,
                        Model model) {
        LaminaFormDTO dto = new LaminaFormDTO();
        dto.setActivo(true);
        dto.setStockActual(1);
        dto.setStockMinimo(0);
        dto.setPrecioCompra(BigDecimal.ZERO);
        dto.setPrecioVenta(LaminaService.PRECIO_VENTA_DEFAULT);
        dto.setLaminaCategoria(laminaCategoria);
        dto.setLaminaMarca(laminaMarca);
        dto.setLaminaContenedor(laminaContenedor);
        model.addAttribute("lamina", dto);
        model.addAttribute("titulo", "Nueva Lamina");
        model.addAttribute("precioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "laminas/formulario";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String editar(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Producto producto = laminaService.obtenerLamina(id).orElse(null);
        if (producto == null) {
            redirectAttributes.addFlashAttribute("error", "La lamina no fue encontrada.");
            return "redirect:/laminas";
        }

        model.addAttribute("lamina", laminaService.convertirADto(producto));
        model.addAttribute("titulo", "Editar Lamina");
        model.addAttribute("precioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "laminas/formulario";
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    public String guardar(@ModelAttribute("lamina") LaminaFormDTO dto,
                          @RequestParam(value = "continuar", defaultValue = "false") boolean continuar,
                          RedirectAttributes redirectAttributes) {
        try {
            Producto producto = laminaService.guardarLamina(dto, false);
            redirectAttributes.addFlashAttribute("success",
                    "Lamina guardada correctamente: " + producto.getLaminaEtiquetaTexto());

            if (continuar) {
                redirectAttributes.addAttribute("laminaCategoria", producto.getLaminaCategoria());
                redirectAttributes.addAttribute("laminaMarca", producto.getLaminaMarca());
                redirectAttributes.addAttribute("laminaContenedor", producto.getLaminaContenedor());
                return "redirect:/laminas/nuevo";
            }

            return "redirect:/laminas";
        } catch (Exception e) {
            log.error("Error guardando lamina", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "No se pudo guardar la lamina.");
            return dto.getId() != null ? "redirect:/laminas/editar/" + dto.getId() : "redirect:/laminas/nuevo";
        }
    }

    @PostMapping("/toggle/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String toggle(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            Producto producto = laminaService.obtenerLamina(id)
                    .orElseThrow(() -> new IllegalArgumentException("La lamina no existe."));
            producto.setActivo(!producto.isActivo());
            laminaService.guardarLamina(laminaService.convertirADto(producto), false);
            redirectAttributes.addFlashAttribute("success",
                    producto.isActivo() ? "Lamina activada correctamente." : "Lamina desactivada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "No se pudo actualizar la lamina.");
        }
        return "redirect:/laminas";
    }

    @PostMapping("/api/guardar-rapido")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR') or hasPermission(null, 'VENTAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> guardarRapido(@RequestBody LaminaFormDTO dto) {
        try {
            Producto producto = laminaService.guardarLamina(dto, true);
            return ResponseEntity.ok(construirRespuestaLamina(producto));
        } catch (Exception e) {
            log.error("Error guardando lamina rapida", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage() != null ? e.getMessage() : "No se pudo guardar la lamina."));
        }
    }

    @GetMapping("/carga-rapida")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR')")
    public String vistaCargaRapida(Model model) {
        LaminaCargaMasivaDTO dto = new LaminaCargaMasivaDTO();
        dto.setStockActual(1);
        dto.setPrecioVenta(LaminaService.PRECIO_VENTA_DEFAULT);
        model.addAttribute("dto", dto);
        model.addAttribute("titulo", "Carga Rapida de Laminas");
        model.addAttribute("precioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "laminas/carga-rapida";
    }

    @PostMapping("/carga-rapida")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR')")
    public String guardarCargaRapida(@ModelAttribute LaminaCargaMasivaDTO dto, RedirectAttributes redirectAttributes) {
        try {
            Map<String, Object> resultado = laminaService.guardarCargaMasiva(dto);
            int creadas = (int) resultado.get("creadas");
            int actualizadas = (int) resultado.get("actualizadas");
            @SuppressWarnings("unchecked")
            List<String> actualizadasDetalle = (List<String>) resultado.get("actualizadasDetalle");
            @SuppressWarnings("unchecked")
            List<String> errores = (List<String>) resultado.get("errores");

            if (creadas > 0 || actualizadas > 0) {
                StringBuilder mensaje = new StringBuilder("Carga rapida completada: ");
                mensaje.append(creadas).append(" laminas creadas");
                if (actualizadas > 0) {
                    mensaje.append(", ").append(actualizadas).append(" existentes actualizadas sumando stock");
                }
                mensaje.append(".");
                redirectAttributes.addFlashAttribute("success", mensaje.toString());
            }
            if (actualizadasDetalle != null && !actualizadasDetalle.isEmpty()) {
                redirectAttributes.addFlashAttribute("actualizacionesCargaRapida", actualizadasDetalle);
            }
            if (!errores.isEmpty()) {
                redirectAttributes.addFlashAttribute("warning", "Algunas lineas no se procesaron.");
                redirectAttributes.addFlashAttribute("erroresCargaRapida", errores);
            }
            return "redirect:/laminas/carga-rapida";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/laminas/carga-rapida";
        }
    }

    @GetMapping("/importar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String vistaImportar(Model model) {
        model.addAttribute("precioVentaDefault", LaminaService.PRECIO_VENTA_DEFAULT);
        return "laminas/importar";
    }

    @PostMapping("/importar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String importar(@RequestParam("archivo") MultipartFile archivo,
                           @RequestParam(value = "actualizarExistentes", defaultValue = "false") boolean actualizarExistentes,
                           RedirectAttributes redirectAttributes) {
        if (archivo.isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Selecciona un archivo Excel para importar laminas.");
            return "redirect:/laminas/importar";
        }

        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null || (!nombreArchivo.endsWith(".xlsx") && !nombreArchivo.endsWith(".xls"))) {
            redirectAttributes.addFlashAttribute("error", "El archivo debe ser Excel (.xlsx o .xls).");
            return "redirect:/laminas/importar";
        }

        try {
            Map<String, Object> resultado = laminaExcelService.importarLaminas(archivo, actualizarExistentes);
            int creadas = (int) resultado.get("creadas");
            int actualizadas = (int) resultado.get("actualizadas");
            int omitidas = (int) resultado.get("omitidas");
            @SuppressWarnings("unchecked")
            List<String> actualizadasDetalle = (List<String>) resultado.get("actualizadasDetalle");
            @SuppressWarnings("unchecked")
            List<String> omitidasDetalle = (List<String>) resultado.get("omitidasDetalle");
            @SuppressWarnings("unchecked")
            List<String> errores = (List<String>) resultado.get("errores");

            StringBuilder mensaje = new StringBuilder("Importacion de laminas completada: ");
            mensaje.append(creadas).append(" creadas");
            if (actualizadas > 0) {
                mensaje.append(", ").append(actualizadas).append(" actualizadas");
            }
            if (omitidas > 0) {
                mensaje.append(", ").append(omitidas).append(" omitidas");
            }

            if (errores.isEmpty()) {
                redirectAttributes.addFlashAttribute("success", mensaje.toString());
            } else {
                redirectAttributes.addFlashAttribute("warning", mensaje.toString());
                redirectAttributes.addFlashAttribute("erroresImportacionLaminas", errores);
            }
            if (actualizadasDetalle != null && !actualizadasDetalle.isEmpty()) {
                redirectAttributes.addFlashAttribute("actualizacionesImportacionLaminas", actualizadasDetalle);
            }
            if (omitidasDetalle != null && !omitidasDetalle.isEmpty()) {
                redirectAttributes.addFlashAttribute("omitidasImportacionLaminas", omitidasDetalle);
            }
        } catch (Exception e) {
            log.error("Error importando laminas", e);
            redirectAttributes.addFlashAttribute("error", e.getMessage() != null ? e.getMessage() : "No se pudo importar el archivo.");
        }

        return "redirect:/laminas/importar";
    }

    @GetMapping("/plantilla-excel")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_VER')")
    public ResponseEntity<byte[]> descargarPlantillaExcel() {
        try {
            byte[] archivo = laminaExcelService.generarPlantilla();
            String fecha = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "plantilla_laminas_" + fecha + ".xlsx");
            return ResponseEntity.ok().headers(headers).body(archivo);
        } catch (Exception e) {
            log.error("Error generando plantilla de laminas", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/categorias")
    public String listarCategorias(Model model) {
        model.addAttribute("categorias", laminaService.listarCategorias());
        return "laminas/categorias";
    }

    @PostMapping("/categorias/guardar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    public String guardarCategoria(@RequestParam("nombre") String nombre, RedirectAttributes redirectAttributes) {
        try {
            laminaService.guardarCategoria(nombre);
            redirectAttributes.addFlashAttribute("success", "Categoria de lamina guardada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/laminas/categorias";
    }

    @PostMapping("/categorias/toggle/{id}")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_EDITAR')")
    public String toggleCategoria(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            laminaService.alternarCategoria(id);
            redirectAttributes.addFlashAttribute("success", "Estado de la categoria actualizado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/laminas/categorias";
    }

    @PostMapping("/categorias/api/guardar")
    @PreAuthorize("hasPermission(null, 'INVENTARIO_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> guardarCategoriaApi(@RequestBody Map<String, String> payload) {
        try {
            LaminaCategoria categoria = laminaService.guardarCategoria(payload.get("nombre"));
            return ResponseEntity.ok(Map.of(
                    "id", categoria.getId(),
                    "nombre", categoria.getNombre(),
                    "activo", categoria.isActivo()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private Map<String, Object> construirRespuestaLamina(Producto producto) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", producto.getId());
        out.put("text", construirTextoLamina(producto));
        out.put("precio", producto.getPrecioVenta());
        out.put("stock", producto.getStockActual());
        out.put("stockMinimo", producto.getStockMinimo());
        out.put("nombre", producto.getNombre());
        out.put("marca", producto.getLaminaMarca() != null ? producto.getLaminaMarca() : producto.getMarca());
        out.put("categoria", producto.getCategoria());
        out.put("laminaCategoria", producto.getLaminaCategoria());
        out.put("descripcion", producto.getDescripcion());
        out.put("imagen", producto.getImagen());
        out.put("codigoBarra", producto.getCodigoBarra());
        out.put("codigoInterno", producto.getCodigoInterno());
        out.put("esLamina", true);
        out.put("laminaNumero", producto.getLaminaNumero());
        out.put("laminaTitulo", producto.getLaminaTitulo());
        out.put("laminaMarca", producto.getLaminaMarca());
        out.put("laminaProveedorRef", producto.getLaminaProveedorRef());
        out.put("laminaZona", producto.getLaminaZona());
        out.put("laminaContenedor", producto.getLaminaContenedor());
        out.put("laminaPosicion", producto.getLaminaPosicion());
        out.put("ubicacionTexto", producto.getLaminaUbicacionTexto());
        out.put("ubicacion", producto.getLaminaUbicacionTexto());
        out.put("ubicacionEstante", producto.getLaminaCategoria());
        out.put("ubicacionFila", producto.getLaminaContenedor());
        out.put("ubicacionColumna", producto.getLaminaPosicion());
        out.put("tieneStock", producto.getStockActual() != null && producto.getStockActual() > 0);
        out.put("stockBajo", false);
        return out;
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
        if (producto.getLaminaCategoria() != null && !producto.getLaminaCategoria().isBlank()) {
            texto.append(" {").append(producto.getLaminaCategoria()).append("}");
        }
        texto.append(" (Stock: ").append(producto.getStockActual() != null ? producto.getStockActual() : 0).append(")");
        return texto.toString();
    }
}
