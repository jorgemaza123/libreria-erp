package com.libreria.sistema.controller;

import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.dto.VentaDTO;
import com.libreria.sistema.service.CotizacionService;
import com.libreria.sistema.service.ConfiguracionService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final ConfiguracionService configuracionService;

    public CotizacionController(CotizacionService cotizacionService, ConfiguracionService configuracionService) {
        this.cotizacionService = cotizacionService;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CREAR')")
    public String nueva() {
        return "cotizaciones/nueva";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    public String editar(@PathVariable Long id, Model model) {
        Cotizacion coti = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacionEdicion", coti);
        return "cotizaciones/nueva";
    }

    @GetMapping("/lista")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
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
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> guardar(@RequestBody VentaDTO dto) {
        try {
            cotizacionService.crearCotizacion(dto);
            return ResponseEntity.ok(Map.of("mensaje", "Cotización creada correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/actualizar/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody VentaDTO dto) {
        try {
            cotizacionService.actualizarCotizacion(id, dto);
            return ResponseEntity.ok(Map.of("mensaje", "Actualizado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Metodo corregido: Asegurado de tener solo un @PostMapping
    @PostMapping("/api/convertir/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CONVERTIR')")
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
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public String imprimir(@PathVariable Long id, Model model) {
        model.addAttribute("cotizacion", cotizacionService.obtenerPorId(id));
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "cotizaciones/impresion";
    }

    @GetMapping("/detalle/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public String verDetalle(@PathVariable Long id, Model model) {
        Cotizacion cotizacion = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacion", cotizacion);
        return "cotizaciones/modal_detalle :: contenido";
    }
}