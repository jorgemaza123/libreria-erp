package com.libreria.sistema.controller;

import com.libreria.sistema.model.Venta;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.service.ProductoService;
import com.libreria.sistema.service.VentaService;
import com.libreria.sistema.model.Producto; // Asegurar imports
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Controller
@RequestMapping("/ventas")
public class VentaController {

    private final ProductoService productoService;
    private final VentaService ventaService;

    public VentaController(ProductoService productoService, VentaService ventaService) {
        this.productoService = productoService;
        this.ventaService = ventaService;
    }

    // Pantalla Principal POS
    @GetMapping("/nueva")
    public String nuevaVenta(Model model) {
        return "ventas/pos";
    }

    // --- CORRECCIÓN AQUÍ: Paginación ---
    @GetMapping("/lista")
    public String listarVentas(@RequestParam(defaultValue = "0") int page, Model model) {
        // 10 ventas por página, ordenadas por ID descendente
        Pageable pageable = PageRequest.of(page, 10, Sort.by("id").descending());
        
        Page<Venta> ventaPage = ventaService.listarVentas(pageable);

        model.addAttribute("ventas", ventaPage);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", ventaPage.getTotalPages());

        if (ventaPage.getTotalPages() > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, ventaPage.getTotalPages())
                .boxed()
                .collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        }

        return "ventas/lista";
    }

    @GetMapping("/detalle/{id}")
    public String verDetalle(@PathVariable Long id, Model model) {
        Venta venta = ventaService.obtenerPorId(id);
        model.addAttribute("venta", venta);
        return "ventas/modal_detalle :: contenido";
    }

    @PostMapping("/anular/{id}")
    public String anularVenta(@PathVariable Long id, RedirectAttributes attributes) {
        try {
            ventaService.anularVenta(id);
            attributes.addFlashAttribute("success", "Venta anulada correctamente.");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/ventas/lista";
    }

    // --- APIs REST (Mantener igual) ---
    @GetMapping("/api/productos-activos")
    @ResponseBody
    public List<Producto> productosActivos() {
        return productoService.listarActivos();
    }

    @PostMapping("/api/procesar")
    @ResponseBody
    public ResponseEntity<?> procesarVenta(@RequestBody VentaDTO ventaDTO) {
        try {
            ventaService.registrarVenta(ventaDTO);
            return ResponseEntity.ok(Map.of("mensaje", "Venta registrada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/imprimir/{id}")
    public String imprimirVenta(@PathVariable Long id, Model model) {
        Venta venta = ventaService.obtenerPorId(id);
        model.addAttribute("venta", venta);
        return "ventas/impresion"; // Nueva plantilla solo para imprimir
    }
}