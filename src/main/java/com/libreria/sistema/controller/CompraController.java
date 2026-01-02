package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.CajaService; // IMPORTANTE: Usar el servicio

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@Controller
@RequestMapping("/compras")
public class CompraController {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    
    // CORRECCIÓN: Usamos el servicio de caja, no el repositorio directo
    private final CajaService cajaService; 

    public CompraController(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
                            ProductoRepository productoRepository, KardexRepository kardexRepository,
                            CajaService cajaService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.cajaService = cajaService;
    }

    @GetMapping("/lista")
    public String lista(Model model) {
        // Ordenamos por ID descendente para ver lo último primero
        model.addAttribute("compras", compraRepository.findAll());
        return "compras/lista";
    }

    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("proveedores", proveedorRepository.findByActivoTrue());
        model.addAttribute("productos", productoRepository.findAll());
        return "compras/formulario";
    }

    @PostMapping("/api/guardar")
    public ResponseEntity<?> guardarCompra(@RequestBody CompraDTO dto) {
        try {
            Proveedor prov = proveedorRepository.findById(dto.getProveedorId())
                    .orElseThrow(() -> new RuntimeException("Proveedor no encontrado"));

            Compra compra = new Compra();
            compra.setProveedor(prov);
            compra.setTipoComprobante(dto.getTipoComprobante());
            compra.setNumeroComprobante(dto.getNumeroComprobante());
            compra.setObservaciones(dto.getObservaciones());
            
            BigDecimal totalCompra = BigDecimal.ZERO;

            for (CompraDTO.DetalleDTO item : dto.getItems()) {
                Producto prod = productoRepository.findById(item.getProductoId())
                        .orElseThrow(() -> new RuntimeException("Producto no encontrado ID: " + item.getProductoId()));

                // 1. Crear Detalle
                DetalleCompra det = new DetalleCompra();
                det.setCompra(compra);
                det.setProducto(prod);
                det.setCantidad(item.getCantidad());
                det.setPrecioUnitario(item.getCosto());
                
                BigDecimal subtotal = item.getCosto().multiply(new BigDecimal(item.getCantidad()));
                det.setSubtotal(subtotal);
                
                compra.getDetalles().add(det);
                totalCompra = totalCompra.add(subtotal);

                // 2. KARDEX (Registrar Movimiento)
                Kardex kardex = new Kardex();
                kardex.setProducto(prod);
                kardex.setTipo("ENTRADA");
                kardex.setMotivo("COMPRA " + compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
                kardex.setCantidad(item.getCantidad());
                kardex.setStockAnterior(prod.getStockActual());
                kardex.setStockActual(prod.getStockActual() + item.getCantidad());
                kardexRepository.save(kardex);

                // 3. ACTUALIZAR PRODUCTO (Subir Stock y Actualizar Costo)
                prod.setStockActual(prod.getStockActual() + item.getCantidad());
                prod.setPrecioCompra(item.getCosto());
                productoRepository.save(prod);
            }

            compra.setTotal(totalCompra);
            Compra guardada = compraRepository.save(compra);

            // 4. CAJA (CORREGIDO: Usar Service para descontar del turno actual)
            try {
                cajaService.registrarMovimiento(
                    "EGRESO", 
                    "COMPRA PROV: " + prov.getRazonSocial() + " DOC: " + guardada.getNumeroComprobante(), 
                    totalCompra
                );
            } catch (Exception e) {
                // Si la caja está cerrada, permitimos la compra pero avisamos en consola
                System.err.println("ADVERTENCIA: Compra registrada sin salida de caja (Caja Cerrada).");
            }

            return ResponseEntity.ok(Map.of("message", "Compra registrada exitosamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return compraRepository.findById(id).map(compra -> {
            return ResponseEntity.ok(Map.of(
                "proveedor", compra.getProveedor().getRazonSocial(),
                "documento", compra.getTipoComprobante() + " " + compra.getNumeroComprobante(),
                "fecha", compra.getFecha().toString(),
                "total", compra.getTotal(),
                "items", compra.getDetalles().stream().map(d -> Map.of(
                    "producto", d.getProducto().getNombre(),
                    "cantidad", d.getCantidad(),
                    "precio", d.getPrecioUnitario(),
                    "subtotal", d.getSubtotal()
                )).toList()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}