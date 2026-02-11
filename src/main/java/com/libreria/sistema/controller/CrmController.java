package com.libreria.sistema.controller;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;
import com.libreria.sistema.service.CrmService;
import com.libreria.sistema.service.RolePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Controller
@RequestMapping("/crm")
@Slf4j
public class CrmController {

    private final CrmService crmService;
    private final RolePermissionService permissionService;
    private final ClienteRepository clienteRepository;
    private final OportunidadCRMRepository oportunidadRepository;
    private final ActividadCRMRepository actividadRepository;
    private final UsuarioRepository usuarioRepository;

    public CrmController(CrmService crmService,
                          RolePermissionService permissionService,
                          ClienteRepository clienteRepository,
                          OportunidadCRMRepository oportunidadRepository,
                          ActividadCRMRepository actividadRepository,
                          UsuarioRepository usuarioRepository) {
        this.crmService = crmService;
        this.permissionService = permissionService;
        this.clienteRepository = clienteRepository;
        this.oportunidadRepository = oportunidadRepository;
        this.actividadRepository = actividadRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // =====================================================
    //  DASHBOARD CRM
    // =====================================================

    @GetMapping
    public String dashboard(Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return "redirect:/?error=sin_permiso";
        }
        Map<String, Object> stats = crmService.obtenerDashboardStats();
        model.addAttribute("stats", stats);
        model.addAttribute("active", "crm_dashboard");
        return "crm/dashboard";
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> dashboardApi(Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(crmService.obtenerDashboardStats());
    }

    // =====================================================
    //  PIPELINE KANBAN
    // =====================================================

    @GetMapping("/pipeline")
    public String pipeline(Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_PIPELINE")) {
            return "redirect:/crm?error=sin_permiso";
        }
        Map<String, List<OportunidadCRM>> pipeline = crmService.obtenerPipelineAgrupado();
        model.addAttribute("pipeline", pipeline);
        model.addAttribute("clientes", clienteRepository.findAllParaSelect());
        model.addAttribute("vendedores", crmService.obtenerVendedores());
        model.addAttribute("active", "crm_pipeline");
        return "crm/pipeline";
    }

    @PostMapping("/oportunidades/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarOportunidad(@RequestBody Map<String, Object> datos, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_CREAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            OportunidadCRM oportunidad = new OportunidadCRM();

            Long idExistente = datos.get("id") != null && !datos.get("id").toString().isEmpty()
                    ? Long.parseLong(datos.get("id").toString()) : null;
            if (idExistente != null) {
                oportunidad = oportunidadRepository.findById(idExistente)
                        .orElseThrow(() -> new RuntimeException("Oportunidad no encontrada"));
            }

            oportunidad.setTitulo((String) datos.get("titulo"));

            Long clienteId = Long.parseLong(datos.get("clienteId").toString());
            oportunidad.setCliente(clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado")));

            Long vendedorId = Long.parseLong(datos.get("vendedorId").toString());
            oportunidad.setVendedor(usuarioRepository.findById(vendedorId)
                    .orElseThrow(() -> new RuntimeException("Vendedor no encontrado")));

            if (datos.get("valorEstimado") != null && !datos.get("valorEstimado").toString().isEmpty()) {
                oportunidad.setValorEstimado(new BigDecimal(datos.get("valorEstimado").toString()));
            }

            if (datos.get("fechaCierreEstimada") != null && !datos.get("fechaCierreEstimada").toString().isEmpty()) {
                oportunidad.setFechaCierreEstimada(LocalDate.parse(datos.get("fechaCierreEstimada").toString()));
            }

            oportunidad.setNotas((String) datos.getOrDefault("notas", null));

            if (idExistente == null) {
                oportunidad.setEtapa("NUEVO");
            }

            OportunidadCRM saved = crmService.crearOportunidad(oportunidad);
            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception e) {
            log.error("Error al guardar oportunidad: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/oportunidades/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerOportunidad(@PathVariable Long id, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return ResponseEntity.status(403).build();
        }
        return oportunidadRepository.findById(id)
                .map(o -> {
                    Map<String, Object> data = new HashMap<>();
                    data.put("id", o.getId());
                    data.put("titulo", o.getTitulo());
                    data.put("etapa", o.getEtapa());
                    data.put("valorEstimado", o.getValorEstimado());
                    data.put("valorReal", o.getValorReal());
                    data.put("notas", o.getNotas());
                    data.put("motivoPerdida", o.getMotivoPerdida());
                    data.put("fechaCierreEstimada", o.getFechaCierreEstimada());
                    data.put("fechaCreacion", o.getFechaCreacion());
                    data.put("clienteId", o.getCliente() != null ? o.getCliente().getId() : null);
                    data.put("clienteNombre", o.getCliente() != null ? o.getCliente().getNombreRazonSocial() : "");
                    data.put("vendedorId", o.getVendedor() != null ? o.getVendedor().getId() : null);
                    data.put("vendedorNombre", o.getVendedor() != null ? o.getVendedor().getNombreCompleto() : "");
                    return ResponseEntity.ok(data);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/oportunidades/{id}/etapa")
    @ResponseBody
    public ResponseEntity<?> cambiarEtapa(@PathVariable Long id,
                                           @RequestBody Map<String, Object> datos,
                                           Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_PIPELINE")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            String etapa = (String) datos.get("etapa");
            Integer orden = datos.get("orden") != null ? Integer.parseInt(datos.get("orden").toString()) : null;
            crmService.cambiarEtapa(id, etapa, orden);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/oportunidades/{id}/ganada")
    @ResponseBody
    public ResponseEntity<?> marcarGanada(@PathVariable Long id,
                                           @RequestBody Map<String, Object> datos,
                                           Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_EDITAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            BigDecimal valorReal = datos.get("valorReal") != null && !datos.get("valorReal").toString().isEmpty()
                    ? new BigDecimal(datos.get("valorReal").toString()) : null;
            crmService.marcarGanada(id, valorReal);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/oportunidades/{id}/perdida")
    @ResponseBody
    public ResponseEntity<?> marcarPerdida(@PathVariable Long id,
                                            @RequestBody Map<String, Object> datos,
                                            Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_EDITAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            String motivo = (String) datos.getOrDefault("motivo", "No especificado");
            crmService.marcarPerdida(id, motivo);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =====================================================
    //  LEADS
    // =====================================================

    @GetMapping("/leads")
    public String leads(@RequestParam(defaultValue = "") String buscar,
                         @RequestParam(defaultValue = "0") int page,
                         Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return "redirect:/crm?error=sin_permiso";
        }
        Page<Cliente> leads = crmService.buscarLeads(buscar, PageRequest.of(page, 20));
        model.addAttribute("leads", leads);
        model.addAttribute("buscar", buscar);
        model.addAttribute("vendedores", crmService.obtenerVendedores());
        model.addAttribute("active", "crm_leads");
        return "crm/leads";
    }

    @PostMapping("/leads/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarLead(@RequestBody Map<String, Object> datos, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_CREAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            Cliente lead = new Cliente();

            Long idExistente = datos.get("id") != null && !datos.get("id").toString().isEmpty()
                    ? Long.parseLong(datos.get("id").toString()) : null;
            if (idExistente != null) {
                lead = clienteRepository.findById(idExistente)
                        .orElseThrow(() -> new RuntimeException("Lead no encontrado"));
            }

            lead.setNombreRazonSocial((String) datos.get("nombreRazonSocial"));
            lead.setTipoDocumento(datos.getOrDefault("tipoDocumento", "0").toString());
            lead.setNumeroDocumento((String) datos.getOrDefault("numeroDocumento", "00000000"));
            lead.setTelefono((String) datos.getOrDefault("telefono", null));
            lead.setEmail((String) datos.getOrDefault("email", null));
            lead.setDireccion((String) datos.getOrDefault("direccion", null));
            lead.setEmpresa((String) datos.getOrDefault("empresa", null));
            lead.setCargo((String) datos.getOrDefault("cargo", null));
            lead.setOrigenLead((String) datos.getOrDefault("origenLead", null));
            lead.setEtiquetas((String) datos.getOrDefault("etiquetas", null));
            lead.setObservaciones((String) datos.getOrDefault("observaciones", null));

            if (datos.get("vendedorAsignadoId") != null && !datos.get("vendedorAsignadoId").toString().isEmpty()) {
                Long vendedorId = Long.parseLong(datos.get("vendedorAsignadoId").toString());
                lead.setVendedorAsignado(usuarioRepository.findById(vendedorId).orElse(null));
            }

            if (datos.get("fechaProximoContacto") != null && !datos.get("fechaProximoContacto").toString().isEmpty()) {
                lead.setFechaProximoContacto(LocalDate.parse(datos.get("fechaProximoContacto").toString()));
            }

            Cliente saved = crmService.crearLead(lead);
            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception e) {
            log.error("Error al guardar lead: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/leads/{id}/convertir")
    @ResponseBody
    public ResponseEntity<?> convertirLead(@PathVariable Long id, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_EDITAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            crmService.convertirLeadACliente(id);
            return ResponseEntity.ok(Map.of("success", true, "mensaje", "Lead convertido a cliente exitosamente"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =====================================================
    //  PERFIL CLIENTE
    // =====================================================

    @GetMapping("/clientes/{id}/perfil")
    public String perfilCliente(@PathVariable Long id, Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return "redirect:/crm?error=sin_permiso";
        }
        Map<String, Object> perfil = crmService.obtenerPerfilCompleto(id);
        model.addAttribute("cliente", perfil.get("cliente"));
        model.addAttribute("actividades", perfil.get("actividades"));
        model.addAttribute("oportunidades", perfil.get("oportunidades"));
        model.addAttribute("vendedores", crmService.obtenerVendedores());
        model.addAttribute("active", "crm_leads");
        return "crm/perfil-cliente";
    }

    @GetMapping("/api/timeline/{clienteId}")
    @ResponseBody
    public ResponseEntity<?> timeline(@PathVariable Long clienteId, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(crmService.obtenerTimeline(clienteId));
    }

    // =====================================================
    //  ACTIVIDADES
    // =====================================================

    @GetMapping("/actividades")
    public String actividades(Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return "redirect:/crm?error=sin_permiso";
        }
        List<ActividadCRM> pendientes = crmService.obtenerTodasTareasPendientes();
        model.addAttribute("pendientes", pendientes);
        model.addAttribute("clientes", clienteRepository.findAllParaSelect());
        model.addAttribute("active", "crm_actividades");
        return "crm/actividades";
    }

    @PostMapping("/actividades/guardar")
    @ResponseBody
    public ResponseEntity<?> guardarActividad(@RequestBody Map<String, Object> datos, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_CREAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            ActividadCRM actividad = new ActividadCRM();
            actividad.setTipo((String) datos.get("tipo"));
            actividad.setAsunto((String) datos.get("asunto"));
            actividad.setDescripcion((String) datos.getOrDefault("descripcion", null));

            Long clienteId = Long.parseLong(datos.get("clienteId").toString());
            actividad.setCliente(clienteRepository.findById(clienteId)
                    .orElseThrow(() -> new RuntimeException("Cliente no encontrado")));

            if (datos.get("oportunidadId") != null && !datos.get("oportunidadId").toString().isEmpty()) {
                Long oportunidadId = Long.parseLong(datos.get("oportunidadId").toString());
                actividad.setOportunidad(oportunidadRepository.findById(oportunidadId).orElse(null));
            }

            if (datos.get("fechaProgramada") != null && !datos.get("fechaProgramada").toString().isEmpty()) {
                actividad.setFechaProgramada(LocalDateTime.parse(datos.get("fechaProgramada").toString()));
            }

            if (datos.get("duracionMinutos") != null && !datos.get("duracionMinutos").toString().isEmpty()) {
                actividad.setDuracionMinutos(Integer.parseInt(datos.get("duracionMinutos").toString()));
            }

            if (datos.get("asignadoId") != null && !datos.get("asignadoId").toString().isEmpty()) {
                Long asignadoId = Long.parseLong(datos.get("asignadoId").toString());
                actividad.setAsignado(usuarioRepository.findById(asignadoId).orElse(null));
            }

            ActividadCRM saved = crmService.registrarActividad(actividad);
            return ResponseEntity.ok(Map.of("success", true, "id", saved.getId()));
        } catch (Exception e) {
            log.error("Error al guardar actividad: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/actividades/{id}/completar")
    @ResponseBody
    public ResponseEntity<?> completarActividad(@PathVariable Long id,
                                                 @RequestBody(required = false) Map<String, Object> datos,
                                                 Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_EDITAR")) {
            return ResponseEntity.status(403).body(Map.of("error", "Sin permiso"));
        }
        try {
            String resultado = datos != null ? (String) datos.getOrDefault("resultado", null) : null;
            crmService.completarActividad(id, resultado);
            return ResponseEntity.ok(Map.of("success", true));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =====================================================
    //  SEGMENTACION
    // =====================================================

    @GetMapping("/segmentacion")
    public String segmentacion(Model model, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return "redirect:/crm?error=sin_permiso";
        }
        model.addAttribute("etiquetas", crmService.obtenerTodasLasEtiquetas());
        model.addAttribute("active", "crm_segmentacion");
        return "crm/segmentacion";
    }

    @PostMapping("/segmentacion/filtrar")
    @ResponseBody
    public ResponseEntity<?> filtrarSegmentacion(@RequestBody Map<String, Object> filtros, Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return ResponseEntity.status(403).build();
        }
        try {
            String tipo = (String) filtros.getOrDefault("tipo", null);
            String categoria = (String) filtros.getOrDefault("categoria", null);
            String etiqueta = (String) filtros.getOrDefault("etiqueta", null);
            String origen = (String) filtros.getOrDefault("origen", null);
            Boolean conCompras = filtros.get("conCompras") != null ? Boolean.parseBoolean(filtros.get("conCompras").toString()) : null;
            int page = filtros.get("page") != null ? Integer.parseInt(filtros.get("page").toString()) : 0;

            if (tipo != null && tipo.isEmpty()) tipo = null;
            if (categoria != null && categoria.isEmpty()) categoria = null;
            if (etiqueta != null && etiqueta.isEmpty()) etiqueta = null;
            if (origen != null && origen.isEmpty()) origen = null;

            Page<Cliente> resultados = crmService.segmentarClientes(tipo, categoria, etiqueta, origen, conCompras, PageRequest.of(page, 50));

            List<Map<String, Object>> clientesList = new ArrayList<>();
            for (Cliente c : resultados.getContent()) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", c.getId());
                item.put("nombreRazonSocial", c.getNombreRazonSocial());
                item.put("numeroDocumento", c.getNumeroDocumento());
                item.put("tipo", c.getTipo());
                item.put("categoria", c.getCategoria());
                item.put("telefono", c.getTelefono());
                item.put("email", c.getEmail());
                item.put("etiquetas", c.getEtiquetas());
                item.put("totalComprasHistorico", c.getTotalComprasHistorico());
                item.put("cantidadCompras", c.getCantidadCompras());
                item.put("origenLead", c.getOrigenLead());
                clientesList.add(item);
            }

            return ResponseEntity.ok(Map.of(
                    "clientes", clientesList,
                    "totalElements", resultados.getTotalElements(),
                    "totalPages", resultados.getTotalPages(),
                    "currentPage", resultados.getNumber()
            ));
        } catch (Exception e) {
            log.error("Error en segmentacion: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // =====================================================
    //  API ETIQUETAS
    // =====================================================

    @GetMapping("/api/etiquetas")
    @ResponseBody
    public ResponseEntity<?> etiquetas(Authentication auth) {
        if (!permissionService.tienePermiso(auth.getName(), "CRM_VER")) {
            return ResponseEntity.status(403).build();
        }
        return ResponseEntity.ok(crmService.obtenerTodasLasEtiquetas());
    }
}
