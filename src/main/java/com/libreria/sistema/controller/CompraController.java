package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CompraDTO;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.CompraService;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/compras")
@Slf4j
@PreAuthorize("hasPermission(null, 'COMPRAS_VER')")
public class CompraController {

    private final CompraRepository compraRepository;
    private final ProveedorRepository proveedorRepository;
    private final ProductoRepository productoRepository;
    private final CompraService compraService;

    public CompraController(CompraRepository compraRepository, ProveedorRepository proveedorRepository,
                            ProductoRepository productoRepository, CompraService compraService) {
        this.compraRepository = compraRepository;
        this.proveedorRepository = proveedorRepository;
        this.productoRepository = productoRepository;
        this.compraService = compraService;
    }

    @GetMapping("/lista")
    public String lista(Model model) {
        // Ordenamos por ID descendente para ver lo último primero
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

    // FIX ERROR-2: toda la lógica de negocio está en CompraService.guardarCompra() con @Transactional.
    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'COMPRAS_CREAR')")
    public ResponseEntity<?> guardarCompra(@RequestBody CompraDTO dto) {
        try {
            compraService.guardarCompra(dto);
            return ResponseEntity.ok(Map.of("message", "Compra registrada exitosamente"));
        } catch (Exception e) {
            log.error("Error al guardar compra", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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

    @PostMapping("/api/anular/{id}")
    @PreAuthorize("hasPermission(null, 'COMPRAS_ELIMINAR')")
    @ResponseBody
    public ResponseEntity<?> anularCompra(@PathVariable Long id) {
        try {
            compraService.anularCompra(id);
            return ResponseEntity.ok(Map.of("message", "Compra anulada exitosamente"));
        } catch (Exception e) {
            log.error("Error al anular compra: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}