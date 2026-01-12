package com.libreria.sistema.service;

import com.libreria.sistema.model.Notificacion;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.NotificacionRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import com.libreria.sistema.repository.VentaRepository;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para gestionar notificaciones del sistema.
 * Genera alertas automáticas para stock bajo, créditos vencidos, incidencias, etc.
 */
@Service
@Slf4j
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final UsuarioRepository usuarioRepository;

    public NotificacionService(NotificacionRepository notificacionRepository,
                                ProductoRepository productoRepository,
                                VentaRepository ventaRepository,
                                UsuarioRepository usuarioRepository) {
        this.notificacionRepository = notificacionRepository;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ==========================================
    //        CONSULTAS DE NOTIFICACIONES
    // ==========================================

    /**
     * Obtiene las últimas notificaciones para el usuario actual
     */
    public List<Notificacion> obtenerUltimas(int limite) {
        Usuario usuario = obtenerUsuarioActual();
        if (usuario != null) {
            return notificacionRepository.findUltimasParaUsuario(usuario, PageRequest.of(0, limite));
        }
        return notificacionRepository.findNoLeidas().stream().limit(limite).toList();
    }

    /**
     * Cuenta notificaciones no leídas para el usuario actual
     */
    public long contarNoLeidas() {
        Usuario usuario = obtenerUsuarioActual();
        if (usuario != null) {
            return notificacionRepository.countNoLeidasParaUsuario(usuario);
        }
        return notificacionRepository.countByLeidaFalse();
    }

    /**
     * Obtiene todas las notificaciones paginadas
     */
    public Page<Notificacion> listarTodas(Pageable pageable) {
        return notificacionRepository.findAllOrdenadas(pageable);
    }

    /**
     * Obtiene notificaciones no resueltas ordenadas por prioridad
     */
    public List<Notificacion> listarNoResueltas() {
        return notificacionRepository.findNoResueltasOrdenadas();
    }

    /**
     * Obtiene estadísticas de notificaciones
     */
    public Map<String, Object> obtenerEstadisticas() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("noLeidas", notificacionRepository.countByLeidaFalse());
        stats.put("total", notificacionRepository.count());

        Map<String, Long> porTipo = new HashMap<>();
        for (Object[] row : notificacionRepository.contarPorTipo()) {
            porTipo.put((String) row[0], (Long) row[1]);
        }
        stats.put("porTipo", porTipo);

        return stats;
    }

    // ==========================================
    //        ACCIONES SOBRE NOTIFICACIONES
    // ==========================================

    /**
     * Marca una notificación como leída
     */
    @Transactional
    public void marcarComoLeida(Long id) {
        notificacionRepository.findById(id).ifPresent(n -> {
            n.setLeida(true);
            n.setFechaLectura(LocalDateTime.now());
            notificacionRepository.save(n);
        });
    }

    /**
     * Marca todas las notificaciones como leídas
     */
    @Transactional
    public int marcarTodasComoLeidas() {
        Usuario usuario = obtenerUsuarioActual();
        if (usuario != null) {
            return notificacionRepository.marcarTodasComoLeidas(usuario, LocalDateTime.now());
        }
        return 0;
    }

    /**
     * Marca una notificación como resuelta
     */
    @Transactional
    public void marcarComoResuelta(Long id) {
        notificacionRepository.findById(id).ifPresent(n -> {
            n.setResuelta(true);
            n.setFechaResolucion(LocalDateTime.now());
            notificacionRepository.save(n);
        });
    }

    /**
     * Marca como resuelta por entidad relacionada
     */
    @Transactional
    public void marcarResueltaPorEntidad(String tipoEntidad, Long entidadId) {
        String entidad = tipoEntidad + ":" + entidadId;
        notificacionRepository.marcarResueltaPorEntidad(entidad, LocalDateTime.now());
    }

    /**
     * Elimina una notificación
     */
    @Transactional
    public void eliminar(Long id) {
        notificacionRepository.deleteById(id);
    }

    // ==========================================
    //        CREACIÓN DE NOTIFICACIONES
    // ==========================================

    /**
     * Crea una notificación genérica
     */
    @Transactional
    public Notificacion crearNotificacion(String tipo, String titulo, String mensaje,
                                           String icono, String color, String url,
                                           String prioridad, String entidadRelacionada) {
        // Evitar duplicados
        if (entidadRelacionada != null &&
            notificacionRepository.existsByEntidadRelacionadaAndResueltaFalse(entidadRelacionada)) {
            return null;
        }

        Notificacion notif = Notificacion.builder()
                .tipo(tipo)
                .titulo(titulo)
                .mensaje(mensaje)
                .icono(icono)
                .color(color)
                .url(url)
                .prioridad(prioridad != null ? prioridad : "NORMAL")
                .entidadRelacionada(entidadRelacionada)
                .build();

        return notificacionRepository.save(notif);
    }

    /**
     * Crea notificación de stock bajo
     */
    @Transactional
    public Notificacion crearNotificacionStockBajo(Producto producto) {
        String entidad = "PRODUCTO:" + producto.getId();

        if (notificacionRepository.existsByEntidadRelacionadaAndResueltaFalse(entidad)) {
            return null;
        }

        String tipo = producto.getStockActual() <= 0 ? "STOCK_AGOTADO" : "STOCK_BAJO";
        String color = producto.getStockActual() <= 0 ? "text-danger" : "text-warning";
        String icono = producto.getStockActual() <= 0 ? "fa-times-circle" : "fa-exclamation-triangle";

        String titulo = producto.getStockActual() <= 0 ?
                "¡AGOTADO! " + producto.getNombre() :
                "Stock bajo: " + producto.getNombre();

        String mensaje = String.format("El producto '%s' tiene stock: %d (mínimo: %d)",
                producto.getNombre(),
                producto.getStockActual(),
                producto.getStockMinimo() != null ? producto.getStockMinimo() : 5);

        return crearNotificacion(tipo, titulo, mensaje, icono, color,
                "/productos", producto.getStockActual() <= 0 ? "URGENTE" : "ALTA", entidad);
    }

    /**
     * Crea notificación de crédito vencido
     */
    @Transactional
    public Notificacion crearNotificacionCreditoVencido(Venta venta) {
        String entidad = "VENTA:" + venta.getId();

        if (notificacionRepository.existsByEntidadRelacionadaAndResueltaFalse(entidad)) {
            return null;
        }

        String titulo = "Crédito vencido: " + venta.getSerie() + "-" + venta.getNumero();
        String mensaje = String.format("Cliente: %s | Saldo: S/ %.2f | Vencido desde: %s",
                venta.getClienteDenominacion(),
                venta.getSaldoPendiente(),
                venta.getFechaVencimiento());

        return crearNotificacion("CREDITO_VENCIDO", titulo, mensaje,
                "fa-clock", "text-danger", "/cobranzas", "ALTA", entidad);
    }

    /**
     * Crea notificación de incidencia
     */
    @Transactional
    public Notificacion crearNotificacionIncidencia(Long incidenciaId, String titulo, String urgencia) {
        String entidad = "INCIDENCIA:" + incidenciaId;

        if (notificacionRepository.existsByEntidadRelacionadaAndResueltaFalse(entidad)) {
            return null;
        }

        String color = "CRITICA".equals(urgencia) ? "text-danger" :
                       "ALTA".equals(urgencia) ? "text-warning" : "text-info";
        String prioridad = "CRITICA".equals(urgencia) ? "URGENTE" :
                          "ALTA".equals(urgencia) ? "ALTA" : "NORMAL";

        return crearNotificacion("INCIDENCIA", "Incidencia: " + titulo,
                "Se ha reportado una nueva incidencia que requiere atención",
                "fa-exclamation-circle", color, "/incidencias", prioridad, entidad);
    }

    /**
     * Crea notificación del sistema
     */
    @Transactional
    public Notificacion crearNotificacionSistema(String titulo, String mensaje) {
        return crearNotificacion("SISTEMA", titulo, mensaje,
                "fa-info-circle", "text-info", null, "NORMAL", null);
    }

    // ==========================================
    //        VERIFICACIONES AUTOMÁTICAS
    // ==========================================

    /**
     * Verifica y genera notificaciones de stock bajo.
     * Se ejecuta cada hora.
     */
    @Scheduled(fixedRate = 3600000) // Cada hora
    @Transactional
    public void verificarStockBajo() {
        log.debug("Verificando productos con stock bajo...");

        List<Producto> productosBajos = productoRepository.obtenerStockCritico();

        for (Producto p : productosBajos) {
            crearNotificacionStockBajo(p);
        }

        // Resolver notificaciones de productos que ya tienen stock
        List<Notificacion> notifStock = notificacionRepository.findNotificacionesStock();
        for (Notificacion n : notifStock) {
            if (n.getEntidadRelacionada() != null && n.getEntidadRelacionada().startsWith("PRODUCTO:")) {
                Long prodId = Long.parseLong(n.getEntidadRelacionada().split(":")[1]);
                productoRepository.findById(prodId).ifPresent(prod -> {
                    int stockMin = prod.getStockMinimo() != null ? prod.getStockMinimo() : 5;
                    if (prod.getStockActual() > stockMin) {
                        n.setResuelta(true);
                        n.setFechaResolucion(LocalDateTime.now());
                        notificacionRepository.save(n);
                        log.info("Notificación de stock resuelta para producto: {}", prod.getNombre());
                    }
                });
            }
        }

        log.debug("Verificación de stock completada. Productos con stock bajo: {}", productosBajos.size());
    }

    /**
     * Verifica y genera notificaciones de créditos vencidos.
     * Se ejecuta cada 6 horas.
     */
    @Scheduled(fixedRate = 21600000) // Cada 6 horas
    @Transactional
    public void verificarCreditosVencidos() {
        log.debug("Verificando créditos vencidos...");

        List<Venta> creditosVencidos = ventaRepository.findVentasCreditoVencidas(LocalDate.now());

        for (Venta v : creditosVencidos) {
            crearNotificacionCreditoVencido(v);
        }

        log.debug("Verificación de créditos completada. Créditos vencidos: {}", creditosVencidos.size());
    }

    /**
     * Limpia notificaciones resueltas antiguas (más de 30 días).
     * Se ejecuta diariamente.
     */
    @Scheduled(cron = "0 0 3 * * ?") // 3am cada día
    @Transactional
    public void limpiarNotificacionesAntiguas() {
        LocalDateTime fechaLimite = LocalDateTime.now().minusDays(30);
        int eliminadas = notificacionRepository.eliminarAntiguasResueltas(fechaLimite);
        if (eliminadas > 0) {
            log.info("Limpieza de notificaciones: {} eliminadas", eliminadas);
        }
    }

    // ==========================================
    //        UTILIDADES
    // ==========================================

    private Usuario obtenerUsuarioActual() {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            return usuarioRepository.findByUsername(username).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
