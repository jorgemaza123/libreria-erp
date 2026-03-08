package com.libreria.sistema.controller;

import com.libreria.sistema.repository.SolicitudProductoRepository;
import com.libreria.sistema.service.ListaEscolarService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
@RequestMapping("/faltantes")
@PreAuthorize("hasAnyRole('ADMIN', 'VENDEDOR')")
public class FaltantesController {

    private final ListaEscolarService listaService;
    private final SolicitudProductoRepository solicitudRepository;

    public FaltantesController(ListaEscolarService listaService,
                               SolicitudProductoRepository solicitudRepository) {
        this.listaService = listaService;
        this.solicitudRepository = solicitudRepository;
    }

    @GetMapping
    public String vista(Model model, @RequestParam(defaultValue = "listas") String tab) {
        model.addAttribute("faltantes", listaService.obtenerProductosFaltantes());
        model.addAttribute("cotizadosProveedor", listaService.obtenerCotizadosProveedor());
        model.addAttribute("estadisticas", listaService.obtenerEstadisticasFaltantes());
        model.addAttribute("solicitudes", solicitudRepository.findByEstadoOrderByContadorDesc("PENDIENTE"));
        model.addAttribute("activeTab", tab);
        return "faltantes/index";
    }
}
