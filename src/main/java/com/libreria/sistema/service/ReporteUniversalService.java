package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de Reportes Universales Avanzados.
 * Genera reportes en Excel y PDF para:
 * - PRODUCTOS: Stock actual, valorizado, bajo stock, más vendidos
 * - VENTAS: Por fecha, usuario, cliente, método de pago, con ganancia
 * - KARDEX: Movimientos detallados por producto
 */
@Service
@Slf4j
public class ReporteUniversalService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final KardexRepository kardexRepository;
    private final UsuarioRepository usuarioRepository;
    private final ConfiguracionService configuracionService;

    public ReporteUniversalService(VentaRepository ventaRepository,
                                    ProductoRepository productoRepository,
                                    KardexRepository kardexRepository,
                                    UsuarioRepository usuarioRepository,
                                    ConfiguracionService configuracionService) {
        this.ventaRepository = ventaRepository;
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
        this.usuarioRepository = usuarioRepository;
        this.configuracionService = configuracionService;
    }

    // ==========================================
    //        REPORTES DE PRODUCTOS - EXCEL
    // ==========================================

    /**
     * Genera reporte Excel de stock actual de todos los productos
     */
    public void generarExcelStockActual(OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stock Actual");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);

        crearFilaCabecera(sheet, headerStyle, "CÓDIGO", "PRODUCTO", "CATEGORÍA", "STOCK", "P.COMPRA", "P.VENTA", "VALORIZADO");

        List<Producto> productos = productoRepository.findByActivoTrue();
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        int rowIdx = 1;
        BigDecimal totalValorizado = BigDecimal.ZERO;

        for (Producto p : productos) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, p.getCodigoInterno(), dataStyle);
            crearCelda(row, 1, p.getNombre(), dataStyle);
            crearCelda(row, 2, p.getCategoria() != null ? p.getCategoria() : "-", dataStyle);
            crearCelda(row, 3, String.valueOf(p.getStockActual()), dataStyle);

            BigDecimal precioCompra = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
            crearCelda(row, 4, moneda + " " + precioCompra, moneyStyle);
            crearCelda(row, 5, moneda + " " + p.getPrecioVenta(), moneyStyle);

            BigDecimal valorizado = precioCompra.multiply(new BigDecimal(p.getStockActual()));
            totalValorizado = totalValorizado.add(valorizado);
            crearCelda(row, 6, moneda + " " + valorizado.setScale(2, RoundingMode.HALF_UP), moneyStyle);
        }

        // Fila de total
        Row totalRow = sheet.createRow(rowIdx);
        crearCelda(totalRow, 5, "TOTAL VALORIZADO:", headerStyle);
        crearCelda(totalRow, 6, moneda + " " + totalValorizado.setScale(2, RoundingMode.HALF_UP), headerStyle);

        autoSizeColumns(sheet, 7);
        workbook.write(outputStream);
        workbook.close();
    }

    /**
     * Genera reporte Excel de productos con stock bajo
     */
    public void generarExcelStockBajo(OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Stock Bajo");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle alertStyle = crearEstiloAlerta(workbook);

        crearFilaCabecera(sheet, headerStyle, "CÓDIGO", "PRODUCTO", "CATEGORÍA", "STOCK ACTUAL", "STOCK MÍNIMO", "DIFERENCIA", "ESTADO");

        List<Producto> productos = productoRepository.obtenerStockCritico();

        int rowIdx = 1;
        for (Producto p : productos) {
            Row row = sheet.createRow(rowIdx++);
            int stockMin = p.getStockMinimo() != null ? p.getStockMinimo() : 5;
            int diferencia = p.getStockActual() - stockMin;

            crearCelda(row, 0, p.getCodigoInterno(), dataStyle);
            crearCelda(row, 1, p.getNombre(), dataStyle);
            crearCelda(row, 2, p.getCategoria() != null ? p.getCategoria() : "-", dataStyle);
            crearCelda(row, 3, String.valueOf(p.getStockActual()), p.getStockActual() <= 0 ? alertStyle : dataStyle);
            crearCelda(row, 4, String.valueOf(stockMin), dataStyle);
            crearCelda(row, 5, String.valueOf(diferencia), dataStyle);
            crearCelda(row, 6, p.getStockActual() <= 0 ? "AGOTADO" : "BAJO", p.getStockActual() <= 0 ? alertStyle : dataStyle);
        }

        autoSizeColumns(sheet, 7);
        workbook.write(outputStream);
        workbook.close();
    }

    /**
     * Genera reporte Excel de productos más vendidos
     */
    public void generarExcelProductosMasVendidos(LocalDate inicio, LocalDate fin, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Más Vendidos");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);

        crearFilaCabecera(sheet, headerStyle, "RANKING", "CÓDIGO", "PRODUCTO", "CANT. VENDIDA", "TOTAL VENTAS", "% DEL TOTAL");

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        // Agrupar por producto
        Map<Long, BigDecimal[]> productosVendidos = new LinkedHashMap<>();
        BigDecimal totalGeneral = BigDecimal.ZERO;

        for (Venta v : ventas) {
            if (v.getItems() != null) {
                for (var item : v.getItems()) {
                    Long prodId = item.getProducto().getId();
                    BigDecimal[] datos = productosVendidos.getOrDefault(prodId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    datos[0] = datos[0].add(item.getCantidad());
                    datos[1] = datos[1].add(item.getSubtotal());
                    productosVendidos.put(prodId, datos);
                    totalGeneral = totalGeneral.add(item.getSubtotal());
                }
            }
        }

        // Ordenar por cantidad vendida
        List<Map.Entry<Long, BigDecimal[]>> sortedList = new ArrayList<>(productosVendidos.entrySet());
        sortedList.sort((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]));

        int rowIdx = 1;
        int ranking = 1;
        for (Map.Entry<Long, BigDecimal[]> entry : sortedList) {
            Producto prod = productoRepository.findById(entry.getKey()).orElse(null);
            if (prod == null) continue;

            BigDecimal cantidad = entry.getValue()[0];
            BigDecimal monto = entry.getValue()[1];
            BigDecimal porcentaje = totalGeneral.compareTo(BigDecimal.ZERO) > 0 ?
                    monto.divide(totalGeneral, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, String.valueOf(ranking++), dataStyle);
            crearCelda(row, 1, prod.getCodigoInterno(), dataStyle);
            crearCelda(row, 2, prod.getNombre(), dataStyle);
            crearCelda(row, 3, cantidad.toString(), dataStyle);
            crearCelda(row, 4, moneda + " " + monto.setScale(2, RoundingMode.HALF_UP), moneyStyle);
            crearCelda(row, 5, porcentaje.setScale(2, RoundingMode.HALF_UP) + "%", dataStyle);
        }

        autoSizeColumns(sheet, 6);
        workbook.write(outputStream);
        workbook.close();
    }

    // ==========================================
    //        REPORTES DE VENTAS - EXCEL
    // ==========================================

    /**
     * Genera reporte Excel de ventas por rango de fechas
     */
    public void generarExcelVentasPorFecha(LocalDate inicio, LocalDate fin, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ventas por Fecha");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);

        crearFilaCabecera(sheet, headerStyle, "FECHA", "COMPROBANTE", "CLIENTE", "MÉTODO PAGO", "SUBTOTAL", "IGV", "TOTAL", "ESTADO");

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        int rowIdx = 1;
        BigDecimal totalSum = BigDecimal.ZERO;

        for (Venta v : ventas) {
            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, v.getFechaEmision().format(formatter), dataStyle);
            crearCelda(row, 1, v.getSerie() + "-" + String.format("%06d", v.getNumero()), dataStyle);
            crearCelda(row, 2, v.getClienteDenominacion(), dataStyle);
            crearCelda(row, 3, v.getMetodoPago() != null ? v.getMetodoPago() : "EFECTIVO", dataStyle);
            crearCelda(row, 4, moneda + " " + (v.getTotalGravada() != null ? v.getTotalGravada() : v.getTotal()), moneyStyle);
            crearCelda(row, 5, moneda + " " + (v.getTotalIgv() != null ? v.getTotalIgv() : BigDecimal.ZERO), moneyStyle);
            crearCelda(row, 6, moneda + " " + v.getTotal(), moneyStyle);
            crearCelda(row, 7, v.getEstado(), dataStyle);

            if (!"ANULADO".equals(v.getEstado())) {
                totalSum = totalSum.add(v.getTotal());
            }
        }

        // Fila de total
        Row totalRow = sheet.createRow(rowIdx);
        crearCelda(totalRow, 5, "TOTAL:", headerStyle);
        crearCelda(totalRow, 6, moneda + " " + totalSum.setScale(2, RoundingMode.HALF_UP), headerStyle);

        autoSizeColumns(sheet, 8);
        workbook.write(outputStream);
        workbook.close();
    }

    /**
     * Genera reporte Excel de ventas por usuario/cajero
     */
    public void generarExcelVentasPorUsuario(LocalDate inicio, LocalDate fin, Long usuarioId, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ventas por Usuario");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        // Filtrar por usuario si se especifica
        if (usuarioId != null) {
            ventas = ventas.stream()
                    .filter(v -> v.getUsuario() != null && v.getUsuario().getId().equals(usuarioId))
                    .collect(Collectors.toList());
        }

        // Agrupar por usuario
        Map<String, List<Venta>> ventasPorUsuario = ventas.stream()
                .filter(v -> v.getUsuario() != null)
                .collect(Collectors.groupingBy(v -> v.getUsuario().getUsername()));

        crearFilaCabecera(sheet, headerStyle, "USUARIO", "NOMBRE COMPLETO", "CANT. VENTAS", "TOTAL VENTAS", "PROMEDIO", "% DEL TOTAL");

        BigDecimal totalGeneral = ventas.stream()
                .filter(v -> !"ANULADO".equals(v.getEstado()))
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int rowIdx = 1;
        for (Map.Entry<String, List<Venta>> entry : ventasPorUsuario.entrySet()) {
            List<Venta> ventasUsuario = entry.getValue().stream()
                    .filter(v -> !"ANULADO".equals(v.getEstado()))
                    .collect(Collectors.toList());

            if (ventasUsuario.isEmpty()) continue;

            BigDecimal totalUsuario = ventasUsuario.stream()
                    .map(Venta::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal promedio = totalUsuario.divide(new BigDecimal(ventasUsuario.size()), 2, RoundingMode.HALF_UP);
            BigDecimal porcentaje = totalGeneral.compareTo(BigDecimal.ZERO) > 0 ?
                    totalUsuario.divide(totalGeneral, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;

            String nombreCompleto = ventasUsuario.get(0).getUsuario().getNombreCompleto();

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, entry.getKey(), dataStyle);
            crearCelda(row, 1, nombreCompleto, dataStyle);
            crearCelda(row, 2, String.valueOf(ventasUsuario.size()), dataStyle);
            crearCelda(row, 3, moneda + " " + totalUsuario.setScale(2, RoundingMode.HALF_UP), moneyStyle);
            crearCelda(row, 4, moneda + " " + promedio, moneyStyle);
            crearCelda(row, 5, porcentaje.setScale(2, RoundingMode.HALF_UP) + "%", dataStyle);
        }

        autoSizeColumns(sheet, 6);
        workbook.write(outputStream);
        workbook.close();
    }

    /**
     * Genera reporte Excel de ventas por método de pago
     */
    public void generarExcelVentasPorMetodoPago(LocalDate inicio, LocalDate fin, String metodoPago, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ventas por Método Pago");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        // Filtrar por método de pago si se especifica
        if (metodoPago != null && !metodoPago.isEmpty()) {
            ventas = ventas.stream()
                    .filter(v -> metodoPago.equalsIgnoreCase(v.getMetodoPago()))
                    .collect(Collectors.toList());
        }

        // Agrupar por método de pago
        Map<String, List<Venta>> ventasPorMetodo = ventas.stream()
                .filter(v -> !"ANULADO".equals(v.getEstado()))
                .collect(Collectors.groupingBy(v -> v.getMetodoPago() != null ? v.getMetodoPago() : "EFECTIVO"));

        crearFilaCabecera(sheet, headerStyle, "MÉTODO DE PAGO", "CANT. VENTAS", "TOTAL VENTAS", "% DEL TOTAL");

        BigDecimal totalGeneral = ventas.stream()
                .filter(v -> !"ANULADO".equals(v.getEstado()))
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        int rowIdx = 1;
        for (Map.Entry<String, List<Venta>> entry : ventasPorMetodo.entrySet()) {
            BigDecimal totalMetodo = entry.getValue().stream()
                    .map(Venta::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal porcentaje = totalGeneral.compareTo(BigDecimal.ZERO) > 0 ?
                    totalMetodo.divide(totalGeneral, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;

            Row row = sheet.createRow(rowIdx++);
            crearCelda(row, 0, entry.getKey(), dataStyle);
            crearCelda(row, 1, String.valueOf(entry.getValue().size()), dataStyle);
            crearCelda(row, 2, moneda + " " + totalMetodo.setScale(2, RoundingMode.HALF_UP), moneyStyle);
            crearCelda(row, 3, porcentaje.setScale(2, RoundingMode.HALF_UP) + "%", dataStyle);
        }

        // Fila de total
        Row totalRow = sheet.createRow(rowIdx);
        crearCelda(totalRow, 1, "TOTAL:", headerStyle);
        crearCelda(totalRow, 2, moneda + " " + totalGeneral.setScale(2, RoundingMode.HALF_UP), headerStyle);

        autoSizeColumns(sheet, 4);
        workbook.write(outputStream);
        workbook.close();
    }

    /**
     * Genera reporte Excel de ventas con detalle de ganancia
     */
    public void generarExcelVentasConGanancia(LocalDate inicio, LocalDate fin, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ventas con Ganancia");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle moneyStyle = crearEstiloMoneda(workbook);
        CellStyle successStyle = crearEstiloExito(workbook);

        crearFilaCabecera(sheet, headerStyle, "FECHA", "COMPROBANTE", "PRODUCTO", "CANT", "P.COMPRA", "P.VENTA", "COSTO", "INGRESO", "GANANCIA", "% MARGEN");

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        int rowIdx = 1;
        BigDecimal totalGanancia = BigDecimal.ZERO;
        BigDecimal totalCosto = BigDecimal.ZERO;
        BigDecimal totalIngreso = BigDecimal.ZERO;

        for (Venta v : ventas) {
            if ("ANULADO".equals(v.getEstado()) || v.getItems() == null) continue;

            for (var item : v.getItems()) {
                Producto prod = item.getProducto();
                BigDecimal precioCompra = prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO;
                BigDecimal costo = precioCompra.multiply(item.getCantidad());
                BigDecimal ingreso = item.getSubtotal();
                BigDecimal ganancia = ingreso.subtract(costo);

                BigDecimal margen = ingreso.compareTo(BigDecimal.ZERO) > 0 ?
                        ganancia.divide(ingreso, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;

                Row row = sheet.createRow(rowIdx++);
                crearCelda(row, 0, v.getFechaEmision().format(formatter), dataStyle);
                crearCelda(row, 1, v.getSerie() + "-" + v.getNumero(), dataStyle);
                crearCelda(row, 2, prod.getNombre(), dataStyle);
                crearCelda(row, 3, item.getCantidad().toString(), dataStyle);
                crearCelda(row, 4, moneda + " " + precioCompra, moneyStyle);
                crearCelda(row, 5, moneda + " " + item.getPrecioUnitario(), moneyStyle);
                crearCelda(row, 6, moneda + " " + costo.setScale(2, RoundingMode.HALF_UP), moneyStyle);
                crearCelda(row, 7, moneda + " " + ingreso.setScale(2, RoundingMode.HALF_UP), moneyStyle);
                crearCelda(row, 8, moneda + " " + ganancia.setScale(2, RoundingMode.HALF_UP), successStyle);
                crearCelda(row, 9, margen.setScale(2, RoundingMode.HALF_UP) + "%", dataStyle);

                totalCosto = totalCosto.add(costo);
                totalIngreso = totalIngreso.add(ingreso);
                totalGanancia = totalGanancia.add(ganancia);
            }
        }

        // Fila de totales
        Row totalRow = sheet.createRow(rowIdx);
        crearCelda(totalRow, 5, "TOTALES:", headerStyle);
        crearCelda(totalRow, 6, moneda + " " + totalCosto.setScale(2, RoundingMode.HALF_UP), headerStyle);
        crearCelda(totalRow, 7, moneda + " " + totalIngreso.setScale(2, RoundingMode.HALF_UP), headerStyle);
        crearCelda(totalRow, 8, moneda + " " + totalGanancia.setScale(2, RoundingMode.HALF_UP), headerStyle);

        BigDecimal margenTotal = totalIngreso.compareTo(BigDecimal.ZERO) > 0 ?
                totalGanancia.divide(totalIngreso, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;
        crearCelda(totalRow, 9, margenTotal.setScale(2, RoundingMode.HALF_UP) + "%", headerStyle);

        autoSizeColumns(sheet, 10);
        workbook.write(outputStream);
        workbook.close();
    }

    // ==========================================
    //        REPORTES DE KARDEX - EXCEL
    // ==========================================

    /**
     * Genera reporte Excel de movimientos Kardex de un producto específico
     */
    public void generarExcelKardexProducto(Long productoId, LocalDate inicio, LocalDate fin, OutputStream outputStream) throws Exception {
        var config = configuracionService.obtenerConfiguracion();
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Kardex");

        CellStyle headerStyle = crearEstiloCabecera(workbook);
        CellStyle dataStyle = crearEstiloDatos(workbook);
        CellStyle entradaStyle = crearEstiloExito(workbook);
        CellStyle salidaStyle = crearEstiloAlerta(workbook);

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            workbook.close();
            return;
        }

        // Encabezado con datos del producto
        Row titleRow = sheet.createRow(0);
        crearCelda(titleRow, 0, "KARDEX DE PRODUCTO", headerStyle);

        Row prodRow1 = sheet.createRow(1);
        crearCelda(prodRow1, 0, "Código:", dataStyle);
        crearCelda(prodRow1, 1, producto.getCodigoInterno(), dataStyle);
        crearCelda(prodRow1, 2, "Producto:", dataStyle);
        crearCelda(prodRow1, 3, producto.getNombre(), dataStyle);

        Row prodRow2 = sheet.createRow(2);
        crearCelda(prodRow2, 0, "Stock Actual:", dataStyle);
        crearCelda(prodRow2, 1, String.valueOf(producto.getStockActual()), dataStyle);

        // Cabecera de tabla
        Row headerRow = sheet.createRow(4);
        String[] headers = {"FECHA", "TIPO", "MOTIVO", "CANTIDAD", "STOCK ANTERIOR", "STOCK ACTUAL"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        // Obtener movimientos del kardex
        List<Kardex> movimientos = kardexRepository.findAll().stream()
                .filter(k -> k.getProducto().getId().equals(productoId))
                .filter(k -> {
                    LocalDate fechaMovimiento = k.getFecha().toLocalDate();
                    return !fechaMovimiento.isBefore(inicio) && !fechaMovimiento.isAfter(fin);
                })
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        int rowIdx = 5;
        for (Kardex k : movimientos) {
            Row row = sheet.createRow(rowIdx++);
            CellStyle tipoStyle = "ENTRADA".equals(k.getTipo()) ? entradaStyle : salidaStyle;

            crearCelda(row, 0, k.getFecha().format(formatter), dataStyle);
            crearCelda(row, 1, k.getTipo(), tipoStyle);
            crearCelda(row, 2, k.getMotivo(), dataStyle);
            crearCelda(row, 3, String.valueOf(k.getCantidad()), dataStyle);
            crearCelda(row, 4, String.valueOf(k.getStockAnterior()), dataStyle);
            crearCelda(row, 5, String.valueOf(k.getStockActual()), dataStyle);
        }

        autoSizeColumns(sheet, 6);
        workbook.write(outputStream);
        workbook.close();
    }

    // ==========================================
    //        REPORTES DE PRODUCTOS - PDF
    // ==========================================

    /**
     * Genera PDF de stock actual
     */
    public void generarPdfStockActual(OutputStream outputStream) throws DocumentException {
        var config = configuracionService.obtenerConfiguracion();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        agregarCabeceraPdf(document, config, "REPORTE DE STOCK ACTUAL");

        PdfPTable table = new PdfPTable(7);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2, 4, 2, 1.5f, 2, 2, 2});

        agregarCabeceraTablaPdf(table, config, "CÓDIGO", "PRODUCTO", "CATEGORÍA", "STOCK", "P.COMPRA", "P.VENTA", "VALORIZADO");

        List<Producto> productos = productoRepository.findByActivoTrue();
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        BigDecimal totalValorizado = BigDecimal.ZERO;
        for (Producto p : productos) {
            BigDecimal precioCompra = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
            BigDecimal valorizado = precioCompra.multiply(new BigDecimal(p.getStockActual()));
            totalValorizado = totalValorizado.add(valorizado);

            table.addCell(crearCeldaPdf(p.getCodigoInterno()));
            table.addCell(crearCeldaPdf(p.getNombre()));
            table.addCell(crearCeldaPdf(p.getCategoria() != null ? p.getCategoria() : "-"));
            table.addCell(crearCeldaPdfCenter(String.valueOf(p.getStockActual())));
            table.addCell(crearCeldaPdfRight(moneda + " " + precioCompra));
            table.addCell(crearCeldaPdfRight(moneda + " " + p.getPrecioVenta()));
            table.addCell(crearCeldaPdfRight(moneda + " " + valorizado.setScale(2, RoundingMode.HALF_UP)));
        }

        document.add(table);

        // Total
        Paragraph pTotal = new Paragraph("TOTAL VALORIZADO: " + moneda + " " + totalValorizado.setScale(2, RoundingMode.HALF_UP),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        pTotal.setAlignment(Element.ALIGN_RIGHT);
        pTotal.setSpacingBefore(10);
        document.add(pTotal);

        document.close();
    }

    /**
     * Genera PDF de productos más vendidos
     */
    public void generarPdfProductosMasVendidos(LocalDate inicio, LocalDate fin, OutputStream outputStream) throws DocumentException {
        var config = configuracionService.obtenerConfiguracion();
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        agregarCabeceraPdf(document, config, "PRODUCTOS MÁS VENDIDOS");
        agregarPeriodoPdf(document, inicio, fin);

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1, 2, 4, 2, 2});

        agregarCabeceraTablaPdf(table, config, "#", "CÓDIGO", "PRODUCTO", "CANT. VENDIDA", "TOTAL");

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";

        Map<Long, BigDecimal[]> productosVendidos = new LinkedHashMap<>();
        for (Venta v : ventas) {
            if (v.getItems() != null) {
                for (var item : v.getItems()) {
                    Long prodId = item.getProducto().getId();
                    BigDecimal[] datos = productosVendidos.getOrDefault(prodId, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                    datos[0] = datos[0].add(item.getCantidad());
                    datos[1] = datos[1].add(item.getSubtotal());
                    productosVendidos.put(prodId, datos);
                }
            }
        }

        List<Map.Entry<Long, BigDecimal[]>> sortedList = new ArrayList<>(productosVendidos.entrySet());
        sortedList.sort((a, b) -> b.getValue()[0].compareTo(a.getValue()[0]));

        int ranking = 1;
        for (Map.Entry<Long, BigDecimal[]> entry : sortedList) {
            if (ranking > 20) break; // Top 20
            Producto prod = productoRepository.findById(entry.getKey()).orElse(null);
            if (prod == null) continue;

            table.addCell(crearCeldaPdfCenter(String.valueOf(ranking++)));
            table.addCell(crearCeldaPdf(prod.getCodigoInterno()));
            table.addCell(crearCeldaPdf(prod.getNombre()));
            table.addCell(crearCeldaPdfCenter(entry.getValue()[0].toString()));
            table.addCell(crearCeldaPdfRight(moneda + " " + entry.getValue()[1].setScale(2, RoundingMode.HALF_UP)));
        }

        document.add(table);
        document.close();
    }

    /**
     * Genera PDF de ventas con ganancia
     */
    public void generarPdfVentasConGanancia(LocalDate inicio, LocalDate fin, OutputStream outputStream) throws DocumentException {
        var config = configuracionService.obtenerConfiguracion();
        Document document = new Document(PageSize.A4.rotate(), 20, 20, 20, 20);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        agregarCabeceraPdf(document, config, "REPORTE DE GANANCIA POR VENTAS");
        agregarPeriodoPdf(document, inicio, fin);

        PdfPTable table = new PdfPTable(8);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.5f, 2, 3, 1, 1.5f, 1.5f, 1.5f, 1.5f});

        agregarCabeceraTablaPdf(table, config, "FECHA", "DOC", "PRODUCTO", "CANT", "COSTO", "VENTA", "GANANCIA", "MARGEN");

        List<Venta> ventas = ventaRepository.findByFechaEmisionBetween(inicio, fin);
        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        BigDecimal totalGanancia = BigDecimal.ZERO;

        for (Venta v : ventas) {
            if ("ANULADO".equals(v.getEstado()) || v.getItems() == null) continue;

            for (var item : v.getItems()) {
                Producto prod = item.getProducto();
                BigDecimal precioCompra = prod.getPrecioCompra() != null ? prod.getPrecioCompra() : BigDecimal.ZERO;
                BigDecimal costo = precioCompra.multiply(item.getCantidad());
                BigDecimal ingreso = item.getSubtotal();
                BigDecimal ganancia = ingreso.subtract(costo);

                BigDecimal margen = ingreso.compareTo(BigDecimal.ZERO) > 0 ?
                        ganancia.divide(ingreso, 4, RoundingMode.HALF_UP).multiply(new BigDecimal("100")) : BigDecimal.ZERO;

                table.addCell(crearCeldaPdf(v.getFechaEmision().format(formatter)));
                table.addCell(crearCeldaPdf(v.getSerie() + "-" + v.getNumero()));
                table.addCell(crearCeldaPdf(prod.getNombre().length() > 25 ? prod.getNombre().substring(0, 25) + "..." : prod.getNombre()));
                table.addCell(crearCeldaPdfCenter(item.getCantidad().toString()));
                table.addCell(crearCeldaPdfRight(moneda + " " + costo.setScale(2, RoundingMode.HALF_UP)));
                table.addCell(crearCeldaPdfRight(moneda + " " + ingreso.setScale(2, RoundingMode.HALF_UP)));

                // Color según ganancia positiva/negativa
                PdfPCell cellGanancia = new PdfPCell(new Phrase(moneda + " " + ganancia.setScale(2, RoundingMode.HALF_UP),
                        FontFactory.getFont(FontFactory.HELVETICA, 8, ganancia.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0) : Color.RED)));
                cellGanancia.setHorizontalAlignment(Element.ALIGN_RIGHT);
                cellGanancia.setPadding(3);
                table.addCell(cellGanancia);

                table.addCell(crearCeldaPdfCenter(margen.setScale(1, RoundingMode.HALF_UP) + "%"));

                totalGanancia = totalGanancia.add(ganancia);
            }
        }

        document.add(table);

        Paragraph pTotal = new Paragraph("GANANCIA TOTAL: " + moneda + " " + totalGanancia.setScale(2, RoundingMode.HALF_UP),
                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, totalGanancia.compareTo(BigDecimal.ZERO) >= 0 ? new Color(0, 128, 0) : Color.RED));
        pTotal.setAlignment(Element.ALIGN_RIGHT);
        pTotal.setSpacingBefore(10);
        document.add(pTotal);

        document.close();
    }

    /**
     * Genera PDF de Kardex de un producto
     */
    public void generarPdfKardexProducto(Long productoId, LocalDate inicio, LocalDate fin, OutputStream outputStream) throws DocumentException {
        var config = configuracionService.obtenerConfiguracion();
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Producto producto = productoRepository.findById(productoId).orElse(null);
        if (producto == null) {
            document.add(new Paragraph("Producto no encontrado"));
            document.close();
            return;
        }

        agregarCabeceraPdf(document, config, "KARDEX DE PRODUCTO");

        // Info del producto
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(60);
        infoTable.setHorizontalAlignment(Element.ALIGN_LEFT);

        infoTable.addCell(crearCeldaPdf("Código:"));
        infoTable.addCell(crearCeldaPdf(producto.getCodigoInterno()));
        infoTable.addCell(crearCeldaPdf("Producto:"));
        infoTable.addCell(crearCeldaPdf(producto.getNombre()));
        infoTable.addCell(crearCeldaPdf("Stock Actual:"));
        infoTable.addCell(crearCeldaPdf(String.valueOf(producto.getStockActual())));

        document.add(infoTable);
        document.add(new Paragraph(" "));

        agregarPeriodoPdf(document, inicio, fin);

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.5f, 1.5f, 4, 1.5f, 1.5f, 1.5f});

        agregarCabeceraTablaPdf(table, config, "FECHA", "TIPO", "MOTIVO", "CANTIDAD", "ANTERIOR", "ACTUAL");

        List<Kardex> movimientos = kardexRepository.findAll().stream()
                .filter(k -> k.getProducto().getId().equals(productoId))
                .filter(k -> {
                    LocalDate fechaMovimiento = k.getFecha().toLocalDate();
                    return !fechaMovimiento.isBefore(inicio) && !fechaMovimiento.isAfter(fin);
                })
                .sorted((a, b) -> b.getFecha().compareTo(a.getFecha()))
                .collect(Collectors.toList());

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

        for (Kardex k : movimientos) {
            table.addCell(crearCeldaPdf(k.getFecha().format(formatter)));

            // Celda de tipo con color
            Color colorTipo = "ENTRADA".equals(k.getTipo()) ? new Color(0, 128, 0) : Color.RED;
            PdfPCell cellTipo = new PdfPCell(new Phrase(k.getTipo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, colorTipo)));
            cellTipo.setPadding(3);
            table.addCell(cellTipo);

            table.addCell(crearCeldaPdf(k.getMotivo()));
            table.addCell(crearCeldaPdfCenter(String.valueOf(k.getCantidad())));
            table.addCell(crearCeldaPdfCenter(String.valueOf(k.getStockAnterior())));
            table.addCell(crearCeldaPdfCenter(String.valueOf(k.getStockActual())));
        }

        document.add(table);
        document.close();
    }

    // ==========================================
    //        UTILIDADES EXCEL
    // ==========================================

    private CellStyle crearEstiloCabecera(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setBold(true);
        font.setColor(IndexedColors.WHITE.getIndex());
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        return style;
    }

    private CellStyle crearEstiloDatos(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }

    private CellStyle crearEstiloMoneda(Workbook workbook) {
        CellStyle style = crearEstiloDatos(workbook);
        style.setAlignment(HorizontalAlignment.RIGHT);
        return style;
    }

    private CellStyle crearEstiloAlerta(Workbook workbook) {
        CellStyle style = crearEstiloDatos(workbook);
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setColor(IndexedColors.RED.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private CellStyle crearEstiloExito(Workbook workbook) {
        CellStyle style = crearEstiloDatos(workbook);
        org.apache.poi.ss.usermodel.Font font = workbook.createFont();
        font.setColor(IndexedColors.GREEN.getIndex());
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void crearFilaCabecera(Sheet sheet, CellStyle style, String... headers) {
        Row header = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = header.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(style);
        }
    }

    private void crearCelda(Row row, int column, String valor, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(valor != null ? valor : "");
        cell.setCellStyle(style);
    }

    private void autoSizeColumns(Sheet sheet, int numColumns) {
        for (int i = 0; i < numColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    // ==========================================
    //        UTILIDADES PDF
    // ==========================================

    private void agregarCabeceraPdf(Document document, Configuracion config, String titulo) throws DocumentException {
        Paragraph pTitulo = new Paragraph(titulo, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
        pTitulo.setAlignment(Element.ALIGN_CENTER);
        document.add(pTitulo);

        Paragraph pEmpresa = new Paragraph(config.getNombreEmpresa() + " - RUC: " + config.getRuc(),
                FontFactory.getFont(FontFactory.HELVETICA, 10));
        pEmpresa.setAlignment(Element.ALIGN_CENTER);
        document.add(pEmpresa);

        Paragraph pFecha = new Paragraph("Generado: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY));
        pFecha.setAlignment(Element.ALIGN_CENTER);
        document.add(pFecha);

        document.add(new Paragraph(" "));
    }

    private void agregarPeriodoPdf(Document document, LocalDate inicio, LocalDate fin) throws DocumentException {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        Paragraph pPeriodo = new Paragraph("Período: " + inicio.format(formatter) + " al " + fin.format(formatter),
                FontFactory.getFont(FontFactory.HELVETICA, 10));
        pPeriodo.setAlignment(Element.ALIGN_CENTER);
        document.add(pPeriodo);
        document.add(new Paragraph(" "));
    }

    private void agregarCabeceraTablaPdf(PdfPTable table, Configuracion config, String... headers) {
        Color colorOscuro = parseColor(config.getColorOscuro(), Color.DARK_GRAY);
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.WHITE)));
            cell.setBackgroundColor(colorOscuro);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
    }

    private PdfPCell crearCeldaPdf(String texto) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", FontFactory.getFont(FontFactory.HELVETICA, 8)));
        cell.setPadding(3);
        return cell;
    }

    private PdfPCell crearCeldaPdfRight(String texto) {
        PdfPCell cell = crearCeldaPdf(texto);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell crearCeldaPdfCenter(String texto) {
        PdfPCell cell = crearCeldaPdf(texto);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    private Color parseColor(String hexColor, Color defaultColor) {
        if (hexColor == null || hexColor.trim().isEmpty()) return defaultColor;
        try {
            String hex = hexColor.replace("#", "");
            return new Color(
                    Integer.parseInt(hex.substring(0, 2), 16),
                    Integer.parseInt(hex.substring(2, 4), 16),
                    Integer.parseInt(hex.substring(4, 6), 16)
            );
        } catch (Exception e) {
            return defaultColor;
        }
    }

    // ==========================================
    //        MÉTODOS AUXILIARES
    // ==========================================

    /**
     * Obtiene lista de usuarios para el select de filtros
     */
    public List<Usuario> obtenerUsuarios() {
        return usuarioRepository.findAll();
    }

    /**
     * Obtiene lista de categorías para el select de filtros
     */
    public List<String> obtenerCategorias() {
        return productoRepository.findDistinctCategorias();
    }

    /**
     * Obtiene lista de productos para el select de Kardex
     */
    public List<Producto> obtenerProductosActivos() {
        return productoRepository.findByActivoTrue();
    }
}
