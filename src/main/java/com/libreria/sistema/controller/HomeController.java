package com.libreria.sistema.controller;

import com.libreria.sistema.service.DashboardService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Controller
public class HomeController {

    private final DashboardService dashboardService;

    public HomeController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
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

        // 1. Obtenemos el mapa con TODOS los datos (KPIs, Gráficos, Tablas)
        Map<String, Object> datos = dashboardService.obtenerDatosDashboard();

        // 2. TRUCO IMPORTANTE: Usamos addAllAttributes
        // Esto "desempaqueta" el mapa.
        // Así en el HTML puedes usar directamente ${kpiVentasMes} en vez de ${stats.kpiVentasMes}
        model.addAllAttributes(datos);

        return "dashboard";
    }

    @GetMapping("/vendedor/dashboard")
    public String dashboardVendedor() {
        return "vendedor_home"; // Panel de botones grandes para el POS
    }

    /**
     * API endpoint para polling del dashboard.
     * Retorna todos los datos actualizados en formato JSON.
     */
    @GetMapping("/api/dashboard/datos")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> obtenerDatosDashboard() {
        Map<String, Object> datos = new HashMap<>(dashboardService.obtenerDatosDashboard());

        // Agregar timestamp de última actualización
        datos.put("ultimaActualizacion",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));

        return ResponseEntity.ok(datos);
    }
}