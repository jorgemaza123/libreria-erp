package com.libreria.sistema.service;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.Proveedor;
import com.libreria.sistema.model.dto.DataTableResponse;
import com.libreria.sistema.model.dto.ReposicionSugeridaDTO;
import com.libreria.sistema.model.dto.StockDTO;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.KardexRepository;
import com.libreria.sistema.repository.ProductoRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockService {

    private static final int DIAS_ROTACION_CORTA = 7;
    private static final int DIAS_ROTACION_BASE = 30;
    private static final int DIAS_COBERTURA_OBJETIVO = 14;

    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final DetalleVentaRepository detalleVentaRepository;

    public StockService(ProductoRepository productoRepository,
                        KardexRepository kardexRepository,
                        DetalleVentaRepository detalleVentaRepository) {
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.detalleVentaRepository = detalleVentaRepository;
    }

    public Map<String, Object> obtenerKPIs() {
        Map<String, Object> kpis = new HashMap<>();
        LocalDate fechaSinMovimiento = fechaInicioSinMovimiento();
        List<ReposicionSugeridaDTO> reposicion = obtenerReposicionSugerida();
        kpis.put("totalProductos", productoRepository.countActivos());
        kpis.put("stockCritico", productoRepository.countStockCritico());
        kpis.put("sinStock", productoRepository.countSinStock());
        kpis.put("valorInventario", productoRepository.calcularValorInventario());
        kpis.put("cantidadEstancados", productoRepository.countProductosSinMovimiento(fechaSinMovimiento));
        kpis.put("capitalEstancado", productoRepository.calcularCapitalEstancado(fechaSinMovimiento));
        kpis.put("temporadaActiva", reposicion.stream().filter(r -> Boolean.TRUE.equals(r.getTemporadaActiva())).count());
        kpis.put("reposicionSugerida", reposicion.size());
        kpis.put("costoReposicionSugerido", reposicion.stream()
                .map(ReposicionSugeridaDTO::getCostoReposicion)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        return kpis;
    }

    public List<ReposicionSugeridaDTO> obtenerReposicionSugerida() {
        LocalDate hoy = LocalDate.now();
        Map<Long, BigDecimal> ventas30d = cantidadesVendidas(hoy.minusDays(DIAS_ROTACION_BASE - 1), hoy);
        Map<Long, BigDecimal> ventas7d = cantidadesVendidas(hoy.minusDays(DIAS_ROTACION_CORTA - 1), hoy);

        return productoRepository.findByActivoTrue().stream()
                .filter(p -> !p.esLamina())
                .map(p -> calcularReposicion(p,
                        ventas30d.getOrDefault(p.getId(), BigDecimal.ZERO),
                        ventas7d.getOrDefault(p.getId(), BigDecimal.ZERO)))
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt((ReposicionSugeridaDTO r) -> prioridadOrden(r.getPrioridad()))
                        .thenComparing(r -> Boolean.TRUE.equals(r.getTemporadaActiva()) ? 0 : 1)
                        .thenComparing(ReposicionSugeridaDTO::getCostoReposicion, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ReposicionSugeridaDTO::getNombre, Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    @Transactional
    public void actualizarConfiguracionReposicion(Long productoId,
                                                   Integer stockMinimo,
                                                   Integer stockMaximo,
                                                   Boolean temporadaActiva,
                                                   Integer stockObjetivoTemporada,
                                                   String usuario) {
        actualizarConfiguracionReposicion(productoId, stockMinimo, stockMaximo, temporadaActiva,
                stockObjetivoTemporada, null, null, usuario);
    }

    @Transactional
    public void actualizarConfiguracionReposicion(Long productoId,
                                                   Integer stockMinimo,
                                                   Integer stockMaximo,
                                                   Boolean temporadaActiva,
                                                   Integer stockObjetivoTemporada,
                                                   Boolean posRapido,
                                                   Integer posRapidoOrden,
                                                   String usuario) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        producto.setStockMinimo(normalizarEntero(stockMinimo, 0));
        producto.setStockMaximo(stockMaximo != null && stockMaximo > 0 ? stockMaximo : null);
        producto.setTemporadaActiva(Boolean.TRUE.equals(temporadaActiva));
        producto.setStockObjetivoTemporada(stockObjetivoTemporada != null && stockObjetivoTemporada > 0
                ? stockObjetivoTemporada
                : null);
        if (posRapido != null) {
            producto.setPosRapido(Boolean.TRUE.equals(posRapido));
            producto.setPosRapidoOrden(posRapidoOrden != null && posRapidoOrden > 0 ? posRapidoOrden : null);
        }
        productoRepository.save(producto);
        log.info("Usuario {} actualizo reposicion/POS rapido de producto {} (ID: {})", usuario, producto.getNombre(), productoId);
    }

    public DataTableResponse<StockDTO> buscarStock(int draw, int start, int length,
            String termino, String categoria, String estado,
            String orderColumn, String orderDir) {
        // Map column index to field name for sorting
        String sortField = mapColumnToField(orderColumn);
        Sort sort = "asc".equalsIgnoreCase(orderDir) ? Sort.by(sortField).ascending() : Sort.by(sortField).descending();
        Pageable pageable = PageRequest.of(start / length, length, sort);

        String terminoParam = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        String categoriaParam = (categoria != null && !categoria.trim().isEmpty()) ? categoria.trim() : null;
        String estadoParam = (estado != null && !estado.trim().isEmpty()) ? estado.trim() : null;

        LocalDate fechaSinMovimiento = fechaInicioSinMovimiento();
        Page<Producto> page = productoRepository.buscarStockFiltrado(terminoParam, categoriaParam, estadoParam,
                fechaSinMovimiento,
                pageable);

        List<Long> idsConVenta = productoRepository.findProductoIdsConVentasDesde(fechaSinMovimiento);
        Set<Long> setConVenta = new HashSet<>(idsConVenta);

        List<StockDTO> dtos = page.getContent().stream()
                .map(p -> convertToDTO(p, setConVenta))
                .collect(Collectors.toList());

        long totalActivos = productoRepository.countActivos();

        return new DataTableResponse<>(draw, totalActivos, page.getTotalElements(), dtos);
    }

    public StockDTO obtenerDetalleProducto(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        List<Long> idsConVenta = productoRepository.findProductoIdsConVentasDesde(fechaInicioSinMovimiento());
        Set<Long> setConVenta = new HashSet<>(idsConVenta);
        return convertToDTO(producto, setConVenta);
    }

    public Page<Kardex> obtenerMovimientos(Long productoId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return kardexRepository.findByProductoId(productoId, pageable);
    }

    public Map<String, Object> obtenerEvolucionStock(Long productoId) {
        LocalDateTime desde = LocalDateTime.now().minusDays(30);
        List<Kardex> movimientos = kardexRepository.findByProductoIdAndFechaAfter(productoId, desde);

        List<String> labels = new ArrayList<>();
        List<Integer> datos = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        if (movimientos.isEmpty()) {
            Producto p = productoRepository.findById(productoId).orElse(null);
            labels.add(LocalDateTime.now().format(fmt));
            datos.add(p != null ? p.getStockActual() : 0);
        } else {
            for (Kardex k : movimientos) {
                labels.add(k.getFecha().format(fmt));
                datos.add(k.getStockActual());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("labels", labels);
        result.put("datos", datos);
        return result;
    }

    @Transactional
    public void ajustarStock(Long productoId, Integer nuevoStock, String motivo, String usuario) {
        if (nuevoStock == null || nuevoStock < 0) {
            throw new RuntimeException("El nuevo stock debe ser 0 o mayor.");
        }

        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        int stockAnterior = producto.getStockActual() != null ? producto.getStockActual() : 0;
        int diferencia = nuevoStock - stockAnterior;

        producto.setStockActual(nuevoStock);
        productoRepository.save(producto);

        Kardex kardex = new Kardex();
        kardex.setProducto(producto);
        kardex.setTipo("AJUSTE");
        kardex.setMotivo(motivo != null && !motivo.isEmpty() ? motivo : "Ajuste desde módulo Stock por " + usuario);
        kardex.setCantidad(Math.abs(diferencia));
        kardex.setStockAnterior(stockAnterior);
        kardex.setStockActual(nuevoStock);
        kardexRepository.save(kardex);

        log.info("Ajuste de stock: Producto={}, {} -> {}, usuario={}", producto.getNombre(), stockAnterior, nuevoStock,
                usuario);
    }

    public byte[] exportarExcel(String termino, String categoria, String estado) throws IOException {
        String terminoParam = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        String categoriaParam = (categoria != null && !categoria.trim().isEmpty()) ? categoria.trim() : null;
        String estadoParam = (estado != null && !estado.trim().isEmpty()) ? estado.trim() : null;

        // Get all matching results (no pagination for export)
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("nombre").ascending());
        Page<Producto> page = productoRepository.buscarStockFiltrado(terminoParam, categoriaParam, estadoParam,
                fechaInicioSinMovimiento(),
                pageable);

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Stock");

            // Header style
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Headers
            Row headerRow = sheet.createRow(0);
            String[] headers = { "Código", "Producto", "Categoría", "Stock Actual", "Stock Mínimo", "Stock Máximo",
                    "Temporada", "Objetivo Temporada", "Estado", "P.Compra", "Valor Stock" };
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // Data rows
            int rowNum = 1;
            for (Producto p : page.getContent()) {
                StockDTO dto = convertToDTO(p);
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getCodigoInterno() != null ? p.getCodigoInterno() : "");
                row.createCell(1).setCellValue(p.getNombre());
                row.createCell(2).setCellValue(p.getCategoria() != null ? p.getCategoria() : "");
                row.createCell(3).setCellValue(p.getStockActual() != null ? p.getStockActual() : 0);
                row.createCell(4).setCellValue(p.getStockMinimo() != null ? p.getStockMinimo() : 0);
                row.createCell(5).setCellValue(p.getStockMaximo() != null ? p.getStockMaximo() : 0);
                row.createCell(6).setCellValue(Boolean.TRUE.equals(p.getTemporadaActiva()) ? "SI" : "NO");
                row.createCell(7).setCellValue(p.getStockObjetivoTemporada() != null ? p.getStockObjetivoTemporada() : 0);
                row.createCell(8).setCellValue(dto.getEstado());
                row.createCell(9).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                row.createCell(10).setCellValue(dto.getValorStock() != null ? dto.getValorStock().doubleValue() : 0);
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            workbook.write(baos);
            return baos.toByteArray();
        }
    }

    // ==================== PRIVATE HELPERS ====================

    private StockDTO convertToDTO(Producto p) {
        return convertToDTO(p, Collections.emptySet());
    }

    private StockDTO convertToDTO(Producto p, Set<Long> setConVenta) {
        String estado = calcularEstado(p, setConVenta);
        String badge = calcularBadge(estado);
        BigDecimal valor = (p.getStockActual() != null && p.getPrecioCompra() != null)
                ? p.getPrecioCompra().multiply(BigDecimal.valueOf(p.getStockActual()))
                : BigDecimal.ZERO;

        return StockDTO.builder()
                .id(p.getId())
                .codigoInterno(p.getCodigoInterno())
                .codigoBarra(p.getCodigoBarra())
                .nombre(p.getNombre())
                .categoria(p.getCategoria())
                .stockActual(p.getStockActual())
                .stockMinimo(p.getStockMinimo())
                .stockMaximo(p.getStockMaximo())
                .temporadaActiva(Boolean.TRUE.equals(p.getTemporadaActiva()))
                .stockObjetivoTemporada(p.getStockObjetivoTemporada())
                .posRapido(Boolean.TRUE.equals(p.getPosRapido()))
                .posRapidoOrden(p.getPosRapidoOrden())
                .estado(estado)
                .badgeClass(badge)
                .enLiquidacion(p.getEnLiquidacion() != null ? p.getEnLiquidacion() : false)
                .precioOriginal(p.getPrecioOriginal())
                .precioCompra(p.getPrecioCompra())
                .valorStock(valor)
                .fechaActualizacion(p.getFechaActualizacion())
                .build();
    }

    private String calcularEstado(Producto p, Set<Long> setConVenta) {
        int stock = p.getStockActual() != null ? p.getStockActual() : 0;
        int minimo = p.getStockMinimo() != null ? p.getStockMinimo() : 0;

        if (stock == 0)
            return "SIN_STOCK";

        if (p.getEnLiquidacion() != null && p.getEnLiquidacion()) {
            return "LIQUIDACION";
        }

        if (p.getId() != null && !setConVenta.contains(p.getId())) {
            return "SIN_MOVIMIENTO";
        }

        if (stock <= minimo)
            return "CRITICO";
        if (stock <= (int) (minimo * 1.5))
            return "BAJO";
        return "OK";
    }

    private String calcularBadge(String estado) {
        return switch (estado) {
            case "SIN_STOCK" -> "badge-dark";
            case "CRITICO" -> "badge-danger";
            case "BAJO" -> "badge-warning";
            case "SIN_MOVIMIENTO" -> "badge-info";
            default -> "badge-success";
        };
    }

    private ReposicionSugeridaDTO calcularReposicion(Producto p, BigDecimal vendido30d, BigDecimal vendido7d) {
        int stock = normalizarEntero(p.getStockActual(), 0);
        int minimo = normalizarEntero(p.getStockMinimo(), 0);
        int maximo = normalizarEntero(p.getStockMaximo(), 0);
        boolean temporada = Boolean.TRUE.equals(p.getTemporadaActiva());
        int objetivoTemporada = normalizarEntero(p.getStockObjetivoTemporada(), 0);
        BigDecimal promedioDiario = dividir(vendido30d, BigDecimal.valueOf(DIAS_ROTACION_BASE), 3);
        BigDecimal diasCobertura = promedioDiario.compareTo(BigDecimal.ZERO) > 0
                ? BigDecimal.valueOf(stock).divide(promedioDiario, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);

        int objetivo = calcularObjetivoReposicion(minimo, maximo, temporada, objetivoTemporada, promedioDiario);
        boolean sinStock = stock == 0;
        boolean debajoMinimo = minimo > 0 && stock <= minimo;
        boolean debajoTemporada = temporada && objetivo > 0 && stock < objetivo;
        boolean coberturaCorta = promedioDiario.compareTo(BigDecimal.ZERO) > 0
                && BigDecimal.valueOf(stock).compareTo(promedioDiario.multiply(BigDecimal.valueOf(DIAS_ROTACION_CORTA))) <= 0;

        if (!sinStock && !debajoMinimo && !debajoTemporada && !coberturaCorta) {
            return null;
        }

        int cantidadSugerida = Math.max(0, objetivo - stock);
        if (cantidadSugerida == 0 && (sinStock || debajoMinimo || coberturaCorta)) {
            cantidadSugerida = Math.max(1, minimo > 0 ? minimo : ceil(promedioDiario.multiply(BigDecimal.valueOf(DIAS_COBERTURA_OBJETIVO))));
        }

        BigDecimal costoUnitario = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
        BigDecimal costoReposicion = costoUnitario.multiply(BigDecimal.valueOf(cantidadSugerida)).setScale(2, RoundingMode.HALF_UP);
        String prioridad = calcularPrioridadReposicion(sinStock, debajoMinimo, debajoTemporada, diasCobertura, vendido7d);

        return ReposicionSugeridaDTO.builder()
                .productoId(p.getId())
                .codigoInterno(p.getCodigoInterno())
                .nombre(p.getNombre())
                .categoria(p.getCategoria())
                .stockActual(stock)
                .stockMinimo(minimo)
                .stockMaximo(maximo)
                .temporadaActiva(temporada)
                .stockObjetivoTemporada(objetivoTemporada)
                .vendido7d(vendido7d)
                .vendido30d(vendido30d)
                .promedioDiario30d(promedioDiario)
                .diasCobertura(diasCobertura)
                .cantidadSugerida(cantidadSugerida)
                .costoUnitario(costoUnitario)
                .costoReposicion(costoReposicion)
                .prioridad(prioridad)
                .motivo(calcularMotivo(sinStock, debajoMinimo, debajoTemporada, coberturaCorta, temporada))
                .badgeClass(badgeReposicion(prioridad))
                .build();
    }

    private int calcularObjetivoReposicion(int minimo,
                                           int maximo,
                                           boolean temporada,
                                           int objetivoTemporada,
                                           BigDecimal promedioDiario) {
        int objetivoPorRotacion = ceil(promedioDiario.multiply(BigDecimal.valueOf(DIAS_COBERTURA_OBJETIVO)));
        int objetivoBase = maximo > 0 ? maximo : Math.max(minimo * 2, objetivoPorRotacion);
        if (objetivoBase == 0 && minimo > 0) {
            objetivoBase = minimo;
        }
        if (temporada && objetivoTemporada > 0) {
            return Math.max(objetivoBase, objetivoTemporada);
        }
        return objetivoBase;
    }

    private String calcularPrioridadReposicion(boolean sinStock,
                                               boolean debajoMinimo,
                                               boolean debajoTemporada,
                                               BigDecimal diasCobertura,
                                               BigDecimal vendido7d) {
        boolean tuvoVentaReciente = vendido7d != null && vendido7d.compareTo(BigDecimal.ZERO) > 0;
        if (sinStock || (debajoMinimo && tuvoVentaReciente)
                || (diasCobertura.compareTo(BigDecimal.ZERO) > 0 && diasCobertura.compareTo(new BigDecimal("3")) <= 0)) {
            return "URGENTE";
        }
        if (debajoMinimo || debajoTemporada
                || (diasCobertura.compareTo(BigDecimal.ZERO) > 0 && diasCobertura.compareTo(new BigDecimal("7")) <= 0)) {
            return "ALTA";
        }
        return "MEDIA";
    }

    private String calcularMotivo(boolean sinStock,
                                  boolean debajoMinimo,
                                  boolean debajoTemporada,
                                  boolean coberturaCorta,
                                  boolean temporada) {
        List<String> motivos = new ArrayList<>();
        if (sinStock) motivos.add("Sin stock");
        if (debajoMinimo) motivos.add("Debajo del minimo");
        if (debajoTemporada) motivos.add("Temporada activa");
        if (coberturaCorta) motivos.add("Rotacion reciente");
        if (temporada && !debajoTemporada) motivos.add("Marcado para temporada");
        return String.join(" + ", motivos);
    }

    private String badgeReposicion(String prioridad) {
        return switch (prioridad) {
            case "URGENTE" -> "badge-danger";
            case "ALTA" -> "badge-warning";
            default -> "badge-info";
        };
    }

    private int prioridadOrden(String prioridad) {
        return switch (prioridad) {
            case "URGENTE" -> 0;
            case "ALTA" -> 1;
            default -> 2;
        };
    }

    private Map<Long, BigDecimal> cantidadesVendidas(LocalDate inicio, LocalDate fin) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (Object[] row : detalleVentaRepository.cantidadVendidaPorProducto(inicio, fin)) {
            if (row[0] == null) continue;
            Long productoId = ((Number) row[0]).longValue();
            BigDecimal cantidad = row[1] instanceof BigDecimal bd
                    ? bd
                    : row[1] instanceof Number n ? BigDecimal.valueOf(n.doubleValue()) : BigDecimal.ZERO;
            out.put(productoId, cantidad);
        }
        return out;
    }

    private LocalDate fechaInicioSinMovimiento() {
        return LocalDate.now().minusDays(DIAS_ROTACION_BASE);
    }

    private BigDecimal dividir(BigDecimal numerador, BigDecimal denominador, int escala) {
        if (denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(escala, RoundingMode.HALF_UP);
        }
        return (numerador != null ? numerador : BigDecimal.ZERO).divide(denominador, escala, RoundingMode.HALF_UP);
    }

    private int ceil(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) <= 0) {
            return 0;
        }
        return valor.setScale(0, RoundingMode.CEILING).intValue();
    }

    private int normalizarEntero(Integer valor, int defecto) {
        return valor != null && valor >= 0 ? valor : defecto;
    }

    public Map<String, Object> obtenerDatosLiquidacion(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        List<Proveedor> proveedores = productoRepository.findLastProveedorByProductoId(productoId, PageRequest.of(0, 1));
        Proveedor ultimoProveedor = proveedores.isEmpty() ? null : proveedores.get(0);

        Map<String, Object> map = new HashMap<>();
        map.put("productoId", producto.getId());
        map.put("nombre", producto.getNombre());
        map.put("stockActual", producto.getStockActual() != null ? producto.getStockActual() : 0);
        map.put("precioCompra", producto.getPrecioCompra() != null ? producto.getPrecioCompra() : BigDecimal.ZERO);
        map.put("precioVenta", producto.getPrecioVenta() != null ? producto.getPrecioVenta() : BigDecimal.ZERO);

        BigDecimal capitalEstancado = (producto.getPrecioCompra() != null && producto.getStockActual() != null)
                ? producto.getPrecioCompra().multiply(BigDecimal.valueOf(producto.getStockActual()))
                : BigDecimal.ZERO;
        map.put("capitalEstancado", capitalEstancado);

        if (ultimoProveedor != null) {
            map.put("proveedorNombre", ultimoProveedor.getRazonSocial());
            map.put("proveedorTelefono", ultimoProveedor.getTelefono());
            map.put("proveedorContacto", ultimoProveedor.getContacto());
        } else {
            map.put("proveedorNombre", null);
        }
        return map;
    }

    private String mapColumnToField(String column) {
        if (column == null)
            return "nombre";
        return switch (column) {
            case "0" -> "codigoInterno";
            case "1" -> "nombre";
            case "2" -> "categoria";
            case "3" -> "stockActual";
            case "4" -> "stockMinimo";
            case "5" -> "stockMaximo";
            case "7" -> "precioCompra";
            default -> "nombre";
        };
    }

    public List<String> obtenerCategorias() {
        return productoRepository.findDistinctCategorias();
    }

    @Transactional
    public void actualizarPrecio(Long productoId, BigDecimal nuevoPrecio, String usuario) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (producto.getEnLiquidacion() == null || !producto.getEnLiquidacion()) {
            producto.setPrecioOriginal(producto.getPrecioVenta());
        }

        producto.setPrecioVenta(nuevoPrecio);
        producto.setEnLiquidacion(true);
        productoRepository.save(producto);
        log.info("Usuario {} puso en liquidación el producto {} (ID: {}) con precio S/ {}", usuario, producto.getNombre(), productoId, nuevoPrecio);
    }

    @Transactional
    public void quitarDeLiquidacion(Long productoId, String usuario) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (producto.getEnLiquidacion() != null && producto.getEnLiquidacion()) {
            if (producto.getPrecioOriginal() != null) {
                producto.setPrecioVenta(producto.getPrecioOriginal());
            }
            producto.setPrecioOriginal(null);
            producto.setEnLiquidacion(false);
            productoRepository.save(producto);
            log.info("Usuario {} retiró de liquidación el producto {} (ID: {}) restaurando precio a S/ {}", usuario, producto.getNombre(), productoId, producto.getPrecioVenta());
        }
    }
}
