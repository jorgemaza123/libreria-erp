package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.CajaService;
import com.libreria.sistema.service.ConfiguracionService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException; // IMPORTANTE
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

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
    private final CorrelativoRepository correlativoRepository;
    private final ConfiguracionService configuracionService;
    
    private final ClienteRepository clienteRepository;
    private final AmortizacionRepository amortizacionRepository;
    private final CajaService cajaService;

    @Autowired
    private SolicitudProductoRepository solicitudRepository;

    public VentaController(ProductoRepository productoRepository, VentaRepository ventaRepository,
            KardexRepository kardexRepository, CorrelativoRepository correlativoRepository,
            ConfiguracionService configuracionService,
            ClienteRepository clienteRepository,
            AmortizacionRepository amortizacionRepository,
            CajaService cajaService) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.kardexRepository = kardexRepository;
        this.correlativoRepository = correlativoRepository;
        this.configuracionService = configuracionService;
        this.clienteRepository = clienteRepository;
        this.amortizacionRepository = amortizacionRepository;
        this.cajaService = cajaService;
    }

    @GetMapping("/nueva")
    public String nuevaVenta() {
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

    @PostMapping("/api/guardar")
    public ResponseEntity<?> guardarVenta(@RequestBody VentaDTO dto) {
        try {
            // --- INICIO TRANSACCIÓN ---
            
            // 1. CLIENTE
            Cliente cliente = clienteRepository.findByNumeroDocumento(dto.getClienteDocumento())
                .orElseGet(() -> {
                    Cliente c = new Cliente();
                    c.setNumeroDocumento(dto.getClienteDocumento());
                    c.setNombreRazonSocial(dto.getClienteNombre());
                    c.setDireccion(dto.getClienteDireccion());
                    c.setTelefono(dto.getClienteTelefono());
                    c.setTipoDocumento(dto.getClienteDocumento().length() == 11 ? "6" : "1"); 
                    return clienteRepository.save(c);
                });

            // 2. CABECERA VENTA
            Venta venta = new Venta();
            venta.setClienteEntity(cliente);
            venta.setClienteDenominacion(cliente.getNombreRazonSocial());
            venta.setClienteNumeroDocumento(cliente.getNumeroDocumento());
            venta.setClienteDireccion(cliente.getDireccion());

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

            // 3. DETALLES (CON VALIDACIÓN DE STOCK)
            BigDecimal totalVenta = BigDecimal.ZERO;
            BigDecimal totalGravada = BigDecimal.ZERO;
            BigDecimal totalIgv = BigDecimal.ZERO;

            for (VentaDTO.DetalleDTO item : dto.getItems()) {
                Producto prod = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

                // Validación estricta de Stock
                if (prod.getStockActual() < item.getCantidad().intValue()) {
                    return ResponseEntity.badRequest().body("Stock insuficiente para: " + prod.getNombre());
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

                // Kardex
                Kardex k = new Kardex();
                k.setProducto(prod);
                k.setTipo("SALIDA");
                k.setMotivo("VENTA " + venta.getSerie() + "-" + venta.getNumero());
                k.setCantidad(cantidad.intValue());
                k.setStockAnterior(prod.getStockActual());
                k.setStockActual(prod.getStockActual() - cantidad.intValue());
                kardexRepository.save(k);

                // ACTUALIZAR STOCK (Aquí JPA verificará la @Version)
                prod.setStockActual(prod.getStockActual() - cantidad.intValue());
                productoRepository.save(prod);
            }

            venta.setTotal(totalVenta);
            venta.setTotalGravada(totalGravada);
            venta.setTotalIgv(totalIgv);

            // 4. CRÉDITO / CAJA
            BigDecimal montoAbonado = BigDecimal.ZERO;

            if ("CREDITO".equals(dto.getFormaPago())) {
                venta.setFormaPago("CREDITO");
                BigDecimal inicial = dto.getMontoInicial() != null ? dto.getMontoInicial() : BigDecimal.ZERO;
                montoAbonado = inicial;
                venta.setMontoPagado(inicial);
                venta.setSaldoPendiente(totalVenta.subtract(inicial));
                int dias = dto.getDiasCredito() != null ? dto.getDiasCredito() : 7;
                venta.setFechaVencimiento(LocalDate.now().plusDays(dias));
            } else {
                venta.setFormaPago("CONTADO");
                venta.setMontoPagado(totalVenta);
                venta.setSaldoPendiente(BigDecimal.ZERO);
                venta.setFechaVencimiento(LocalDate.now());
                montoAbonado = totalVenta;
            }

            Venta guardada = ventaRepository.save(venta);

            if (montoAbonado.compareTo(BigDecimal.ZERO) > 0) {
                Amortizacion amo = new Amortizacion();
                amo.setVenta(guardada);
                amo.setMonto(montoAbonado);
                amo.setMetodoPago("EFECTIVO");
                amo.setObservacion("PAGO INICIAL / CONTADO");
                amortizacionRepository.save(amo);

                try {
                    cajaService.registrarMovimiento("INGRESO", 
                        "VENTA " + guardada.getSerie() + "-" + guardada.getNumero(), 
                        montoAbonado);
                } catch (Exception e) {
                    System.err.println("ADVERTENCIA: Venta sin movimiento de caja: " + e.getMessage());
                }
            }

            return ResponseEntity.ok(Map.of("id", guardada.getId()));

        } catch (OptimisticLockingFailureException e) {
            // ERROR DE CONCURRENCIA
            return ResponseEntity.badRequest().body("ERROR DE STOCK: Otro vendedor actualizó el producto mientras usted procesaba la venta. Por favor, vuelva a intentarlo.");
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
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
}