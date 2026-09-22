package com.libreria.sistema.controller;

import com.libreria.sistema.model.SolicitudProducto;
import com.libreria.sistema.repository.SolicitudProductoRepository;
import com.libreria.sistema.service.SolicitudPdfService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Controller
@RequestMapping("/solicitudes")
@PreAuthorize("hasPermission(null, 'SOLICITUDES_VER')")
public class SolicitudController {

    private final SolicitudProductoRepository solicitudRepository;
    private final SolicitudPdfService pdfService;

    public SolicitudController(SolicitudProductoRepository solicitudRepository, SolicitudPdfService pdfService) {
        this.solicitudRepository = solicitudRepository;
        this.pdfService = pdfService;
    }

    // LISTAR PEDIDOS PENDIENTES — redirige a la vista unificada
    @GetMapping("/lista")
    public String listaSolicitudes() {
        return "redirect:/faltantes?tab=solicitudes";
    }

    // EXPORTAR PEDIDOS EN PDF
    @GetMapping("/exportar/pdf")
    public void exportarPdf(@RequestParam(required = false) List<Long> ids,
                            HttpServletResponse response) {
        try {
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "inline; filename=pedidos_vendedores.pdf");
            List<SolicitudProducto> solicitudes = obtenerSolicitudesPendientes(ids);
            pdfService.generarPdf(solicitudes, response.getOutputStream());
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    @GetMapping("/exportar/excel")
    public void exportarExcel(@RequestParam(required = false) List<Long> ids,
                              HttpServletResponse response) throws IOException {
        try {
            byte[] excel = pdfService.generarExcel(obtenerSolicitudesPendientes(ids));
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=pedidos_vendedores.xlsx");
            response.getOutputStream().write(excel);
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
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

    @PostMapping("/eliminar-masivo")
    @PreAuthorize("hasPermission(null, 'SOLICITUDES_ELIMINAR')")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> eliminarMasivo(@RequestBody SolicitudMasivaRequest request) {
        List<Long> ids = limpiarIds(request != null ? request.getIds() : List.of());
        if (ids.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", "Seleccione al menos una solicitud"));
        }

        List<SolicitudProducto> solicitudes = solicitudRepository
                .findByIdInAndEstadoOrderByContadorDescUltimaSolicitudDesc(ids, "PENDIENTE");
        solicitudRepository.deleteAll(solicitudes);

        return ResponseEntity.ok(Map.of(
                "success", true,
                "eliminados", solicitudes.size()));
    }

    private List<SolicitudProducto> obtenerSolicitudesPendientes(List<Long> ids) {
        List<Long> limpios = limpiarIds(ids);
        if (limpios.isEmpty()) {
            return solicitudRepository.findByEstadoOrderByContadorDesc("PENDIENTE");
        }
        return solicitudRepository.findByIdInAndEstadoOrderByContadorDescUltimaSolicitudDesc(limpios, "PENDIENTE");
    }

    private List<Long> limpiarIds(List<Long> ids) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }

    public static class SolicitudMasivaRequest {
        private List<Long> ids = List.of();

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }
    }
}
