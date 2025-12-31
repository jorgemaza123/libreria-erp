package com.libreria.sistema.controller;

import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.service.CotizacionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.List;

@Controller
@RequestMapping("/cotizaciones")
public class CotizacionController {

    private final CotizacionService cotizacionService;

    public CotizacionController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    @GetMapping("/nueva")
    public String nueva() {
        return "cotizaciones/nueva";
    }

    // Ruta para abrir modo edición
    @GetMapping("/editar/{id}")
    public String editar(@PathVariable Long id, Model model) {
        Cotizacion coti = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacionEdicion", coti);
        return "cotizaciones/nueva";
    }

    @GetMapping("/lista")
    public String lista(@RequestParam(defaultValue = "0") int page, Model model) {
        Pageable pageable = PageRequest.of(page, 10, Sort.by("id").descending());
        Page<Cotizacion> pageCoti = cotizacionService.listar(pageable);

        model.addAttribute("cotizaciones", pageCoti);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageCoti.getTotalPages());

        if (pageCoti.getTotalPages() > 0) {
            List<Integer> pageNumbers = IntStream.rangeClosed(1, pageCoti.getTotalPages())
                    .boxed().collect(Collectors.toList());
            model.addAttribute("pageNumbers", pageNumbers);
        } else {
            model.addAttribute("pageNumbers", List.of());
        }
        
        return "cotizaciones/lista";
    }

    @PostMapping("/api/guardar")
    @ResponseBody
    public ResponseEntity<?> guardar(@RequestBody VentaDTO dto) {
        try {
            cotizacionService.crearCotizacion(dto);
            return ResponseEntity.ok(Map.of("mensaje", "Cotización creada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // API Actualizar (Edición)
    @PutMapping("/api/actualizar/{id}")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody VentaDTO dto) {
        try {
            cotizacionService.actualizarCotizacion(id, dto);
            return ResponseEntity.ok(Map.of("mensaje", "Actualizado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    @PostMapping("/api/convertir/{id}")
    @ResponseBody
    public ResponseEntity<?> convertir(@PathVariable Long id, @RequestParam String tipo) {
        try {
            Long ventaId = cotizacionService.convertirAVenta(id, tipo);
            return ResponseEntity.ok(Map.of(
                "mensaje", "Cotización convertida a Venta exitosamente",
                "ventaId", ventaId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/imprimir/{id}")
    public String imprimir(@PathVariable Long id, Model model) {
        model.addAttribute("cotizacion", cotizacionService.obtenerPorId(id));
        return "cotizaciones/impresion";
    }

    // NUEVO: Endpoint para el Modal de Detalle (Igual que en Ventas)
    // Endpoint para el Modal de Detalle
    @GetMapping("/detalle/{id}")
    public String verDetalle(@PathVariable Long id, Model model) {
        Cotizacion cotizacion = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacion", cotizacion);
        // IMPORTANTE: Debe coincidir con la ubicación real del archivo HTML
        return "cotizaciones/modal_detalle :: contenido"; 
    }
}