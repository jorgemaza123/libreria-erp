package com.libreria.sistema.controller;

import com.libreria.sistema.model.SolicitudProducto;
import com.libreria.sistema.repository.SolicitudProductoRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/solicitudes")
@PreAuthorize("hasPermission(null, 'SOLICITUDES_VER')")
public class SolicitudController {

    private final SolicitudProductoRepository solicitudRepository;

    public SolicitudController(SolicitudProductoRepository solicitudRepository) {
        this.solicitudRepository = solicitudRepository;
    }

    // LISTAR PEDIDOS PENDIENTES — redirige a la vista unificada
    @GetMapping("/lista")
    public String listaSolicitudes() {
        return "redirect:/faltantes?tab=solicitudes";
    }

    // MARCAR COMO ATENDIDO (Cuando ya compraste el producto)
    @PostMapping("/atender/{id}")
    @PreAuthorize("hasPermission(null, 'SOLICITUDES_EDITAR')")
    public String atenderSolicitud(@PathVariable Long id) {
        solicitudRepository.findById(id).ifPresent(solicitud -> {
            solicitud.setEstado("ATENDIDO");
            solicitudRepository.save(solicitud);
        });
        return "redirect:/faltantes?tab=solicitudes";
    }

    // ELIMINAR SOLICITUD (Si fue un error)
    @GetMapping("/eliminar/{id}")
    @PreAuthorize("hasPermission(null, 'SOLICITUDES_ELIMINAR')")
    public String eliminarSolicitud(@PathVariable Long id) {
        solicitudRepository.deleteById(id);
        return "redirect:/faltantes?tab=solicitudes";
    }
}