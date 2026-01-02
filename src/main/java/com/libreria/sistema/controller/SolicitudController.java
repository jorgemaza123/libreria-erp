package com.libreria.sistema.controller;

import com.libreria.sistema.model.SolicitudProducto;
import com.libreria.sistema.repository.SolicitudProductoRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/solicitudes")
public class SolicitudController {

    private final SolicitudProductoRepository solicitudRepository;

    public SolicitudController(SolicitudProductoRepository solicitudRepository) {
        this.solicitudRepository = solicitudRepository;
    }

    // LISTAR PEDIDOS PENDIENTES (Ordenados por mayor demanda)
    @GetMapping("/lista")
    public String listaSolicitudes(Model model) {
        // Asumimos que tienes un método findByEstadoOrderByContadorDesc o usamos findAll y filtramos
        // Para asegurar compatibilidad con JPA estándar, usaremos findByEstado
        model.addAttribute("solicitudes", solicitudRepository.findByEstadoOrderByContadorDesc("PENDIENTE")); 
        return "solicitudes/lista";
    }

    // MARCAR COMO ATENDIDO (Cuando ya compraste el producto)
    @PostMapping("/atender/{id}")
    public String atenderSolicitud(@PathVariable Long id) {
        solicitudRepository.findById(id).ifPresent(solicitud -> {
            solicitud.setEstado("ATENDIDO"); // O "COMPRADO"
            solicitudRepository.save(solicitud);
        });
        return "redirect:/solicitudes/lista";
    }
    
    // ELIMINAR SOLICITUD (Si fue un error)
    @GetMapping("/eliminar/{id}")
    public String eliminarSolicitud(@PathVariable Long id) {
        solicitudRepository.deleteById(id);
        return "redirect:/solicitudes/lista";
    }
}