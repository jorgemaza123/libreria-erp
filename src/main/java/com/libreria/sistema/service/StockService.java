package com.libreria.sistema.service;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.DataTableResponse;
import com.libreria.sistema.model.dto.StockDTO;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class StockService {

    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;

    public StockService(ProductoRepository productoRepository, KardexRepository kardexRepository) {
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
    }

    public Map<String, Object> obtenerKPIs() {
        Map<String, Object> kpis = new HashMap<>();
        kpis.put("totalProductos", productoRepository.countActivos());
        kpis.put("stockCritico", productoRepository.countStockCritico());
        kpis.put("sinStock", productoRepository.countSinStock());
        kpis.put("valorInventario", productoRepository.calcularValorInventario());
        return kpis;
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

        Page<Producto> page = productoRepository.buscarStockFiltrado(terminoParam, categoriaParam, estadoParam, pageable);

        List<StockDTO> dtos = page.getContent().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());

        long totalActivos = productoRepository.countActivos();

        return new DataTableResponse<>(draw, totalActivos, page.getTotalElements(), dtos);
    }

    public StockDTO obtenerDetalleProducto(Long productoId) {
        Producto producto = productoRepository.findById(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
        return convertToDTO(producto);
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
        Producto producto = productoRepository.findByIdWithLock(productoId)
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

        log.info("Ajuste de stock: Producto={}, {} -> {}, usuario={}", producto.getNombre(), stockAnterior, nuevoStock, usuario);
    }

    public byte[] exportarExcel(String termino, String categoria, String estado) throws IOException {
        String terminoParam = (termino != null && !termino.trim().isEmpty()) ? termino.trim() : null;
        String categoriaParam = (categoria != null && !categoria.trim().isEmpty()) ? categoria.trim() : null;
        String estadoParam = (estado != null && !estado.trim().isEmpty()) ? estado.trim() : null;

        // Get all matching results (no pagination for export)
        Pageable pageable = PageRequest.of(0, 10000, Sort.by("nombre").ascending());
        Page<Producto> page = productoRepository.buscarStockFiltrado(terminoParam, categoriaParam, estadoParam, pageable);

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
            String[] headers = {"Código", "Producto", "Categoría", "Stock Actual", "Stock Mínimo", "Stock Máximo", "Estado", "P.Compra", "Valor Stock"};
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
                row.createCell(6).setCellValue(dto.getEstado());
                row.createCell(7).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                row.createCell(8).setCellValue(dto.getValorStock() != null ? dto.getValorStock().doubleValue() : 0);
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
        String estado = calcularEstado(p);
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
                .estado(estado)
                .badgeClass(badge)
                .precioCompra(p.getPrecioCompra())
                .valorStock(valor)
                .fechaActualizacion(p.getFechaActualizacion())
                .build();
    }

    private String calcularEstado(Producto p) {
        int stock = p.getStockActual() != null ? p.getStockActual() : 0;
        int minimo = p.getStockMinimo() != null ? p.getStockMinimo() : 0;

        if (stock == 0) return "SIN_STOCK";
        if (stock <= minimo) return "CRITICO";
        if (stock <= (int)(minimo * 1.5)) return "BAJO";
        return "OK";
    }

    private String calcularBadge(String estado) {
        return switch (estado) {
            case "SIN_STOCK" -> "badge-dark";
            case "CRITICO" -> "badge-danger";
            case "BAJO" -> "badge-warning";
            default -> "badge-success";
        };
    }

    private String mapColumnToField(String column) {
        if (column == null) return "nombre";
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
}
