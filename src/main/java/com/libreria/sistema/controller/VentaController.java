package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final KardexRepository kardexRepository;
    private final CajaRepository cajaRepository;
    private final CorrelativoRepository correlativoRepository; // <--- 1. NUEVO

    // 2. AGREGAR AL CONSTRUCTOR
    public VentaController(ProductoRepository productoRepository, VentaRepository ventaRepository,
                           KardexRepository kardexRepository, CajaRepository cajaRepository,
                           CorrelativoRepository correlativoRepository) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.kardexRepository = kardexRepository;
        this.cajaRepository = cajaRepository;
        this.correlativoRepository = correlativoRepository;
    }

    @GetMapping("/nueva")
    public String nuevaVenta(Model model) {
        model.addAttribute("productos", productoRepository.findByActivoTrue());
        return "ventas/pos";
    }

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
            data.put("ubicacion", (p.getUbicacionEstante()!=null?p.getUbicacionEstante():"") + "-" + (p.getUbicacionFila()!=null?p.getUbicacionFila():""));
            return ResponseEntity.ok(data);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/guardar")
    public ResponseEntity<?> guardarVenta(@RequestBody VentaDTO dto) {
        try {
            Venta venta = new Venta();
            
            venta.setClienteDenominacion(dto.getClienteNombre());
            venta.setClienteNumeroDocumento(dto.getClienteDocumento());
            
            // 3. LÓGICA DE CORRELATIVOS REAL
            String tipo = dto.getTipoComprobante() != null ? dto.getTipoComprobante() : "NOTA_VENTA";
            String serie = tipo.equals("FACTURA") ? "F001" : (tipo.equals("BOLETA") ? "B001" : "N001");
            
            // Buscamos en la tabla real
            Correlativo correlativo = correlativoRepository.findByCodigoAndSerie(tipo, serie)
                    .orElse(new Correlativo(tipo, serie, 0)); // Si no existe, empieza en 0

            Integer nuevoNumero = correlativo.getUltimoNumero() + 1;
            correlativo.setUltimoNumero(nuevoNumero);
            correlativoRepository.save(correlativo); // Guardamos el avance

            venta.setTipoComprobante(tipo);
            venta.setSerie(serie);
            venta.setNumero(nuevoNumero); // Asignamos el número real
            
            venta.setFechaEmision(LocalDate.now());
            venta.setFechaVencimiento(LocalDate.now());
            venta.setMoneda("PEN");
            venta.setTipoOperacion("0101");
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

                // Cálculos SUNAT
                BigDecimal precioFinalUnitario = item.getPrecioVenta();
                BigDecimal cantidad = item.getCantidad();
                BigDecimal subtotalItem = precioFinalUnitario.multiply(cantidad);

                BigDecimal valorUnitario = precioFinalUnitario.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
                
                BigDecimal valorVentaItem = valorUnitario.multiply(cantidad);
                BigDecimal igvItem = subtotalItem.subtract(valorVentaItem);

                // Detalle
                DetalleVenta det = new DetalleVenta();
                det.setVenta(venta);
                det.setProducto(prod);
                det.setCantidad(cantidad);
                det.setDescripcion(prod.getNombre());
                det.setUnidadMedida("NIU");
                
                det.setPrecioUnitario(precioFinalUnitario);
                det.setValorUnitario(valorUnitario);
                det.setPorcentajeIgv(new BigDecimal("18.00"));
                det.setCodigoTipoAfectacionIgv("10");
                det.setSubtotal(subtotalItem);

                venta.getItems().add(det);

                totalVenta = totalVenta.add(subtotalItem);
                totalGravada = totalGravada.add(valorVentaItem);
                totalIgv = totalIgv.add(igvItem);

                // KARDEX
                Kardex k = new Kardex();
                k.setProducto(prod);
                k.setTipo("SALIDA");
                k.setMotivo("VENTA POS " + venta.getTipoComprobante() + " " + venta.getSerie() + "-" + venta.getNumero());
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

            // CAJA
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

    @GetMapping("/detalle/{id}")
    public String verDetalle(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        model.addAttribute("venta", venta);
        // Necesitas crear el archivo: templates/ventas/modal_detalle.html
        return "ventas/modal_detalle :: contenido"; 
    }

    // ... otros métodos ...

    // NUEVO: Endpoint para abrir la vista de impresión limpia
    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        Venta venta = ventaRepository.findById(id).orElse(null);
        if(venta == null) return "redirect:/ventas/lista";
        
        model.addAttribute("venta", venta);
        return "ventas/impresion"; // Vista exclusiva para imprimir (sin menú, sin sidebar)
    }
}
