package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.ReporteProblema;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.ReporteProblemaRepository;
import com.libreria.sistema.repository.UsuarioRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para gestionar reportes de problemas/incidencias en la tienda.
 */
@Service
@Slf4j
public class ReporteProblemaService {

    private final ReporteProblemaRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final ProductoRepository productoRepository;

    public ReporteProblemaService(ReporteProblemaRepository reporteRepository,
                                   UsuarioRepository usuarioRepository,
                                   ProductoRepository productoRepository) {
        this.reporteRepository = reporteRepository;
        this.usuarioRepository = usuarioRepository;
        this.productoRepository = productoRepository;
    }

    /**
     * Crea un nuevo reporte de problema
     */
    @Transactional
    public ReporteProblema crearReporte(String tipoProblema, String titulo, String descripcion,
                                         Long productoId, String ubicacion, String urgencia,
                                         Integer cantidadAfectada, String imagenBase64) {

        // Obtener usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        ReporteProblema reporte = new ReporteProblema();
        reporte.setTipoProblema(tipoProblema);
        reporte.setTitulo(titulo);
        reporte.setDescripcion(descripcion);
        reporte.setUbicacion(ubicacion);
        reporte.setUrgencia(urgencia != null ? urgencia : "MEDIA");
        reporte.setEstado("PENDIENTE");
        reporte.setUsuarioReporta(usuario);
        reporte.setCantidadAfectada(cantidadAfectada);
        reporte.setImagenBase64(imagenBase64);

        // Si hay producto relacionado
        if (productoId != null) {
            Producto producto = productoRepository.findById(productoId).orElse(null);
            reporte.setProducto(producto);
        }

        ReporteProblema guardado = reporteRepository.save(reporte);

        log.info("Nuevo reporte de problema creado. ID: {}, Tipo: {}, Usuario: {}",
                guardado.getId(), tipoProblema, username);

        return guardado;
    }

    /**
     * Obtiene todos los reportes paginados
     */
    public Page<ReporteProblema> listarTodos(Pageable pageable) {
        return reporteRepository.findAll(pageable);
    }

    /**
     * Obtiene reportes por estado
     */
    public List<ReporteProblema> listarPorEstado(String estado) {
        return reporteRepository.findByEstadoOrderByFechaReporteDesc(estado);
    }

    /**
     * Obtiene reportes pendientes ordenados por urgencia
     */
    public List<ReporteProblema> listarPendientesUrgentes() {
        return reporteRepository.findPendientesOrdenadosPorUrgencia();
    }

    /**
     * Obtiene un reporte por ID
     */
    public Optional<ReporteProblema> obtenerPorId(Long id) {
        return reporteRepository.findById(id);
    }

    /**
     * Actualiza el estado de un reporte
     */
    @Transactional
    public ReporteProblema actualizarEstado(Long id, String nuevoEstado, String comentario, String accionTomada) {
        ReporteProblema reporte = reporteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reporte no encontrado"));

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuarioAtiende = usuarioRepository.findByUsername(username).orElse(null);

        reporte.setEstado(nuevoEstado);
        reporte.setComentarioResolucion(comentario);
        reporte.setUsuarioAtiende(usuarioAtiende);

        if (accionTomada != null && !accionTomada.isEmpty()) {
            reporte.setAccionTomada(accionTomada);
        }

        if ("RESUELTO".equals(nuevoEstado) || "DESCARTADO".equals(nuevoEstado)) {
            reporte.setFechaResolucion(LocalDateTime.now());
        }

        log.info("Reporte {} actualizado a estado: {} por usuario: {}", id, nuevoEstado, username);

        return reporteRepository.save(reporte);
    }

    /**
     * Obtiene estadísticas de reportes
     */
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();

        // Conteo por estado
        stats.put("pendientes", reporteRepository.countByEstado("PENDIENTE"));
        stats.put("enRevision", reporteRepository.countByEstado("EN_REVISION"));
        stats.put("resueltos", reporteRepository.countByEstado("RESUELTO"));
        stats.put("descartados", reporteRepository.countByEstado("DESCARTADO"));
        stats.put("total", reporteRepository.count());

        // Conteo por tipo
        Map<String, Long> porTipo = new HashMap<>();
        for (Object[] row : reporteRepository.contarPorTipo()) {
            porTipo.put((String) row[0], (Long) row[1]);
        }
        stats.put("porTipo", porTipo);

        return stats;
    }

    /**
     * Obtiene reportes del usuario actual
     */
    public List<ReporteProblema> listarMisReportes() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username).orElse(null);
        if (usuario == null) return List.of();
        return reporteRepository.findByUsuarioReportaOrderByFechaReporteDesc(usuario);
    }

    /**
     * Elimina un reporte (solo admin)
     */
    @Transactional
    public void eliminar(Long id) {
        reporteRepository.deleteById(id);
        log.info("Reporte {} eliminado", id);
    }

    /**
     * Obtiene los tipos de problemas disponibles
     */
    public Map<String, String> obtenerTiposProblema() {
        Map<String, String> tipos = new HashMap<>();
        tipos.put("PRODUCTO_DANADO", "Producto Dañado");
        tipos.put("PRODUCTO_DEFECTUOSO", "Producto Defectuoso");
        tipos.put("PRODUCTO_VENCIDO", "Producto Vencido");
        tipos.put("FALTANTE", "Faltante de Inventario");
        tipos.put("ROBO_PERDIDA", "Robo o Pérdida");
        tipos.put("INFRAESTRUCTURA", "Problema de Infraestructura");
        tipos.put("EQUIPO", "Problema con Equipo/Maquinaria");
        tipos.put("SEGURIDAD", "Problema de Seguridad");
        tipos.put("OTRO", "Otro");
        return tipos;
    }

    /**
     * Obtiene los niveles de urgencia
     */
    public Map<String, String> obtenerNivelesUrgencia() {
        Map<String, String> niveles = new HashMap<>();
        niveles.put("BAJA", "Baja - Puede esperar");
        niveles.put("MEDIA", "Media - Atender pronto");
        niveles.put("ALTA", "Alta - Atender hoy");
        niveles.put("CRITICA", "Crítica - Atender inmediatamente");
        return niveles;
    }

    /**
     * Obtiene las acciones posibles
     */
    public Map<String, String> obtenerAcciones() {
        Map<String, String> acciones = new HashMap<>();
        acciones.put("DESCUENTO", "Aplicar descuento y vender");
        acciones.put("DEVOLUCION_PROVEEDOR", "Devolver al proveedor");
        acciones.put("BAJA_INVENTARIO", "Dar de baja del inventario");
        acciones.put("REPARACION", "Enviar a reparación");
        acciones.put("REPOSICION", "Solicitar reposición");
        acciones.put("OTRO", "Otra acción");
        return acciones;
    }
}
