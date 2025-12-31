package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Controller
@RequestMapping("/compras")
public class CompraController {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final CajaRepository cajaRepository;

    public CompraController(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
                            ProductoRepository productoRepository, KardexRepository kardexRepository,
                            CajaRepository cajaRepository) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.cajaRepository = cajaRepository;
    }

    // VISTA: Listado
    @GetMapping("/lista")
    public String lista(Model model) {
        model.addAttribute("compras", compraRepository.findAll());
        return "compras/lista";
    }

    // VISTA: Formulario Nueva Compra
    @GetMapping("/nueva")
    public String nueva(Model model) {
        model.addAttribute("proveedores", proveedorRepository.findByActivoTrue());
        model.addAttribute("productos", productoRepository.findAll()); // Para el buscador
        return "compras/formulario";
    }

    // API: Procesar Compra
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

                // 2. KARDEX (Registrar antes de modificar stock actual)
                Kardex kardex = new Kardex();
                kardex.setProducto(prod);
                kardex.setTipo("ENTRADA");
                kardex.setMotivo("COMPRA " + compra.getTipoComprobante() + " " + compra.getNumeroComprobante());
                kardex.setCantidad(item.getCantidad());
                kardex.setStockAnterior(prod.getStockActual());
                kardex.setStockActual(prod.getStockActual() + item.getCantidad());
                kardexRepository.save(kardex);

                // 3. ACTUALIZAR PRODUCTO (Stock y Precio Compra referencial)
                prod.setStockActual(prod.getStockActual() + item.getCantidad());
                prod.setPrecioCompra(item.getCosto()); // Actualizamos el costo al último comprado
                productoRepository.save(prod);
            }

            compra.setTotal(totalCompra);
            Compra guardada = compraRepository.save(compra);

            // 4. CAJA (Egreso de Dinero)
            MovimientoCaja egreso = new MovimientoCaja();
            egreso.setFecha(LocalDateTime.now());
            egreso.setTipo("EGRESO");
            egreso.setConcepto("PAGO A PROVEEDOR: " + prov.getRazonSocial() + " - REF: " + guardada.getNumeroComprobante());
            egreso.setMonto(totalCompra);
            cajaRepository.save(egreso);

            return ResponseEntity.ok(Map.of("message", "Compra registrada exitosamente"));

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
// API: OBTENER DETALLE DE UNA COMPRA (PARA EL MODAL)
    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalle(@PathVariable Long id) {
        return compraRepository.findById(id).map(compra -> {
            // Devolvemos un mapa simple para evitar problemas de recursión JSON
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