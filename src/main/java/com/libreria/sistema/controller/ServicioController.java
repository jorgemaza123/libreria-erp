package com.libreria.sistema.controller;

import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.service.CotizacionService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/servicios")
public class ServicioController {

    private final CotizacionService cotizacionService;

    public ServicioController(CotizacionService cotizacionService) {
        this.cotizacionService = cotizacionService;
    }

    // Vista de Recepción de Equipo / Servicio
    @GetMapping("/nuevo")
    public String nuevoServicio(Model model) {
        // Reutilizamos el objeto cotización pero la vista será distinta
        model.addAttribute("titulo", "Orden de Servicio Técnico");
        return "servicios/nuevo"; // Nueva vista HTML
    }
}