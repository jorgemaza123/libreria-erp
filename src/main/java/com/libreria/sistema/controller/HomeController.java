package com.libreria.sistema.controller;

import com.libreria.sistema.service.CajaService;
import com.libreria.sistema.service.DashboardService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

@Controller
public class HomeController {

    private final DashboardService dashboardService;
    private final CajaService cajaService;

    public HomeController(DashboardService dashboardService, CajaService cajaService) {
        this.dashboardService = dashboardService;
        this.cajaService = cajaService;
    }

    @GetMapping("/")
    public String root(Authentication auth, Model model) {
        if (auth != null) {
            // Si es VENDEDOR -> Redirigir a su panel especial
            if (auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_VENDEDOR"))) {
                return "redirect:/vendedor/dashboard";
            }
        }
        
        // Si es ADMIN -> Mostrar Dashboard con Gráficos
        // Cargar datos para el dashboard.html
        Map<String, java.math.BigDecimal> balance = cajaService.obtenerBalanceHoy();
        model.addAttribute("balance", balance);

        Map<String, Object> stats = dashboardService.obtenerDatosDashboard();
        model.addAttribute("stats", stats);

        return "dashboard"; // <--- AQUÍ ESTABA EL ERROR (Antes decía "index")
    }

    @GetMapping("/vendedor/dashboard")
    public String dashboardVendedor() {
        return "vendedor_home"; // Panel de botones grandes
    }
}