package com.libreria.sistema.controller;

import com.libreria.sistema.service.CajaService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
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
        // Si no hay caja abierta, mandar a abrir
        if (cajaService.obtenerSesionActiva().isEmpty()) {
            return "redirect:/caja/apertura";
        }

        model.addAttribute("movimientos", cajaService.listarMovimientosSesion());
        model.addAttribute("balance", cajaService.obtenerBalanceSesion());
        return "caja/index";
    }

    @GetMapping("/apertura")
    public String vistaApertura() {
        if (cajaService.obtenerSesionActiva().isPresent()) {
            return "redirect:/caja";
        }
        return "caja/apertura";
    }

    @PostMapping("/abrir")
    @PreAuthorize("hasPermission(null, 'CAJA_CREAR')")
    public String abrir(@RequestParam BigDecimal montoInicial, RedirectAttributes attr) {
        try {
            cajaService.abrirCaja(montoInicial);
            attr.addFlashAttribute("success", "Caja abierta correctamente");
            return "redirect:/caja";
        } catch (Exception e) {
            attr.addFlashAttribute("error", e.getMessage());
            return "redirect:/caja/apertura";
        }
    }

    @GetMapping("/cierre")
    public String vistaCierre(Model model) {
        if (cajaService.obtenerSesionActiva().isEmpty()) return "redirect:/caja/apertura";
        
        model.addAttribute("balance", cajaService.obtenerBalanceSesion());
        return "caja/cierre";
    }

    @PostMapping("/cerrar")
    public String cerrar(@RequestParam BigDecimal montoReal, RedirectAttributes attr) {
        try {
            cajaService.cerrarCaja(montoReal);
            attr.addFlashAttribute("success", "Caja cerrada y arqueada correctamente.");
            return "redirect:/caja/apertura"; // Vuelve a pedir apertura para el siguiente turno
        } catch (Exception e) {
            attr.addFlashAttribute("error", e.getMessage());
            return "redirect:/caja";
        }
    }

    // Registrar GASTO ADMINISTRATIVO o Retiro
    @PostMapping("/movimiento")
    public String movimiento(@RequestParam String tipo, 
                             @RequestParam String concepto, 
                             @RequestParam BigDecimal monto, 
                             RedirectAttributes attr) {
        try {
            cajaService.registrarMovimiento(tipo, concepto, monto);
            attr.addFlashAttribute("success", "Registrado");
        } catch (Exception e) {
            attr.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/caja";
    }
}