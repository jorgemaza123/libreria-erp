package com.libreria.sistema.controller;

import com.libreria.sistema.service.CajaService;
import com.libreria.sistema.service.DashboardService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class DashboardController {

    private final DashboardService dashboardService;
    private final CajaService cajaService; // Reutilizamos para ver saldo actual

    public DashboardController(DashboardService dashboardService, CajaService cajaService) {
        this.dashboardService = dashboardService;
        this.cajaService = cajaService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        // 1. Datos Financieros (Caja)
        Map<String, java.math.BigDecimal> balance = cajaService.obtenerBalanceHoy();
        model.addAttribute("balance", balance);

        // 2. Datos Estadísticos (Gráficos y Alertas)
        Map<String, Object> stats = dashboardService.obtenerDatosDashboard();
        model.addAttribute("stats", stats);

        return "dashboard"; // dashboard.html
    }
}