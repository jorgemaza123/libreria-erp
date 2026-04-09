package com.libreria.sistema.controller;

import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.dto.CotizacionDTO;
import com.libreria.sistema.service.CotizacionContratoPdfService;
import com.libreria.sistema.service.CotizacionPdfService;
import com.libreria.sistema.service.CotizacionService;
import com.libreria.sistema.service.ConfiguracionService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Controller
@RequestMapping("/cotizaciones")
@Slf4j
public class CotizacionController {

    private final CotizacionService cotizacionService;
    private final CotizacionPdfService cotizacionPdfService;
    private final CotizacionContratoPdfService cotizacionContratoPdfService;
    private final ConfiguracionService configuracionService;

    public CotizacionController(CotizacionService cotizacionService,
                                CotizacionPdfService cotizacionPdfService,
                                CotizacionContratoPdfService cotizacionContratoPdfService,
                                ConfiguracionService configuracionService) {
        this.cotizacionService = cotizacionService;
        this.cotizacionPdfService = cotizacionPdfService;
        this.cotizacionContratoPdfService = cotizacionContratoPdfService;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/nueva")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CREAR')")
    public String nueva() {
        return "cotizaciones/nueva";
    }

    @GetMapping("/editar/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    public String editar(@PathVariable Long id, Model model) {
        Cotizacion coti = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacionEdicion", coti);
        return "cotizaciones/nueva";
    }

    @GetMapping("/lista")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public String lista(@RequestParam(defaultValue = "0") int page,
                        @RequestParam(required = false) String termino,
                        @RequestParam(required = false) String estado,
                        @RequestParam(required = false) String fechaDesde,
                        @RequestParam(required = false) String fechaHasta,
                        Model model) {
        Pageable pageable = PageRequest.of(page, 15, Sort.by("id").descending());
        Page<Cotizacion> pageCoti = cotizacionService.buscarFiltrado(termino, estado, fechaDesde, fechaHasta, pageable);

        model.addAttribute("cotizaciones", pageCoti);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", pageCoti.getTotalPages());
        model.addAttribute("termino", termino);
        model.addAttribute("estado", estado);
        model.addAttribute("fechaDesde", fechaDesde);
        model.addAttribute("fechaHasta", fechaHasta);
        model.addAttribute("ordenesVinculadas", cotizacionService.obtenerOrdenesVinculadas(pageCoti.getContent()));
        model.addAttribute("kpis", cotizacionService.obtenerKPIs());

        return "cotizaciones/lista";
    }

    @PostMapping("/api/guardar")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CREAR')")
    @ResponseBody
    public ResponseEntity<?> guardar(@RequestBody CotizacionDTO dto) {
        try {
            Cotizacion c = cotizacionService.crearCotizacion(dto);
            return ResponseEntity.ok(Map.of("mensaje", "Cotización creada correctamente", "id", c.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/actualizar/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> actualizar(@PathVariable Long id, @RequestBody CotizacionDTO dto) {
        try {
            cotizacionService.actualizarCotizacion(id, dto);
            return ResponseEntity.ok(Map.of("mensaje", "Actualizado correctamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/convertir/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_CONVERTIR')")
    @ResponseBody
    public ResponseEntity<?> convertir(@PathVariable Long id, @RequestParam String tipo) {
        try {
            Long ventaId = cotizacionService.convertirAVenta(id, tipo);
            return ResponseEntity.ok(Map.of(
                    "mensaje", "Cotización convertida a Venta exitosamente",
                    "ventaId", ventaId
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/cambiar-estado/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> cambiarEstado(@PathVariable Long id, @RequestParam String estado) {
        try {
            cotizacionService.cambiarEstado(id, estado);
            return ResponseEntity.ok(Map.of("mensaje", "Estado actualizado a " + estado));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/anular/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_EDITAR')")
    @ResponseBody
    public ResponseEntity<?> anular(@PathVariable Long id) {
        try {
            cotizacionService.anularCotizacion(id);
            return ResponseEntity.ok(Map.of("mensaje", "Cotización anulada"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/kpis")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> kpis() {
        return ResponseEntity.ok(cotizacionService.obtenerKPIs());
    }

    @GetMapping("/api/rentabilidad")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> rentabilidad(@RequestParam(required = false) String desde,
                                          @RequestParam(required = false) String hasta) {
        if (desde == null || desde.isEmpty()) desde = java.time.LocalDate.now().withDayOfMonth(1).toString();
        if (hasta == null || hasta.isEmpty()) hasta = java.time.LocalDate.now().toString();
        String desdeTs = desde + " 00:00:00";
        String hastaTs = hasta + " 23:59:59";
        return ResponseEntity.ok(cotizacionService.obtenerRentabilidadServicios(desdeTs, hastaTs));
    }

    @GetMapping("/pdf/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Cotizacion cotizacion = cotizacionService.obtenerPorId(id);
        if (cotizacion == null) {
            response.sendError(404, "Cotización no encontrada");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "inline; filename=Cotizacion-" + cotizacion.getSerie() + "-" + cotizacion.getNumero() + ".pdf");

        cotizacionPdfService.generarPdf(cotizacion, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @GetMapping("/contrato-pdf/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public void descargarContratoPdf(@PathVariable Long id, HttpServletResponse response) throws Exception {
        Cotizacion cotizacion = cotizacionService.obtenerPorId(id);
        if (cotizacion == null) {
            response.sendError(404, "Cotización no encontrada");
            return;
        }

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition",
                "inline; filename=Contrato-Cotizacion-" + cotizacion.getSerie() + "-" + cotizacion.getNumero() + ".pdf");

        cotizacionContratoPdfService.generarPdf(cotizacion, response.getOutputStream());
        response.getOutputStream().flush();
    }

    @GetMapping("/imprimir/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public String imprimir(@PathVariable Long id, Model model) {
        model.addAttribute("cotizacion", cotizacionService.obtenerPorId(id));
        model.addAttribute("config", configuracionService.obtenerConfiguracion());
        return "cotizaciones/impresion";
    }

    @GetMapping("/exportar/excel")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public ResponseEntity<byte[]> exportarExcel(@RequestParam(required = false) String termino,
                                                @RequestParam(required = false) String estado,
                                                @RequestParam(required = false) String fechaDesde,
                                                @RequestParam(required = false) String fechaHasta) {
        try {
            byte[] bytes = cotizacionService.exportarExcel(termino, estado, fechaDesde, fechaHasta);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "cotizaciones.xlsx");
            return ResponseEntity.ok().headers(headers).body(bytes);
        } catch (Exception e) {
            log.error("Error exportando Excel de cotizaciones", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/detalle/{id}")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    public String verDetalle(@PathVariable Long id, Model model) {
        Cotizacion cotizacion = cotizacionService.obtenerPorId(id);
        model.addAttribute("cotizacion", cotizacion);
        return "cotizaciones/modal_detalle :: contenido";
    }

    @GetMapping("/api/categorias")
    @ResponseBody
    public ResponseEntity<?> categorias() {
        return ResponseEntity.ok(cotizacionService.obtenerCategoriasActivas());
    }

    @GetMapping("/api/tendencia")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> tendencia() {
        return ResponseEntity.ok(cotizacionService.obtenerTendenciaMensual());
    }

    @GetMapping("/api/conversion-categoria")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> conversionCategoria(@RequestParam(required = false) String desde,
                                                 @RequestParam(required = false) String hasta) {
        if (desde == null || desde.isEmpty()) desde = java.time.LocalDate.now().withDayOfMonth(1).toString();
        if (hasta == null || hasta.isEmpty()) hasta = java.time.LocalDate.now().toString();
        return ResponseEntity.ok(cotizacionService.obtenerConversionPorCategoria(desde + " 00:00:00", hasta + " 23:59:59"));
    }

    @GetMapping("/api/rentabilidad-ventas")
    @PreAuthorize("hasPermission(null, 'COTIZACIONES_VER')")
    @ResponseBody
    public ResponseEntity<?> rentabilidadVentas(@RequestParam(required = false) String desde,
                                                @RequestParam(required = false) String hasta) {
        java.time.LocalDate desdeDate = (desde != null && !desde.isEmpty())
                ? java.time.LocalDate.parse(desde) : java.time.LocalDate.now().withDayOfMonth(1);
        java.time.LocalDate hastaDate = (hasta != null && !hasta.isEmpty())
                ? java.time.LocalDate.parse(hasta) : java.time.LocalDate.now();
        return ResponseEntity.ok(cotizacionService.obtenerRentabilidadVentas(desdeDate, hastaDate));
    }
}
