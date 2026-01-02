package com.libreria.sistema.controller;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.service.CajaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/gastos")
public class GastoController {

    private final CajaService cajaService;
    private final CajaRepository cajaRepository;

    public GastoController(CajaService cajaService, CajaRepository cajaRepository) {
        this.cajaService = cajaService;
        this.cajaRepository = cajaRepository;
    }

    @GetMapping
    public String index(Model model) {
        // Obtenemos solo los movimientos que son GASTOS (Filtrado simple por concepto o tipo)
        // Nota: En un sistema más grande, tendrías una tabla 'Gastos' separada.
        // Aquí filtramos los EGRESOS que contienen la palabra "GASTO" o "PAGO SERVICIO"
        List<MovimientoCaja> gastos = cajaRepository.findAll().stream()
                .filter(m -> "EGRESO".equals(m.getTipo()) && !m.getConcepto().startsWith("COMPRA"))
                .sorted((a, b) -> b.getId().compareTo(a.getId())) // Más recientes primero
                .collect(Collectors.toList());

        model.addAttribute("gastos", gastos);
        return "gastos/index";
    }

    @PostMapping("/guardar")
    public String registrarGasto(@RequestParam String concepto, 
                                 @RequestParam BigDecimal monto, 
                                 RedirectAttributes attr) {
        try {
            // Validamos que el monto sea positivo
            if (monto.compareTo(BigDecimal.ZERO) <= 0) {
                attr.addFlashAttribute("error", "El monto debe ser mayor a 0.");
                return "redirect:/gastos";
            }

            // Registramos usando el Service para que descuente de la CAJA ABIERTA
            cajaService.registrarMovimiento("EGRESO", "GASTO OP: " + concepto.toUpperCase(), monto);
            
            attr.addFlashAttribute("success", "Gasto registrado correctamente.");
        } catch (Exception e) {
            // Si la caja está cerrada o hay otro error
            attr.addFlashAttribute("error", "Error al registrar: " + e.getMessage());
        }
        return "redirect:/gastos";
    }
}