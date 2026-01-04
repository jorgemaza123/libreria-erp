package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.ConfiguracionService;
import com.libreria.sistema.service.VentaService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final ConfiguracionService configuracionService;
    private final VentaService ventaService;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    public VentaController(ProductoRepository productoRepository,
                           VentaRepository ventaRepository,
                           ConfiguracionService configuracionService,
                           VentaService ventaService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.configuracionService = configuracionService;
        this.ventaService = ventaService;
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
            map.put("precioMin", p.getPrecioVenta().multiply(new BigDecimal("0.90")));
            map.put("stock", p.getStockActual());
            map.put("nombre", p.getNombre());
            map.put("marca", p.getMarca());
            map.put("imagen", p.getImagen());
            map.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-" + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Guarda una nueva venta usando VentaService (con soporte DUAL-MODE)
     * - Si facturaElectronicaActiva = false: usa series internas (I001/IF001)
     * - Si facturaElectronicaActiva = true: usa series oficiales (B001/F001) y envía a SUNAT
     */
    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'VENTAS_CREAR')")
    public ResponseEntity<?> guardarVenta(@RequestBody VentaDTO dto) {
        try {
            // Delegar toda la lógica al VentaService
            Map<String, Object> resultado = ventaService.crearVenta(dto);
            return ResponseEntity.ok(resultado);

        } catch (OptimisticLockingFailureException e) {
            // ERROR DE CONCURRENCIA EN STOCK
            return ResponseEntity.badRequest()
                .body("ERROR DE STOCK: Otro vendedor actualizó el producto mientras procesaba la venta. Intente nuevamente.");

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest()
                .body("Error: " + e.getMessage());
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
            data.put("precioMinimo", p.getPrecioVenta().multiply(new BigDecimal("0.90")));
            data.put("stock", p.getStockActual());
            data.put("imagen", p.getImagen());
            data.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-" + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null) return "redirect:/ventas/lista";
        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/impresion";
    }

    @GetMapping("/ticket/{id}")
    public String ticket(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null) return "redirect:/ventas/lista";
        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/ticket";
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
}