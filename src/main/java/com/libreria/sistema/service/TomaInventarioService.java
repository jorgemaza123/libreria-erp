package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.model.dto.CosteoProductoDTO;
import com.libreria.sistema.repository.*;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio para gestión de Tomas de Inventario Físico.
 * Permite auditar y corregir el stock del sistema mediante conteo físico.
 */
@Service
@Slf4j
public class TomaInventarioService {

    private static final String KEY_ZONAS_RAPIDAS = "INVENTARIO_ZONAS_RAPIDAS";
    private static final String KEY_ZONAS_RAPIDAS_ESTADOS = "INVENTARIO_ZONAS_RAPIDAS_ESTADOS";
    private static final String ZONAS_DEFAULT = "MOSTRADOR,VITRINA,ALMACEN,UTILES,JUGUETES,SUBLIMACION,FLORES";
    private static final String ESTADO_ZONA_PENDIENTE = "PENDIENTE";
    private static final String ESTADO_ZONA_TERMINADA = "TERMINADA";
    private static final String ESTADO_ZONA_REVISADA = "REVISADA";
    private static final DateTimeFormatter FORMATO_FECHA_AUDITORIA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final TomaInventarioRepository tomaRepository;
    private final DetalleTomaInventarioRepository detalleRepository;
    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final UsuarioRepository usuarioRepository;
    private final SystemConfigurationService systemConfigurationService;
    private final CosteoEmpresarialService costeoEmpresarialService;
    private final HistorialPrecioProductoService historialPrecioProductoService;

    public TomaInventarioService(TomaInventarioRepository tomaRepository,
                                  DetalleTomaInventarioRepository detalleRepository,
                                  ProductoRepository productoRepository,
                                  KardexRepository kardexRepository,
                                  UsuarioRepository usuarioRepository,
                                  SystemConfigurationService systemConfigurationService,
                                  CosteoEmpresarialService costeoEmpresarialService,
                                  HistorialPrecioProductoService historialPrecioProductoService) {
        this.tomaRepository = tomaRepository;
        this.detalleRepository = detalleRepository;
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.usuarioRepository = usuarioRepository;
        this.systemConfigurationService = systemConfigurationService;
        this.costeoEmpresarialService = costeoEmpresarialService;
        this.historialPrecioProductoService = historialPrecioProductoService;
    }

    // ========== CONSULTAS ==========

    /**
     * Lista todas las tomas de inventario paginadas.
     */
    public Page<TomaInventario> listarTomas(Pageable pageable) {
        return tomaRepository.findAllByOrderByFechaInicioDesc(pageable);
    }

    public Map<Long, Long> contarProductosPorTomas(List<TomaInventario> tomas) {
        if (tomas == null || tomas.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Long> ids = tomas.stream()
                .map(TomaInventario::getId)
                .filter(id -> id != null)
                .toList();
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<Long, Long> conteos = new HashMap<>();
        for (Object[] row : detalleRepository.countDetallesByTomaIds(ids)) {
            conteos.put((Long) row[0], ((Number) row[1]).longValue());
        }
        return conteos;
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

    public List<DetalleTomaInventario> listarDetallesConDiferencia(Long tomaId) {
        return detalleRepository.findConDiferencia(tomaId);
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

    @Transactional(readOnly = true)
    public List<ConteoRapidoDTO> buscarParaConteoRapido(Long tomaId, String termino) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }

        String filtro = termino == null ? "" : termino.trim();
        List<DetalleTomaInventario> detalles = filtro.isBlank()
                ? detalleRepository.findPendientesConteo(tomaId).stream()
                        .filter(detalle -> detalle.getProducto() != null && detalle.getProducto().controlaStockInventario())
                        .limit(12).toList()
                : detalleRepository.findByTomaIdAndFiltro(tomaId, filtro).stream()
                        .filter(detalle -> detalle.getProducto() != null && detalle.getProducto().controlaStockInventario())
                        .limit(12).toList();

        Map<Long, ConteoRapidoDTO> resultados = new LinkedHashMap<>();
        for (DetalleTomaInventario detalle : detalles) {
            ConteoRapidoDTO dto = toConteoRapido(detalle);
            resultados.put(dto.productoId, dto);
        }

        if (!filtro.isBlank() && resultados.size() < 12) {
            Set<Long> agregados = new LinkedHashSet<>(resultados.keySet());
            agregarProductoExacto(filtro, agregados, resultados, tomaId);
            productoRepository.buscarStockFiltrado(
                    filtro,
                    null,
                    null,
                    LocalDate.now().minusDays(30),
                    PageRequest.of(0, 12))
                    .getContent()
                    .forEach(producto -> agregarProductoResultado(producto, agregados, resultados, tomaId));
        }

        return resultados.values().stream().limit(12).toList();
    }

    @Transactional(readOnly = true)
    public List<ConteoRapidoDTO> ultimosContados(Long tomaId) {
        return detalleRepository.findUltimosContados(tomaId, PageRequest.of(0, 8))
                .stream()
                .map(this::toConteoRapido)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ConteoRapidoDTO> listarProductosEtiquetasRegularizacion(Long tomaId) {
        return detalleRepository.findByTomaInventarioId(tomaId).stream()
                .filter(detalle -> detalle.getProducto() != null)
                .filter(detalle -> detalle.getProducto().controlaStockInventario())
                .map(this::toConteoRapido)
                .limit(800)
                .toList();
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

    public Optional<TomaInventario> obtenerTomaAbiertaActual() {
        List<TomaInventario> abiertas = obtenerTomasAbiertas();
        return abiertas.isEmpty() ? Optional.empty() : Optional.of(abiertas.get(0));
    }

    public List<String> obtenerZonasRapidas() {
        String raw = systemConfigurationService.getValue(KEY_ZONAS_RAPIDAS).orElse(ZONAS_DEFAULT);
        return normalizarListaZonas(raw);
    }

    @Transactional
    public List<String> guardarZonasRapidas(List<String> zonas) {
        List<String> limpias = normalizarListaZonas(zonas == null ? "" : String.join(",", zonas));
        if (limpias.isEmpty()) {
            limpias = normalizarListaZonas(ZONAS_DEFAULT);
        }
        systemConfigurationService.setValue(KEY_ZONAS_RAPIDAS, String.join(",", limpias),
                "Zonas configurables para regularizacion rapida de inventario");
        Map<String, String> estadosActuales = obtenerMapaEstadosZonas();
        Map<String, String> nuevosEstados = new LinkedHashMap<>();
        for (String zona : limpias) {
            nuevosEstados.put(zona, estadosActuales.getOrDefault(zona, ESTADO_ZONA_PENDIENTE));
        }
        guardarMapaEstadosZonas(nuevosEstados);
        return limpias;
    }

    @Transactional(readOnly = true)
    public List<ZonaConteoDTO> obtenerZonasRapidasConEstado(Long tomaId) {
        List<String> zonas = obtenerZonasRapidas();
        Map<String, String> estados = obtenerMapaEstadosZonas();
        Map<String, ZonaConteoDTO> out = new LinkedHashMap<>();
        for (String zona : zonas) {
            ZonaConteoDTO dto = new ZonaConteoDTO();
            dto.nombre = zona;
            dto.estado = normalizarEstadoZona(estados.get(zona));
            out.put(zona, dto);
        }

        if (tomaId != null) {
            for (DetalleTomaInventario detalle : detalleRepository.findByTomaInventarioId(tomaId)) {
                String zona = normalizarZona(detalle.getZonaConteo());
                if ("SIN ZONA".equals(zona)) {
                    continue;
                }
                ZonaConteoDTO dto = out.computeIfAbsent(zona, key -> {
                    ZonaConteoDTO nuevo = new ZonaConteoDTO();
                    nuevo.nombre = key;
                    nuevo.estado = normalizarEstadoZona(estados.get(key));
                    return nuevo;
                });
                if (Boolean.TRUE.equals(detalle.getContado())) {
                    dto.contados++;
                    dto.total = dto.contados;
                    if (detalle.getDiferencia() != null && detalle.getDiferencia() != 0) {
                        dto.diferencias++;
                    }
                }
            }
        }
        return new ArrayList<>(out.values());
    }

    @Transactional
    public List<ZonaConteoDTO> guardarEstadoZonaRapida(String zona, String estado) {
        String zonaLimpia = normalizarZona(zona);
        if ("SIN ZONA".equals(zonaLimpia)) {
            throw new IllegalArgumentException("Selecciona una zona valida.");
        }
        String estadoLimpio = normalizarEstadoZona(estado);
        List<String> zonas = obtenerZonasRapidas();
        if (!zonas.contains(zonaLimpia)) {
            zonas = new ArrayList<>(zonas);
            zonas.add(zonaLimpia);
            systemConfigurationService.setValue(KEY_ZONAS_RAPIDAS, String.join(",", zonas),
                    "Zonas configurables para regularizacion rapida de inventario");
        }
        Map<String, String> estados = obtenerMapaEstadosZonas();
        estados.put(zonaLimpia, estadoLimpio);
        guardarMapaEstadosZonas(estados);
        return obtenerZonasRapidasConEstado(obtenerTomaAbiertaActual().map(TomaInventario::getId).orElse(null));
    }

    @Transactional(readOnly = true)
    public List<ProductoSospechosoDTO> listarProductosSospechosos(Long tomaId) {
        List<Producto> productos = productoRepository.findProductosParaCategorizacion();
        Map<String, Long> nombresParecidos = productos.stream()
                .collect(Collectors.groupingBy(this::claveProductoParecido, Collectors.counting()));
        Map<Long, List<String>> duplicadosFuzzy = detectarDuplicadosFuzzy(productos, nombresParecidos);

        return productos.stream()
                .map(producto -> toSospechoso(producto, nombresParecidos, duplicadosFuzzy))
                .filter(dto -> !dto.alertas.isEmpty())
                .sorted(Comparator
                        .comparingInt((ProductoSospechosoDTO dto) -> dto.severidad).reversed()
                        .thenComparing(dto -> dto.nombre == null ? "" : dto.nombre))
                .limit(80)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<Long> obtenerProductoIdsEtiquetasRegularizacion(Long tomaId) {
        return detalleRepository.findByTomaInventarioId(tomaId).stream()
                .filter(detalle -> detalle.getProducto() != null)
                .map(DetalleTomaInventario::getProducto)
                .filter(producto -> producto.controlaStockInventario() && !producto.esInsumo())
                .filter(producto -> normalizarTexto(producto.getCodigoBarra()) == null
                        || normalizarTexto(producto.getCodigoInterno()) == null)
                .map(Producto::getId)
                .distinct()
                .limit(500)
                .toList();
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
        List<Producto> productosActivos = productoRepository.findByActivoTrue().stream()
                .filter(Producto::controlaStockInventario)
                .toList();
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

    @Transactional
    public TomaInventario iniciarTomaRapida(String observaciones) {
        Optional<TomaInventario> abierta = obtenerTomaAbiertaActual();
        if (abierta.isPresent()) {
            return abierta.get();
        }
        String texto = observaciones == null || observaciones.isBlank()
                ? "Regularizacion rapida de inventario"
                : observaciones;
        return iniciarToma(texto);
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
        if (detalle.getProducto() != null && !detalle.getProducto().controlaStockInventario()) {
            throw new IllegalArgumentException("Este registro esta marcado como servicio o inactivo; no se cuenta como stock fisico.");
        }

        // Registrar el conteo. Si la diferencia es grande, queda pendiente de segundo conteo.
        registrarConteoAuditado(detalle, cantidadFisica, usuarioActual());
        detalle.setObservacion(observacion);

        log.debug("Conteo registrado - Producto: {}, Sistema: {}, Físico: {}, Diferencia: {}",
                detalle.getProducto().getNombre(),
                detalle.getStockSistema(),
                cantidadFisica,
                detalle.getDiferencia());

        return detalleRepository.save(detalle);
    }

    @Transactional
    public ConteoRapidoDTO guardarConteoRapido(Long tomaId,
                                               Long productoId,
                                               Integer cantidadFisica,
                                               String zona,
                                               String observacion) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }
        if (cantidadFisica == null || cantidadFisica < 0) {
            throw new IllegalArgumentException("La cantidad fisica debe ser mayor o igual a 0");
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        if (!producto.controlaStockInventario()) {
            throw new IllegalArgumentException("Este registro esta marcado como servicio o inactivo; no se cuenta como stock fisico.");
        }
        DetalleTomaInventario detalle = obtenerOCrearDetalle(toma, producto);
        registrarConteoAuditado(detalle, cantidadFisica, usuarioActual());
        detalle.setZonaConteo(normalizarZona(zona));
        detalle.setObservacion(normalizarTexto(observacion));

        log.debug("Conteo rapido - Producto: {}, Sistema: {}, Fisico: {}, Zona: {}",
                producto.getNombre(), detalle.getStockSistema(), cantidadFisica, detalle.getZonaConteo());

        return toConteoRapido(detalleRepository.save(detalle));
    }

    @Transactional
    public ConteoRapidoDTO incorporarProductoNuevo(Long tomaId, Producto producto, Integer cantidadFisica, String zona) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }

        DetalleTomaInventario detalle = obtenerOCrearDetalle(toma, producto);
        if (cantidadFisica != null && cantidadFisica >= 0) {
            registrarConteoAuditado(detalle, cantidadFisica, usuarioActual());
        }
        detalle.setZonaConteo(normalizarZona(zona));
        detalle.setObservacion("Producto creado durante regularizacion rapida");
        return toConteoRapido(detalleRepository.save(detalle));
    }

    @Transactional
    public ConteoRapidoDTO deshacerUltimoConteo(Long tomaId) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }
        DetalleTomaInventario detalle = detalleRepository.findByTomaInventarioId(tomaId)
                .stream()
                .filter(d -> Boolean.TRUE.equals(d.getContado()) || d.getPrimerConteoFisico() != null)
                .max(Comparator
                        .comparing(this::fechaUltimoConteo)
                        .thenComparing(d -> d.getId() != null ? d.getId() : 0L))
                .orElseThrow(() -> new RuntimeException("No hay conteos para deshacer."));
        detalle.limpiarConteos();
        detalle.setUsuarioDeshacerConteo(usuarioActual());
        detalle.setFechaDeshacerConteo(LocalDateTime.now());
        detalle.setObservacion(normalizarTexto(detalle.getObservacion()) == null
                ? "Conteo deshecho"
                : detalle.getObservacion() + " | Conteo deshecho");
        return toConteoRapido(detalleRepository.save(detalle));
    }

    @Transactional
    public ConteoRapidoDTO guardarCorreccionProducto(Long tomaId,
                                                      Long productoId,
                                                      String nombre,
                                                      String categoria,
                                                      String marca,
                                                      String color,
                                                      String modelo,
                                                      String tipo,
                                                      String clasificacion,
                                                      BigDecimal costo,
                                                      BigDecimal precio) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        DetalleTomaInventario detalle = obtenerOCrearDetalle(toma, producto);
        if (detalle.getCostoAnteriorCorreccion() == null) {
            detalle.setCostoAnteriorCorreccion(producto.getPrecioCompra());
        }
        if (detalle.getPrecioAnteriorCorreccion() == null) {
            detalle.setPrecioAnteriorCorreccion(producto.getPrecioVenta());
        }
        String nombreLimpio = normalizarTexto(nombre);
        if (nombreLimpio != null) {
            detalle.setNombreCorregido(nombreLimpio.toUpperCase());
        }
        detalle.setCategoriaCorregida(mayusculas(categoria));
        detalle.setMarcaCorregida(mayusculas(marca));
        detalle.setColorCorregido(normalizarTexto(color));
        detalle.setModeloCorregido(normalizarTexto(modelo));
        detalle.setTipoCorregido(mayusculas(tipo));
        detalle.setClasificacionCorregida(normalizarTexto(clasificacion) != null
                ? Producto.normalizarClasificacionInventario(clasificacion)
                : null);
        if (costo != null && costo.compareTo(BigDecimal.ZERO) >= 0) {
            detalle.setCostoCorregido(costo.setScale(4, RoundingMode.HALF_UP));
        }
        if (precio != null && precio.compareTo(BigDecimal.ZERO) >= 0) {
            detalle.setPrecioCorregido(precio.setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal precioAnterior = producto.getPrecioVenta();
        BigDecimal costoAnterior = producto.getPrecioCompra();
        String usuario = usuarioActual();

        detalle.setFechaCorreccion(LocalDateTime.now());
        detalle.setUsuarioCorreccion(usuario);

        aplicarDatosCorregidosEnProducto(producto, detalle);
        CosteoProductoDTO analisis = costeoEmpresarialService.actualizarSnapshotProducto(producto);
        productoRepository.save(producto);
        historialPrecioProductoService.registrar(
                producto,
                precioAnterior,
                producto.getPrecioVenta(),
                costoAnterior,
                producto.getPrecioCompra(),
                analisis.getPrecioMinimo(),
                analisis.getPrecioSugerido(),
                "REGULARIZACION",
                "Correccion inmediata desde regularizacion rapida");

        detalle.setCorreccionPendiente(false);
        detalle.setCorreccionAplicada(true);
        detalle.setFechaAplicacionCorreccion(LocalDateTime.now());
        detalle.setUsuarioAplicacionCorreccion(usuario);
        detalle.setCostoAplicadoCorreccion(producto.getPrecioCompra());
        detalle.setPrecioAplicadoCorreccion(producto.getPrecioVenta());
        return toConteoRapido(detalleRepository.save(detalle));
    }

    @Transactional
    public Map<String, Object> aplicarPreciosSugeridosContados(Long tomaId) {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        if (!TomaInventario.ESTADO_ABIERTO.equals(toma.getEstado())) {
            throw new IllegalStateException("La toma de inventario ya esta cerrada");
        }
        Map<String, Object> correcciones = aplicarCorreccionesPendientesInterno(tomaId);
        int revisados = 0;
        int actualizados = 0;
        BigDecimal diferenciaTotal = BigDecimal.ZERO;
        for (DetalleTomaInventario detalle : detalleRepository.findByTomaInventarioId(tomaId)) {
            if (!Boolean.TRUE.equals(detalle.getContado()) || detalle.getProducto() == null) {
                continue;
            }
            Producto producto = detalle.getProducto();
            if (!producto.esMercaderiaVendible()) {
                continue;
            }
            revisados++;
            var analisis = costeoEmpresarialService.actualizarSnapshotProducto(producto);
            BigDecimal sugerido = analisis.getPrecioSugerido();
            BigDecimal actual = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO;
            if (sugerido != null && sugerido.compareTo(BigDecimal.ZERO) > 0 && sugerido.compareTo(actual) != 0) {
                diferenciaTotal = diferenciaTotal.add(sugerido.subtract(actual));
                BigDecimal costoActual = producto.getPrecioCompra();
                producto.setPrecioVenta(sugerido);
                productoRepository.save(producto);
                historialPrecioProductoService.registrar(
                        producto,
                        actual,
                        sugerido,
                        costoActual,
                        producto.getPrecioCompra(),
                        analisis.getPrecioMinimo(),
                        sugerido,
                        "REGULARIZACION",
                        "Aplicacion de precio sugerido desde regularizacion rapida");
                actualizados++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.putAll(correcciones);
        out.put("revisados", revisados);
        out.put("actualizados", actualizados);
        out.put("diferenciaTotal", diferenciaTotal.setScale(2, RoundingMode.HALF_UP));
        return out;
    }

    private Map<String, Object> aplicarCorreccionesPendientesInterno(Long tomaId) {
        int revisadas = 0;
        int aplicadas = 0;
        for (DetalleTomaInventario detalle : detalleRepository.findByTomaInventarioId(tomaId)) {
            if (!Boolean.TRUE.equals(detalle.getCorreccionPendiente()) || detalle.getProducto() == null) {
                continue;
            }
            revisadas++;
            Producto producto = detalle.getProducto();
            BigDecimal precioAnterior = producto.getPrecioVenta();
            BigDecimal costoAnterior = producto.getPrecioCompra();

            aplicarDatosCorregidosEnProducto(producto, detalle);

            CosteoProductoDTO analisis = costeoEmpresarialService.actualizarSnapshotProducto(producto);
            productoRepository.save(producto);
            historialPrecioProductoService.registrar(
                    producto,
                    precioAnterior,
                    producto.getPrecioVenta(),
                    costoAnterior,
                    producto.getPrecioCompra(),
                    analisis.getPrecioMinimo(),
                    analisis.getPrecioSugerido(),
                    "REGULARIZACION",
                    "Correccion de producto aplicada por lote en regularizacion rapida");

            detalle.setCorreccionPendiente(false);
            detalle.setCorreccionAplicada(true);
            detalle.setFechaCorreccion(LocalDateTime.now());
            detalle.setFechaAplicacionCorreccion(LocalDateTime.now());
            detalle.setUsuarioAplicacionCorreccion(usuarioActual());
            detalle.setCostoAplicadoCorreccion(producto.getPrecioCompra());
            detalle.setPrecioAplicadoCorreccion(producto.getPrecioVenta());
            detalleRepository.save(detalle);
            aplicadas++;
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("correccionesRevisadas", revisadas);
        out.put("correccionesAplicadas", aplicadas);
        return out;
    }

    private void aplicarDatosCorregidosEnProducto(Producto producto, DetalleTomaInventario detalle) {
        if (normalizarTexto(detalle.getNombreCorregido()) != null) {
            producto.setNombre(detalle.getNombreCorregido());
        }
        producto.setCategoria(detalle.getCategoriaCorregida());
        producto.setMarca(detalle.getMarcaCorregida());
        producto.setColor(detalle.getColorCorregido());
        producto.setModelo(detalle.getModeloCorregido());

        String clasificacionFinal = normalizarTexto(detalle.getClasificacionCorregida()) != null
                ? Producto.normalizarClasificacionInventario(detalle.getClasificacionCorregida())
                : Producto.normalizarClasificacionInventario(producto.getClasificacion());
        producto.setClasificacion(clasificacionFinal);

        if (Producto.CLASIFICACION_SERVICIO.equals(clasificacionFinal)) {
            producto.setTipo("SERVICIO");
            producto.setStockActual(0);
            producto.setStockMinimo(0);
            producto.setStockMaximo(null);
            producto.setUbicacionEstante(null);
            producto.setUbicacionFila(null);
            producto.setUbicacionColumna(null);
        } else if (Producto.CLASIFICACION_INACTIVO.equals(clasificacionFinal)) {
            producto.setActivo(false);
            producto.setPosRapido(false);
            producto.setTemporadaActiva(false);
        } else {
            if (normalizarTexto(detalle.getTipoCorregido()) != null) {
                producto.setTipo(detalle.getTipoCorregido());
            }
            if (producto.getStockActual() == null) {
                producto.setStockActual(0);
            }
        }

        if (detalle.getCostoCorregido() != null) {
            producto.setPrecioCompra(detalle.getCostoCorregido().setScale(2, RoundingMode.HALF_UP));
        }
        if (detalle.getPrecioCorregido() != null) {
            producto.setPrecioVenta(detalle.getPrecioCorregido().setScale(2, RoundingMode.HALF_UP));
        }
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
                    if (detalle.getProducto() != null && !detalle.getProducto().controlaStockInventario()) {
                        continue;
                    }
                    registrarConteoAuditado(detalle, conteo.getCantidadFisica(), usuarioActual());
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
        aplicarCorreccionesPendientesInterno(tomaId);

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
            if (producto == null || !producto.controlaStockInventario()) {
                detalle.setAjusteAplicado(true);
                detalleRepository.save(detalle);
                resultado.sinCambios++;
                continue;
            }
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
            detalle.setStockAnteriorAjuste(stockAnterior);
            detalle.setStockNuevoAjuste(stockNuevo);
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

    @Transactional(readOnly = true)
    public byte[] exportarRegularizacionExcel(Long tomaId) throws IOException {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        List<DetalleTomaInventario> detalles = detalleRepository.findByTomaInventarioId(tomaId);

        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Regularizacion");
            CellStyle header = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Regularizacion de inventario " + toma.getCodigo());
            title.createCell(1).setCellValue("Estado: " + toma.getEstado());

            String[] cols = {"Zona", "Codigo", "Producto actual", "Producto final", "Categoria actual", "Categoria final",
                    "Marca", "Color", "Clasificacion / Modelo", "Stock sistema", "Stock fisico", "Diferencia",
                    "Costo actual", "Costo final", "Precio actual", "Precio final", "Correccion", "Contado",
                    "Usuario conteo", "Usuario correccion", "Usuario aplicacion", "Stock anterior aplicado",
                    "Stock nuevo aplicado", "Observacion", "Conteo 1", "Usuario conteo 1", "Fecha conteo 1",
                    "Conteo 2", "Usuario conteo 2", "Fecha conteo 2", "Segundo conteo requerido",
                    "Segundo conteo confirmado", "Fecha conteo final", "Fecha correccion", "Fecha aplicacion correccion",
                    "Costo anterior correccion", "Costo aplicado correccion", "Precio anterior correccion",
                    "Precio aplicado correccion", "Usuario deshizo conteo", "Fecha deshacer conteo"};
            Row h = sheet.createRow(2);
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
                h.getCell(i).setCellStyle(header);
            }

            int rowIndex = 3;
            for (DetalleTomaInventario d : detalles) {
                Producto p = d.getProducto();
                Row r = sheet.createRow(rowIndex++);
                r.createCell(0).setCellValue(texto(d.getZonaConteo()));
                r.createCell(1).setCellValue(p != null ? primerTexto(p.getCodigoBarra(), p.getCodigoInterno(), "ID-" + p.getId()) : "");
                r.createCell(2).setCellValue(p != null ? texto(p.getNombre()) : "");
                r.createCell(3).setCellValue(texto(valorFinal(d.getNombreCorregido(), p != null ? p.getNombre() : "")));
                r.createCell(4).setCellValue(p != null ? texto(p.getCategoria()) : "");
                r.createCell(5).setCellValue(texto(valorFinal(d.getCategoriaCorregida(), p != null ? p.getCategoria() : "")));
                r.createCell(6).setCellValue(texto(valorFinal(d.getMarcaCorregida(), p != null ? p.getMarca() : "")));
                r.createCell(7).setCellValue(texto(valorFinal(d.getColorCorregido(), p != null ? p.getColor() : "")));
                r.createCell(8).setCellValue(p != null
                        ? texto(p.getClasificacion()) + (normalizarTexto(p.getModelo()) != null ? " / " + p.getModelo() : "")
                        : "");
                r.createCell(9).setCellValue(d.getStockSistema() != null ? d.getStockSistema() : 0);
                r.createCell(10).setCellValue(d.getStockFisico() != null ? d.getStockFisico() : 0);
                r.createCell(11).setCellValue(d.getDiferencia() != null ? d.getDiferencia() : 0);
                r.createCell(12).setCellValue(p != null && p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                r.createCell(13).setCellValue(valorFinal(d.getCostoCorregido(), p != null ? p.getPrecioCompra() : null).doubleValue());
                r.createCell(14).setCellValue(p != null && p.getPrecioVenta() != null ? p.getPrecioVenta().doubleValue() : 0);
                r.createCell(15).setCellValue(valorFinal(d.getPrecioCorregido(), p != null ? p.getPrecioVenta() : null).doubleValue());
                r.createCell(16).setCellValue(estadoCorreccion(d));
                r.createCell(17).setCellValue(Boolean.TRUE.equals(d.getContado()) ? "SI" : "NO");
                r.createCell(18).setCellValue(texto(d.getUsuarioConteo()));
                r.createCell(19).setCellValue(texto(d.getUsuarioCorreccion()));
                r.createCell(20).setCellValue(texto(d.getUsuarioAplicacionCorreccion()));
                r.createCell(21).setCellValue(d.getStockAnteriorAjuste() != null ? d.getStockAnteriorAjuste() : 0);
                r.createCell(22).setCellValue(d.getStockNuevoAjuste() != null ? d.getStockNuevoAjuste() : 0);
                r.createCell(23).setCellValue(texto(d.getObservacion()));
                r.createCell(24).setCellValue(d.getPrimerConteoFisico() != null ? d.getPrimerConteoFisico() : 0);
                r.createCell(25).setCellValue(texto(d.getUsuarioPrimerConteo()));
                r.createCell(26).setCellValue(textoFecha(d.getFechaPrimerConteo()));
                r.createCell(27).setCellValue(d.getSegundoConteoFisico() != null ? d.getSegundoConteoFisico() : 0);
                r.createCell(28).setCellValue(texto(d.getUsuarioSegundoConteo()));
                r.createCell(29).setCellValue(textoFecha(d.getFechaSegundoConteo()));
                r.createCell(30).setCellValue(Boolean.TRUE.equals(d.getSegundoConteoRequerido()) ? "SI" : "NO");
                r.createCell(31).setCellValue(Boolean.TRUE.equals(d.getSegundoConteoConfirmado()) ? "SI" : "NO");
                r.createCell(32).setCellValue(textoFecha(d.getFechaConteo()));
                r.createCell(33).setCellValue(textoFecha(d.getFechaCorreccion()));
                r.createCell(34).setCellValue(textoFecha(d.getFechaAplicacionCorreccion()));
                r.createCell(35).setCellValue(d.getCostoAnteriorCorreccion() != null ? d.getCostoAnteriorCorreccion().doubleValue() : 0);
                r.createCell(36).setCellValue(d.getCostoAplicadoCorreccion() != null ? d.getCostoAplicadoCorreccion().doubleValue() : 0);
                r.createCell(37).setCellValue(d.getPrecioAnteriorCorreccion() != null ? d.getPrecioAnteriorCorreccion().doubleValue() : 0);
                r.createCell(38).setCellValue(d.getPrecioAplicadoCorreccion() != null ? d.getPrecioAplicadoCorreccion().doubleValue() : 0);
                r.createCell(39).setCellValue(texto(d.getUsuarioDeshacerConteo()));
                r.createCell(40).setCellValue(textoFecha(d.getFechaDeshacerConteo()));
            }

            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public void exportarRegularizacionPdf(Long tomaId, OutputStream out) throws DocumentException {
        TomaInventario toma = tomaRepository.findById(tomaId)
                .orElseThrow(() -> new RuntimeException("Toma no encontrada"));
        List<DetalleTomaInventario> detalles = detalleRepository.findByTomaInventarioId(tomaId);
        EstadisticasToma stats = obtenerEstadisticas(tomaId);

        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 7);

        Paragraph titulo = new Paragraph("Regularizacion de inventario " + toma.getCodigo(), title);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);
        doc.add(new Paragraph("Contados: " + stats.productosContados + "/" + stats.totalProductos
                + " | Faltantes: " + stats.totalFaltantes
                + " | Sobrantes: " + stats.totalSobrantes, small));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(16);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{0.9f, 1.0f, 2.2f, 1.0f, 0.9f, 0.7f, 0.7f, 0.7f, 0.7f, 1.0f, 0.7f, 1.0f, 0.9f, 1.0f, 1.0f, 1.2f});
        String[] headers = {"Zona", "Codigo", "Producto final", "Categoria", "Marca", "Sistema", "Fisico", "Dif.", "C1", "Fecha C1", "C2", "Fecha C2", "Costo", "Precio", "Correccion", "Observacion"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, head));
            cell.setBackgroundColor(new Color(15, 118, 110));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(4);
            table.addCell(cell);
        }
        for (DetalleTomaInventario d : detalles) {
            Producto p = d.getProducto();
            addPdf(table, texto(d.getZonaConteo()), body, Element.ALIGN_LEFT);
            addPdf(table, p != null ? primerTexto(p.getCodigoBarra(), p.getCodigoInterno(), "ID-" + p.getId()) : "", body, Element.ALIGN_LEFT);
            addPdf(table, p != null ? texto(valorFinal(d.getNombreCorregido(), p.getNombre())) : "", body, Element.ALIGN_LEFT);
            addPdf(table, p != null ? texto(valorFinal(d.getCategoriaCorregida(), p.getCategoria())) : "", body, Element.ALIGN_LEFT);
            addPdf(table, p != null ? texto(valorFinal(d.getMarcaCorregida(), p.getMarca())) : "", body, Element.ALIGN_LEFT);
            addPdf(table, String.valueOf(d.getStockSistema() != null ? d.getStockSistema() : 0), body, Element.ALIGN_RIGHT);
            addPdf(table, String.valueOf(d.getStockFisico() != null ? d.getStockFisico() : 0), body, Element.ALIGN_RIGHT);
            addPdf(table, String.valueOf(d.getDiferencia() != null ? d.getDiferencia() : 0), body, Element.ALIGN_RIGHT);
            addPdf(table, d.getPrimerConteoFisico() != null ? String.valueOf(d.getPrimerConteoFisico()) : "-", body, Element.ALIGN_RIGHT);
            addPdf(table, textoFecha(d.getFechaPrimerConteo()), body, Element.ALIGN_LEFT);
            addPdf(table, d.getSegundoConteoFisico() != null ? String.valueOf(d.getSegundoConteoFisico()) : "-", body, Element.ALIGN_RIGHT);
            addPdf(table, textoFecha(d.getFechaSegundoConteo()), body, Element.ALIGN_LEFT);
            addPdf(table, moneda(valorFinal(d.getCostoCorregido(), p != null ? p.getPrecioCompra() : null)), body, Element.ALIGN_RIGHT);
            addPdf(table, moneda(valorFinal(d.getPrecioCorregido(), p != null ? p.getPrecioVenta() : null)), body, Element.ALIGN_RIGHT);
            addPdf(table, estadoCorreccion(d), body, Element.ALIGN_CENTER);
            addPdf(table, texto(d.getObservacion()), body, Element.ALIGN_LEFT);
        }
        doc.add(table);
        doc.close();
    }

    private DetalleTomaInventario obtenerOCrearDetalle(TomaInventario toma, Producto producto) {
        return detalleRepository.findByTomaInventarioIdAndProductoId(toma.getId(), producto.getId())
                .orElseGet(() -> {
                    DetalleTomaInventario detalle = new DetalleTomaInventario();
                    detalle.setTomaInventario(toma);
                    detalle.setProducto(producto);
                    detalle.setStockSistema(producto.getStockActual() != null ? producto.getStockActual() : 0);
                    detalle.setContado(false);
                    detalle.setAjusteAplicado(false);
                    return detalle;
                });
    }

    private void agregarProductoExacto(String filtro,
                                       Set<Long> agregados,
                                       Map<Long, ConteoRapidoDTO> resultados,
                                       Long tomaId) {
        productoRepository.findByCodigoBarra(filtro)
                .ifPresent(producto -> agregarProductoResultado(producto, agregados, resultados, tomaId));
        productoRepository.findByCodigoInterno(filtro)
                .ifPresent(producto -> agregarProductoResultado(producto, agregados, resultados, tomaId));
    }

    private void agregarProductoResultado(Producto producto,
                                          Set<Long> agregados,
                                          Map<Long, ConteoRapidoDTO> resultados,
                                          Long tomaId) {
        if (producto == null || producto.getId() == null || agregados.contains(producto.getId())) {
            return;
        }
        if (!producto.controlaStockInventario()) {
            return;
        }
        agregados.add(producto.getId());
        ConteoRapidoDTO dto = detalleRepository.findByTomaInventarioIdAndProductoId(tomaId, producto.getId())
                .map(this::toConteoRapido)
                .orElseGet(() -> toConteoRapido(producto));
        resultados.put(producto.getId(), dto);
    }

    private ConteoRapidoDTO toConteoRapido(DetalleTomaInventario detalle) {
        Producto producto = detalle.getProducto();
        ConteoRapidoDTO dto = toConteoRapido(producto);
        dto.detalleId = detalle.getId();
        dto.nombreOriginal = producto.getNombre();
        dto.categoriaOriginal = producto.getCategoria();
        dto.marcaOriginal = producto.getMarca();
        dto.colorOriginal = producto.getColor();
        dto.modeloOriginal = producto.getModelo();
        dto.tipoOriginal = producto.getTipo();
        dto.clasificacionOriginal = producto.getClasificacion();
        dto.precioCompraOriginal = producto.getPrecioCompra() != null ? producto.getPrecioCompra() : BigDecimal.ZERO;
        dto.precioVentaOriginal = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO;
        if (Boolean.TRUE.equals(detalle.getCorreccionPendiente()) || Boolean.TRUE.equals(detalle.getCorreccionAplicada())) {
            dto.nombre = valorFinal(detalle.getNombreCorregido(), dto.nombre);
            dto.categoria = valorFinal(detalle.getCategoriaCorregida(), dto.categoria);
            dto.marca = valorFinal(detalle.getMarcaCorregida(), dto.marca);
            dto.color = valorFinal(detalle.getColorCorregido(), dto.color);
            dto.modelo = valorFinal(detalle.getModeloCorregido(), dto.modelo);
            dto.tipo = valorFinal(detalle.getTipoCorregido(), dto.tipo);
            dto.clasificacion = valorFinal(detalle.getClasificacionCorregida(), dto.clasificacion);
            dto.precioCompra = detalle.getCostoCorregido() != null ? detalle.getCostoCorregido() : dto.precioCompra;
            dto.precioVenta = detalle.getPrecioCorregido() != null ? detalle.getPrecioCorregido() : dto.precioVenta;
        }
        dto.stockSistema = detalle.getStockSistema();
        dto.stockFisico = detalle.getStockFisico();
        dto.diferencia = detalle.getDiferencia();
        dto.contado = Boolean.TRUE.equals(detalle.getContado());
        dto.zonaConteo = detalle.getZonaConteo();
        dto.observacion = detalle.getObservacion();
        dto.enToma = true;
        dto.correccionPendiente = Boolean.TRUE.equals(detalle.getCorreccionPendiente());
        dto.correccionAplicada = Boolean.TRUE.equals(detalle.getCorreccionAplicada());
        dto.controlaStock = producto.controlaStockInventario();
        dto.primerConteoFisico = detalle.getPrimerConteoFisico();
        dto.segundoConteoFisico = detalle.getSegundoConteoFisico();
        dto.fechaPrimerConteo = textoFecha(detalle.getFechaPrimerConteo());
        dto.fechaSegundoConteo = textoFecha(detalle.getFechaSegundoConteo());
        dto.fechaConteo = textoFecha(detalle.getFechaConteo());
        dto.usuarioPrimerConteo = detalle.getUsuarioPrimerConteo();
        dto.usuarioSegundoConteo = detalle.getUsuarioSegundoConteo();
        dto.segundoConteoRequerido = Boolean.TRUE.equals(detalle.getSegundoConteoRequerido());
        dto.segundoConteoConfirmado = Boolean.TRUE.equals(detalle.getSegundoConteoConfirmado());
        dto.requiereSegundoConteo = dto.segundoConteoRequerido && !dto.segundoConteoConfirmado;
        dto.alertaConteo = dto.requiereSegundoConteo
                ? "Diferencia grande: ya se guardo el conteo 1. Falta registrar el conteo 2."
                : null;
        dto.usuarioConteo = detalle.getUsuarioConteo();
        dto.usuarioCorreccion = detalle.getUsuarioCorreccion();
        dto.usuarioAplicacionCorreccion = detalle.getUsuarioAplicacionCorreccion();
        dto.fechaCorreccion = textoFecha(detalle.getFechaCorreccion());
        dto.fechaAplicacionCorreccion = textoFecha(detalle.getFechaAplicacionCorreccion());
        return dto;
    }

    private ConteoRapidoDTO toConteoRapido(Producto producto) {
        ConteoRapidoDTO dto = new ConteoRapidoDTO();
        dto.productoId = producto.getId();
        dto.codigo = primerTexto(producto.getCodigoBarra(), producto.getCodigoInterno(), "ID-" + producto.getId());
        dto.codigoInterno = producto.getCodigoInterno();
        dto.codigoBarra = producto.getCodigoBarra();
        dto.nombre = producto.getNombre();
        dto.categoria = producto.getCategoria();
        dto.marca = producto.getMarca();
        dto.color = producto.getColor();
        dto.modelo = producto.getModelo();
        dto.tipo = producto.getTipo();
        dto.clasificacion = producto.getClasificacion();
        dto.stockSistema = producto.getStockActual() != null ? producto.getStockActual() : 0;
        dto.precioCompra = producto.getPrecioCompra() != null ? producto.getPrecioCompra() : BigDecimal.ZERO;
        dto.precioVenta = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO;
        dto.ubicacion = producto.getUbicacionResumenTexto();
        dto.contado = false;
        dto.enToma = false;
        dto.correccionPendiente = false;
        dto.correccionAplicada = false;
        dto.controlaStock = producto.controlaStockInventario();
        dto.requiereSegundoConteo = false;
        dto.segundoConteoRequerido = false;
        dto.segundoConteoConfirmado = false;
        dto.nombreOriginal = dto.nombre;
        dto.categoriaOriginal = dto.categoria;
        dto.marcaOriginal = dto.marca;
        dto.colorOriginal = dto.color;
        dto.modeloOriginal = dto.modelo;
        dto.tipoOriginal = dto.tipo;
        dto.clasificacionOriginal = dto.clasificacion;
        dto.precioCompraOriginal = dto.precioCompra;
        dto.precioVentaOriginal = dto.precioVenta;
        return dto;
    }

    private String normalizarZona(String valor) {
        String limpio = normalizarTexto(valor);
        return limpio == null ? "SIN ZONA" : limpio.toUpperCase();
    }

    private String mayusculas(String valor) {
        String limpio = normalizarTexto(valor);
        return limpio == null ? null : limpio.toUpperCase();
    }

    private String valorFinal(String correccion, String original) {
        String limpio = normalizarTexto(correccion);
        return limpio != null ? limpio : original;
    }

    private BigDecimal valorFinal(BigDecimal correccion, BigDecimal original) {
        return correccion != null ? correccion : (original != null ? original : BigDecimal.ZERO);
    }

    private String estadoCorreccion(DetalleTomaInventario detalle) {
        if (Boolean.TRUE.equals(detalle.getCorreccionPendiente())) {
            return "PENDIENTE";
        }
        if (Boolean.TRUE.equals(detalle.getCorreccionAplicada())) {
            return "APLICADA";
        }
        return "";
    }

    private List<String> normalizarListaZonas(String raw) {
        if (raw == null || raw.isBlank()) {
            raw = ZONAS_DEFAULT;
        }
        Set<String> zonas = new LinkedHashSet<>();
        for (String item : raw.split("[,;\\n\\r]+")) {
            String limpio = normalizarTexto(item);
            if (limpio != null) {
                zonas.add(limpio.toUpperCase());
            }
        }
        return zonas.stream().toList();
    }

    private String texto(String valor) {
        return valor == null ? "" : valor;
    }

    private String textoFecha(LocalDateTime valor) {
        return valor == null ? "" : FORMATO_FECHA_AUDITORIA.format(valor);
    }

    private String moneda(BigDecimal valor) {
        BigDecimal seguro = valor != null ? valor : BigDecimal.ZERO;
        return "S/ " + seguro.setScale(2, RoundingMode.HALF_UP);
    }

    private void addPdf(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto(text), font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private String normalizarTexto(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = valor.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    private String primerTexto(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor;
            }
        }
        return "-";
    }

    private String usuarioActual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getName() != null ? authentication.getName() : "sistema";
    }

    private void registrarConteoAuditado(DetalleTomaInventario detalle, Integer cantidadFisica, String usuario) {
        boolean reconteoPendiente = Boolean.TRUE.equals(detalle.getSegundoConteoRequerido())
                && !Boolean.TRUE.equals(detalle.getSegundoConteoConfirmado())
                && detalle.getPrimerConteoFisico() != null;
        if (reconteoPendiente) {
            detalle.registrarSegundoConteo(cantidadFisica, usuario);
            return;
        }

        boolean requiereSegundo = requiereSegundoConteo(detalle.getStockSistema(), cantidadFisica);
        detalle.registrarPrimerConteo(cantidadFisica, usuario, requiereSegundo);
    }

    private LocalDateTime fechaUltimoConteo(DetalleTomaInventario detalle) {
        if (detalle.getFechaSegundoConteo() != null) {
            return detalle.getFechaSegundoConteo();
        }
        if (detalle.getFechaConteo() != null) {
            return detalle.getFechaConteo();
        }
        if (detalle.getFechaPrimerConteo() != null) {
            return detalle.getFechaPrimerConteo();
        }
        return LocalDateTime.MIN;
    }

    private boolean requiereSegundoConteo(Integer stockSistema, Integer stockFisico) {
        if (stockSistema == null || stockFisico == null) {
            return false;
        }
        int diferencia = Math.abs(stockFisico - stockSistema);
        if (diferencia >= 10) {
            return true;
        }
        int base = Math.max(Math.abs(stockSistema), 1);
        return stockSistema >= 5 && diferencia >= Math.ceil(base * 0.5);
    }

    private Map<String, String> obtenerMapaEstadosZonas() {
        Map<String, String> estados = new LinkedHashMap<>();
        String raw = systemConfigurationService.getValue(KEY_ZONAS_RAPIDAS_ESTADOS).orElse("");
        for (String item : raw.split("[|;\\n\\r]+")) {
            String limpio = normalizarTexto(item);
            if (limpio == null || !limpio.contains("=")) {
                continue;
            }
            String[] partes = limpio.split("=", 2);
            String zona = normalizarZona(partes[0]);
            if (!"SIN ZONA".equals(zona)) {
                estados.put(zona, normalizarEstadoZona(partes.length > 1 ? partes[1] : null));
            }
        }
        return estados;
    }

    private void guardarMapaEstadosZonas(Map<String, String> estados) {
        String value = estados.entrySet().stream()
                .map(entry -> normalizarZona(entry.getKey()) + "=" + normalizarEstadoZona(entry.getValue()))
                .collect(Collectors.joining("|"));
        systemConfigurationService.setValue(KEY_ZONAS_RAPIDAS_ESTADOS, value,
                "Estado de tandas de regularizacion rapida de inventario");
    }

    private String normalizarEstadoZona(String estado) {
        String limpio = mayusculas(estado);
        if (ESTADO_ZONA_TERMINADA.equals(limpio) || "TERMINADO".equals(limpio)) {
            return ESTADO_ZONA_TERMINADA;
        }
        if (ESTADO_ZONA_REVISADA.equals(limpio) || "REVISADO".equals(limpio)) {
            return ESTADO_ZONA_REVISADA;
        }
        return ESTADO_ZONA_PENDIENTE;
    }

    private ProductoSospechosoDTO toSospechoso(Producto producto,
                                               Map<String, Long> nombresParecidos,
                                               Map<Long, List<String>> duplicadosFuzzy) {
        ProductoSospechosoDTO dto = new ProductoSospechosoDTO();
        dto.productoId = producto.getId();
        dto.codigo = primerTexto(producto.getCodigoBarra(), producto.getCodigoInterno(), "ID-" + producto.getId());
        dto.nombre = producto.getNombre();
        dto.categoria = producto.getCategoria();
        dto.clasificacion = Producto.normalizarClasificacionInventario(producto.getClasificacion());
        dto.stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
        dto.costo = producto.getPrecioCompra() != null ? producto.getPrecioCompra() : BigDecimal.ZERO;
        dto.precio = producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO;

        if (normalizarTexto(producto.getCategoria()) == null) {
            dto.alertas.add("SIN CATEGORIA");
        }
        if (!producto.esServicioInventario() && dto.costo.compareTo(BigDecimal.ZERO) <= 0) {
            dto.alertas.add("SIN COSTO");
        }
        if (!producto.esInsumo() && dto.precio.compareTo(BigDecimal.ZERO) <= 0) {
            dto.alertas.add("SIN PRECIO");
        }
        if (!producto.esServicioInventario() && dto.stockActual < 0) {
            dto.alertas.add("STOCK NEGATIVO");
        }
        if (!producto.esInsumo() && dto.precio.compareTo(BigDecimal.ZERO) > 0
                && dto.costo.compareTo(BigDecimal.ZERO) > 0
                && dto.precio.compareTo(dto.costo) < 0) {
            dto.alertas.add("MARGEN NEGATIVO");
        }
        if (producto.getPrecioMinimoEmpresarial() != null
                && dto.precio.compareTo(BigDecimal.ZERO) > 0
                && dto.precio.compareTo(producto.getPrecioMinimoEmpresarial()) < 0) {
            dto.alertas.add("DEBAJO DEL MINIMO");
        }
        if (producto.esDesconocidoInventario()) {
            dto.alertas.add("DESCONOCIDO");
        }
        List<String> similares = duplicadosFuzzy.getOrDefault(producto.getId(), List.of());
        if (nombresParecidos.getOrDefault(claveProductoParecido(producto), 0L) > 1 || !similares.isEmpty()) {
            dto.alertas.add("DUPLICADO PARECIDO");
            dto.duplicadosParecidos.addAll(similares);
            dto.similitudDuplicado = extraerMayorSimilitud(similares);
        }
        dto.severidad = calcularSeveridadSospechoso(dto.alertas);
        return dto;
    }

    private int calcularSeveridadSospechoso(List<String> alertas) {
        int score = 0;
        for (String alerta : alertas) {
            score += switch (alerta) {
                case "MARGEN NEGATIVO", "STOCK NEGATIVO", "DEBAJO DEL MINIMO" -> 4;
                case "SIN COSTO", "SIN PRECIO", "DUPLICADO PARECIDO" -> 3;
                case "SIN CATEGORIA", "DESCONOCIDO" -> 2;
                default -> 1;
            };
        }
        return score;
    }

    private String claveProductoParecido(Producto producto) {
        String base = String.join(" ",
                texto(producto.getNombre()),
                texto(producto.getCategoria()),
                texto(producto.getMarca()),
                texto(producto.getColor()),
                texto(producto.getModelo()));
        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
        return normalized.isBlank() ? "ID-" + producto.getId() : normalized;
    }

    private Map<Long, List<String>> detectarDuplicadosFuzzy(List<Producto> productos, Map<String, Long> exactos) {
        Map<Long, List<String>> resultados = new HashMap<>();
        List<Producto> candidatos = productos.stream()
                .filter(p -> p.getId() != null)
                .filter(p -> normalizarBusqueda(p.getNombre()).length() >= 4)
                .limit(2500)
                .toList();

        Map<String, List<Producto>> buckets = new LinkedHashMap<>();
        for (Producto producto : candidatos) {
            for (String bucket : bucketsProductoFuzzy(producto)) {
                buckets.computeIfAbsent(bucket, key -> new ArrayList<>()).add(producto);
            }
        }

        Set<String> paresRevisados = new LinkedHashSet<>();
        for (List<Producto> grupo : buckets.values()) {
            for (int i = 0; i < grupo.size(); i++) {
                Producto a = grupo.get(i);
                for (int j = i + 1; j < grupo.size(); j++) {
                    Producto b = grupo.get(j);
                    String par = a.getId() < b.getId() ? a.getId() + ":" + b.getId() : b.getId() + ":" + a.getId();
                    if (!paresRevisados.add(par)) {
                        continue;
                    }
                    double score = similitudProducto(a, b, exactos);
                    if (score >= 0.80d) {
                        agregarDuplicadoFuzzy(resultados, a, b, score);
                        agregarDuplicadoFuzzy(resultados, b, a, score);
                    }
                }
            }
        }
        return resultados;
    }

    private Set<String> bucketsProductoFuzzy(Producto producto) {
        Set<String> buckets = new LinkedHashSet<>();
        String nombre = normalizarBusqueda(producto.getNombre());
        if (!nombre.isBlank()) {
            String primerToken = nombre.split("\\s+")[0];
            if (primerToken.length() >= 3) {
                buckets.add("TOK:" + primerToken.substring(0, Math.min(5, primerToken.length())));
            }
            buckets.add("INI:" + nombre.charAt(0));
        }
        String categoria = normalizarBusqueda(producto.getCategoria());
        if (!categoria.isBlank() && !nombre.isBlank()) {
            buckets.add("CAT:" + categoria.substring(0, Math.min(5, categoria.length())) + ":" + nombre.charAt(0));
        }
        return buckets;
    }

    private void agregarDuplicadoFuzzy(Map<Long, List<String>> resultados, Producto base, Producto parecido, double score) {
        List<String> similares = resultados.computeIfAbsent(base.getId(), k -> new ArrayList<>());
        if (similares.size() >= 5) {
            return;
        }
        String codigo = primerTexto(parecido.getCodigoBarra(), parecido.getCodigoInterno(), "ID-" + parecido.getId());
        String detalle = codigo + " | " + texto(parecido.getNombre()) + " | " + Math.round(score * 100) + "%";
        if (!similares.contains(detalle)) {
            similares.add(detalle);
        }
    }

    private double similitudProducto(Producto a, Producto b, Map<String, Long> exactos) {
        String claveA = claveProductoParecido(a);
        String claveB = claveProductoParecido(b);
        if (claveA.equals(claveB) && exactos.getOrDefault(claveA, 0L) > 1) {
            return 1.0d;
        }

        String nombreA = normalizarBusqueda(a.getNombre());
        String nombreB = normalizarBusqueda(b.getNombre());
        if (nombreA.isBlank() || nombreB.isBlank()) {
            return 0.0d;
        }
        double nombreScore = similitudTexto(nombreA, nombreB);
        double tokenExactoScore = similitudTokens(textoProductoFuzzy(a), textoProductoFuzzy(b));
        double tokenFuzzyScore = similitudTokensFuzzy(textoProductoFuzzy(a), textoProductoFuzzy(b));
        double fragmentoScore = similitudNgramas(nombreA, nombreB, 3);
        double score = (nombreScore * 0.42d)
                + (tokenExactoScore * 0.20d)
                + (tokenFuzzyScore * 0.25d)
                + (fragmentoScore * 0.13d);

        if (mismoValorFuzzy(a.getCategoria(), b.getCategoria())) {
            score += 0.07d;
        }
        if (mismoValorFuzzy(a.getMarca(), b.getMarca())) {
            score += 0.04d;
        }
        if (mismoValorFuzzy(a.getColor(), b.getColor())) {
            score += 0.03d;
        }
        if (mismoValorFuzzy(a.getModelo(), b.getModelo())) {
            score += 0.03d;
        }
        if (nombreA.contains(nombreB) || nombreB.contains(nombreA)) {
            score += 0.08d;
        }
        if (valorDistintoFuzzy(a.getColor(), b.getColor())) {
            score -= 0.06d;
        }
        if (valorDistintoFuzzy(a.getModelo(), b.getModelo())) {
            score -= 0.06d;
        }
        if (tokensNumericosDiferentes(nombreA, nombreB)) {
            score -= 0.05d;
        }
        return Math.max(0.0d, Math.min(score, 1.0d));
    }

    private Double extraerMayorSimilitud(List<String> similares) {
        double mayor = 0.0d;
        for (String similar : similares) {
            int pipe = similar.lastIndexOf('|');
            int percent = similar.lastIndexOf('%');
            if (pipe >= 0 && percent > pipe) {
                try {
                    mayor = Math.max(mayor, Double.parseDouble(similar.substring(pipe + 1, percent).trim()));
                } catch (NumberFormatException ignored) {
                    // Solo afecta la etiqueta visible de similitud; las alertas siguen vigentes.
                }
            }
        }
        return mayor > 0 ? mayor : null;
    }

    private String textoProductoFuzzy(Producto producto) {
        return String.join(" ",
                normalizarBusqueda(producto.getNombre()),
                normalizarBusqueda(producto.getCategoria()),
                normalizarBusqueda(producto.getMarca()),
                normalizarBusqueda(producto.getColor()),
                normalizarBusqueda(producto.getModelo()));
    }

    private boolean mismoValorFuzzy(String a, String b) {
        String na = normalizarBusqueda(a);
        String nb = normalizarBusqueda(b);
        return !na.isBlank() && na.equals(nb);
    }

    private boolean valorDistintoFuzzy(String a, String b) {
        String na = normalizarBusqueda(a);
        String nb = normalizarBusqueda(b);
        return !na.isBlank() && !nb.isBlank() && !na.equals(nb);
    }

    private String normalizarBusqueda(String valor) {
        String limpio = normalizarTexto(valor);
        if (limpio == null) {
            return "";
        }
        return Normalizer.normalize(limpio, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private double similitudTexto(String a, String b) {
        if (a.equals(b)) {
            return 1.0d;
        }
        int max = Math.max(a.length(), b.length());
        if (max == 0) {
            return 1.0d;
        }
        return 1.0d - (distanciaLevenshtein(a, b) / (double) max);
    }

    private double similitudTokens(String a, String b) {
        Set<String> tokensA = java.util.Arrays.stream(a.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> tokensB = java.util.Arrays.stream(b.split("\\s+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0d;
        }
        Set<String> interseccion = new LinkedHashSet<>(tokensA);
        interseccion.retainAll(tokensB);
        Set<String> union = new LinkedHashSet<>(tokensA);
        union.addAll(tokensB);
        return union.isEmpty() ? 0.0d : interseccion.size() / (double) union.size();
    }

    private double similitudTokensFuzzy(String a, String b) {
        Set<String> tokensA = tokensSignificativos(a);
        Set<String> tokensB = tokensSignificativos(b);
        if (tokensA.isEmpty() || tokensB.isEmpty()) {
            return 0.0d;
        }
        Set<String> base = tokensA.size() <= tokensB.size() ? tokensA : tokensB;
        Set<String> comparacion = tokensA.size() <= tokensB.size() ? tokensB : tokensA;
        double acumulado = 0.0d;
        for (String tokenBase : base) {
            double mejor = 0.0d;
            for (String tokenComparacion : comparacion) {
                mejor = Math.max(mejor, similitudTexto(tokenBase, tokenComparacion));
                if (mejor == 1.0d) {
                    break;
                }
            }
            acumulado += mejor;
        }
        return acumulado / Math.max(tokensA.size(), tokensB.size());
    }

    private Set<String> tokensSignificativos(String valor) {
        return java.util.Arrays.stream(valor.split("\\s+"))
                .map(this::normalizarTokenFuzzy)
                .filter(token -> token.length() >= 3)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private String normalizarTokenFuzzy(String token) {
        if (token == null) {
            return "";
        }
        String limpio = token.trim();
        if (limpio.length() > 4 && limpio.endsWith("ES")) {
            limpio = limpio.substring(0, limpio.length() - 2);
        } else if (limpio.length() > 4 && limpio.endsWith("S")) {
            limpio = limpio.substring(0, limpio.length() - 1);
        }
        return limpio;
    }

    private double similitudNgramas(String a, String b, int size) {
        Set<String> ngramasA = ngramas(a, size);
        Set<String> ngramasB = ngramas(b, size);
        if (ngramasA.isEmpty() || ngramasB.isEmpty()) {
            return 0.0d;
        }
        Set<String> interseccion = new LinkedHashSet<>(ngramasA);
        interseccion.retainAll(ngramasB);
        Set<String> union = new LinkedHashSet<>(ngramasA);
        union.addAll(ngramasB);
        return union.isEmpty() ? 0.0d : interseccion.size() / (double) union.size();
    }

    private Set<String> ngramas(String valor, int size) {
        Set<String> out = new LinkedHashSet<>();
        String limpio = valor == null ? "" : valor.replace(" ", "");
        if (limpio.length() < size) {
            if (!limpio.isBlank()) {
                out.add(limpio);
            }
            return out;
        }
        for (int i = 0; i <= limpio.length() - size; i++) {
            out.add(limpio.substring(i, i + size));
        }
        return out;
    }

    private boolean tokensNumericosDiferentes(String a, String b) {
        Set<String> numerosA = tokensNumericos(a);
        Set<String> numerosB = tokensNumericos(b);
        if (numerosA.isEmpty() || numerosB.isEmpty()) {
            return false;
        }
        Set<String> interseccion = new LinkedHashSet<>(numerosA);
        interseccion.retainAll(numerosB);
        return interseccion.isEmpty();
    }

    private Set<String> tokensNumericos(String valor) {
        return java.util.Arrays.stream(valor.split("\\s+"))
                .filter(token -> token.matches("\\d+"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private int distanciaLevenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + costo);
            }
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        return prev[b.length()];
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

    public static class ConteoRapidoDTO {
        public Long detalleId;
        public Long productoId;
        public String codigo;
        public String codigoInterno;
        public String codigoBarra;
        public String nombre;
        public String categoria;
        public String marca;
        public String color;
        public String modelo;
        public String tipo;
        public String clasificacion;
        public Integer stockSistema;
        public Integer stockFisico;
        public Integer diferencia;
        public Boolean contado;
        public Boolean enToma;
        public String zonaConteo;
        public String observacion;
        public BigDecimal precioCompra;
        public BigDecimal precioVenta;
        public String ubicacion;
        public Boolean controlaStock;
        public Boolean requiereSegundoConteo;
        public Boolean segundoConteoRequerido;
        public Boolean segundoConteoConfirmado;
        public Integer primerConteoFisico;
        public Integer segundoConteoFisico;
        public String fechaPrimerConteo;
        public String fechaSegundoConteo;
        public String fechaConteo;
        public String usuarioPrimerConteo;
        public String usuarioSegundoConteo;
        public String alertaConteo;
        public Boolean correccionPendiente;
        public Boolean correccionAplicada;
        public String usuarioConteo;
        public String usuarioCorreccion;
        public String usuarioAplicacionCorreccion;
        public String fechaCorreccion;
        public String fechaAplicacionCorreccion;
        public String nombreOriginal;
        public String categoriaOriginal;
        public String marcaOriginal;
        public String colorOriginal;
        public String modeloOriginal;
        public String tipoOriginal;
        public String clasificacionOriginal;
        public BigDecimal precioCompraOriginal;
        public BigDecimal precioVentaOriginal;
    }

    public static class ZonaConteoDTO {
        public String nombre;
        public String estado = ESTADO_ZONA_PENDIENTE;
        public int total = 0;
        public int contados = 0;
        public int pendientes = 0;
        public int diferencias = 0;
    }

    public static class ProductoSospechosoDTO {
        public Long productoId;
        public String codigo;
        public String nombre;
        public String categoria;
        public String clasificacion;
        public Integer stockActual;
        public BigDecimal costo;
        public BigDecimal precio;
        public List<String> alertas = new ArrayList<>();
        public List<String> duplicadosParecidos = new ArrayList<>();
        public Double similitudDuplicado;
        public int severidad = 0;
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
