package com.libreria.sistema.controller;

import com.libreria.sistema.model.Compra;
import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ProveedorRepository;
import com.libreria.sistema.service.CompraService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/compras")
@Slf4j
@PreAuthorize("hasPermission(null, 'COMPRAS_VER')")
public class CompraController {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final CompraService compraService;

    @PersistenceContext
    private EntityManager em;

    public CompraController(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
            ProductoRepository productoRepository, CompraService compraService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.compraService = compraService;
    }

    @GetMapping("/lista")
    public String lista(Model model) {
        model.addAttribute("compras", compraRepository.findAll());
        return "compras/lista";
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    public String nueva(Model model) {
        model.addAttribute("proveedores", proveedorRepository.findByActivoTrue());
        model.addAttribute("productos", productoRepository.findAll());
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

    /** Detalle simple (existente) */
    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return compraRepository.findById(id).map(compra -> ResponseEntity.ok(Map.of(
                "proveedor", compra.getProveedor() != null ? compra.getProveedor().getRazonSocial() : "—",
                "documento", compra.getTipoComprobante() + " " + compra.getNumeroComprobante(),
                "fecha", compra.getFecha() != null ? compra.getFecha().toString() : "—",
                "total", compra.getTotal(),
                "items", compra.getDetalles().stream().map(d -> Map.of(
                        "producto", d.getProducto().getNombre(),
                        "cantidad", d.getCantidad(),
                        "precio", d.getPrecioUnitario(),
                        "subtotal", d.getSubtotal())).toList())))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * NUEVO — Trazabilidad completa por factura.
     * Para cada producto de la compra, calcula cuánto se vendió DESDE esa fecha
     * usando los registros de DetalleVenta, sin modificar la BD.
     */
    @GetMapping("/api/detalle-completo/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalleCompleto(@PathVariable Long id) {
        Optional<Compra> opt = compraRepository.findById(id);
        if (opt.isEmpty())
            return ResponseEntity.notFound().build();

        Compra compra = opt.get();
        LocalDate fechaCompra = compra.getFecha() != null
                ? compra.getFecha().toLocalDate()
                : LocalDate.now().minusYears(10);

        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal totalInvertido = BigDecimal.ZERO;
        BigDecimal totalRecuperado = BigDecimal.ZERO;

        try {
            for (DetalleCompra det : compra.getDetalles()) {
                if (det.getProducto() == null)
                    continue;
                Long prodId = det.getProducto().getId();
                int cantComprada = det.getCantidad();
                BigDecimal costoUnit = det.getPrecioUnitario();
                BigDecimal subtotal = det.getSubtotal();

                // Ventas del producto desde la fecha de compra (estados válidos)
                // DetalleVenta.cantidad es BigDecimal, así que tratamos el resultado como tal
                Object vendidosObj = em.createQuery(
                        "SELECT SUM(dv.cantidad) FROM DetalleVenta dv " +
                                "WHERE dv.producto.id = :pid " +
                                "AND dv.venta.fechaEmision >= :fecha " +
                                "AND dv.venta.estado IN ('EMITIDO','PAGADO_TOTAL','DEVUELTO_PARCIAL')")
                        .setParameter("pid", prodId)
                        .setParameter("fecha", fechaCompra)
                        .getSingleResult();
                BigDecimal vendidosBD = vendidosObj != null
                        ? new BigDecimal(vendidosObj.toString()).setScale(0, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                int vendidos = vendidosBD.intValue();

                // Ingreso generado por esas ventas
                Object ingresoObj = em.createQuery(
                        "SELECT SUM(dv.subtotal) FROM DetalleVenta dv " +
                                "WHERE dv.producto.id = :pid " +
                                "AND dv.venta.fechaEmision >= :fecha " +
                                "AND dv.venta.estado IN ('EMITIDO','PAGADO_TOTAL','DEVUELTO_PARCIAL')")
                        .setParameter("pid", prodId)
                        .setParameter("fecha", fechaCompra)
                        .getSingleResult();
                BigDecimal ingresoGenerado = ingresoObj != null
                        ? new BigDecimal(ingresoObj.toString()).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                // Pendientes (estimado: mínimo entre comprado y stock actual)
                int stockActual = det.getProducto().getStockActual() != null ? det.getProducto().getStockActual() : 0;
                int pendientes = Math.max(0, Math.min(cantComprada - vendidos, stockActual));
                int vendidosReal = Math.min(vendidos, cantComprada); // no puede superar lo comprado

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
            resp.put("proveedor", compra.getProveedor() != null ? compra.getProveedor().getRazonSocial() : "—");
            resp.put("documento", compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
            resp.put("fecha", compra.getFecha() != null ? compra.getFecha().toString() : "—");
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

    /**
     * Crea un producto nuevo al vuelo desde el formulario de compras.
     * Retorna id, nombre, precioCompra y precioVenta para auto-selección en el
     * carrito.
     */
    @PostMapping("/api/crear-producto")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    @ResponseBody
    public ResponseEntity<?> crearProductoRapido(@RequestBody Map<String, Object> datos) {
        try {
            Map<String, Object> result = compraService.crearProductoRapido(datos);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Error al crear producto rápido desde compras: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Obtiene el siguiente SKU disponible para auto-completar el formulario
     * de creación rápida de producto.
     */
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
            return ResponseEntity.ok(Map.of("sku", "SKU-00001")); // Fallback
        }
    }
}