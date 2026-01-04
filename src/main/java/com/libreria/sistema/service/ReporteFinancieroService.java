package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.servlet.http.HttpServletResponse;
import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReporteFinancieroService {

    @Autowired
    private VentaRepository ventaRepository;

    @Autowired
    private CompraRepository compraRepository;

    @Autowired
    private MovimientoCajaRepository movimientoCajaRepository;

    @Autowired
    private AmortizacionRepository amortizacionRepository;

    @Autowired
    private DevolucionVentaRepository devolucionRepository;

    @Autowired
    private ProductoRepository productoRepository;

    @Autowired
    private ConfiguracionService configuracionService;

    /**
     * Generar flujo de caja con ingresos y egresos
     */
    public Map<String, Object> generarFlujoCaja(LocalDate fechaInicio, LocalDate fechaFin) {
        LocalDateTime inicio = fechaInicio.atStartOfDay();
        LocalDateTime fin = fechaFin.atTime(23, 59, 59);

        Map<String, Object> resultado = new HashMap<>();
        List<Map<String, Object>> detalleIngresos = new ArrayList<>();
        List<Map<String, Object>> detalleEgresos = new ArrayList<>();

        BigDecimal totalIngresos = BigDecimal.ZERO;
        BigDecimal totalEgresos = BigDecimal.ZERO;

        // === INGRESOS ===

        // 1. Ventas al contado
        List<Venta> ventasContado = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null &&
                        !v.getFechaEmision().isBefore(fechaInicio) &&
                        !v.getFechaEmision().isAfter(fechaFin) &&
                        "CONTADO".equals(v.getFormaPago()) &&
                        !"ANULADO".equals(v.getEstado()))
                .collect(Collectors.toList());

        BigDecimal ingresoVentasContado = ventasContado.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleIngresos.add(Map.of(
                "concepto", "Ventas al Contado",
                "cantidad", ventasContado.size(),
                "monto", ingresoVentasContado
        ));
        totalIngresos = totalIngresos.add(ingresoVentasContado);

        // 2. Amortizaciones (pagos de crédito)
        List<Amortizacion> amortizaciones = amortizacionRepository.findAll().stream()
                .filter(a -> a.getFechaPago() != null &&
                        !a.getFechaPago().isBefore(inicio) &&
                        !a.getFechaPago().isAfter(fin))
                .collect(Collectors.toList());

        BigDecimal ingresoAmortizaciones = amortizaciones.stream()
                .map(Amortizacion::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleIngresos.add(Map.of(
                "concepto", "Cobros de Crédito (Amortizaciones)",
                "cantidad", amortizaciones.size(),
                "monto", ingresoAmortizaciones
        ));
        totalIngresos = totalIngresos.add(ingresoAmortizaciones);

        // 3. Otros ingresos (MovimientoCaja tipo INGRESO)
        List<MovimientoCaja> otrosIngresos = movimientoCajaRepository.findAll().stream()
                .filter(m -> "INGRESO".equals(m.getTipo()) &&
                        m.getFecha() != null &&
                        !m.getFecha().isBefore(inicio) &&
                        !m.getFecha().isAfter(fin))
                .collect(Collectors.toList());

        BigDecimal montoOtrosIngresos = otrosIngresos.stream()
                .map(MovimientoCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleIngresos.add(Map.of(
                "concepto", "Otros Ingresos",
                "cantidad", otrosIngresos.size(),
                "monto", montoOtrosIngresos
        ));
        totalIngresos = totalIngresos.add(montoOtrosIngresos);

        // === EGRESOS ===

        // 1. Compras (excluir anuladas)
        List<Compra> compras = compraRepository.findAll().stream()
                .filter(c -> c.getFecha() != null &&
                        !c.getFecha().toLocalDate().isBefore(fechaInicio) &&
                        !c.getFecha().toLocalDate().isAfter(fechaFin) &&
                        !"ANULADA".equals(c.getEstado()))
                .collect(Collectors.toList());

        BigDecimal egresoCompras = compras.stream()
                .map(Compra::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleEgresos.add(Map.of(
                "concepto", "Compras a Proveedores",
                "cantidad", compras.size(),
                "monto", egresoCompras
        ));
        totalEgresos = totalEgresos.add(egresoCompras);

        // 2. Devoluciones (reembolsos en efectivo)
        List<DevolucionVenta> devoluciones = devolucionRepository.findAll().stream()
                .filter(d -> d.getFechaCreacion() != null &&
                        !d.getFechaCreacion().toLocalDate().isBefore(fechaInicio) &&
                        !d.getFechaCreacion().toLocalDate().isAfter(fechaFin) &&
                        "EFECTIVO".equals(d.getMetodoReembolso()) &&
                        "PROCESADA".equals(d.getEstado()))
                .collect(Collectors.toList());

        BigDecimal egresoDevoluciones = devoluciones.stream()
                .map(DevolucionVenta::getTotalDevuelto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleEgresos.add(Map.of(
                "concepto", "Devoluciones (Reembolsos)",
                "cantidad", devoluciones.size(),
                "monto", egresoDevoluciones
        ));
        totalEgresos = totalEgresos.add(egresoDevoluciones);

        // 3. Otros egresos (MovimientoCaja tipo EGRESO)
        List<MovimientoCaja> otrosEgresos = movimientoCajaRepository.findAll().stream()
                .filter(m -> "EGRESO".equals(m.getTipo()) &&
                        m.getFecha() != null &&
                        !m.getFecha().isBefore(inicio) &&
                        !m.getFecha().isAfter(fin))
                .collect(Collectors.toList());

        BigDecimal montoOtrosEgresos = otrosEgresos.stream()
                .map(MovimientoCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        detalleEgresos.add(Map.of(
                "concepto", "Gastos Administrativos",
                "cantidad", otrosEgresos.size(),
                "monto", montoOtrosEgresos
        ));
        totalEgresos = totalEgresos.add(montoOtrosEgresos);

        // === RESULTADO ===
        BigDecimal saldo = totalIngresos.subtract(totalEgresos);

        resultado.put("totalIngresos", totalIngresos);
        resultado.put("totalEgresos", totalEgresos);
        resultado.put("saldo", saldo);
        resultado.put("detalleIngresos", detalleIngresos);
        resultado.put("detalleEgresos", detalleEgresos);
        resultado.put("fechaInicio", fechaInicio);
        resultado.put("fechaFin", fechaFin);

        return resultado;
    }

    /**
     * Generar rentabilidad por productos
     */
    public List<Map<String, Object>> generarRentabilidadProductos(LocalDate fechaInicio, LocalDate fechaFin) {
        List<Map<String, Object>> rentabilidad = new ArrayList<>();

        // Obtener todas las ventas en el rango (no anuladas)
        List<Venta> ventas = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null &&
                        !v.getFechaEmision().isBefore(fechaInicio) &&
                        !v.getFechaEmision().isAfter(fechaFin) &&
                        !"ANULADO".equals(v.getEstado()) &&
                        !"DEVUELTO_TOTAL".equals(v.getEstado()))
                .collect(Collectors.toList());

        // Agrupar productos vendidos
        Map<Long, List<DetalleVenta>> productoVentas = new HashMap<>();
        for (Venta venta : ventas) {
            for (DetalleVenta detalle : venta.getItems()) {
                Producto producto = detalle.getProducto();
                if (producto != null) {
                    productoVentas.computeIfAbsent(producto.getId(), k -> new ArrayList<>()).add(detalle);
                }
            }
        }

        // Calcular rentabilidad por producto
        for (Map.Entry<Long, List<DetalleVenta>> entry : productoVentas.entrySet()) {
            Long productoId = entry.getKey();
            List<DetalleVenta> detalles = entry.getValue();

            if (detalles.isEmpty()) continue;

            Producto producto = detalles.get(0).getProducto();

            // Cantidad vendida
            int cantidadVendida = detalles.stream()
                    .mapToInt(d -> d.getCantidad().intValue())
                    .sum();

            // Precio venta promedio
            BigDecimal precioVentaPromedio = detalles.stream()
                    .map(DetalleVenta::getPrecioUnitario)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(detalles.size()), 2, RoundingMode.HALF_UP);

            // Precio compra (del producto actual)
            BigDecimal precioCompra = producto.getPrecioCompra() != null
                    ? producto.getPrecioCompra()
                    : BigDecimal.ZERO;

            // Margen bruto
            BigDecimal margenBruto = precioVentaPromedio.subtract(precioCompra);

            // Margen %
            BigDecimal margenPorcentaje = precioVentaPromedio.compareTo(BigDecimal.ZERO) > 0
                    ? margenBruto.divide(precioVentaPromedio, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                    : BigDecimal.ZERO;

            // Ganancia total
            BigDecimal gananciaTotal = margenBruto.multiply(BigDecimal.valueOf(cantidadVendida));

            // Total vendido
            BigDecimal totalVendido = precioVentaPromedio.multiply(BigDecimal.valueOf(cantidadVendida));

            Map<String, Object> item = new HashMap<>();
            item.put("productoId", productoId);
            item.put("productoNombre", producto.getNombre());
            item.put("productoCategoria", producto.getCategoria());
            item.put("cantidadVendida", cantidadVendida);
            item.put("precioCompra", precioCompra);
            item.put("precioVentaPromedio", precioVentaPromedio);
            item.put("margenBruto", margenBruto);
            item.put("margenPorcentaje", margenPorcentaje);
            item.put("gananciaTotal", gananciaTotal);
            item.put("totalVendido", totalVendido);

            rentabilidad.add(item);
        }

        // Ordenar por ganancia total descendente
        rentabilidad.sort((a, b) ->
                ((BigDecimal) b.get("gananciaTotal")).compareTo((BigDecimal) a.get("gananciaTotal"))
        );

        return rentabilidad;
    }

    /**
     * Generar análisis de ventas
     */
    public Map<String, Object> generarAnalisisVentas(LocalDate fechaInicio, LocalDate fechaFin) {
        Map<String, Object> resultado = new HashMap<>();

        // Obtener ventas del periodo
        List<Venta> ventas = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null &&
                        !v.getFechaEmision().isBefore(fechaInicio) &&
                        !v.getFechaEmision().isAfter(fechaFin) &&
                        !"ANULADO".equals(v.getEstado()))
                .collect(Collectors.toList());

        // 1. Ventas por día (serie temporal)
        Map<LocalDate, BigDecimal> ventasPorDia = new TreeMap<>();
        for (Venta venta : ventas) {
            LocalDate fecha = venta.getFechaEmision();
            ventasPorDia.merge(fecha, venta.getTotal(), BigDecimal::add);
        }

        List<Map<String, Object>> serieTemporal = new ArrayList<>();
        for (Map.Entry<LocalDate, BigDecimal> entry : ventasPorDia.entrySet()) {
            serieTemporal.add(Map.of(
                    "fecha", entry.getKey(),
                    "total", entry.getValue()
            ));
        }
        resultado.put("ventasPorDia", serieTemporal);

        // 2. Ventas por usuario/vendedor
        Map<String, BigDecimal> ventasPorUsuario = new HashMap<>();
        Map<String, Integer> cantidadPorUsuario = new HashMap<>();
        for (Venta venta : ventas) {
            String usuario = venta.getUsuario() != null
                    ? venta.getUsuario().getNombreCompleto()
                    : "Sin asignar";
            ventasPorUsuario.merge(usuario, venta.getTotal(), BigDecimal::add);
            cantidadPorUsuario.merge(usuario, 1, Integer::sum);
        }

        List<Map<String, Object>> ventasPorVendedor = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : ventasPorUsuario.entrySet()) {
            ventasPorVendedor.add(Map.of(
                    "vendedor", entry.getKey(),
                    "total", entry.getValue(),
                    "cantidad", cantidadPorUsuario.get(entry.getKey())
            ));
        }
        ventasPorVendedor.sort((a, b) ->
                ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total"))
        );
        resultado.put("ventasPorVendedor", ventasPorVendedor);

        // 3. Productos más vendidos (top 10)
        Map<Long, Integer> productosVendidos = new HashMap<>();
        Map<Long, String> nombresProductos = new HashMap<>();
        Map<Long, BigDecimal> totalesProductos = new HashMap<>();

        for (Venta venta : ventas) {
            for (DetalleVenta detalle : venta.getItems()) {
                Producto producto = detalle.getProducto();
                if (producto != null) {
                    productosVendidos.merge(producto.getId(), detalle.getCantidad().intValue(), Integer::sum);
                    nombresProductos.put(producto.getId(), producto.getNombre());
                    totalesProductos.merge(producto.getId(), detalle.getSubtotal(), BigDecimal::add);
                }
            }
        }

        List<Map<String, Object>> topProductos = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productosVendidos.entrySet()) {
            topProductos.add(Map.of(
                    "productoId", entry.getKey(),
                    "productoNombre", nombresProductos.get(entry.getKey()),
                    "cantidadVendida", entry.getValue(),
                    "totalVendido", totalesProductos.get(entry.getKey())
            ));
        }
        topProductos.sort((a, b) ->
                ((Integer) b.get("cantidadVendida")).compareTo((Integer) a.get("cantidadVendida"))
        );
        resultado.put("topProductos", topProductos.stream().limit(10).collect(Collectors.toList()));

        // 4. Productos sin rotación (no vendidos en el periodo)
        Set<Long> productosConVentas = productosVendidos.keySet();
        List<Producto> todosProductos = productoRepository.findAll();
        List<Map<String, Object>> sinRotacion = todosProductos.stream()
                .filter(p -> p.isActivo() && !productosConVentas.contains(p.getId()))
                .map(p -> Map.of(
                        "productoId", (Object) p.getId(),
                        "productoNombre", (Object) p.getNombre(),
                        "categoria", (Object) (p.getCategoria() != null ? p.getCategoria() : "Sin categoría"),
                        "stockActual", (Object) p.getStockActual()
                ))
                .collect(Collectors.toList());
        resultado.put("productosSinRotacion", sinRotacion);

        // Totales generales
        resultado.put("totalVentas", ventas.size());
        resultado.put("montoTotalVentas", ventas.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return resultado;
    }

    /**
     * Generar dashboard financiero
     */
    public Map<String, Object> generarDashboardFinanciero() {
        Map<String, Object> dashboard = new HashMap<>();

        // Mes actual
        YearMonth mesActual = YearMonth.now();
        LocalDate inicioMes = mesActual.atDay(1);
        LocalDate finMes = mesActual.atEndOfMonth();

        // Mes anterior (año pasado mismo mes)
        YearMonth mesAnterior = YearMonth.now().minusYears(1);
        LocalDate inicioMesAnterior = mesAnterior.atDay(1);
        LocalDate finMesAnterior = mesAnterior.atEndOfMonth();

        // Total ventas mes actual
        List<Venta> ventasMesActual = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null &&
                        !v.getFechaEmision().isBefore(inicioMes) &&
                        !v.getFechaEmision().isAfter(finMes) &&
                        !"ANULADO".equals(v.getEstado()))
                .collect(Collectors.toList());

        BigDecimal totalVentasMesActual = ventasMesActual.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        dashboard.put("ventasMesActual", totalVentasMesActual);
        dashboard.put("cantidadVentasMesActual", ventasMesActual.size());

        // Total gastos mes actual (egresos + compras)
        BigDecimal gastosMes = BigDecimal.ZERO;

        List<Compra> comprasMes = compraRepository.findAll().stream()
                .filter(c -> c.getFecha() != null &&
                        !c.getFecha().toLocalDate().isBefore(inicioMes) &&
                        !c.getFecha().toLocalDate().isAfter(finMes) &&
                        !"ANULADA".equals(c.getEstado()))
                .collect(Collectors.toList());

        gastosMes = gastosMes.add(comprasMes.stream()
                .map(Compra::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        LocalDateTime inicioMesDT = inicioMes.atStartOfDay();
        LocalDateTime finMesDT = finMes.atTime(23, 59, 59);

        List<MovimientoCaja> egresosMes = movimientoCajaRepository.findAll().stream()
                .filter(m -> "EGRESO".equals(m.getTipo()) &&
                        m.getFecha() != null &&
                        !m.getFecha().isBefore(inicioMesDT) &&
                        !m.getFecha().isAfter(finMesDT))
                .collect(Collectors.toList());

        gastosMes = gastosMes.add(egresosMes.stream()
                .map(MovimientoCaja::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        dashboard.put("gastosMesActual", gastosMes);

        // Ganancia neta
        BigDecimal gananciaNeta = totalVentasMesActual.subtract(gastosMes);
        dashboard.put("gananciaNeta", gananciaNeta);

        // Ventas mismo mes año anterior
        List<Venta> ventasMesAnterior = ventaRepository.findAll().stream()
                .filter(v -> v.getFechaEmision() != null &&
                        !v.getFechaEmision().isBefore(inicioMesAnterior) &&
                        !v.getFechaEmision().isAfter(finMesAnterior) &&
                        !"ANULADO".equals(v.getEstado()))
                .collect(Collectors.toList());

        BigDecimal totalVentasMesAnterior = ventasMesAnterior.stream()
                .map(Venta::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Variación porcentual
        BigDecimal variacion = BigDecimal.ZERO;
        if (totalVentasMesAnterior.compareTo(BigDecimal.ZERO) > 0) {
            variacion = totalVentasMesActual.subtract(totalVentasMesAnterior)
                    .divide(totalVentasMesAnterior, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }
        dashboard.put("variacionAnual", variacion);

        // Top 5 productos del mes
        Map<Long, Integer> productosDelMes = new HashMap<>();
        Map<Long, String> nombresProductos = new HashMap<>();
        Map<Long, BigDecimal> totalesProductos = new HashMap<>();

        for (Venta venta : ventasMesActual) {
            for (DetalleVenta detalle : venta.getItems()) {
                Producto producto = detalle.getProducto();
                if (producto != null) {
                    productosDelMes.merge(producto.getId(), detalle.getCantidad().intValue(), Integer::sum);
                    nombresProductos.put(producto.getId(), producto.getNombre());
                    totalesProductos.merge(producto.getId(), detalle.getSubtotal(), BigDecimal::add);
                }
            }
        }

        List<Map<String, Object>> top5Productos = new ArrayList<>();
        for (Map.Entry<Long, Integer> entry : productosDelMes.entrySet()) {
            top5Productos.add(Map.of(
                    "nombre", nombresProductos.get(entry.getKey()),
                    "cantidad", entry.getValue(),
                    "total", totalesProductos.get(entry.getKey())
            ));
        }
        top5Productos.sort((a, b) ->
                ((Integer) b.get("cantidad")).compareTo((Integer) a.get("cantidad"))
        );
        dashboard.put("top5Productos", top5Productos.stream().limit(5).collect(Collectors.toList()));

        // Top 5 clientes del mes
        Map<Long, BigDecimal> clientesDelMes = new HashMap<>();
        Map<Long, String> nombresClientes = new HashMap<>();
        Map<Long, Integer> cantidadCompras = new HashMap<>();

        for (Venta venta : ventasMesActual) {
            Cliente cliente = venta.getClienteEntity();
            if (cliente != null) {
                clientesDelMes.merge(cliente.getId(), venta.getTotal(), BigDecimal::add);
                nombresClientes.put(cliente.getId(), cliente.getNombreRazonSocial());
                cantidadCompras.merge(cliente.getId(), 1, Integer::sum);
            }
        }

        List<Map<String, Object>> top5Clientes = new ArrayList<>();
        for (Map.Entry<Long, BigDecimal> entry : clientesDelMes.entrySet()) {
            top5Clientes.add(Map.of(
                    "nombre", nombresClientes.get(entry.getKey()),
                    "total", entry.getValue(),
                    "cantidad", cantidadCompras.get(entry.getKey())
            ));
        }
        top5Clientes.sort((a, b) ->
                ((BigDecimal) b.get("total")).compareTo((BigDecimal) a.get("total"))
        );
        dashboard.put("top5Clientes", top5Clientes.stream().limit(5).collect(Collectors.toList()));

        // Ventas vs Gastos últimos 12 meses (para gráfico)
        List<Map<String, Object>> ultimos12Meses = new ArrayList<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mes = YearMonth.now().minusMonths(i);
            LocalDate inicio = mes.atDay(1);
            LocalDate fin = mes.atEndOfMonth();

            BigDecimal ventasMes = ventaRepository.findAll().stream()
                    .filter(v -> v.getFechaEmision() != null &&
                            !v.getFechaEmision().isBefore(inicio) &&
                            !v.getFechaEmision().isAfter(fin) &&
                            !"ANULADO".equals(v.getEstado()))
                    .map(Venta::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal gastos = BigDecimal.ZERO;
            gastos = gastos.add(compraRepository.findAll().stream()
                    .filter(c -> c.getFecha() != null &&
                            !c.getFecha().toLocalDate().isBefore(inicio) &&
                            !c.getFecha().toLocalDate().isAfter(fin) &&
                            !"ANULADA".equals(c.getEstado()))
                    .map(Compra::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

            ultimos12Meses.add(Map.of(
                    "mes", mes.getMonth().toString() + " " + mes.getYear(),
                    "ventas", ventasMes,
                    "gastos", gastos
            ));
        }
        dashboard.put("ultimos12Meses", ultimos12Meses);

        return dashboard;
    }

    /**
     * Exportar flujo de caja a Excel
     */
    public void exportarFlujoCajaExcel(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarFlujoCaja(fechaInicio, fechaFin);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Flujo de Caja");

        // Estilos
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("\"S/ \"#,##0.00"));

        // Título
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("FLUJO DE CAJA");
        titleCell.setCellStyle(headerStyle);

        Row periodoRow = sheet.createRow(1);
        periodoRow.createCell(0).setCellValue("Periodo: " + fechaInicio + " al " + fechaFin);

        // INGRESOS
        int rowNum = 3;
        Row ingresoHeader = sheet.createRow(rowNum++);
        ingresoHeader.createCell(0).setCellValue("INGRESOS");
        ingresoHeader.getCell(0).setCellStyle(headerStyle);

        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Concepto");
        headerRow.createCell(1).setCellValue("Cantidad");
        headerRow.createCell(2).setCellValue("Monto");
        headerRow.getCell(0).setCellStyle(headerStyle);
        headerRow.getCell(1).setCellStyle(headerStyle);
        headerRow.getCell(2).setCellStyle(headerStyle);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detalleIngresos = (List<Map<String, Object>>) datos.get("detalleIngresos");
        for (Map<String, Object> ingreso : detalleIngresos) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue((String) ingreso.get("concepto"));
            row.createCell(1).setCellValue((Integer) ingreso.get("cantidad"));
            Cell montoCell = row.createCell(2);
            montoCell.setCellValue(((BigDecimal) ingreso.get("monto")).doubleValue());
            montoCell.setCellStyle(moneyStyle);
        }

        Row totalIngresoRow = sheet.createRow(rowNum++);
        totalIngresoRow.createCell(0).setCellValue("TOTAL INGRESOS");
        totalIngresoRow.getCell(0).setCellStyle(headerStyle);
        Cell totalIngresoCell = totalIngresoRow.createCell(2);
        totalIngresoCell.setCellValue(((BigDecimal) datos.get("totalIngresos")).doubleValue());
        totalIngresoCell.setCellStyle(moneyStyle);

        // EGRESOS
        rowNum++;
        Row egresoHeader = sheet.createRow(rowNum++);
        egresoHeader.createCell(0).setCellValue("EGRESOS");
        egresoHeader.getCell(0).setCellStyle(headerStyle);

        Row headerRow2 = sheet.createRow(rowNum++);
        headerRow2.createCell(0).setCellValue("Concepto");
        headerRow2.createCell(1).setCellValue("Cantidad");
        headerRow2.createCell(2).setCellValue("Monto");
        headerRow2.getCell(0).setCellStyle(headerStyle);
        headerRow2.getCell(1).setCellStyle(headerStyle);
        headerRow2.getCell(2).setCellStyle(headerStyle);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> detalleEgresos = (List<Map<String, Object>>) datos.get("detalleEgresos");
        for (Map<String, Object> egreso : detalleEgresos) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue((String) egreso.get("concepto"));
            row.createCell(1).setCellValue((Integer) egreso.get("cantidad"));
            Cell montoCell = row.createCell(2);
            montoCell.setCellValue(((BigDecimal) egreso.get("monto")).doubleValue());
            montoCell.setCellStyle(moneyStyle);
        }

        Row totalEgresoRow = sheet.createRow(rowNum++);
        totalEgresoRow.createCell(0).setCellValue("TOTAL EGRESOS");
        totalEgresoRow.getCell(0).setCellStyle(headerStyle);
        Cell totalEgresoCell = totalEgresoRow.createCell(2);
        totalEgresoCell.setCellValue(((BigDecimal) datos.get("totalEgresos")).doubleValue());
        totalEgresoCell.setCellStyle(moneyStyle);

        // SALDO
        rowNum++;
        Row saldoRow = sheet.createRow(rowNum++);
        saldoRow.createCell(0).setCellValue("SALDO NETO");
        saldoRow.getCell(0).setCellStyle(headerStyle);
        Cell saldoCell = saldoRow.createCell(2);
        saldoCell.setCellValue(((BigDecimal) datos.get("saldo")).doubleValue());
        saldoCell.setCellStyle(moneyStyle);

        // Ajustar columnas
        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);

        // Escribir archivo
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=flujo_caja_" +
                fechaInicio + "_" + fechaFin + ".xlsx");
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * Exportar flujo de caja a PDF
     */
    public void exportarFlujoCajaPDF(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarFlujoCaja(fechaInicio, fechaFin);
        Configuracion config = configuracionService.obtenerConfiguracion();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=flujo_caja_" +
                fechaInicio + "_" + fechaFin + ".pdf");

        Document document = new Document(PageSize.A4);
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            // Título
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph(config.getNombreEmpresa(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("FLUJO DE CAJA", titleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);

            Paragraph periodo = new Paragraph("Periodo: " + fechaInicio + " al " + fechaFin,
                    new Font(Font.HELVETICA, 10));
            periodo.setAlignment(Element.ALIGN_CENTER);
            document.add(periodo);
            document.add(new Paragraph(" "));

            // Tabla INGRESOS
            Paragraph ingresoTitle = new Paragraph("INGRESOS", new Font(Font.HELVETICA, 12, Font.BOLD));
            document.add(ingresoTitle);

            PdfPTable tableIngresos = new PdfPTable(3);
            tableIngresos.setWidthPercentage(100);
            tableIngresos.setWidths(new int[]{3, 1, 2});

            // Header
            PdfPCell cell = new PdfPCell(new Paragraph("Concepto", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableIngresos.addCell(cell);

            cell = new PdfPCell(new Paragraph("Cantidad", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableIngresos.addCell(cell);

            cell = new PdfPCell(new Paragraph("Monto", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableIngresos.addCell(cell);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detalleIngresos = (List<Map<String, Object>>) datos.get("detalleIngresos");
            for (Map<String, Object> ingreso : detalleIngresos) {
                tableIngresos.addCell((String) ingreso.get("concepto"));
                tableIngresos.addCell(ingreso.get("cantidad").toString());
                tableIngresos.addCell("S/ " + ingreso.get("monto"));
            }

            // Total
            cell = new PdfPCell(new Paragraph("TOTAL INGRESOS", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setColspan(2);
            tableIngresos.addCell(cell);
            cell = new PdfPCell(new Paragraph("S/ " + datos.get("totalIngresos"), new Font(Font.HELVETICA, 10, Font.BOLD)));
            tableIngresos.addCell(cell);

            document.add(tableIngresos);
            document.add(new Paragraph(" "));

            // Tabla EGRESOS
            Paragraph egresoTitle = new Paragraph("EGRESOS", new Font(Font.HELVETICA, 12, Font.BOLD));
            document.add(egresoTitle);

            PdfPTable tableEgresos = new PdfPTable(3);
            tableEgresos.setWidthPercentage(100);
            tableEgresos.setWidths(new int[]{3, 1, 2});

            // Header
            cell = new PdfPCell(new Paragraph("Concepto", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableEgresos.addCell(cell);

            cell = new PdfPCell(new Paragraph("Cantidad", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableEgresos.addCell(cell);

            cell = new PdfPCell(new Paragraph("Monto", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setBackgroundColor(Color.LIGHT_GRAY);
            tableEgresos.addCell(cell);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> detalleEgresos = (List<Map<String, Object>>) datos.get("detalleEgresos");
            for (Map<String, Object> egreso : detalleEgresos) {
                tableEgresos.addCell((String) egreso.get("concepto"));
                tableEgresos.addCell(egreso.get("cantidad").toString());
                tableEgresos.addCell("S/ " + egreso.get("monto"));
            }

            // Total
            cell = new PdfPCell(new Paragraph("TOTAL EGRESOS", new Font(Font.HELVETICA, 10, Font.BOLD)));
            cell.setColspan(2);
            tableEgresos.addCell(cell);
            cell = new PdfPCell(new Paragraph("S/ " + datos.get("totalEgresos"), new Font(Font.HELVETICA, 10, Font.BOLD)));
            tableEgresos.addCell(cell);

            document.add(tableEgresos);
            document.add(new Paragraph(" "));

            // SALDO NETO
            Paragraph saldo = new Paragraph("SALDO NETO: S/ " + datos.get("saldo"),
                    new Font(Font.HELVETICA, 14, Font.BOLD));
            saldo.setAlignment(Element.ALIGN_RIGHT);
            document.add(saldo);

        } catch (DocumentException e) {
            log.error("Error generando PDF: {}", e.getMessage(), e);
        } finally {
            document.close();
        }
    }

    /**
     * Exportar rentabilidad a Excel
     */
    public void exportarRentabilidadExcel(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rentabilidad = generarRentabilidadProductos(fechaInicio, fechaFin);

        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Rentabilidad");

        // Estilos
        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        CellStyle moneyStyle = workbook.createCellStyle();
        moneyStyle.setDataFormat(workbook.createDataFormat().getFormat("\"S/ \"#,##0.00"));

        CellStyle percentStyle = workbook.createCellStyle();
        percentStyle.setDataFormat(workbook.createDataFormat().getFormat("0.00\"%\""));

        // Título
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("ANÁLISIS DE RENTABILIDAD POR PRODUCTOS");
        titleCell.setCellStyle(headerStyle);

        Row periodoRow = sheet.createRow(1);
        periodoRow.createCell(0).setCellValue("Periodo: " + fechaInicio + " al " + fechaFin);

        // Headers
        int rowNum = 3;
        Row headerRow = sheet.createRow(rowNum++);
        headerRow.createCell(0).setCellValue("Producto");
        headerRow.createCell(1).setCellValue("Categoría");
        headerRow.createCell(2).setCellValue("Cant. Vendida");
        headerRow.createCell(3).setCellValue("P. Compra");
        headerRow.createCell(4).setCellValue("P. Venta Prom.");
        headerRow.createCell(5).setCellValue("Margen Unit.");
        headerRow.createCell(6).setCellValue("Margen %");
        headerRow.createCell(7).setCellValue("Ganancia Total");
        headerRow.createCell(8).setCellValue("Total Vendido");

        for (int i = 0; i < 9; i++) {
            headerRow.getCell(i).setCellStyle(headerStyle);
        }

        // Datos
        for (Map<String, Object> item : rentabilidad) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue((String) item.get("productoNombre"));
            row.createCell(1).setCellValue((String) item.get("productoCategoria"));
            row.createCell(2).setCellValue((Integer) item.get("cantidadVendida"));

            Cell pc = row.createCell(3);
            pc.setCellValue(((BigDecimal) item.get("precioCompra")).doubleValue());
            pc.setCellStyle(moneyStyle);

            Cell pv = row.createCell(4);
            pv.setCellValue(((BigDecimal) item.get("precioVentaPromedio")).doubleValue());
            pv.setCellStyle(moneyStyle);

            Cell mb = row.createCell(5);
            mb.setCellValue(((BigDecimal) item.get("margenBruto")).doubleValue());
            mb.setCellStyle(moneyStyle);

            Cell mp = row.createCell(6);
            mp.setCellValue(((BigDecimal) item.get("margenPorcentaje")).doubleValue() / 100);
            mp.setCellStyle(percentStyle);

            Cell gt = row.createCell(7);
            gt.setCellValue(((BigDecimal) item.get("gananciaTotal")).doubleValue());
            gt.setCellStyle(moneyStyle);

            Cell tv = row.createCell(8);
            tv.setCellValue(((BigDecimal) item.get("totalVendido")).doubleValue());
            tv.setCellStyle(moneyStyle);
        }

        // Ajustar columnas
        for (int i = 0; i < 9; i++) {
            sheet.autoSizeColumn(i);
        }

        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=rentabilidad_" +
                fechaInicio + "_" + fechaFin + ".xlsx");
        workbook.write(response.getOutputStream());
        workbook.close();
    }

    /**
     * Exportar rentabilidad a PDF
     */
    public void exportarRentabilidadPDF(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rentabilidad = generarRentabilidadProductos(fechaInicio, fechaFin);
        Configuracion config = configuracionService.obtenerConfiguracion();

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=rentabilidad_" +
                fechaInicio + "_" + fechaFin + ".pdf");

        Document document = new Document(PageSize.A4.rotate()); // Landscape
        try {
            PdfWriter.getInstance(document, response.getOutputStream());
            document.open();

            // Título
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph(config.getNombreEmpresa(), titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph("ANÁLISIS DE RENTABILIDAD POR PRODUCTOS", titleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            document.add(subtitle);

            Paragraph periodo = new Paragraph("Periodo: " + fechaInicio + " al " + fechaFin,
                    new Font(Font.HELVETICA, 10));
            periodo.setAlignment(Element.ALIGN_CENTER);
            document.add(periodo);
            document.add(new Paragraph(" "));

            // Tabla
            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new int[]{3, 2, 1, 1, 1, 1, 1, 2});

            // Headers
            Font headerFont = new Font(Font.HELVETICA, 8, Font.BOLD);
            String[] headers = {"Producto", "Categoría", "Cant.", "P.Compra", "P.Venta", "Margen", "Marg.%", "Ganancia"};
            for (String header : headers) {
                PdfPCell cell = new PdfPCell(new Paragraph(header, headerFont));
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                table.addCell(cell);
            }

            // Datos
            Font dataFont = new Font(Font.HELVETICA, 8);
            for (Map<String, Object> item : rentabilidad) {
                table.addCell(new Paragraph((String) item.get("productoNombre"), dataFont));
                table.addCell(new Paragraph((String) item.get("productoCategoria"), dataFont));
                table.addCell(new Paragraph(item.get("cantidadVendida").toString(), dataFont));
                table.addCell(new Paragraph("S/ " + item.get("precioCompra"), dataFont));
                table.addCell(new Paragraph("S/ " + item.get("precioVentaPromedio"), dataFont));
                table.addCell(new Paragraph("S/ " + item.get("margenBruto"), dataFont));
                table.addCell(new Paragraph(item.get("margenPorcentaje") + "%", dataFont));
                table.addCell(new Paragraph("S/ " + item.get("gananciaTotal"), dataFont));
            }

            document.add(table);

        } catch (DocumentException e) {
            log.error("Error generando PDF: {}", e.getMessage(), e);
        } finally {
            document.close();
        }
    }
}
