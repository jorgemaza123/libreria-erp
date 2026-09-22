package com.libreria.sistema.controller;

import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ProveedorRepository;
import com.libreria.sistema.service.CompraService;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.CosteoSugerenciaService;
import com.libreria.sistema.service.ProductoExcelService;
import com.libreria.sistema.service.ReposicionPendienteService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping("/compras")
@Slf4j
@PreAuthorize("hasPermission(null, 'COMPRAS_VER')")
public class CompraController {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CompraService compraService;
    private final ProductoExcelService productoExcelService;
    private final ConfiguracionService configuracionService;
    private final CosteoSugerenciaService costeoSugerenciaService;
    private final ReposicionPendienteService reposicionPendienteService;

    public CompraController(
            CompraRepository compraRepository,
            ProveedorRepository proveedorRepository,
            ProductoRepository productoRepository,
            DetalleVentaRepository detalleVentaRepository,
            CompraService compraService,
            ProductoExcelService productoExcelService,
            ConfiguracionService configuracionService,
            CosteoSugerenciaService costeoSugerenciaService,
            ReposicionPendienteService reposicionPendienteService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.compraService = compraService;
        this.productoExcelService = productoExcelService;
        this.configuracionService = configuracionService;
        this.costeoSugerenciaService = costeoSugerenciaService;
        this.reposicionPendienteService = reposicionPendienteService;
    }

    @GetMapping("/lista")
    public String lista(Model model) {
        model.addAttribute("compras", compraRepository.findAll(Sort.by(Sort.Direction.DESC, "fecha")));
        return "compras/lista";
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    public String nueva(@RequestParam(required = false) List<Long> reposicionIds, Model model) {
        List<Proveedor> proveedores = proveedorRepository.findByActivoTrue().stream()
                .sorted(Comparator.comparing(Proveedor::getRazonSocial, String.CASE_INSENSITIVE_ORDER))
                .toList();
        List<Producto> productos = productoRepository.findCatalogoGeneralOrdenado();
        model.addAttribute("proveedores", proveedores);
        model.addAttribute("productos", productos);
        model.addAttribute("configCosteo", configuracionService.obtenerConfiguracion());
        model.addAttribute("factorIndirectoSugerido", costeoSugerenciaService.obtenerFactorSugeridoGlobal());
        model.addAttribute("reposicionPrefill", reposicionPendienteService.prepararItemsParaCompra(reposicionIds));
        return "compras/formulario";
    }

    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    public ResponseEntity<?> guardarCompra(@RequestBody CompraDTO dto) {
        try {
            CompraService.ResultadoCompra resultado = compraService.guardarCompra(dto);
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Compra registrada exitosamente");
            response.put("alertasPrecios", resultado.getAlertasPrecios());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error al guardar compra", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/productos-catalogo")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> listarProductosCatalogo() {
        List<Map<String, Object>> productos = productoRepository.findCatalogoGeneralOrdenado()
                .stream()
                .map(this::mapearProductoCatalogo)
                .toList();
        return ResponseEntity.ok(productos);
    }

    @PostMapping("/api/crear-proveedor")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR') or hasPermission(null, 'PROVEEDORES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> crearProveedorRapido(@RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.ok(compraService.crearProveedorRapido(datos));
        } catch (Exception e) {
            log.error("Error al crear proveedor rapido", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/proveedor/{proveedorId}/compras-recientes")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> listarComprasRecientesProveedor(@PathVariable Long proveedorId) {
        if (!proveedorRepository.existsById(proveedorId)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Proveedor no encontrado"));
        }

        List<Map<String, Object>> compras = compraRepository.findRecientesParaClonar(proveedorId, PageRequest.of(0, 8))
                .stream()
                .map(compra -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", compra.getId());
                    row.put("fecha", compra.getFecha() != null ? compra.getFecha().toString() : "");
                    row.put("tipoComprobante", compra.getTipoComprobante());
                    row.put("numeroComprobante", compra.getNumeroComprobante());
                    row.put("total", compra.getTotal() != null ? compra.getTotal() : BigDecimal.ZERO);
                    row.put("items", compra.getDetalles() != null ? compra.getDetalles().size() : 0);
                    return row;
                })
                .toList();

        return ResponseEntity.ok(compras);
    }

    @GetMapping("/api/clonar-compra/{id}")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> obtenerCompraParaClonar(@PathVariable Long id) {
        Optional<Compra> opt = compraRepository.findConDetallesById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Compra compra = opt.get();
        if ("ANULADA".equalsIgnoreCase(compra.getEstado())) {
            return ResponseEntity.badRequest().body(Map.of("error", "No se puede clonar una compra anulada"));
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("id", compra.getId());
        response.put("proveedorId", compra.getProveedor() != null ? compra.getProveedor().getId() : null);
        response.put("proveedor", compra.getProveedor() != null ? compra.getProveedor().getRazonSocial() : "");
        response.put("tipoComprobante", compra.getTipoComprobante());
        response.put("numeroComprobante", compra.getNumeroComprobante());
        response.put("items", compra.getDetalles().stream()
                .filter(det -> det.getProducto() != null)
                .map(det -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    BigDecimal costoBase = det.getCostoUnitarioBase() != null
                            ? det.getCostoUnitarioBase()
                            : det.getPrecioUnitario();
                    BigDecimal totalBase = costoBase.multiply(BigDecimal.valueOf(det.getCantidad()))
                            .setScale(2, RoundingMode.HALF_UP);
                    item.put("productoId", det.getProducto().getId());
                    item.put("nombre", construirEtiquetaProducto(det.getProducto()));
                    item.put("cantidad", det.getCantidad());
                    item.put("costo", costoBase);
                    item.put("totalPagado", totalBase);
                    item.put("stockActual", det.getProducto().getStockActual() != null ? det.getProducto().getStockActual() : 0);
                    item.put("costoAnterior", det.getProducto().getPrecioCompra() != null ? det.getProducto().getPrecioCompra() : BigDecimal.ZERO);
                    item.put("precioVenta", det.getProducto().getPrecioVenta() != null ? det.getProducto().getPrecioVenta() : BigDecimal.ZERO);
                    item.put("clasificacion", det.getProducto().getClasificacion() != null ? det.getProducto().getClasificacion() : Producto.CLASIFICACION_MERCADERIA);
                    item.put("usarReglaManualPrecio", Boolean.TRUE.equals(det.getProducto().getUsarReglaManualPrecio()));
                    item.put("gananciaObjetivoPct", det.getProducto().getGananciaObjetivoPct());
                    item.put("gananciaMinimaPct", det.getProducto().getGananciaMinimaPct());
                    return item;
                })
                .toList());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/api/importar-productos")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR') or hasPermission(null, 'INVENTARIO_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> importarProductosParaCompra(
            @RequestParam("archivo") MultipartFile archivo,
            @RequestParam(value = "actualizarExistentes", defaultValue = "false") boolean actualizarExistentes) {

        if (archivo.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Seleccione un archivo Excel"));
        }

        String nombreArchivo = archivo.getOriginalFilename();
        if (nombreArchivo == null || (!nombreArchivo.endsWith(".xlsx") && !nombreArchivo.endsWith(".xls"))) {
            return ResponseEntity.badRequest().body(Map.of("error", "El archivo debe ser un Excel (.xlsx o .xls)"));
        }

        try {
            Map<String, Object> resultado = productoExcelService.importarProductos(archivo, actualizarExistentes);
            StringBuilder message = new StringBuilder("Importacion completada: ");
            message.append(resultado.get("creados")).append(" creados");
            if (((int) resultado.get("actualizados")) > 0) {
                message.append(", ").append(resultado.get("actualizados")).append(" actualizados");
            }
            if (((int) resultado.get("omitidos")) > 0) {
                message.append(", ").append(resultado.get("omitidos")).append(" omitidos");
            }
            resultado.put("message", message.toString());
            return ResponseEntity.ok(resultado);
        } catch (Exception e) {
            log.error("Error importando productos desde compras", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/crear-productos-lote")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> crearProductosRapidosLote(@RequestBody List<Map<String, Object>> items) {
        try {
            return ResponseEntity.ok(compraService.crearProductosRapidosLote(items));
        } catch (Exception e) {
            log.error("Error al crear productos faltantes en lote", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/actualizar-precio-venta")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> actualizarPrecioVenta(@RequestBody List<Map<String, Object>> items) {
        try {
            compraService.actualizarPreciosVenta(items);
            return ResponseEntity.ok(Map.of("message", "Precios actualizados correctamente"));
        } catch (Exception e) {
            log.error("Error al actualizar precios: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return compraRepository.findConDetallesById(id).map(compra -> ResponseEntity.ok(Map.of(
                "proveedor", compra.getProveedor() != null ? compra.getProveedor().getRazonSocial() : "-",
                "documento", compra.getTipoComprobante() + " " + compra.getNumeroComprobante(),
                "fecha", compra.getFecha() != null ? compra.getFecha().toString() : "-",
                "total", compra.getTotal(),
                "items", compra.getDetalles().stream().map(d -> Map.of(
                        "producto", d.getProducto().getNombre(),
                        "cantidad", d.getCantidad(),
                        "precio", d.getPrecioUnitario(),
                        "subtotal", d.getSubtotal())).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/api/detalle-completo/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalleCompleto(@PathVariable Long id) {
        Optional<Compra> opt = compraRepository.findConDetallesById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Compra compra = opt.get();
        LocalDate fechaCompra = compra.getFecha() != null
                ? compra.getFecha().toLocalDate()
                : LocalDate.now().minusYears(10);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalInvertido = BigDecimal.ZERO;
        BigDecimal totalRecuperado = BigDecimal.ZERO;

        try {
            List<Long> productoIds = compra.getDetalles().stream()
                    .filter(det -> det.getProducto() != null && det.getProducto().getId() != null)
                    .map(det -> det.getProducto().getId())
                    .distinct()
                    .toList();
            Map<Long, Object[]> resumenVentasPorProducto = new HashMap<>();
            if (!productoIds.isEmpty()) {
                for (Object[] row : detalleVentaRepository.resumenVentasPorProductoDesde(productoIds, fechaCompra)) {
                    resumenVentasPorProducto.put((Long) row[0], row);
                }
            }

            for (DetalleCompra det : compra.getDetalles()) {
                if (det.getProducto() == null) {
                    continue;
                }
                Long prodId = det.getProducto().getId();
                int cantComprada = det.getCantidad();
                BigDecimal costoUnit = det.getPrecioUnitario();
                BigDecimal subtotal = det.getSubtotal();

                Object[] resumenVentas = resumenVentasPorProducto.get(prodId);
                BigDecimal vendidosBD = resumenVentas != null
                        ? toBigDecimal(resumenVentas[1]).setScale(0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                int vendidos = vendidosBD.intValue();

                BigDecimal ingresoGenerado = resumenVentas != null
                        ? toBigDecimal(resumenVentas[2]).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                int stockActual = det.getProducto().getStockActual() != null ? det.getProducto().getStockActual() : 0;
                int pendientes = Math.max(0, Math.min(cantComprada - vendidos, stockActual));
                int vendidosReal = Math.min(vendidos, cantComprada);

                double pctRotacion = cantComprada > 0
                        ? Math.min(100.0, (vendidosReal * 100.0) / cantComprada)
                        : 0.0;

                BigDecimal costoRecuperado = costoUnit.multiply(BigDecimal.valueOf(vendidosReal))
                        .setScale(2, RoundingMode.HALF_UP);

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("productoId", prodId);
                row.put("producto", det.getProducto().getNombre());
                row.put("cantComprada", cantComprada);
                row.put("costoUnit", costoUnit.setScale(4, RoundingMode.HALF_UP));
                row.put("subtotal", subtotal.setScale(2, RoundingMode.HALF_UP));
                row.put("stockActual", stockActual);
                row.put("vendidos", vendidosReal);
                row.put("pendientes", pendientes);
                row.put("pctRotacion", Math.round(pctRotacion * 10.0) / 10.0);
                row.put("ingresoGenerado", ingresoGenerado);
                row.put("costoRecuperado", costoRecuperado);
                row.put("precioVenta", det.getProducto().getPrecioVenta());
                items.add(row);

                totalInvertido = totalInvertido.add(subtotal);
                totalRecuperado = totalRecuperado.add(ingresoGenerado);
            }

            BigDecimal pendienteRecuperar = totalInvertido.subtract(totalRecuperado);
            boolean recuperado = totalRecuperado.compareTo(totalInvertido) >= 0;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("proveedor", compra.getProveedor() != null ? compra.getProveedor().getRazonSocial() : "-");
            resp.put("documento", compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
            resp.put("fecha", compra.getFecha() != null ? compra.getFecha().toString() : "-");
            resp.put("totalInvertido", totalInvertido.setScale(2, RoundingMode.HALF_UP));
            resp.put("totalRecuperado", totalRecuperado.setScale(2, RoundingMode.HALF_UP));
            resp.put("pendienteRecuperar", pendienteRecuperar.setScale(2, RoundingMode.HALF_UP));
            resp.put("recuperado", recuperado);
            resp.put("items", items);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("Error en trazabilidad compra {}: {}", id, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Error al calcular trazabilidad: " + e.getMessage()));
        }
    }

    @PostMapping("/api/anular/{id}")
    @PreAuthorize("hasPermission(null, 'COMPRAS_ELIMINAR')")
    @ResponseBody
    public ResponseEntity<?> anularCompra(@PathVariable Long id) {
        try {
            compraService.anularCompra(id);
            return ResponseEntity.ok(Map.of("message", "Compra anulada exitosamente"));
        } catch (Exception e) {
            log.error("Error al anular compra: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/crear-producto")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> crearProductoRapido(@RequestBody Map<String, Object> datos) {
        try {
            return ResponseEntity.ok(compraService.crearProductoRapido(datos));
        } catch (Exception e) {
            log.error("Error al crear producto rapido desde compras: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/siguiente-sku")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> obtenerSiguienteSku() {
        try {
            String siguiente = productoRepository.findUltimoSku()
                    .map(ultimo -> {
                        try {
                            int numero = Integer.parseInt(ultimo.replace("SKU-", ""));
                            return String.format("SKU-%05d", numero + 1);
                        } catch (NumberFormatException e) {
                            return "SKU-00001";
                        }
                    })
                    .orElse("SKU-00001");
            return ResponseEntity.ok(Map.of("sku", siguiente));
        } catch (Exception e) {
            log.error("Error al generar siguiente SKU", e);
            return ResponseEntity.ok(Map.of("sku", "SKU-00001"));
        }
    }

    private Map<String, Object> mapearProductoCatalogo(Producto prod) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", prod.getId());
        result.put("nombre", prod.getNombre());
        result.put("codigoBarra", prod.getCodigoBarra() != null ? prod.getCodigoBarra() : "");
        result.put("codigoInterno", prod.getCodigoInterno() != null ? prod.getCodigoInterno() : "");
        result.put("precioCompra", prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO);
        result.put("precioVenta", prod.getPrecioVenta() != null ? prod.getPrecioVenta() : BigDecimal.ZERO);
        result.put("clasificacion", prod.getClasificacion() != null ? prod.getClasificacion() : Producto.CLASIFICACION_MERCADERIA);
        result.put("stockActual", prod.getStockActual() != null ? prod.getStockActual() : 0);
        result.put("usarReglaManualPrecio", Boolean.TRUE.equals(prod.getUsarReglaManualPrecio()));
        result.put("gananciaObjetivoPct", prod.getGananciaObjetivoPct());
        result.put("gananciaMinimaPct", prod.getGananciaMinimaPct());
        result.put("label", construirEtiquetaProducto(prod));
        return result;
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private String construirEtiquetaProducto(Producto prod) {
        if (prod.getCodigoBarra() != null && !prod.getCodigoBarra().isBlank()) {
            return prod.getCodigoBarra() + " - " + prod.getNombre();
        }
        if (prod.getCodigoInterno() != null && !prod.getCodigoInterno().isBlank()) {
            return prod.getCodigoInterno() + " - " + prod.getNombre();
        }
        return prod.getNombre();
    }
}
