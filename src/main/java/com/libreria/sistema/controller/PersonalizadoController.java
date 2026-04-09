package com.libreria.sistema.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.libreria.sistema.model.AdicionalPersonalizado;
import com.libreria.sistema.model.CategoriaAdicionalPersonalizado;
import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.InsumoPersonalizado;
import com.libreria.sistema.model.PedidoPersonalizado;
import com.libreria.sistema.model.PlantillaPersonalizada;
import com.libreria.sistema.model.PresentacionCompra;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.model.ZonaEntregaPersonalizado;
import com.libreria.sistema.model.dto.CompraPersonalizadaDTO;
import com.libreria.sistema.model.dto.InsumoPersonalizadoFormDTO;
import com.libreria.sistema.model.dto.PedidoPersonalizadoDTO;
import com.libreria.sistema.model.dto.PlantillaPersonalizadaFormDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.InsumoPersonalizadoRepository;
import com.libreria.sistema.repository.PedidoPersonalizadoRepository;
import com.libreria.sistema.repository.PlantillaPersonalizadaRepository;
import com.libreria.sistema.repository.PresentacionCompraRepository;
import com.libreria.sistema.repository.ProveedorRepository;
import com.libreria.sistema.repository.ZonaEntregaPersonalizadoRepository;
import com.libreria.sistema.service.PersonalizadoCatalogoService;
import com.libreria.sistema.service.PersonalizadoPdfService;
import com.libreria.sistema.service.PersonalizadoPedidoService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/personalizado")
@PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
@Slf4j
public class PersonalizadoController {

    private final PersonalizadoCatalogoService catalogoService;
    private final PersonalizadoPedidoService pedidoService;
    private final PersonalizadoPdfService pdfService;
    private final ProveedorRepository proveedorRepository;
    private final CompraRepository compraRepository;
    private final PresentacionCompraRepository presentacionRepository;
    private final PlantillaPersonalizadaRepository plantillaRepository;
    private final InsumoPersonalizadoRepository insumoRepository;
    private final PedidoPersonalizadoRepository pedidoRepository;
    private final ZonaEntregaPersonalizadoRepository zonaRepository;
    private final ObjectMapper objectMapper;

    public PersonalizadoController(PersonalizadoCatalogoService catalogoService,
                                   PersonalizadoPedidoService pedidoService,
                                   PersonalizadoPdfService pdfService,
                                   ProveedorRepository proveedorRepository,
                                   CompraRepository compraRepository,
                                   PresentacionCompraRepository presentacionRepository,
                                   PlantillaPersonalizadaRepository plantillaRepository,
                                   InsumoPersonalizadoRepository insumoRepository,
                                   PedidoPersonalizadoRepository pedidoRepository,
                                   ZonaEntregaPersonalizadoRepository zonaRepository,
                                   ObjectMapper objectMapper) {
        this.catalogoService = catalogoService;
        this.pedidoService = pedidoService;
        this.pdfService = pdfService;
        this.proveedorRepository = proveedorRepository;
        this.compraRepository = compraRepository;
        this.presentacionRepository = presentacionRepository;
        this.plantillaRepository = plantillaRepository;
        this.insumoRepository = insumoRepository;
        this.pedidoRepository = pedidoRepository;
        this.zonaRepository = zonaRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("resumen", catalogoService.obtenerResumen());
        model.addAttribute("pedidosRecientes", pedidoRepository.findAllByOrderByFechaCreacionDesc().stream().limit(8).toList());
        model.addAttribute("plantillasActivas", plantillaRepository.findByActivoTrueOrderByNombreComercialAsc().stream().limit(8).toList());
        return "personalizado/index";
    }

    @GetMapping("/insumos")
    public String listarInsumos(Model model) {
        model.addAttribute("insumos", catalogoService.listarInsumos());
        return "personalizado/insumos/lista";
    }

    @GetMapping("/insumos/nuevo")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR')")
    public String nuevoInsumo(Model model) {
        cargarFormularioInsumo(model, null);
        return "personalizado/insumos/formulario";
    }

    @GetMapping("/insumos/editar/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String editarInsumo(@PathVariable Long id, Model model) {
        cargarFormularioInsumo(model, id);
        return "personalizado/insumos/formulario";
    }

    @GetMapping("/insumos/detalle/{id}")
    public String detalleInsumo(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        InsumoPersonalizado insumo = catalogoService.obtenerInsumoDetalle(id).orElse(null);
        if (insumo == null) {
            redirectAttributes.addFlashAttribute("error", "Insumo personalizado no encontrado.");
            return "redirect:/personalizado/insumos";
        }
        model.addAttribute("insumo", insumo);
        model.addAttribute("presentaciones", catalogoService.mapearPresentaciones(id));
        model.addAttribute("comprasRelacionadas", listarComprasPersonalizadas().stream()
                .filter(compra -> compra.getDetalles().stream().anyMatch(det ->
                        det.getProducto() != null
                                && insumo.getProducto() != null
                                && det.getProducto().getId().equals(insumo.getProducto().getId())))
                .limit(8)
                .toList());
        return "personalizado/insumos/detalle";
    }

    @PostMapping("/insumos/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR') or hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String guardarInsumo(@ModelAttribute InsumoPersonalizadoFormDTO dto,
                                @RequestParam(value = "fotoFile", required = false) MultipartFile fotoFile,
                                RedirectAttributes redirectAttributes) {
        try {
            InsumoPersonalizado insumo = catalogoService.guardarInsumo(dto, fotoFile);
            redirectAttributes.addFlashAttribute("success", "Insumo personalizado guardado correctamente.");
            return "redirect:/personalizado/insumos/detalle/" + insumo.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return dto.getId() != null
                    ? "redirect:/personalizado/insumos/editar/" + dto.getId()
                    : "redirect:/personalizado/insumos/nuevo";
        }
    }

    @PostMapping("/insumos/toggle/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String alternarInsumo(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogoService.alternarEstadoInsumo(id);
            redirectAttributes.addFlashAttribute("success", "Estado del insumo actualizado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personalizado/insumos";
    }

    @GetMapping("/compras")
    public String listarComprasPersonalizadas(Model model) {
        model.addAttribute("compras", listarComprasPersonalizadas());
        return "personalizado/compras/lista";
    }

    @GetMapping("/compras/nueva")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR')")
    public String nuevaCompraPersonalizada(Model model) {
        List<Proveedor> proveedores = proveedorRepository.findByActivoTrue().stream()
                .sorted((a, b) -> a.getRazonSocial().compareToIgnoreCase(b.getRazonSocial()))
                .toList();
        model.addAttribute("proveedores", proveedores);
        model.addAttribute("presentacionesGlobales", presentacionRepository.findGlobalesActivas(PresentacionCompra.TIPO_INSUMO_PERSONALIZADO));
        model.addAttribute("hoy", LocalDate.now());
        return "personalizado/compras/formulario";
    }

    @PostMapping("/compras/api/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR')")
    @ResponseBody
    public ResponseEntity<?> guardarCompraPersonalizada(@RequestBody CompraPersonalizadaDTO dto) {
        try {
            Compra compra = catalogoService.registrarCompraPersonalizada(dto);
            return ResponseEntity.ok(Map.of(
                    "id", compra.getId(),
                    "message", "Compra personalizada registrada correctamente."
            ));
        } catch (Exception e) {
            log.error("Error guardando compra personalizada", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/plantillas")
    public String listarPlantillas(Model model) {
        model.addAttribute("plantillas", catalogoService.listarPlantillas());
        return "personalizado/plantillas/lista";
    }

    @GetMapping("/plantillas/nueva")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR')")
    public String nuevaPlantilla(Model model) {
        cargarFormularioPlantilla(model, null);
        return "personalizado/plantillas/formulario";
    }

    @GetMapping("/plantillas/editar/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String editarPlantilla(@PathVariable Long id, Model model) {
        cargarFormularioPlantilla(model, id);
        return "personalizado/plantillas/formulario";
    }

    @GetMapping("/plantillas/detalle/{id}")
    public String detallePlantilla(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        PlantillaPersonalizada plantilla = catalogoService.obtenerPlantillaDetalle(id).orElse(null);
        if (plantilla == null) {
            redirectAttributes.addFlashAttribute("error", "Plantilla no encontrada.");
            return "redirect:/personalizado/plantillas";
        }
        model.addAttribute("plantilla", plantilla);
        model.addAttribute("pedidosRelacionados", pedidoRepository.findAllByOrderByFechaCreacionDesc().stream()
                .filter(pedido -> pedido.getItems().stream().anyMatch(item ->
                        item.getPlantilla() != null && item.getPlantilla().getId().equals(id)))
                .limit(8)
                .toList());
        return "personalizado/plantillas/detalle";
    }

    @PostMapping("/plantillas/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR') or hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String guardarPlantilla(@ModelAttribute PlantillaPersonalizadaFormDTO dto,
                                   @RequestParam(value = "fotoFile", required = false) MultipartFile fotoFile,
                                   RedirectAttributes redirectAttributes) {
        try {
            PlantillaPersonalizada plantilla = catalogoService.guardarPlantilla(dto, fotoFile);
            redirectAttributes.addFlashAttribute("success", "Plantilla guardada correctamente.");
            return "redirect:/personalizado/plantillas/detalle/" + plantilla.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return dto.getId() != null
                    ? "redirect:/personalizado/plantillas/editar/" + dto.getId()
                    : "redirect:/personalizado/plantillas/nueva";
        }
    }

    @PostMapping("/plantillas/toggle/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String alternarPlantilla(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            catalogoService.alternarEstadoPlantilla(id);
            redirectAttributes.addFlashAttribute("success", "Estado de la plantilla actualizado.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personalizado/plantillas";
    }

    @PostMapping("/plantillas/duplicar/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String duplicarPlantilla(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            PlantillaPersonalizada copia = catalogoService.duplicarPlantilla(id);
            redirectAttributes.addFlashAttribute("success", "Plantilla duplicada correctamente.");
            return "redirect:/personalizado/plantillas/editar/" + copia.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/personalizado/plantillas";
        }
    }

    @GetMapping("/adicionales")
    public String adicionales(Model model) {
        model.addAttribute("categorias", catalogoService.listarCategoriasAdicionales());
        model.addAttribute("adicionales", catalogoService.listarAdicionales());
        model.addAttribute("insumosActivos", insumoRepository.findActivos());
        return "personalizado/adicionales/index";
    }

    @PostMapping("/adicionales/categorias/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CONFIGURAR')")
    public String guardarCategoria(@RequestParam(required = false) Long id,
                                   @RequestParam String codigo,
                                   @RequestParam String nombre,
                                   @RequestParam(required = false) String descripcion,
                                   @RequestParam(required = false) Integer orden,
                                   @RequestParam(defaultValue = "true") Boolean activo,
                                   RedirectAttributes redirectAttributes) {
        try {
            catalogoService.guardarCategoria(id, codigo, nombre, descripcion, orden, activo);
            redirectAttributes.addFlashAttribute("success", "Categoría guardada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personalizado/adicionales";
    }

    @PostMapping("/adicionales/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CONFIGURAR')")
    public String guardarAdicional(@RequestParam(required = false) Long id,
                                   @RequestParam String codigo,
                                   @RequestParam String nombre,
                                   @RequestParam(required = false) Long categoriaId,
                                   @RequestParam String tipoOrigen,
                                   @RequestParam(required = false) Long insumoId,
                                   @RequestParam(required = false) BigDecimal costoManual,
                                   @RequestParam(required = false) BigDecimal precioBase,
                                   @RequestParam(defaultValue = "true") Boolean editablePrecio,
                                   @RequestParam(defaultValue = "true") Boolean editableCantidad,
                                   @RequestParam(defaultValue = "true") Boolean activo,
                                   @RequestParam(required = false) String descripcion,
                                   RedirectAttributes redirectAttributes) {
        try {
            catalogoService.guardarAdicional(id, codigo, nombre, categoriaId, tipoOrigen, insumoId,
                    costoManual, precioBase, editablePrecio, editableCantidad, activo, descripcion);
            redirectAttributes.addFlashAttribute("success", "Adicional guardado correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personalizado/adicionales";
    }

    @GetMapping("/configuracion")
    public String configuracion(Model model) {
        model.addAttribute("zonas", catalogoService.listarZonas());
        model.addAttribute("presentacionesGlobales", presentacionRepository.findGlobalesActivas(PresentacionCompra.TIPO_INSUMO_PERSONALIZADO));
        return "personalizado/configuracion/index";
    }

    @PostMapping("/configuracion/zonas/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CONFIGURAR')")
    public String guardarZona(@RequestParam(required = false) Long id,
                              @RequestParam String departamento,
                              @RequestParam String provincia,
                              @RequestParam String distrito,
                              @RequestParam(required = false) BigDecimal tarifaBase,
                              @RequestParam(required = false) Integer plazoEstimadoDias,
                              @RequestParam(defaultValue = "true") Boolean activo,
                              RedirectAttributes redirectAttributes) {
        try {
            catalogoService.guardarZona(id, departamento, provincia, distrito, tarifaBase, plazoEstimadoDias, activo);
            redirectAttributes.addFlashAttribute("success", "Zona de entrega guardada correctamente.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/personalizado/configuracion";
    }

    @GetMapping("/pedidos")
    public String listarPedidos(@RequestParam(defaultValue = "") String termino,
                                @RequestParam(defaultValue = "") String estado,
                                Model model) {
        model.addAttribute("pedidos", pedidoService.listarPedidos(termino, estado));
        model.addAttribute("termino", termino);
        model.addAttribute("estado", estado);
        return "personalizado/pedidos/lista";
    }

    @GetMapping("/pedidos/nuevo")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR')")
    public String nuevoPedido(Model model) {
        cargarFormularioPedido(model, null);
        return "personalizado/pedidos/formulario";
    }

    @GetMapping("/pedidos/editar/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_EDITAR')")
    public String editarPedido(@PathVariable Long id, Model model) {
        cargarFormularioPedido(model, id);
        return "personalizado/pedidos/formulario";
    }

    @GetMapping("/pedidos/detalle/{id}")
    public String detallePedido(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        PedidoPersonalizado pedido = pedidoService.obtenerDetalle(id).orElse(null);
        if (pedido == null) {
            redirectAttributes.addFlashAttribute("error", "Pedido no encontrado.");
            return "redirect:/personalizado/pedidos";
        }
        model.addAttribute("pedido", pedido);
        model.addAttribute("whatsappUrl", pedidoService.construirWhatsappUrl(id));
        return "personalizado/pedidos/detalle";
    }

    @PostMapping("/pedidos/api/guardar")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR') or hasPermission(null, 'PERSONALIZADO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> guardarPedido(@RequestBody PedidoPersonalizadoDTO dto) {
        try {
            PedidoPersonalizado pedido = pedidoService.guardar(dto);
            return ResponseEntity.ok(Map.of(
                    "id", pedido.getId(),
                    "codigoPedido", pedido.getCodigoPedido(),
                    "estado", pedido.getEstado(),
                    "message", "Pedido guardado correctamente."
            ));
        } catch (Exception e) {
            log.error("Error guardando pedido personalizado", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/calcular-pedido")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_CREAR') or hasPermission(null, 'PERSONALIZADO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> calcularPedido(@RequestBody PedidoPersonalizadoDTO dto) {
        try {
            return ResponseEntity.ok(pedidoService.calcular(dto));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/cerrar-venta/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VENDER')")
    @ResponseBody
    public ResponseEntity<?> cerrarVenta(@PathVariable Long id,
                                         @RequestParam(required = false) String tipoComprobante,
                                         @RequestParam(required = false) String formaPago,
                                         @RequestParam(required = false) String metodoPago) {
        try {
            return ResponseEntity.ok(pedidoService.cerrarVenta(id, tipoComprobante, formaPago, metodoPago));
        } catch (Exception e) {
            log.error("Error cerrando pedido personalizado {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/whatsapp/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
    @ResponseBody
    public ResponseEntity<?> whatsappPedido(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(Map.of("url", pedidoService.construirWhatsappUrl(id)));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/pdf/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
    public void pdfPedido(@PathVariable Long id, HttpServletResponse response) throws IOException {
        PedidoPersonalizado pedido = pedidoService.obtenerDetalle(id).orElse(null);
        if (pedido == null) {
            response.sendError(404, "Pedido no encontrado");
            return;
        }
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=\"Pedido_" + pedido.getCodigoPedido() + ".pdf\"");
            pdfService.generarPedidoPdf(pedido, response.getOutputStream());
        } catch (Exception e) {
            log.error("Error generando PDF del pedido {}", id, e);
            response.sendError(500, "No se pudo generar el PDF.");
        }
    }

    @GetMapping("/api/buscar-insumos")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
    @ResponseBody
    public ResponseEntity<?> buscarInsumos(@RequestParam(defaultValue = "") String term) {
        return ResponseEntity.ok(catalogoService.buscarInsumos(term));
    }

    @GetMapping("/api/buscar-plantillas")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
    @ResponseBody
    public ResponseEntity<?> buscarPlantillas(@RequestParam(defaultValue = "") String term) {
        return ResponseEntity.ok(catalogoService.buscarPlantillas(term));
    }

    @GetMapping("/api/plantillas/{id}")
    @PreAuthorize("hasPermission(null, 'PERSONALIZADO_VER')")
    @ResponseBody
    public ResponseEntity<?> obtenerPlantillaJson(@PathVariable Long id) {
        PlantillaPersonalizadaFormDTO dto = catalogoService.construirFormularioPlantilla(id);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", dto.getId());
        out.put("codigoModelo", dto.getCodigoModelo());
        out.put("nombreComercial", dto.getNombreComercial());
        out.put("categoria", dto.getCategoria());
        out.put("fotoPrincipal", dto.getFotoActual());
        out.put("descripcionComercial", dto.getDescripcionComercial());
        out.put("margenMinimoPct", dto.getMargenMinimoPct());
        out.put("margenObjetivoPct", dto.getMargenObjetivoPct());
        out.put("componentesJson", dto.getComponentesJson());
        out.put("rangosJson", dto.getRangosJson());
        return ResponseEntity.ok(out);
    }

    private void cargarFormularioInsumo(Model model, Long id) {
        model.addAttribute("dto", catalogoService.construirFormularioInsumo(id));
    }

    private void cargarFormularioPlantilla(Model model, Long id) {
        model.addAttribute("dto", catalogoService.construirFormularioPlantilla(id));
        model.addAttribute("insumosActivos", insumoRepository.findActivos());
        model.addAttribute("adicionalesActivos", catalogoService.listarAdicionales().stream()
                .filter(adicional -> Boolean.TRUE.equals(adicional.getActivo()))
                .toList());
        model.addAttribute("categoriasAdicionales", catalogoService.listarCategoriasAdicionales());
    }

    private void cargarFormularioPedido(Model model, Long id) {
        PedidoPersonalizadoDTO dto = pedidoService.construirDto(id);
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            dto.setItems(List.of(new PedidoPersonalizadoDTO.ItemDTO()));
        }
        model.addAttribute("dto", dto);
        model.addAttribute("pedidoJson", serializar(dto));
        model.addAttribute("plantillasActivas", plantillaRepository.findByActivoTrueOrderByNombreComercialAsc());
        model.addAttribute("insumosActivos", insumoRepository.findActivos());
        model.addAttribute("adicionalesActivos", catalogoService.listarAdicionales().stream()
                .filter(adicional -> Boolean.TRUE.equals(adicional.getActivo()))
                .toList());
        model.addAttribute("zonasActivas", zonaRepository.findByActivoTrueOrderByDepartamentoAscProvinciaAscDistritoAsc());
        model.addAttribute("esEdicion", id != null);
    }

    private List<Compra> listarComprasPersonalizadas() {
        return compraRepository.findAll(Sort.by(Sort.Direction.DESC, "fecha")).stream()
                .filter(compra -> compra.getDetalles() != null && compra.getDetalles().stream().anyMatch(det ->
                        det.getProducto() != null
                                && Producto.ORIGEN_CATALOGO_PERSONALIZADO.equalsIgnoreCase(det.getProducto().getOrigenCatalogo())))
                .toList();
    }

    private String serializar(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("No se pudo serializar payload del módulo Personalizado: {}", e.getMessage());
            return "{}";
        }
    }
}
