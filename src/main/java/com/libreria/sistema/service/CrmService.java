package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
public class CrmService {

    private final ClienteRepository clienteRepository;
    private final OportunidadCRMRepository oportunidadRepository;
    private final ActividadCRMRepository actividadRepository;
    private final UsuarioRepository usuarioRepository;
    private final VentaRepository ventaRepository;
    private final NotificacionRepository notificacionRepository;

    public CrmService(ClienteRepository clienteRepository,
                      OportunidadCRMRepository oportunidadRepository,
                      ActividadCRMRepository actividadRepository,
                      UsuarioRepository usuarioRepository,
                      VentaRepository ventaRepository,
                      NotificacionRepository notificacionRepository) {
        this.clienteRepository = clienteRepository;
        this.oportunidadRepository = oportunidadRepository;
        this.actividadRepository = actividadRepository;
        this.usuarioRepository = usuarioRepository;
        this.ventaRepository = ventaRepository;
        this.notificacionRepository = notificacionRepository;
    }

    // =====================================================
    //  DASHBOARD CRM
    // =====================================================

    public Map<String, Object> obtenerDashboardStats() {
        Map<String, Object> stats = new HashMap<>();

        LocalDate hoy = LocalDate.now();
        LocalDate inicioMes = hoy.withDayOfMonth(1);

        // Leads
        long totalLeads = clienteRepository.countByTipoAndActivoTrue("LEAD");
        long totalClientes = clienteRepository.countByTipoAndActivoTrue("CLIENTE");
        stats.put("totalLeads", totalLeads);
        stats.put("totalClientes", totalClientes);

        // Pipeline
        long oportunidadesActivas = oportunidadRepository.countActivas();
        BigDecimal valorPipeline = oportunidadRepository.sumarValorPipeline();
        stats.put("oportunidadesActivas", oportunidadesActivas);
        stats.put("valorPipeline", valorPipeline);

        // Ganadas este mes
        long ganadasMes = oportunidadRepository.countGanadasEnPeriodo(inicioMes, hoy);
        BigDecimal valorGanadasMes = oportunidadRepository.sumarGanadasEnPeriodo(inicioMes, hoy);
        stats.put("ganadasMes", ganadasMes);
        stats.put("valorGanadasMes", valorGanadasMes);

        // Tasa de conversion
        long perdidasMes = oportunidadRepository.countPerdidasEnPeriodo(inicioMes, hoy);
        long totalCerradasMes = ganadasMes + perdidasMes;
        double tasaConversion = totalCerradasMes > 0 ? (double) ganadasMes / totalCerradasMes * 100 : 0;
        stats.put("tasaConversion", BigDecimal.valueOf(tasaConversion).setScale(1, RoundingMode.HALF_UP));

        // Actividades pendientes
        long actividadesPendientes = actividadRepository.countPendientes();
        long actividadesVencidas = actividadRepository.countVencidas(LocalDateTime.now());
        stats.put("actividadesPendientes", actividadesPendientes);
        stats.put("actividadesVencidas", actividadesVencidas);

        // Estadisticas por etapa del pipeline
        List<Object[]> statsPorEtapa = oportunidadRepository.estadisticasPorEtapa();
        stats.put("estadisticasPorEtapa", statsPorEtapa);

        // Leads por origen
        List<Object[]> leadsPorOrigen = clienteRepository.contarLeadsPorOrigen();
        stats.put("leadsPorOrigen", leadsPorOrigen);

        return stats;
    }

    // =====================================================
    //  LEADS
    // =====================================================

    public Page<Cliente> buscarLeads(String termino, Pageable pageable) {
        return clienteRepository.buscarPorTipoPaginado("LEAD", termino, pageable);
    }

    @Transactional
    public Cliente crearLead(Cliente lead) {
        lead.setTipo("LEAD");
        if (lead.getCategoria() == null) lead.setCategoria("NUEVO");
        return clienteRepository.save(lead);
    }

    @Transactional
    public Cliente convertirLeadACliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Lead no encontrado"));

        if (!"LEAD".equals(cliente.getTipo())) {
            throw new RuntimeException("Este registro ya es un cliente");
        }

        cliente.setTipo("CLIENTE");
        cliente.setCategoria("NUEVO");
        clienteRepository.save(cliente);

        // Registrar actividad
        Usuario usuario = obtenerUsuarioActual();
        ActividadCRM actividad = new ActividadCRM();
        actividad.setTipo("NOTA");
        actividad.setAsunto("Lead convertido a cliente");
        actividad.setDescripcion("El lead fue convertido a cliente por " + usuario.getNombreCompleto());
        actividad.setCliente(cliente);
        actividad.setUsuario(usuario);
        actividad.setEstado("COMPLETADA");
        actividad.setFechaCompletada(LocalDateTime.now());
        actividadRepository.save(actividad);

        log.info("Lead {} convertido a cliente por {}", clienteId, usuario.getUsername());
        return cliente;
    }

    // =====================================================
    //  OPORTUNIDADES
    // =====================================================

    public List<OportunidadCRM> obtenerPipeline() {
        return oportunidadRepository.findActivasConRelaciones();
    }

    public Map<String, List<OportunidadCRM>> obtenerPipelineAgrupado() {
        List<OportunidadCRM> todas = oportunidadRepository.findActivasConRelaciones();
        Map<String, List<OportunidadCRM>> agrupado = new LinkedHashMap<>();
        agrupado.put("NUEVO", new ArrayList<>());
        agrupado.put("CONTACTADO", new ArrayList<>());
        agrupado.put("COTIZADO", new ArrayList<>());
        agrupado.put("NEGOCIACION", new ArrayList<>());

        for (OportunidadCRM o : todas) {
            String etapa = o.getEtapa();
            if (agrupado.containsKey(etapa)) {
                agrupado.get(etapa).add(o);
            }
        }
        return agrupado;
    }

    @Transactional
    public OportunidadCRM crearOportunidad(OportunidadCRM oportunidad) {
        if (oportunidad.getVendedor() == null) {
            oportunidad.setVendedor(obtenerUsuarioActual());
        }
        return oportunidadRepository.save(oportunidad);
    }

    @Transactional
    public OportunidadCRM cambiarEtapa(Long oportunidadId, String nuevaEtapa, Integer orden) {
        OportunidadCRM oportunidad = oportunidadRepository.findById(oportunidadId)
                .orElseThrow(() -> new RuntimeException("Oportunidad no encontrada"));

        oportunidad.setEtapa(nuevaEtapa);
        if (orden != null) {
            oportunidad.setOrden(orden);
        }
        return oportunidadRepository.save(oportunidad);
    }

    @Transactional
    public OportunidadCRM marcarGanada(Long oportunidadId, BigDecimal valorReal) {
        OportunidadCRM oportunidad = oportunidadRepository.findById(oportunidadId)
                .orElseThrow(() -> new RuntimeException("Oportunidad no encontrada"));

        oportunidad.setEtapa("GANADO");
        oportunidad.setFechaCierreReal(LocalDate.now());
        if (valorReal != null) {
            oportunidad.setValorReal(valorReal);
        } else {
            oportunidad.setValorReal(oportunidad.getValorEstimado());
        }

        return oportunidadRepository.save(oportunidad);
    }

    @Transactional
    public OportunidadCRM marcarPerdida(Long oportunidadId, String motivo) {
        OportunidadCRM oportunidad = oportunidadRepository.findById(oportunidadId)
                .orElseThrow(() -> new RuntimeException("Oportunidad no encontrada"));

        oportunidad.setEtapa("PERDIDO");
        oportunidad.setFechaCierreReal(LocalDate.now());
        oportunidad.setMotivoPerdida(motivo);

        return oportunidadRepository.save(oportunidad);
    }

    public List<OportunidadCRM> obtenerOportunidadesCliente(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        return oportunidadRepository.findByClienteOrderByFechaCreacionDesc(cliente);
    }

    // =====================================================
    //  ACTIVIDADES
    // =====================================================

    @Transactional
    public ActividadCRM registrarActividad(ActividadCRM actividad) {
        if (actividad.getUsuario() == null) {
            actividad.setUsuario(obtenerUsuarioActual());
        }
        return actividadRepository.save(actividad);
    }

    @Transactional
    public ActividadCRM completarActividad(Long actividadId, String resultado) {
        ActividadCRM actividad = actividadRepository.findById(actividadId)
                .orElseThrow(() -> new RuntimeException("Actividad no encontrada"));

        actividad.setEstado("COMPLETADA");
        actividad.setFechaCompletada(LocalDateTime.now());
        if (resultado != null && !resultado.isEmpty()) {
            actividad.setResultado(resultado);
        }
        return actividadRepository.save(actividad);
    }

    public List<ActividadCRM> obtenerMisTareasPendientes() {
        Usuario usuario = obtenerUsuarioActual();
        return actividadRepository.findTareasPendientesDeUsuario(usuario);
    }

    public List<ActividadCRM> obtenerTodasTareasPendientes() {
        return actividadRepository.findTodasPendientes();
    }

    // =====================================================
    //  PERFIL CLIENTE - TIMELINE
    // =====================================================

    public Map<String, Object> obtenerPerfilCompleto(Long clienteId) {
        Map<String, Object> perfil = new HashMap<>();

        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));
        perfil.put("cliente", cliente);

        // Actividades CRM
        List<ActividadCRM> actividades = actividadRepository.findTimelineCliente(cliente);
        perfil.put("actividades", actividades);

        // Oportunidades
        List<OportunidadCRM> oportunidades = oportunidadRepository.findByClienteOrderByFechaCreacionDesc(cliente);
        perfil.put("oportunidades", oportunidades);

        return perfil;
    }

    public List<Map<String, Object>> obtenerTimeline(Long clienteId) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new RuntimeException("Cliente no encontrado"));

        List<Map<String, Object>> timeline = new ArrayList<>();

        // Actividades CRM
        List<ActividadCRM> actividades = actividadRepository.findTimelineCliente(cliente);
        for (ActividadCRM a : actividades) {
            Map<String, Object> item = new HashMap<>();
            item.put("tipo", "actividad");
            item.put("subtipo", a.getTipo());
            item.put("titulo", a.getAsunto());
            item.put("descripcion", a.getDescripcion());
            item.put("fecha", a.getFechaCreacion());
            item.put("usuario", a.getUsuario() != null ? a.getUsuario().getNombreCompleto() : "");
            item.put("estado", a.getEstado());
            timeline.add(item);
        }

        // Oportunidades
        List<OportunidadCRM> oportunidades = oportunidadRepository.findByClienteOrderByFechaCreacionDesc(cliente);
        for (OportunidadCRM o : oportunidades) {
            Map<String, Object> item = new HashMap<>();
            item.put("tipo", "oportunidad");
            item.put("subtipo", o.getEtapa());
            item.put("titulo", o.getTitulo());
            item.put("descripcion", "Valor: S/ " + (o.getValorEstimado() != null ? o.getValorEstimado() : "0"));
            item.put("fecha", o.getFechaCreacion());
            item.put("usuario", o.getVendedor() != null ? o.getVendedor().getNombreCompleto() : "");
            item.put("estado", o.getEtapa());
            timeline.add(item);
        }

        // Ordenar por fecha descendente
        timeline.sort((a, b) -> {
            LocalDateTime fa = (LocalDateTime) a.get("fecha");
            LocalDateTime fb = (LocalDateTime) b.get("fecha");
            if (fa == null && fb == null) return 0;
            if (fa == null) return 1;
            if (fb == null) return -1;
            return fb.compareTo(fa);
        });

        return timeline;
    }

    // =====================================================
    //  SEGMENTACION
    // =====================================================

    public Page<Cliente> segmentarClientes(String tipo, String categoria, String etiqueta,
                                            String origen, Boolean conCompras, Pageable pageable) {
        return clienteRepository.findClientesSegmentados(tipo, categoria, etiqueta, origen, conCompras, pageable);
    }

    // =====================================================
    //  ETIQUETAS
    // =====================================================

    public Set<String> obtenerTodasLasEtiquetas() {
        Set<String> etiquetas = new TreeSet<>();
        List<Cliente> clientes = clienteRepository.findByActivoTrue();
        for (Cliente c : clientes) {
            if (c.getEtiquetas() != null && !c.getEtiquetas().isEmpty()) {
                for (String tag : c.getEtiquetas().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) {
                        etiquetas.add(trimmed);
                    }
                }
            }
        }
        return etiquetas;
    }

    // =====================================================
    //  UTILIDADES
    // =====================================================

    private Usuario obtenerUsuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));
    }

    public Optional<OportunidadCRM> obtenerOportunidadPorId(Long id) {
        return oportunidadRepository.findById(id);
    }

    public Optional<Cliente> obtenerClientePorId(Long id) {
        return clienteRepository.findById(id);
    }

    public List<Cliente> buscarClientesParaSelect(String termino) {
        if (termino == null || termino.isEmpty()) {
            return clienteRepository.findAllParaSelect();
        }
        return clienteRepository.buscarParaAutocompletado(termino);
    }

    public List<Usuario> obtenerVendedores() {
        return usuarioRepository.findAll().stream()
                .filter(Usuario::isActivo)
                .toList();
    }
}
