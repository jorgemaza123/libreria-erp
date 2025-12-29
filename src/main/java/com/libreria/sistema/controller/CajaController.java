package com.libreria.sistema.controller;

import com.libreria.sistema.service.CajaService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;

@Controller
@RequestMapping("/caja")
public class CajaController {

    private final CajaService cajaService;

    public CajaController(CajaService cajaService) {
        this.cajaService = cajaService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("movimientos", cajaService.listarMovimientosHoy());
        model.addAttribute("balance", cajaService.obtenerBalanceHoy());
        return "caja/index";
    }

    @PostMapping("/guardar")
    public String guardar(@RequestParam String tipo, 
                          @RequestParam String concepto, 
                          @RequestParam BigDecimal monto,
                          RedirectAttributes attributes) {
        try {
            cajaService.registrarMovimiento(tipo, concepto, monto);
            attributes.addFlashAttribute("success", "Movimiento registrado correctamente");
        } catch (Exception e) {
            attributes.addFlashAttribute("error", "Error al registrar movimiento");
        }
        return "redirect:/caja";
    }
}