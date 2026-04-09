package com.libreria.sistema.controller;

import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.service.CajaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/gastos")
@PreAuthorize("hasPermission(null, 'GASTOS_VER')")
public class GastoController {

    private final CajaService cajaService;
    private final CajaRepository cajaRepository;

    public GastoController(CajaService cajaService, CajaRepository cajaRepository) {
        this.cajaService = cajaService;
        this.cajaRepository = cajaRepository;
    }

    // U-2: añadir filtro de fechas con default últimos 30 días para evitar cargar toda la historia
    @GetMapping
    public String index(@RequestParam(required = false) String fechaInicio,
                        @RequestParam(required = false) String fechaFin,
                        Model model) {
        LocalDate fin = (fechaFin != null && !fechaFin.isBlank()) ? LocalDate.parse(fechaFin) : LocalDate.now();
        LocalDate inicio = (fechaInicio != null && !fechaInicio.isBlank()) ? LocalDate.parse(fechaInicio) : fin.minusDays(30);
        LocalDateTime inicioRango = inicio.atStartOfDay();
        LocalDateTime finRango = fin.atTime(23, 59, 59);

        // FIX ERROR-12 + U-2: filtrar por categoría GASTO_OPERATIVO y rango de fechas en BD
        List<MovimientoCaja> gastos = cajaRepository.findByCategoriaMovimientoAndRango(
                CategoriaMovimiento.GASTO_OPERATIVO, inicioRango, finRango);
        model.addAttribute("gastos", gastos);
        model.addAttribute("fechaInicio", inicio);
        model.addAttribute("fechaFin", fin);
        return "gastos/index";
    }

    @PostMapping("/guardar")
    @PreAuthorize("hasPermission(null, 'GASTOS_CREAR')")
    public String registrarGasto(@RequestParam String concepto,
                                 @RequestParam BigDecimal monto, 
                                 RedirectAttributes attr) {
        try {
            String conceptoLimpio = concepto != null ? concepto.trim() : "";
            if (conceptoLimpio.isBlank()) {
                attr.addFlashAttribute("error", "El concepto es obligatorio.");
                return "redirect:/gastos";
            }

            // Validamos que el monto sea positivo
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                attr.addFlashAttribute("error", "El monto debe ser mayor a 0.");
                return "redirect:/gastos";
            }

            // Registramos usando el Service para que descuente de la CAJA ABIERTA
            cajaService.registrarMovimiento("EGRESO", "GASTO OP: " + conceptoLimpio.toUpperCase(), monto,
                    com.libreria.sistema.model.CategoriaMovimiento.GASTO_OPERATIVO);
            
            attr.addFlashAttribute("success", "Gasto registrado correctamente.");
        } catch (Exception e) {
            // Si la caja está cerrada o hay otro error
            attr.addFlashAttribute("error", "Error al registrar: " + e.getMessage());
        }
        return "redirect:/gastos";
    }
}
