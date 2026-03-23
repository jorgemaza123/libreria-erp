package com.libreria.sistema.service;

import com.libreria.sistema.model.*;
import com.libreria.sistema.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ReporteFinancieroService {

    private static final String SERVICIO_RAPIDO_CODE = "SERV-001";
    private static final String APERTURA_CAJA = "APERTURA_CAJA";
    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final Locale LOCALE_ES_PE = new Locale("es", "PE");
    private static final DateTimeFormatter MES_LABEL = DateTimeFormatter.ofPattern("MMM yyyy", LOCALE_ES_PE);
    private static final DateTimeFormatter FECHA_PDF = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired private VentaRepository ventaRepository;
    @Autowired private MovimientoCajaRepository movimientoCajaRepository;
    @Autowired private DetalleDevolucionRepository detalleDevolucionRepository;
    @Autowired private ProductoRepository productoRepository;
    @Autowired private ConfiguracionService configuracionService;

    private static class ResumenMovimiento {
        long cantidad;
        BigDecimal total = BigDecimal.ZERO;
        void add(BigDecimal monto) {
            cantidad++;
            total = total.add(valorSeguro(monto));
        }
    }

    private static class DetalleVentaNeto {
        Long ventaId;
        LocalDate fechaVenta;
        String vendedor;
        Long clienteId;
        String clienteNombre;
        Long productoId;
        String nombreItem;
        String categoria;
        BigDecimal cantidad = BigDecimal.ZERO;
        BigDecimal ingresoTotal = BigDecimal.ZERO;
        BigDecimal costoTotal = BigDecimal.ZERO;
    }

    public Map<String, Object> generarFlujoCaja(LocalDate fechaInicio, LocalDate fechaFin) {
        List<MovimientoCaja> movimientos = movimientoCajaRepository.findByFechaBetween(
                fechaInicio.atStartOfDay(), fechaFin.atTime(23, 59, 59));
        Map<String, ResumenMovimiento> resumenes = resumirMovimientosCaja(movimientos);

        ResumenMovimiento ventas = resumenes.getOrDefault(CategoriaMovimiento.VENTA, new ResumenMovimiento());
        ResumenMovimiento cobranzas = resumenes.getOrDefault(CategoriaMovimiento.COBRANZA, new ResumenMovimiento());
        ResumenMovimiento apertura = resumenes.getOrDefault(APERTURA_CAJA, new ResumenMovimiento());
        ResumenMovimiento aportes = resumenes.getOrDefault(CategoriaMovimiento.APORTE_DUENO, new ResumenMovimiento());
        ResumenMovimiento otrosIngresos = resumenes.getOrDefault(CategoriaMovimiento.OTRO_INGRESO, new ResumenMovimiento());
        ResumenMovimiento compras = resumenes.getOrDefault(CategoriaMovimiento.COMPRA_MERCADERIA, new ResumenMovimiento());
        ResumenMovimiento gastos = resumenes.getOrDefault(CategoriaMovimiento.GASTO_OPERATIVO, new ResumenMovimiento());
        ResumenMovimiento devoluciones = resumenes.getOrDefault(CategoriaMovimiento.DEVOLUCION, new ResumenMovimiento());
        ResumenMovimiento retiros = resumenes.getOrDefault(CategoriaMovimiento.RETIRO_DUENO, new ResumenMovimiento());
        ResumenMovimiento otrosEgresos = resumenes.getOrDefault(CategoriaMovimiento.OTRO_EGRESO, new ResumenMovimiento());

        List<Map<String, Object>> detalleIngresos = new ArrayList<>();
        detalleIngresos.add(crearFilaDetalle("Ventas cobradas", ventas));
        detalleIngresos.add(crearFilaDetalle("Cobros de credito", cobranzas));
        detalleIngresos.add(crearFilaDetalle("Fondo inicial / apertura", apertura));
        detalleIngresos.add(crearFilaDetalle("Aportes del dueno", aportes));
        detalleIngresos.add(crearFilaDetalle("Otros ingresos", otrosIngresos));

        List<Map<String, Object>> detalleEgresos = new ArrayList<>();
        detalleEgresos.add(crearFilaDetalle("Compras de mercaderia", compras));
        detalleEgresos.add(crearFilaDetalle("Gastos operativos", gastos));
        detalleEgresos.add(crearFilaDetalle("Devoluciones / reembolsos", devoluciones));
        detalleEgresos.add(crearFilaDetalle("Retiros del dueno", retiros));
        detalleEgresos.add(crearFilaDetalle("Otros egresos", otrosEgresos));

        BigDecimal totalIngresos = sumarMontos(detalleIngresos);
        BigDecimal totalEgresos = sumarMontos(detalleEgresos);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fechaInicio", fechaInicio);
        out.put("fechaFin", fechaFin);
        out.put("detalleIngresos", detalleIngresos);
        out.put("detalleEgresos", detalleEgresos);
        out.put("totalIngresos", totalIngresos);
        out.put("totalEgresos", totalEgresos);
        out.put("saldo", totalIngresos.subtract(totalEgresos));
        out.put("ventasCobradas", ventas.total);
        out.put("cobrosCredito", cobranzas.total);
        out.put("fondoInicial", apertura.total);
        out.put("aportesDueno", aportes.total);
        out.put("otrosIngresos", otrosIngresos.total);
        out.put("comprasMercaderia", compras.total);
        out.put("gastosOperativos", gastos.total);
        out.put("devoluciones", devoluciones.total);
        out.put("retirosDueno", retiros.total);
        out.put("otrosEgresos", otrosEgresos.total);
        out.put("movimientosProcesados", movimientos.size());
        return out;
    }

    public List<Map<String, Object>> generarRentabilidadProductos(LocalDate fechaInicio, LocalDate fechaFin) {
        List<DetalleVentaNeto> netos = construirDetallesNetos(obtenerVentasReportables(fechaInicio, fechaFin));
        Map<String, Map<String, Object>> agrupado = new LinkedHashMap<>();

        for (DetalleVentaNeto detalle : netos) {
            String key = claveProducto(detalle.productoId, detalle.nombreItem) + "|" + normalizar(detalle.categoria);
            Map<String, Object> item = agrupado.computeIfAbsent(key, k -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("productoId", detalle.productoId);
                data.put("productoNombre", detalle.nombreItem);
                data.put("productoCategoria", detalle.categoria);
                data.put("cantidadVendida", BigDecimal.ZERO);
                data.put("costoTotal", BigDecimal.ZERO);
                data.put("totalVendido", BigDecimal.ZERO);
                return data;
            });
            item.put("cantidadVendida", valorSeguro((BigDecimal) item.get("cantidadVendida")).add(detalle.cantidad));
            item.put("costoTotal", valorSeguro((BigDecimal) item.get("costoTotal")).add(detalle.costoTotal));
            item.put("totalVendido", valorSeguro((BigDecimal) item.get("totalVendido")).add(detalle.ingresoTotal));
        }

        List<Map<String, Object>> rentabilidad = new ArrayList<>();
        for (Map<String, Object> item : agrupado.values()) {
            BigDecimal cantidad = valorSeguro((BigDecimal) item.remove("cantidadVendida"));
            BigDecimal costoTotal = valorSeguro((BigDecimal) item.remove("costoTotal"));
            BigDecimal vendido = valorSeguro((BigDecimal) item.get("totalVendido"));
            if (cantidad.compareTo(BigDecimal.ZERO) <= 0 || vendido.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal ganancia = vendido.subtract(costoTotal);
            BigDecimal pCompra = costoTotal.divide(cantidad, 2, RoundingMode.HALF_UP);
            BigDecimal pVenta = vendido.divide(cantidad, 2, RoundingMode.HALF_UP);
            BigDecimal margenBruto = pVenta.subtract(pCompra);
            BigDecimal margenPorcentaje = ganancia.divide(vendido, 4, RoundingMode.HALF_UP).multiply(CIEN);

            item.put("cantidadVendida", cantidad.stripTrailingZeros());
            item.put("precioCompra", pCompra);
            item.put("precioVentaPromedio", pVenta);
            item.put("margenBruto", margenBruto);
            item.put("margenPorcentaje", margenPorcentaje);
            item.put("gananciaTotal", ganancia);
            rentabilidad.add(item);
        }

        rentabilidad.sort((a, b) -> valorSeguro((BigDecimal) b.get("gananciaTotal"))
                .compareTo(valorSeguro((BigDecimal) a.get("gananciaTotal"))));
        return rentabilidad;
    }

    public Map<String, Object> generarAnalisisVentas(LocalDate fechaInicio, LocalDate fechaFin) {
        List<Venta> ventas = obtenerVentasReportables(fechaInicio, fechaFin);
        List<DetalleVentaNeto> netos = construirDetallesNetos(ventas);
        Map<Long, BigDecimal> totalesVenta = calcularTotalesNetosPorVenta(netos);

        Map<LocalDate, BigDecimal> ventasPorDia = new LinkedHashMap<>();
        Map<String, BigDecimal> ventasUsuario = new HashMap<>();
        Map<String, Integer> cantidadUsuario = new HashMap<>();
        int totalVentas = 0;
        BigDecimal montoTotal = BigDecimal.ZERO;

        for (Venta venta : ventas) {
            BigDecimal totalNeto = valorSeguro(totalesVenta.get(venta.getId()));
            if (totalNeto.compareTo(BigDecimal.ZERO) <= 0) continue;
            totalVentas++;
            montoTotal = montoTotal.add(totalNeto);
            ventasPorDia.merge(venta.getFechaEmision(), totalNeto, BigDecimal::add);
            String vendedor = nombreVendedor(venta);
            ventasUsuario.merge(vendedor, totalNeto, BigDecimal::add);
            cantidadUsuario.merge(vendedor, 1, Integer::sum);
        }

        List<Map<String, Object>> serie = ventasPorDia.entrySet().stream().map(e -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("fecha", e.getKey());
            item.put("total", e.getValue());
            return item;
        }).collect(Collectors.toList());

        List<Map<String, Object>> ventasPorVendedor = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : ventasUsuario.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("vendedor", entry.getKey());
            item.put("nombre", entry.getKey());
            item.put("total", entry.getValue());
            item.put("cantidad", cantidadUsuario.getOrDefault(entry.getKey(), 0));
            ventasPorVendedor.add(item);
        }
        ventasPorVendedor.sort((a, b) -> valorSeguro((BigDecimal) b.get("total"))
                .compareTo(valorSeguro((BigDecimal) a.get("total"))));

        List<Map<String, Object>> topProductos = construirTopProductos(netos, 10);
        Set<Long> conVentas = netos.stream().map(n -> n.productoId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Map<String, Object>> sinRotacion = productoRepository.findAll().stream()
                .filter(Producto::isActivo)
                .filter(p -> !"SERVICIO".equalsIgnoreCase(p.getTipo()))
                .filter(p -> !conVentas.contains(p.getId()))
                .sorted(Comparator.comparing(Producto::getNombre, String.CASE_INSENSITIVE_ORDER))
                .map(p -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productoId", p.getId());
                    item.put("productoNombre", p.getNombre());
                    item.put("categoria", p.getCategoria() != null ? p.getCategoria() : "Sin categoria");
                    item.put("stockActual", p.getStockActual() != null ? p.getStockActual() : 0);
                    return item;
                }).collect(Collectors.toList());

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ventasPorDia", serie);
        out.put("ventasPorVendedor", ventasPorVendedor);
        out.put("topProductos", topProductos);
        out.put("productosSinRotacion", sinRotacion);
        out.put("totalVentas", totalVentas);
        out.put("montoTotalVentas", montoTotal);
        out.put("ticketPromedio", totalVentas > 0 ? montoTotal.divide(BigDecimal.valueOf(totalVentas), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        out.put("diasConVentas", ventasPorDia.size());
        out.put("vendedoresActivos", ventasPorVendedor.size());
        out.put("cantidadProductosSinRotacion", sinRotacion.size());
        return out;
    }

    public Map<String, Object> generarDashboardFinanciero() {
        YearMonth actual = YearMonth.now();
        LocalDate inicioMes = actual.atDay(1);
        LocalDate finMes = actual.atEndOfMonth();
        YearMonth anterior = actual.minusMonths(1);

        List<Venta> ventasMes = obtenerVentasReportables(inicioMes, finMes);
        List<DetalleVentaNeto> netosMes = construirDetallesNetos(ventasMes);
        Map<Long, BigDecimal> totalesMes = calcularTotalesNetosPorVenta(netosMes);
        BigDecimal ventasMesTotal = totalesMes.values().stream().map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
        long cantidadVentasMes = totalesMes.values().stream().filter(v -> valorSeguro(v).compareTo(BigDecimal.ZERO) > 0).count();

        BigDecimal ventasMesAnterior = construirDetallesNetos(
                obtenerVentasReportables(anterior.atDay(1), anterior.atEndOfMonth()))
                .stream()
                .collect(Collectors.groupingBy(d -> d.ventaId, Collectors.reducing(BigDecimal.ZERO, d -> d.ingresoTotal, BigDecimal::add)))
                .values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MovimientoCaja> movimientosMes = movimientoCajaRepository.findByFechaBetween(inicioMes.atStartOfDay(), finMes.atTime(23, 59, 59));
        Map<String, ResumenMovimiento> resumenMes = resumirMovimientosCaja(movimientosMes);
        BigDecimal comprasMes = resumenMes.getOrDefault(CategoriaMovimiento.COMPRA_MERCADERIA, new ResumenMovimiento()).total;
        BigDecimal gastosOperativos = resumenMes.getOrDefault(CategoriaMovimiento.GASTO_OPERATIVO, new ResumenMovimiento()).total;
        BigDecimal devolucionesMes = resumenMes.getOrDefault(CategoriaMovimiento.DEVOLUCION, new ResumenMovimiento()).total;
        BigDecimal retirosMes = resumenMes.getOrDefault(CategoriaMovimiento.RETIRO_DUENO, new ResumenMovimiento()).total;
        BigDecimal otrosEgresos = resumenMes.getOrDefault(CategoriaMovimiento.OTRO_EGRESO, new ResumenMovimiento()).total;
        BigDecimal cobrosCredito = resumenMes.getOrDefault(CategoriaMovimiento.COBRANZA, new ResumenMovimiento()).total;
        BigDecimal salidasMes = comprasMes.add(gastosOperativos).add(devolucionesMes).add(retirosMes).add(otrosEgresos);

        BigDecimal variacion = BigDecimal.ZERO;
        if (ventasMesAnterior.compareTo(BigDecimal.ZERO) > 0) {
            variacion = ventasMesTotal.subtract(ventasMesAnterior).divide(ventasMesAnterior, 4, RoundingMode.HALF_UP).multiply(CIEN);
        }

        Map<YearMonth, BigDecimal> ventasSerie = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> salidasSerie = new LinkedHashMap<>();
        for (int i = 11; i >= 0; i--) {
            YearMonth mes = actual.minusMonths(i);
            ventasSerie.put(mes, BigDecimal.ZERO);
            salidasSerie.put(mes, movimientoCajaRepository.findByFechaBetween(mes.atDay(1).atStartOfDay(), mes.atEndOfMonth().atTime(23, 59, 59))
                    .stream()
                    .filter(m -> "EGRESO".equalsIgnoreCase(m.getTipo()))
                    .map(MovimientoCaja::getMonto)
                    .map(ReporteFinancieroService::valorSeguro)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));
        }

        LocalDate inicioSerie = actual.minusMonths(11).atDay(1);
        List<Venta> ventasSerieBase = obtenerVentasReportables(inicioSerie, finMes);
        Map<Long, BigDecimal> totalesSerie = calcularTotalesNetosPorVenta(construirDetallesNetos(ventasSerieBase));
        for (Venta venta : ventasSerieBase) {
            BigDecimal totalNeto = valorSeguro(totalesSerie.get(venta.getId()));
            if (totalNeto.compareTo(BigDecimal.ZERO) <= 0) continue;
            ventasSerie.merge(YearMonth.from(venta.getFechaEmision()), totalNeto, BigDecimal::add);
        }

        List<Map<String, Object>> ultimos12Meses = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : ventasSerie.entrySet()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("mes", capitalizarMes(entry.getKey().format(MES_LABEL)));
            item.put("ventas", entry.getValue());
            item.put("salidas", salidasSerie.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            item.put("gastos", salidasSerie.getOrDefault(entry.getKey(), BigDecimal.ZERO));
            ultimos12Meses.add(item);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ventasMesActual", ventasMesTotal);
        out.put("cantidadVentasMesActual", cantidadVentasMes);
        out.put("ticketPromedioMesActual", cantidadVentasMes > 0 ? ventasMesTotal.divide(BigDecimal.valueOf(cantidadVentasMes), 2, RoundingMode.HALF_UP) : BigDecimal.ZERO);
        out.put("comprasMesActual", comprasMes);
        out.put("cobrosCreditoMesActual", cobrosCredito);
        out.put("devolucionesMesActual", devolucionesMes);
        out.put("salidasMesActual", salidasMes);
        out.put("gastosMesActual", salidasMes);
        out.put("resultadoPeriodo", ventasMesTotal.subtract(salidasMes));
        out.put("gananciaNeta", ventasMesTotal.subtract(salidasMes));
        out.put("variacionMensual", variacion);
        out.put("variacionAnual", variacion);
        out.put("top5Productos", construirTopProductos(netosMes, 5));
        out.put("top5Clientes", construirTopClientes(ventasMes, totalesMes, 5));
        out.put("ventasPorVendedor", construirTopVendedores(ventasMes, totalesMes));
        out.put("ultimos12Meses", ultimos12Meses);
        return out;
    }

    public void exportarFlujoCajaExcel(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarFlujoCaja(fechaInicio, fechaFin);
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Flujo de Caja");
            CellStyle title = estiloTitulo(workbook), header = estiloCabecera(workbook, IndexedColors.BLUE_GREY),
                    section = estiloSeccion(workbook), money = estiloMoneda(workbook), plain = estiloPlano(workbook),
                    num = estiloNumero(workbook);
            int rowNum = 0;
            fila(sheet, rowNum++, 0, "FLUJO DE CAJA", title);
            fila(sheet, rowNum++, 0, "Periodo analizado: " + fechaInicio + " al " + fechaFin, plain);
            fila(sheet, rowNum++, 0, "Resumen simple", section);
            fila(sheet, rowNum, 0, "Dinero que entro", plain); filaMoneda(sheet, rowNum++, 1, (BigDecimal) datos.get("totalIngresos"), money);
            fila(sheet, rowNum, 0, "Dinero que salio", plain); filaMoneda(sheet, rowNum++, 1, (BigDecimal) datos.get("totalEgresos"), money);
            fila(sheet, rowNum, 0, "Saldo final del periodo", plain); filaMoneda(sheet, rowNum++, 1, (BigDecimal) datos.get("saldo"), money);
            rowNum++;

            @SuppressWarnings("unchecked") List<Map<String, Object>> ingresos = (List<Map<String, Object>>) datos.get("detalleIngresos");
            @SuppressWarnings("unchecked") List<Map<String, Object>> egresos = (List<Map<String, Object>>) datos.get("detalleEgresos");

            rowNum = escribirDetalleExcel(sheet, rowNum, "DETALLE DE INGRESOS", ingresos, (BigDecimal) datos.get("totalIngresos"), header, section, money, num, plain);
            rowNum++;
            rowNum = escribirDetalleExcel(sheet, rowNum, "DETALLE DE EGRESOS", egresos, (BigDecimal) datos.get("totalEgresos"), header, section, money, num, plain);
            rowNum++;
            fila(sheet, rowNum, 0, "TOTAL FINAL DEL PERIODO", section);
            filaMoneda(sheet, rowNum, 2, (BigDecimal) datos.get("saldo"), money);
            for (int i = 0; i < 3; i++) sheet.autoSizeColumn(i);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=flujo_caja_" + fechaInicio + "_" + fechaFin + ".xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    public void exportarFlujoCajaPDF(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarFlujoCaja(fechaInicio, fechaFin);
        Configuracion config = configuracionService.obtenerConfiguracion();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=flujo_caja_" + fechaInicio + "_" + fechaFin + ".pdf");

        Document doc = new Document(PageSize.A4, 28, 28, 32, 32);
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            Font title = new Font(Font.HELVETICA, 15, Font.BOLD), sub = new Font(Font.HELVETICA, 10),
                    section = new Font(Font.HELVETICA, 10, Font.BOLD), body = new Font(Font.HELVETICA, 9),
                    total = new Font(Font.HELVETICA, 10, Font.BOLD);
            encabezadoPdf(doc, config.getNombreEmpresa(), "Reporte de Flujo de Caja", fechaInicio, fechaFin, title, sub);
            doc.add(tablaResumenPdf(new String[]{"Ingresos del periodo", "Egresos del periodo", "Saldo final"},
                    new String[]{moneda((BigDecimal) datos.get("totalIngresos")), moneda((BigDecimal) datos.get("totalEgresos")), moneda((BigDecimal) datos.get("saldo"))},
                    section, total));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Detalle de ingresos", section));
            doc.add(tablaDetallePdf((List<Map<String, Object>>) datos.get("detalleIngresos"), (BigDecimal) datos.get("totalIngresos"), body, total));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Detalle de egresos", section));
            doc.add(tablaDetallePdf((List<Map<String, Object>>) datos.get("detalleEgresos"), (BigDecimal) datos.get("totalEgresos"), body, total));
            doc.add(new Paragraph(" "));
            Paragraph cierre = new Paragraph("En este periodo ingresaron " + moneda((BigDecimal) datos.get("totalIngresos"))
                    + ", salieron " + moneda((BigDecimal) datos.get("totalEgresos"))
                    + " y quedo un saldo final de " + moneda((BigDecimal) datos.get("saldo")) + ".", total);
            cierre.setAlignment(Element.ALIGN_RIGHT);
            doc.add(cierre);
        } catch (DocumentException e) {
            log.error("Error generando PDF de flujo de caja: {}", e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    public void exportarRentabilidadExcel(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rentabilidad = generarRentabilidadProductos(fechaInicio, fechaFin);
        BigDecimal totalGanancia = rentabilidad.stream().map(i -> (BigDecimal) i.get("gananciaTotal")).map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVendido = rentabilidad.stream().map(i -> (BigDecimal) i.get("totalVendido")).map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal margenGlobal = totalVendido.compareTo(BigDecimal.ZERO) > 0 ? totalGanancia.divide(totalVendido, 4, RoundingMode.HALF_UP).multiply(CIEN) : BigDecimal.ZERO;
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Rentabilidad");
            CellStyle title = estiloTitulo(workbook), header = estiloCabecera(workbook, IndexedColors.SEA_GREEN),
                    section = estiloSeccion(workbook), money = estiloMoneda(workbook), percent = estiloPorcentaje(workbook), plain = estiloPlano(workbook);
            int rowNum = 0;
            fila(sheet, rowNum++, 0, "RENTABILIDAD POR PRODUCTOS", title);
            fila(sheet, rowNum++, 0, "Periodo analizado: " + fechaInicio + " al " + fechaFin, plain);
            fila(sheet, rowNum++, 0, "Resumen ejecutivo", section);
            fila(sheet, rowNum, 0, "Total vendido", plain); filaMoneda(sheet, rowNum++, 1, totalVendido, money);
            fila(sheet, rowNum, 0, "Ganancia total", plain); filaMoneda(sheet, rowNum++, 1, totalGanancia, money);
            fila(sheet, rowNum, 0, "Margen global", plain); filaPorcentaje(sheet, rowNum++, 1, margenGlobal, percent);
            rowNum++;
            Row h = sheet.createRow(rowNum++);
            String[] headers = {"Producto", "Categoria", "Cant. vendida", "P. compra prom.", "P. venta prom.", "Margen unit.", "Margen %", "Ganancia total", "Total vendido"};
            for (int i = 0; i < headers.length; i++) celda(h, i, headers[i], header);
            for (Map<String, Object> item : rentabilidad) {
                Row row = sheet.createRow(rowNum++);
                celda(row, 0, String.valueOf(item.get("productoNombre")), plain);
                celda(row, 1, String.valueOf(item.get("productoCategoria")), plain);
                celdaNumeroDecimal(row, 2, (BigDecimal) item.get("cantidadVendida"), plain);
                celdaMoneda(row, 3, (BigDecimal) item.get("precioCompra"), money);
                celdaMoneda(row, 4, (BigDecimal) item.get("precioVentaPromedio"), money);
                celdaMoneda(row, 5, (BigDecimal) item.get("margenBruto"), money);
                celdaPorcentaje(row, 6, (BigDecimal) item.get("margenPorcentaje"), percent);
                celdaMoneda(row, 7, (BigDecimal) item.get("gananciaTotal"), money);
                celdaMoneda(row, 8, (BigDecimal) item.get("totalVendido"), money);
            }
            Row totalRow = sheet.createRow(rowNum);
            celda(totalRow, 0, "TOTAL FINAL", section);
            celdaMoneda(totalRow, 7, totalGanancia, money);
            celdaMoneda(totalRow, 8, totalVendido, money);
            for (int i = 0; i < 9; i++) sheet.autoSizeColumn(i);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=rentabilidad_" + fechaInicio + "_" + fechaFin + ".xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    public void exportarRentabilidadPDF(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        List<Map<String, Object>> rentabilidad = generarRentabilidadProductos(fechaInicio, fechaFin);
        Configuracion config = configuracionService.obtenerConfiguracion();
        BigDecimal totalGanancia = rentabilidad.stream().map(i -> (BigDecimal) i.get("gananciaTotal")).map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalVendido = rentabilidad.stream().map(i -> (BigDecimal) i.get("totalVendido")).map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal margenGlobal = totalVendido.compareTo(BigDecimal.ZERO) > 0 ? totalGanancia.divide(totalVendido, 4, RoundingMode.HALF_UP).multiply(CIEN) : BigDecimal.ZERO;
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=rentabilidad_" + fechaInicio + "_" + fechaFin + ".pdf");

        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            Font title = new Font(Font.HELVETICA, 15, Font.BOLD), sub = new Font(Font.HELVETICA, 10),
                    section = new Font(Font.HELVETICA, 9, Font.BOLD), body = new Font(Font.HELVETICA, 8),
                    total = new Font(Font.HELVETICA, 9, Font.BOLD);
            encabezadoPdf(doc, config.getNombreEmpresa(), "Reporte de Rentabilidad por Productos", fechaInicio, fechaFin, title, sub);
            doc.add(tablaResumenPdf(new String[]{"Total vendido", "Ganancia total", "Margen global", "Productos analizados"},
                    new String[]{moneda(totalVendido), moneda(totalGanancia), porcentaje(margenGlobal), String.valueOf(rentabilidad.size())},
                    section, total));
            doc.add(new Paragraph(" "));
            PdfPTable table = new PdfPTable(9);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{3.2f, 1.7f, 1f, 1.25f, 1.25f, 1.25f, 1f, 1.4f, 1.5f});
            for (String header : new String[]{"Producto", "Categoria", "Cant.", "P. compra", "P. venta", "Margen", "Margen %", "Ganancia", "Total vendido"}) {
                table.addCell(celdaPdf(header, section, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
            }
            for (Map<String, Object> item : rentabilidad) {
                table.addCell(celdaPdf(String.valueOf(item.get("productoNombre")), body, null, Element.ALIGN_LEFT, 1));
                table.addCell(celdaPdf(String.valueOf(item.get("productoCategoria")), body, null, Element.ALIGN_LEFT, 1));
                table.addCell(celdaPdf(cantidad((BigDecimal) item.get("cantidadVendida")), body, null, Element.ALIGN_CENTER, 1));
                table.addCell(celdaPdf(moneda((BigDecimal) item.get("precioCompra")), body, null, Element.ALIGN_RIGHT, 1));
                table.addCell(celdaPdf(moneda((BigDecimal) item.get("precioVentaPromedio")), body, null, Element.ALIGN_RIGHT, 1));
                table.addCell(celdaPdf(moneda((BigDecimal) item.get("margenBruto")), body, null, Element.ALIGN_RIGHT, 1));
                table.addCell(celdaPdf(porcentaje((BigDecimal) item.get("margenPorcentaje")), body, null, Element.ALIGN_RIGHT, 1));
                table.addCell(celdaPdf(moneda((BigDecimal) item.get("gananciaTotal")), body, null, Element.ALIGN_RIGHT, 1));
                table.addCell(celdaPdf(moneda((BigDecimal) item.get("totalVendido")), body, null, Element.ALIGN_RIGHT, 1));
            }
            table.addCell(celdaPdf("TOTAL FINAL", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 7));
            table.addCell(celdaPdf(moneda(totalGanancia), total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
            table.addCell(celdaPdf(moneda(totalVendido), total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
            doc.add(table);
        } catch (DocumentException e) {
            log.error("Error generando PDF de rentabilidad: {}", e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    public void exportarAnalisisVentasExcel(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarAnalisisVentas(fechaInicio, fechaFin);
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle title = estiloTitulo(workbook), header = estiloCabecera(workbook, IndexedColors.LIGHT_BLUE),
                    section = estiloSeccion(workbook), money = estiloMoneda(workbook), plain = estiloPlano(workbook), num = estiloNumero(workbook);

            Sheet resumen = workbook.createSheet("Resumen");
            int rowNum = 0;
            fila(resumen, rowNum++, 0, "ANALISIS DE VENTAS", title);
            fila(resumen, rowNum++, 0, "Periodo analizado: " + fechaInicio + " al " + fechaFin, plain);
            fila(resumen, rowNum++, 0, "Resumen general", section);
            fila(resumen, rowNum, 0, "Ventas netas registradas", plain); filaNumero(resumen, rowNum++, 1, ((Number) datos.get("totalVentas")).longValue(), num);
            fila(resumen, rowNum, 0, "Monto total vendido", plain); filaMoneda(resumen, rowNum++, 1, (BigDecimal) datos.get("montoTotalVentas"), money);
            fila(resumen, rowNum, 0, "Ticket promedio", plain); filaMoneda(resumen, rowNum++, 1, (BigDecimal) datos.get("ticketPromedio"), money);
            fila(resumen, rowNum, 0, "Dias con ventas", plain); filaNumero(resumen, rowNum++, 1, ((Number) datos.get("diasConVentas")).longValue(), num);
            rowNum++;
            Row h = resumen.createRow(rowNum++);
            celda(h, 0, "Fecha", header);
            celda(h, 1, "Monto neto", header);
            for (Map<String, Object> item : (List<Map<String, Object>>) datos.get("ventasPorDia")) {
                Row row = resumen.createRow(rowNum++);
                celda(row, 0, String.valueOf(item.get("fecha")), plain);
                celdaMoneda(row, 1, (BigDecimal) item.get("total"), money);
            }

            Sheet productos = workbook.createSheet("Top Productos");
            rowNum = 0;
            fila(productos, rowNum++, 0, "TOP PRODUCTOS", title);
            Row hp = productos.createRow(rowNum++);
            celda(hp, 0, "Producto", header);
            celda(hp, 1, "Cantidad", header);
            celda(hp, 2, "Total", header);
            for (Map<String, Object> item : (List<Map<String, Object>>) datos.get("topProductos")) {
                Row row = productos.createRow(rowNum++);
                celda(row, 0, String.valueOf(item.get("productoNombre")), plain);
                celdaNumeroDecimal(row, 1, (BigDecimal) item.get("cantidadVendida"), plain);
                celdaMoneda(row, 2, (BigDecimal) item.get("totalVendido"), money);
            }

            Sheet vendedores = workbook.createSheet("Vendedores");
            rowNum = 0;
            fila(vendedores, rowNum++, 0, "VENTAS POR VENDEDOR", title);
            Row hv = vendedores.createRow(rowNum++);
            celda(hv, 0, "Vendedor", header);
            celda(hv, 1, "Ventas", header);
            celda(hv, 2, "Total", header);
            for (Map<String, Object> item : (List<Map<String, Object>>) datos.get("ventasPorVendedor")) {
                Row row = vendedores.createRow(rowNum++);
                celda(row, 0, String.valueOf(item.get("vendedor")), plain);
                celdaNumero(row, 1, ((Number) item.get("cantidad")).longValue(), num);
                celdaMoneda(row, 2, (BigDecimal) item.get("total"), money);
            }

            Sheet sinRotacion = workbook.createSheet("Sin Rotacion");
            rowNum = 0;
            fila(sinRotacion, rowNum++, 0, "PRODUCTOS SIN ROTACION", title);
            Row hs = sinRotacion.createRow(rowNum++);
            celda(hs, 0, "Producto", header);
            celda(hs, 1, "Categoria", header);
            celda(hs, 2, "Stock actual", header);
            for (Map<String, Object> item : (List<Map<String, Object>>) datos.get("productosSinRotacion")) {
                Row row = sinRotacion.createRow(rowNum++);
                celda(row, 0, String.valueOf(item.get("productoNombre")), plain);
                celda(row, 1, String.valueOf(item.get("categoria")), plain);
                celdaNumero(row, 2, ((Number) item.get("stockActual")).longValue(), num);
            }
            for (Sheet sheet : List.of(resumen, productos, vendedores, sinRotacion)) for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=analisis_ventas_" + fechaInicio + "_" + fechaFin + ".xlsx");
            workbook.write(response.getOutputStream());
        }
    }

    public void exportarAnalisisVentasPDF(LocalDate fechaInicio, LocalDate fechaFin, HttpServletResponse response) throws IOException {
        Map<String, Object> datos = generarAnalisisVentas(fechaInicio, fechaFin);
        Configuracion config = configuracionService.obtenerConfiguracion();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "attachment; filename=analisis_ventas_" + fechaInicio + "_" + fechaFin + ".pdf");

        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        try {
            PdfWriter.getInstance(doc, response.getOutputStream());
            doc.open();
            Font title = new Font(Font.HELVETICA, 15, Font.BOLD), sub = new Font(Font.HELVETICA, 10),
                    section = new Font(Font.HELVETICA, 9, Font.BOLD), body = new Font(Font.HELVETICA, 8),
                    total = new Font(Font.HELVETICA, 9, Font.BOLD);
            encabezadoPdf(doc, config.getNombreEmpresa(), "Reporte de Analisis de Ventas", fechaInicio, fechaFin, title, sub);
            doc.add(tablaResumenPdf(new String[]{"Ventas", "Monto total", "Ticket promedio", "Dias con ventas", "Vendedores activos"},
                    new String[]{String.valueOf(datos.get("totalVentas")), moneda((BigDecimal) datos.get("montoTotalVentas")), moneda((BigDecimal) datos.get("ticketPromedio")),
                            String.valueOf(datos.get("diasConVentas")), String.valueOf(datos.get("vendedoresActivos"))},
                    section, total));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Top productos vendidos", section));
            doc.add(tablaTopProductosPdf((List<Map<String, Object>>) datos.get("topProductos"), body, total));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Ventas por vendedor", section));
            doc.add(tablaVendedoresPdf((List<Map<String, Object>>) datos.get("ventasPorVendedor"), body, total));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("Productos sin rotacion", section));
            doc.add(tablaSinRotacionPdf((List<Map<String, Object>>) datos.get("productosSinRotacion"), body, total));
        } catch (DocumentException e) {
            log.error("Error generando PDF de analisis: {}", e.getMessage(), e);
        } finally {
            doc.close();
        }
    }

    private List<Venta> obtenerVentasReportables(LocalDate fechaInicio, LocalDate fechaFin) {
        return ventaRepository.findByFechaEmisionBetweenWithDetalles(fechaInicio, fechaFin).stream()
                .filter(v -> !"ANULADO".equalsIgnoreCase(v.getEstado()))
                .collect(Collectors.toList());
    }

    private List<DetalleVentaNeto> construirDetallesNetos(List<Venta> ventas) {
        Map<String, DetalleVentaNeto> netos = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            if (venta.getItems() == null) continue;
            for (DetalleVenta detalle : venta.getItems()) {
                if (detalle == null || detalle.getProducto() == null) continue;
                BigDecimal cantidad = valorSeguro(detalle.getCantidad());
                BigDecimal ingreso = valorSeguro(detalle.getSubtotal());
                if (cantidad.compareTo(BigDecimal.ZERO) <= 0 || ingreso.compareTo(BigDecimal.ZERO) <= 0) continue;
                String nombre = nombreReporte(detalle);
                String key = claveDetalle(venta.getId(), detalle.getProducto().getId(), nombre);
                DetalleVentaNeto item = netos.computeIfAbsent(key, k -> {
                    DetalleVentaNeto data = new DetalleVentaNeto();
                    data.ventaId = venta.getId();
                    data.fechaVenta = venta.getFechaEmision();
                    data.vendedor = nombreVendedor(venta);
                    data.clienteId = venta.getClienteEntity() != null ? venta.getClienteEntity().getId() : null;
                    data.clienteNombre = nombreCliente(venta);
                    data.productoId = detalle.getProducto().getId();
                    data.nombreItem = nombre;
                    data.categoria = categoriaReporte(detalle);
                    return data;
                });
                item.cantidad = item.cantidad.add(cantidad);
                item.ingresoTotal = item.ingresoTotal.add(ingreso);
                item.costoTotal = item.costoTotal.add(valorSeguro(detalle.getCostoUnitario()).multiply(cantidad));
            }
        }
        aplicarDevolucionesNetas(netos, ventas);
        return netos.values().stream()
                .filter(d -> d.cantidad.compareTo(BigDecimal.ZERO) > 0 || d.ingresoTotal.compareTo(BigDecimal.ZERO) > 0)
                .sorted(Comparator.comparing((DetalleVentaNeto d) -> d.fechaVenta).thenComparing(d -> d.nombreItem, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());
    }

    private void aplicarDevolucionesNetas(Map<String, DetalleVentaNeto> netos, List<Venta> ventas) {
        if (ventas.isEmpty() || netos.isEmpty()) return;
        List<Long> ventaIds = ventas.stream().map(Venta::getId).filter(Objects::nonNull).collect(Collectors.toList());
        List<DetalleDevolucion> devoluciones = detalleDevolucionRepository.findActivosByVentaOriginalIds(ventaIds);
        if (devoluciones.isEmpty()) return;
        Map<String, List<DetalleVentaNeto>> porProducto = netos.values().stream().collect(Collectors.groupingBy(d -> d.ventaId + "|" + d.productoId));

        for (DetalleDevolucion devolucion : devoluciones) {
            if (devolucion.getDevolucion() == null || devolucion.getDevolucion().getVentaOriginal() == null) continue;
            Long ventaId = devolucion.getDevolucion().getVentaOriginal().getId();
            Long productoId = devolucion.getProducto() != null ? devolucion.getProducto().getId() : null;
            DetalleVentaNeto item = netos.get(claveDetalle(ventaId, productoId, nombreReporte(devolucion)));
            if (item == null) {
                item = porProducto.getOrDefault(ventaId + "|" + productoId, List.of()).stream()
                        .filter(d -> d.cantidad.compareTo(BigDecimal.ZERO) > 0 || d.ingresoTotal.compareTo(BigDecimal.ZERO) > 0)
                        .findFirst().orElse(null);
            }
            if (item == null || item.cantidad.compareTo(BigDecimal.ZERO) <= 0) continue;
            BigDecimal cantidadActual = valorSeguro(item.cantidad);
            BigDecimal cantidadDevuelta = valorSeguro(devolucion.getCantidadDevuelta()).min(cantidadActual);
            BigDecimal ingresoActual = valorSeguro(item.ingresoTotal);
            BigDecimal costoActual = valorSeguro(item.costoTotal);
            BigDecimal ingresoReducir = valorSeguro(devolucion.getSubtotal());
            if (ingresoReducir.compareTo(ingresoActual) > 0) {
                ingresoReducir = ingresoActual.divide(cantidadActual, 4, RoundingMode.HALF_UP).multiply(cantidadDevuelta).min(ingresoActual);
            }
            BigDecimal costoReducir = BigDecimal.ZERO;
            if (costoActual.compareTo(BigDecimal.ZERO) > 0) {
                costoReducir = costoActual.divide(cantidadActual, 4, RoundingMode.HALF_UP).multiply(cantidadDevuelta).min(costoActual);
            }
            item.cantidad = cantidadActual.subtract(cantidadDevuelta).max(BigDecimal.ZERO);
            item.ingresoTotal = ingresoActual.subtract(ingresoReducir).max(BigDecimal.ZERO);
            item.costoTotal = costoActual.subtract(costoReducir).max(BigDecimal.ZERO);
        }
    }

    private Map<Long, BigDecimal> calcularTotalesNetosPorVenta(List<DetalleVentaNeto> netos) {
        Map<Long, BigDecimal> out = new HashMap<>();
        for (DetalleVentaNeto item : netos) out.merge(item.ventaId, valorSeguro(item.ingresoTotal), BigDecimal::add);
        return out;
    }

    private List<Map<String, Object>> construirTopProductos(List<DetalleVentaNeto> netos, int limite) {
        Map<String, Map<String, Object>> agrupado = new LinkedHashMap<>();
        for (DetalleVentaNeto item : netos) {
            String key = claveProducto(item.productoId, item.nombreItem);
            Map<String, Object> out = agrupado.computeIfAbsent(key, k -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("productoId", item.productoId);
                data.put("nombre", item.nombreItem);
                data.put("productoNombre", item.nombreItem);
                data.put("cantidadVendida", BigDecimal.ZERO);
                data.put("cantidad", BigDecimal.ZERO);
                data.put("totalVendido", BigDecimal.ZERO);
                data.put("total", BigDecimal.ZERO);
                return data;
            });
            BigDecimal cantidad = valorSeguro((BigDecimal) out.get("cantidadVendida")).add(item.cantidad);
            BigDecimal total = valorSeguro((BigDecimal) out.get("totalVendido")).add(item.ingresoTotal);
            out.put("cantidadVendida", cantidad);
            out.put("cantidad", cantidad);
            out.put("totalVendido", total);
            out.put("total", total);
        }
        return agrupado.values().stream()
                .sorted((a, b) -> {
                    int cmp = valorSeguro((BigDecimal) b.get("cantidadVendida")).compareTo(valorSeguro((BigDecimal) a.get("cantidadVendida")));
                    return cmp != 0 ? cmp : valorSeguro((BigDecimal) b.get("totalVendido")).compareTo(valorSeguro((BigDecimal) a.get("totalVendido")));
                })
                .limit(limite)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> construirTopClientes(List<Venta> ventas, Map<Long, BigDecimal> totalesVenta, int limite) {
        Map<String, Map<String, Object>> agrupado = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            BigDecimal total = valorSeguro(totalesVenta.get(venta.getId()));
            if (total.compareTo(BigDecimal.ZERO) <= 0) continue;
            String nombre = nombreCliente(venta);
            String key = venta.getClienteEntity() != null && venta.getClienteEntity().getId() != null ? "C|" + venta.getClienteEntity().getId() : "N|" + normalizar(nombre);
            Map<String, Object> item = agrupado.computeIfAbsent(key, k -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("nombre", nombre);
                data.put("total", BigDecimal.ZERO);
                data.put("cantidad", 0);
                return data;
            });
            item.put("total", valorSeguro((BigDecimal) item.get("total")).add(total));
            item.put("cantidad", ((Integer) item.get("cantidad")) + 1);
        }
        return agrupado.values().stream()
                .sorted((a, b) -> valorSeguro((BigDecimal) b.get("total")).compareTo(valorSeguro((BigDecimal) a.get("total"))))
                .limit(limite)
                .collect(Collectors.toList());
    }

    private List<Map<String, Object>> construirTopVendedores(List<Venta> ventas, Map<Long, BigDecimal> totalesVenta) {
        Map<String, Map<String, Object>> agrupado = new LinkedHashMap<>();
        for (Venta venta : ventas) {
            BigDecimal total = valorSeguro(totalesVenta.get(venta.getId()));
            if (total.compareTo(BigDecimal.ZERO) <= 0) continue;
            String nombre = nombreVendedor(venta);
            Map<String, Object> item = agrupado.computeIfAbsent(nombre, k -> {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("nombre", nombre);
                data.put("vendedor", nombre);
                data.put("total", BigDecimal.ZERO);
                data.put("cantidad", 0);
                return data;
            });
            item.put("total", valorSeguro((BigDecimal) item.get("total")).add(total));
            item.put("cantidad", ((Integer) item.get("cantidad")) + 1);
        }
        return agrupado.values().stream()
                .sorted((a, b) -> valorSeguro((BigDecimal) b.get("total")).compareTo(valorSeguro((BigDecimal) a.get("total"))))
                .collect(Collectors.toList());
    }

    private Map<String, ResumenMovimiento> resumirMovimientosCaja(List<MovimientoCaja> movimientos) {
        Map<String, ResumenMovimiento> out = new HashMap<>();
        for (MovimientoCaja mov : movimientos) out.computeIfAbsent(categoriaFinanciera(mov), k -> new ResumenMovimiento()).add(mov.getMonto());
        return out;
    }

    private String categoriaFinanciera(MovimientoCaja mov) {
        String categoria = mov.getCategoriaMovimiento();
        String concepto = normalizar(mov.getConcepto());
        if (CategoriaMovimiento.OTRO_INGRESO.equalsIgnoreCase(categoria) && concepto.contains("apertura")) return APERTURA_CAJA;
        if (categoria != null && !categoria.isBlank()) return categoria;
        return "INGRESO".equalsIgnoreCase(mov.getTipo()) ? CategoriaMovimiento.OTRO_INGRESO : CategoriaMovimiento.OTRO_EGRESO;
    }

    private Map<String, Object> crearFilaDetalle(String concepto, ResumenMovimiento resumen) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("concepto", concepto);
        out.put("cantidad", resumen.cantidad);
        out.put("monto", resumen.total);
        return out;
    }

    private BigDecimal sumarMontos(List<Map<String, Object>> items) {
        return items.stream().map(i -> (BigDecimal) i.get("monto")).map(ReporteFinancieroService::valorSeguro).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String nombreReporte(DetalleVenta detalle) {
        Producto producto = detalle.getProducto();
        String descripcion = detalle.getDescripcion() != null ? detalle.getDescripcion().trim() : "";
        String nombre = producto != null && producto.getNombre() != null ? producto.getNombre().trim() : "";
        if (producto != null && "SERVICIO".equalsIgnoreCase(producto.getTipo()) && !descripcion.isBlank() && !descripcion.equalsIgnoreCase(nombre)) return descripcion;
        if (producto != null && SERVICIO_RAPIDO_CODE.equalsIgnoreCase(producto.getCodigoInterno()) && !descripcion.isBlank()) return descripcion;
        if (!nombre.isBlank()) return nombre;
        return descripcion.isBlank() ? "Sin descripcion" : descripcion;
    }

    private String nombreReporte(DetalleDevolucion detalle) {
        Producto producto = detalle.getProducto();
        String descripcion = detalle.getDescripcion() != null ? detalle.getDescripcion().trim() : "";
        String nombre = producto != null && producto.getNombre() != null ? producto.getNombre().trim() : "";
        if (producto != null && "SERVICIO".equalsIgnoreCase(producto.getTipo()) && !descripcion.isBlank() && !descripcion.equalsIgnoreCase(nombre)) return descripcion;
        if (producto != null && SERVICIO_RAPIDO_CODE.equalsIgnoreCase(producto.getCodigoInterno()) && !descripcion.isBlank()) return descripcion;
        if (!nombre.isBlank()) return nombre;
        return descripcion.isBlank() ? "Sin descripcion" : descripcion;
    }

    private String categoriaReporte(DetalleVenta detalle) {
        if (detalle.getCategoriaServicio() != null && !detalle.getCategoriaServicio().isBlank()) return detalle.getCategoriaServicio();
        if (detalle.getProducto() != null && detalle.getProducto().getCategoria() != null && !detalle.getProducto().getCategoria().isBlank()) return detalle.getProducto().getCategoria();
        if (detalle.getProducto() != null && detalle.getProducto().getTipo() != null && !detalle.getProducto().getTipo().isBlank()) return detalle.getProducto().getTipo();
        return "Sin categoria";
    }

    private String nombreCliente(Venta venta) {
        if (venta.getClienteEntity() != null && venta.getClienteEntity().getNombreRazonSocial() != null && !venta.getClienteEntity().getNombreRazonSocial().isBlank()) {
            return venta.getClienteEntity().getNombreRazonSocial();
        }
        if (venta.getClienteDenominacion() != null && !venta.getClienteDenominacion().isBlank()) return venta.getClienteDenominacion();
        return "Cliente no identificado";
    }

    private String nombreVendedor(Venta venta) { return venta.getUsuario() != null && venta.getUsuario().getNombreCompleto() != null ? venta.getUsuario().getNombreCompleto() : "Sin asignar"; }
    private String claveDetalle(Long ventaId, Long productoId, String nombre) { return ventaId + "|" + productoId + "|" + normalizar(nombre); }
    private String claveProducto(Long productoId, String nombre) { return productoId + "|" + normalizar(nombre); }
    private String normalizar(String texto) { return texto == null ? "" : texto.trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT); }
    private static BigDecimal valorSeguro(BigDecimal valor) { return valor != null ? valor : BigDecimal.ZERO; }
    private String capitalizarMes(String texto) { String limpio = texto == null ? "" : texto.replace(".", "").trim(); return limpio.isBlank() ? "" : Character.toUpperCase(limpio.charAt(0)) + limpio.substring(1); }
    private String moneda(BigDecimal valor) { return "S/ " + valorSeguro(valor).setScale(2, RoundingMode.HALF_UP); }
    private String porcentaje(BigDecimal valor) { return valorSeguro(valor).setScale(2, RoundingMode.HALF_UP) + "%"; }
    private String cantidad(BigDecimal valor) { BigDecimal v = valorSeguro(valor).stripTrailingZeros(); return v.scale() < 0 ? v.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : v.toPlainString(); }

    private void encabezadoPdf(Document doc, String empresa, String titulo, LocalDate inicio, LocalDate fin, Font title, Font sub) throws DocumentException {
        Paragraph pEmpresa = new Paragraph(empresa, title); pEmpresa.setAlignment(Element.ALIGN_CENTER); doc.add(pEmpresa);
        Paragraph pTitulo = new Paragraph(titulo, title); pTitulo.setAlignment(Element.ALIGN_CENTER); doc.add(pTitulo);
        Paragraph pPeriodo = new Paragraph("Periodo: " + inicio.format(FECHA_PDF) + " al " + fin.format(FECHA_PDF), sub);
        pPeriodo.setAlignment(Element.ALIGN_CENTER); doc.add(pPeriodo); doc.add(new Paragraph(" "));
    }

    private PdfPTable tablaResumenPdf(String[] labels, String[] valores, Font section, Font total) throws DocumentException {
        PdfPTable table = new PdfPTable(labels.length);
        table.setWidthPercentage(100);
        float[] widths = new float[labels.length];
        Arrays.fill(widths, 2f);
        table.setWidths(widths);
        for (String label : labels) table.addCell(celdaPdf(label, section, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
        for (String valor : valores) table.addCell(celdaPdf(valor, total, null, Element.ALIGN_CENTER, 1));
        return table;
    }

    private PdfPTable tablaDetallePdf(List<Map<String, Object>> items, BigDecimal totalMonto, Font body, Font total) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1.2f, 1.8f});
        table.addCell(celdaPdf("Concepto", total, new Color(229, 231, 235), Element.ALIGN_LEFT, 1));
        table.addCell(celdaPdf("Cantidad", total, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
        table.addCell(celdaPdf("Monto", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
        for (Map<String, Object> item : items) {
            table.addCell(celdaPdf(String.valueOf(item.get("concepto")), body, null, Element.ALIGN_LEFT, 1));
            table.addCell(celdaPdf(String.valueOf(item.get("cantidad")), body, null, Element.ALIGN_CENTER, 1));
            table.addCell(celdaPdf(moneda((BigDecimal) item.get("monto")), body, null, Element.ALIGN_RIGHT, 1));
        }
        table.addCell(celdaPdf("TOTAL", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 2));
        table.addCell(celdaPdf(moneda(totalMonto), total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
        return table;
    }

    private PdfPTable tablaTopProductosPdf(List<Map<String, Object>> items, Font body, Font total) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1.2f, 1.7f});
        table.addCell(celdaPdf("Producto", total, new Color(229, 231, 235), Element.ALIGN_LEFT, 1));
        table.addCell(celdaPdf("Cant.", total, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
        table.addCell(celdaPdf("Total", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
        BigDecimal acumulado = BigDecimal.ZERO;
        for (Map<String, Object> item : items) {
            table.addCell(celdaPdf(String.valueOf(item.get("productoNombre")), body, null, Element.ALIGN_LEFT, 1));
            table.addCell(celdaPdf(cantidad((BigDecimal) item.get("cantidadVendida")), body, null, Element.ALIGN_CENTER, 1));
            table.addCell(celdaPdf(moneda((BigDecimal) item.get("totalVendido")), body, null, Element.ALIGN_RIGHT, 1));
            acumulado = acumulado.add(valorSeguro((BigDecimal) item.get("totalVendido")));
        }
        table.addCell(celdaPdf("TOTAL TOP PRODUCTOS", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 2));
        table.addCell(celdaPdf(moneda(acumulado), total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
        return table;
    }

    private PdfPTable tablaVendedoresPdf(List<Map<String, Object>> items, Font body, Font total) throws DocumentException {
        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{3.8f, 1.2f, 1.7f});
        table.addCell(celdaPdf("Vendedor", total, new Color(229, 231, 235), Element.ALIGN_LEFT, 1));
        table.addCell(celdaPdf("Ventas", total, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
        table.addCell(celdaPdf("Total", total, new Color(229, 231, 235), Element.ALIGN_RIGHT, 1));
        for (Map<String, Object> item : items) {
            table.addCell(celdaPdf(String.valueOf(item.get("vendedor")), body, null, Element.ALIGN_LEFT, 1));
            table.addCell(celdaPdf(String.valueOf(item.get("cantidad")), body, null, Element.ALIGN_CENTER, 1));
            table.addCell(celdaPdf(moneda((BigDecimal) item.get("total")), body, null, Element.ALIGN_RIGHT, 1));
        }
        return table;
    }

    private PdfPTable tablaSinRotacionPdf(List<Map<String, Object>> items, Font body, Font total) throws DocumentException {
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4f, 1.2f});
        table.addCell(celdaPdf("Producto", total, new Color(229, 231, 235), Element.ALIGN_LEFT, 1));
        table.addCell(celdaPdf("Stock", total, new Color(229, 231, 235), Element.ALIGN_CENTER, 1));
        int limite = Math.min(items.size(), 15);
        for (int i = 0; i < limite; i++) {
            Map<String, Object> item = items.get(i);
            table.addCell(celdaPdf(String.valueOf(item.get("productoNombre")), body, null, Element.ALIGN_LEFT, 1));
            table.addCell(celdaPdf(String.valueOf(item.get("stockActual")), body, null, Element.ALIGN_CENTER, 1));
        }
        if (limite == 0) table.addCell(celdaPdf("Todos los productos tuvieron rotacion reciente.", body, null, Element.ALIGN_CENTER, 2));
        return table;
    }

    private PdfPCell celdaPdf(String texto, Font font, Color fondo, int align, int colspan) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(6f);
        if (colspan > 1) cell.setColspan(colspan);
        if (fondo != null) cell.setBackgroundColor(fondo);
        return cell;
    }

    private int escribirDetalleExcel(Sheet sheet, int rowNum, String titulo, List<Map<String, Object>> items, BigDecimal totalMonto,
                                     CellStyle header, CellStyle section, CellStyle money, CellStyle num, CellStyle plain) {
        fila(sheet, rowNum++, 0, titulo, section);
        Row h = sheet.createRow(rowNum++);
        celda(h, 0, "Concepto", header); celda(h, 1, "Cantidad", header); celda(h, 2, "Monto", header);
        long totalCantidad = 0;
        for (Map<String, Object> item : items) {
            Row row = sheet.createRow(rowNum++);
            celda(row, 0, String.valueOf(item.get("concepto")), plain);
            long cantidad = ((Number) item.get("cantidad")).longValue();
            totalCantidad += cantidad;
            celdaNumero(row, 1, cantidad, num);
            celdaMoneda(row, 2, (BigDecimal) item.get("monto"), money);
        }
        Row totalRow = sheet.createRow(rowNum++);
        celda(totalRow, 0, "TOTAL", header);
        celdaNumero(totalRow, 1, totalCantidad, num);
        celdaMoneda(totalRow, 2, totalMonto, money);
        return rowNum;
    }

    private CellStyle estiloTitulo(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true); font.setFontHeightInPoints((short) 13); style.setFont(font);
        return style;
    }
    private CellStyle estiloCabecera(Workbook wb, IndexedColors color) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true); font.setColor(IndexedColors.WHITE.getIndex()); style.setFont(font);
        style.setFillForegroundColor(color.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setAlignment(HorizontalAlignment.CENTER); style.setVerticalAlignment(VerticalAlignment.CENTER);
        return style;
    }
    private CellStyle estiloSeccion(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        org.apache.poi.ss.usermodel.Font font = wb.createFont();
        font.setBold(true); style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex()); style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return style;
    }
    private CellStyle estiloMoneda(Workbook wb) { CellStyle style = wb.createCellStyle(); style.setDataFormat(wb.createDataFormat().getFormat("\"S/ \"#,##0.00")); style.setAlignment(HorizontalAlignment.RIGHT); return style; }
    private CellStyle estiloNumero(Workbook wb) { CellStyle style = wb.createCellStyle(); style.setAlignment(HorizontalAlignment.CENTER); return style; }
    private CellStyle estiloPlano(Workbook wb) { return wb.createCellStyle(); }
    private CellStyle estiloPorcentaje(Workbook wb) { CellStyle style = wb.createCellStyle(); style.setDataFormat(wb.createDataFormat().getFormat("0.00%")); style.setAlignment(HorizontalAlignment.RIGHT); return style; }

    private void fila(Sheet sheet, int rowNum, int col, String texto, CellStyle style) { celda(sheet.createRow(rowNum), col, texto, style); }
    private void filaMoneda(Sheet sheet, int rowNum, int col, BigDecimal valor, CellStyle style) { celdaMoneda(sheet.getRow(rowNum) != null ? sheet.getRow(rowNum) : sheet.createRow(rowNum), col, valor, style); }
    private void filaNumero(Sheet sheet, int rowNum, int col, long valor, CellStyle style) { celdaNumero(sheet.getRow(rowNum) != null ? sheet.getRow(rowNum) : sheet.createRow(rowNum), col, valor, style); }
    private void filaPorcentaje(Sheet sheet, int rowNum, int col, BigDecimal valor, CellStyle style) { celdaPorcentaje(sheet.getRow(rowNum) != null ? sheet.getRow(rowNum) : sheet.createRow(rowNum), col, valor, style); }
    private void celda(Row row, int col, String valor, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(valor); cell.setCellStyle(style); }
    private void celdaNumero(Row row, int col, long valor, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(valor); cell.setCellStyle(style); }
    private void celdaNumeroDecimal(Row row, int col, BigDecimal valor, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(valorSeguro(valor).doubleValue()); cell.setCellStyle(style); }
    private void celdaMoneda(Row row, int col, BigDecimal valor, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(valorSeguro(valor).doubleValue()); cell.setCellStyle(style); }
    private void celdaPorcentaje(Row row, int col, BigDecimal valor, CellStyle style) { Cell cell = row.createCell(col); cell.setCellValue(valorSeguro(valor).doubleValue() / 100d); cell.setCellStyle(style); }
}
