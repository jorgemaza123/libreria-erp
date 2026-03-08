package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.List;
import java.util.Optional;

/**
 * Servicio para gestión de Tomas de Inventario Físico.
 * Permite auditar y corregir el stock del sistema mediante conteo físico.
 */
@Service
@Slf4j
public class TomaInventarioService {

    private final TomaInventarioRepository tomaRepository;
    private final DetalleTomaInventarioRepository detalleRepository;
    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final UsuarioRepository usuarioRepository;

    public TomaInventarioService(TomaInventarioRepository tomaRepository,
                                  DetalleTomaInventarioRepository detalleRepository,
                                  ProductoRepository productoRepository,
                                  KardexRepository kardexRepository,
                                  UsuarioRepository usuarioRepository) {
        this.tomaRepository = tomaRepository;
        this.detalleRepository = detalleRepository;
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.usuarioRepository = usuarioRepository;
    }

    // ========== CONSULTAS ==========

    /**
     * Lista todas las tomas de inventario paginadas.
     */
    public Page<TomaInventario> listarTomas(Pageable pageable) {
        return tomaRepository.findAllByOrderByFechaInicioDesc(pageable);
    }

    /**
     * Obtiene una toma por ID.
     */
    public Optional<TomaInventario> obtenerPorId(Long id) {
        return tomaRepository.findById(id);
    }

    /**
     * Obtiene una toma con todos sus detalles cargados.
     */
    public Optional<TomaInventario> obtenerConDetalles(Long id) {
        return tomaRepository.findByIdConDetalles(id);
    }

    /**
     * Lista los detalles de una toma ordenados por nombre de producto.
     */
    public List<DetalleTomaInventario> listarDetalles(Long tomaId) {
        return detalleRepository.findByTomaInventarioId(tomaId);
    }

    /**
     * Busca detalles por filtro de texto (nombre o código).
     */
    public List<DetalleTomaInventario> buscarDetalles(Long tomaId, String filtro) {
        if (filtro == null || filtro.isBlank()) {
            return listarDetalles(tomaId);
        }
        return detalleRepository.findByTomaIdAndFiltro(tomaId, filtro.trim());
    }

    /**
     * Obtiene un detalle por ID.
     */
    public Optional<DetalleTomaInventario> obtenerDetalle(Long detalleId) {
        return detalleRepository.findById(detalleId);
    }

    /**
     * Verifica si existe una toma de inventario abierta.
     */
    public boolean existeTomaAbierta() {
        return tomaRepository.existeTomaAbierta();
    }

    /**
     * Obtiene las tomas abiertas.
     */
    public List<TomaInventario> obtenerTomasAbiertas() {
        return tomaRepository.findTomasAbiertas();
    }

    // ========== OPERACIONES DE NEGOCIO ==========

    /**
     * Inicia una nueva toma de inventario.
     * Crea un snapshot del stock actual de TODOS los productos activos.
     *
     * @param observaciones Observaciones iniciales de la toma
     * @return La toma de inventario creada
     */
    @Transactional
    public TomaInventario iniciarToma(String observaciones) {
        // Verificar que no haya otra toma abierta
        if (existeTomaAbierta()) {
            throw new IllegalStateException("Ya existe una toma de inventario abierta. Debe cerrarla antes de iniciar otra.");
        }

        // Obtener usuario actual
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuario = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Crear la toma de inventario
        TomaInventario toma = new TomaInventario();
        toma.setCodigo(generarCodigo());
        toma.setUsuario(usuario);
        toma.setObservaciones(observaciones);
        toma.setEstado(TomaInventario.ESTADO_ABIERTO);

        toma = tomaRepository.save(toma);

        // Crear snapshot de todos los productos activos
        List<Producto> productosActivos = productoRepository.findByActivoTrue();
        log.info("Iniciando toma de inventario {} con {} productos", toma.getCodigo(), productosActivos.size());

        for (Producto producto : productosActivos) {
            DetalleTomaInventario detalle = new DetalleTomaInventario();
            detalle.setTomaInventario(toma);
            detalle.setProducto(producto);
            detalle.setStockSistema(producto.getStockActual() != null ? producto.getStockActual() : 0);
            detalle.setContado(false);
            detalle.setAjusteAplicado(false);

            detalleRepository.save(detalle);
        }

        log.info("Toma de inventario {} iniciada correctamente", toma.getCodigo());
        return toma;
    }

    /**
     * Genera un código único para la toma de inventario.
     * Formato: TI-YYYY-NNNN (ej: TI-2024-0001)
     */
    private String generarCodigo() {
        String prefijo = "TI-" + Year.now().getValue() + "-";
        Optional<String> ultimoCodigo = tomaRepository.findUltimoCodigo(prefijo);

        int siguiente = 1;
        if (ultimoCodigo.isPresent()) {
            try {
                String[] partes = ultimoCodigo.get().split("-");
                siguiente = Integer.parseInt(partes[2]) + 1;
            } catch (Exception e) {
                log.warn("Error parseando último código: {}", ultimoCodigo.get());
            }
        }

        return prefijo + String.format("%04d", siguiente);
    }

    /**
     * Registra el conteo físico de un producto.
     *
     * @param detalleId ID del detalle a actualizar
     * @param cantidadFisica Cantidad contada físicamente
     * @param observacion Observación opcional
     * @return El detalle actualizado
     */
    @Transactional
    public DetalleTomaInventario guardarConteo(Long detalleId, Integer cantidadFisica, String observacion) {
        DetalleTomaInventario detalle = detalleRepository.findById(detalleId)
                .orElseThrow(() -> new RuntimeException("Detalle no encontrado"));

        // Verificar que la toma esté abierta
        if (!TomaInventario.ESTADO_ABIERTO.equals(detalle.getTomaInventario().getEstado())) {
            throw new IllegalStateException("La toma de inventario ya está cerrada");
        }

        // Validar cantidad
        if (cantidadFisica == null || cantidadFisica < 0) {
            throw new IllegalArgumentException("La cantidad física debe ser mayor o igual a 0");
        }

        // Registrar el conteo
        detalle.registrarConteo(cantidadFisica);
        detalle.setObservacion(observacion);

        log.debug("Conteo registrado - Producto: {}, Sistema: {}, Físico: {}, Diferencia: {}",
                detalle.getProducto().getNombre(),
                detalle.getStockSistema(),
                cantidadFisica,
                detalle.getDiferencia());

        return detalleRepository.save(detalle);
    }

    /**
     * Guarda múltiples conteos de una vez (para guardado masivo).
     *
     * @param tomaId ID de la toma
     * @param conteos Lista de conteos (detalleId -> cantidadFisica)
     */
    @Transactional
    public void guardarConteosMasivo(Long tomaId, List<ConteoDTO> conteos) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));

        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya está cerrada");
        }

        for (ConteoDTO conteo : conteos) {
            if (conteo.getCantidadFisica() != null) {
                DetalleTomaInventario detalle = detalleRepository.findById(conteo.getDetalleId())
                        .orElse(null);
                if (detalle != null && detalle.getTomaInventario().getId().equals(tomaId)) {
                    detalle.registrarConteo(conteo.getCantidadFisica());
                    detalleRepository.save(detalle);
                }
            }
        }

        log.info("Guardados {} conteos para toma {}", conteos.size(), toma.getCodigo());
    }

    /**
     * Procesa y cierra la toma de inventario.
     * Aplica los ajustes de stock y registra movimientos en Kardex.
     *
     * @param tomaId ID de la toma a procesar
     * @return Resumen del procesamiento
     */
    @Transactional
    public ResultadoProcesamiento procesarAjuste(Long tomaId) {
        TomaInventario toma = tomaRepository.findByIdConDetalles(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));

        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya fue procesada");
        }

        // Obtener usuario que cierra
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuarioCierre = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        ResultadoProcesamiento resultado = new ResultadoProcesamiento();
        String motivoBase = "AJUSTE INVENTARIO #" + toma.getCodigo();

        log.info("Procesando toma de inventario {} con {} detalles", toma.getCodigo(), toma.getDetalles().size());

        for (DetalleTomaInventario detalle : toma.getDetalles()) {
            // Solo procesar si fue contado y tiene diferencia
            if (!detalle.getContado() || detalle.getDiferencia() == null || detalle.getDiferencia() == 0) {
                resultado.sinCambios++;
                continue;
            }

            if (detalle.getAjusteAplicado()) {
                resultado.yaAplicados++;
                continue;
            }

            Producto producto = detalle.getProducto();
            int stockAnterior = producto.getStockActual() != null ? producto.getStockActual() : 0;
            int stockNuevo = detalle.getStockFisico();
            int diferencia = detalle.getDiferencia();

            // Crear movimiento en Kardex
            Kardex kardex = new Kardex();
            kardex.setProducto(producto);
            kardex.setStockAnterior(stockAnterior);
            kardex.setStockActual(stockNuevo);
            kardex.setCantidad(Math.abs(diferencia));

            // FIX ERROR-4: tipo unificado a "AJUSTE"; el detalle queda en el motivo
            kardex.setTipo("AJUSTE");
            if (diferencia > 0) {
                kardex.setMotivo(motivoBase + " - SOBRANTE");
                resultado.sobrantes++;
                resultado.totalSobrante += diferencia;
            } else {
                kardex.setMotivo(motivoBase + " - FALTANTE");
                resultado.faltantes++;
                resultado.totalFaltante += Math.abs(diferencia);
            }

            kardexRepository.save(kardex);

            // Actualizar stock del producto
            producto.setStockActual(stockNuevo);
            productoRepository.save(producto);

            // Marcar ajuste como aplicado
            detalle.setAjusteAplicado(true);
            detalleRepository.save(detalle);

            resultado.ajustesAplicados++;

            log.debug("Ajuste aplicado - Producto: {}, Antes: {}, Después: {}, Diferencia: {}",
                    producto.getNombre(), stockAnterior, stockNuevo, diferencia);
        }

        // Marcar toma como procesada
        toma.setEstado(TomaInventario.ESTADO_PROCESADO);
        toma.setFechaCierre(LocalDateTime.now());
        toma.setUsuarioCierre(usuarioCierre);
        tomaRepository.save(toma);

        log.info("Toma {} procesada: {} ajustes aplicados, {} faltantes, {} sobrantes",
                toma.getCodigo(), resultado.ajustesAplicados, resultado.faltantes, resultado.sobrantes);

        return resultado;
    }

    /**
     * Cancela una toma de inventario sin aplicar cambios.
     *
     * @param tomaId ID de la toma a cancelar
     */
    @Transactional
    public void cancelarToma(Long tomaId) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));

        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("Solo se pueden cancelar tomas abiertas");
        }

        // Obtener usuario que cancela
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        Usuario usuarioCierre = usuarioRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        toma.setEstado(TomaInventario.ESTADO_CANCELADO);
        toma.setFechaCierre(LocalDateTime.now());
        toma.setUsuarioCierre(usuarioCierre);
        toma.setObservaciones((toma.getObservaciones() != null ? toma.getObservaciones() + " | " : "")
                + "CANCELADA por " + username);

        tomaRepository.save(toma);
        log.info("Toma de inventario {} cancelada por {}", toma.getCodigo(), username);
    }

    /**
     * Obtiene estadísticas de una toma.
     */
    public EstadisticasToma obtenerEstadisticas(Long tomaId) {
        EstadisticasToma stats = new EstadisticasToma();

        List<DetalleTomaInventario> detalles = detalleRepository.findByTomaInventarioId(tomaId);
        stats.totalProductos = detalles.size();
        stats.productosContados = (int) detalles.stream().filter(DetalleTomaInventario::getContado).count();
        stats.productosPendientes = stats.totalProductos - stats.productosContados;

        stats.productosConDiferencia = (int) detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() != 0).count();

        stats.totalFaltantes = detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() < 0)
                .mapToInt(d -> Math.abs(d.getDiferencia())).sum();

        stats.totalSobrantes = detalles.stream()
                .filter(d -> d.getDiferencia() != null && d.getDiferencia() > 0)
                .mapToInt(DetalleTomaInventario::getDiferencia).sum();

        stats.porcentajeAvance = stats.totalProductos > 0
                ? (stats.productosContados * 100.0 / stats.totalProductos) : 0;

        return stats;
    }

    // ========== DTOs INTERNOS ==========

    /**
     * DTO para recibir conteos masivos.
     */
    public static class ConteoDTO {
        private Long detalleId;
        private Integer cantidadFisica;

        public Long getDetalleId() { return detalleId; }
        public void setDetalleId(Long detalleId) { this.detalleId = detalleId; }
        public Integer getCantidadFisica() { return cantidadFisica; }
        public void setCantidadFisica(Integer cantidadFisica) { this.cantidadFisica = cantidadFisica; }
    }

    /**
     * Resultado del procesamiento de una toma.
     */
    public static class ResultadoProcesamiento {
        public int ajustesAplicados = 0;
        public int faltantes = 0;
        public int sobrantes = 0;
        public int sinCambios = 0;
        public int yaAplicados = 0;
        public int totalFaltante = 0;
        public int totalSobrante = 0;
    }

    /**
     * Estadísticas de una toma de inventario.
     */
    public static class EstadisticasToma {
        public int totalProductos = 0;
        public int productosContados = 0;
        public int productosPendientes = 0;
        public int productosConDiferencia = 0;
        public int totalFaltantes = 0;
        public int totalSobrantes = 0;
        public double porcentajeAvance = 0;
    }
}
