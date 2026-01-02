package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.ConfiguracionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

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
public class VentaController {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final KardexRepository kardexRepository;
    private final CajaRepository cajaRepository;
    private final CorrelativoRepository correlativoRepository;
    private final ConfiguracionService configuracionService;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    public VentaController(ProductoRepository productoRepository, VentaRepository ventaRepository,
            KardexRepository kardexRepository, CajaRepository cajaRepository,
            CorrelativoRepository correlativoRepository,
            ConfiguracionService configuracionService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.kardexRepository = kardexRepository;
        this.cajaRepository = cajaRepository;
        this.correlativoRepository = correlativoRepository;
        this.configuracionService = configuracionService;
    }

    // VISTA POS: Carga rápida (sin productos)
    @GetMapping("/nueva")
    public String nuevaVenta(Model model) {
        return "ventas/pos";
    }

    // BUSCADOR AJAX (Para Select2)
    @GetMapping("/api/buscar-productos")
    @ResponseBody
    public List<Map<String, Object>> buscarProductos(@RequestParam String term) {
        return productoRepository.buscarInteligente(term).stream().map(p -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", p.getId());
            // Texto que se ve en el desplegable
            map.put("text", p.getCodigoBarra() + " - " + p.getNombre() + " (Stock: " + p.getStockActual() + ")");

            // Datos extra para el JS
            map.put("precio", p.getPrecioVenta());
            map.put("precioMin", p.getPrecioVenta().multiply(new BigDecimal("0.90"))); // Margen 10%
            map.put("stock", p.getStockActual());
            map.put("nombre", p.getNombre());
            map.put("marca", p.getMarca());
            map.put("imagen", p.getImagen());
            map.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-"
                    + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return map;
        }).collect(Collectors.toList());
    }

    // OBTENER UN SOLO PRODUCTO (Para el visor lateral)
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
            data.put("ubicacion", (p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "") + "-"
                    + (p.getUbicacionFila() != null ? p.getUbicacionFila() : ""));
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    // GUARDAR VENTA (Transaccional)
    @PostMapping("/api/guardar")
    public ResponseEntity<?> guardarVenta(@RequestBody VentaDTO dto) {
        try {
            Venta venta = new Venta();
            venta.setClienteDenominacion(dto.getClienteNombre());
            venta.setClienteNumeroDocumento(dto.getClienteDocumento());

            String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "NOTA_VENTA";
            String serie = tipo.equals("FACTURA") ? "F001" : (tipo.equals("BOLETA") ? "B001" : "N001");

            Correlativo correlativo = correlativoRepository.findByCodigoAndSerie(tipo, serie)
                    .orElse(new Correlativo(tipo, serie, 0));

            Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
            correlativo.setUltimoNumero(nuevoNumero);
            correlativoRepository.save(correlativo);

            venta.setTipoComprobante(tipo);
            venta.setSerie(serie);
            venta.setNumero(nuevoNumero);
            venta.setFechaEmision(LocalDate.now());
            venta.setEstado("EMITIDO");

            BigDecimal totalVenta = BigDecimal.ZERO;
            BigDecimal totalGravada = BigDecimal.ZERO;
            BigDecimal totalIgv = BigDecimal.ZERO;

            for (VentaDTO.DetalleDTO item : dto.getItems()) {
                Producto prod = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

                if (prod.getStockActual() < item.getCantidad().intValue()) {
                    return ResponseEntity.badRequest().body("Stock insuficiente: " + prod.getNombre());
                }

                BigDecimal precioFinal = item.getPrecioVenta();
                BigDecimal cantidad = item.getCantidad();
                BigDecimal subtotalItem = precioFinal.multiply(cantidad);
                BigDecimal valorUnitario = precioFinal.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
                BigDecimal valorVenta = valorUnitario.multiply(cantidad);
                BigDecimal igvItem = subtotalItem.subtract(valorVenta);

                DetalleVenta det = new DetalleVenta();
                det.setVenta(venta);
                det.setProducto(prod);
                det.setCantidad(cantidad);
                det.setDescripcion(prod.getNombre());
                det.setPrecioUnitario(precioFinal);
                det.setValorUnitario(valorUnitario);
                det.setSubtotal(subtotalItem);

                venta.getItems().add(det);

                totalVenta = totalVenta.add(subtotalItem);
                totalGravada = totalGravada.add(valorVenta);
                totalIgv = totalIgv.add(igvItem);

                // Kardex y Stock
                Kardex k = new Kardex();
                k.setProducto(prod);
                k.setTipo("SALIDA");
                k.setMotivo("VENTA " + venta.getSerie() + "-" + venta.getNumero());
                k.setCantidad(cantidad.intValue());
                k.setStockAnterior(prod.getStockActual());
                k.setStockActual(prod.getStockActual() - cantidad.intValue());
                kardexRepository.save(k);

                prod.setStockActual(prod.getStockActual() - cantidad.intValue());
                productoRepository.save(prod);
            }

            venta.setTotal(totalVenta);
            venta.setTotalGravada(totalGravada);
            venta.setTotalIgv(totalIgv);

            Venta guardada = ventaRepository.save(venta);

            // Caja
            MovimientoCaja caja = new MovimientoCaja();
            caja.setFecha(LocalDateTime.now());
            caja.setTipo("INGRESO");
            caja.setConcepto("VENTA " + guardada.getSerie() + "-" + guardada.getNumero());
            caja.setMonto(totalVenta);
            cajaRepository.save(caja);

            return ResponseEntity.ok(Map.of("id", guardada.getId()));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // IMPRESIÓN
    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null)
            return "redirect:/ventas/lista";

        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/impresion";
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

    // NUEVO ENDPOINT TICKET
    @GetMapping("/ticket/{id}")
    public String ticket(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if (venta == null)
            return "redirect:/ventas/lista";

        model.addAttribute("venta", venta);
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "ventas/ticket";
    }
}