package com.libreria.sistema.service;

import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.ConfigCuentaFija;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.CompraRepository;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
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
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.PageRequest;
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
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class MiNegocioService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal NOVENTA_DIAS = BigDecimal.valueOf(90);
    private static final BigDecimal HORAS_DIA_REFERENCIA = BigDecimal.TEN;
    private static final int DIAS_INVENTARIO_ESTANCADO = 60;

    private final VentaRepository ventaRepository;
    private final CompraRepository compraRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CajaRepository cajaRepository;
    private final ProductoRepository productoRepository;
    private final CuentaFijaService cuentaFijaService;

    /**
     * Calcula todos los indicadores para el período dado.
     * También calcula el período anterior de igual duración para comparación.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> obtenerAnalisis(LocalDate inicio, LocalDate fin) {
        Map<String, Object> r = new LinkedHashMap<>();
        long diasPeriodo = Math.max(1, ChronoUnit.DAYS.between(inicio, fin) + 1);
        BigDecimal diasPeriodoBd = BigDecimal.valueOf(diasPeriodo);

        // ── Período actual ───────────────────────────────────────────
        BigDecimal vendido    = safe(ventaRepository.sumVentasValidasByPeriodo(inicio, fin));
        long       numVentas  = ventaRepository.countVentasValidasByPeriodo(inicio, fin);
        BigDecimal invertido  = safe(compraRepository.sumTotalByPeriodo(
                inicio.atStartOfDay(), fin.atTime(23, 59, 59)));
        BigDecimal ganancia   = safe(detalleVentaRepository.sumarUtilidadPorPeriodo(inicio, fin));
        BigDecimal margenPct  = porcentaje(ganancia, vendido, 1);
        BigDecimal margenFactor = factor(ganancia, vendido);
        BigDecimal ticketPromedio = numVentas > 0
                ? dividir(vendido, BigDecimal.valueOf(numVentas), 2)
                : BigDecimal.ZERO;
        BigDecimal costoMercaderiaVendida = maxCero(vendido.subtract(ganancia));

        // ── Caja del período ────────────────────────────────────────
        BigDecimal ingresosCaja = safe(cajaRepository.sumarIngresosPorFechas(inicio, fin));
        BigDecimal egresosCaja  = safe(cajaRepository.sumarEgresosPorFechas(inicio, fin));
        BigDecimal flujoCaja    = ingresosCaja.subtract(egresosCaja);
        BigDecimal ratioLiquidez = dividir(ingresosCaja, egresosCaja, 2);

        BigDecimal ventasCobradasCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.VENTA, inicio, fin));
        BigDecimal cobranzasCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.COBRANZA, inicio, fin));
        BigDecimal anticiposServicioCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.ANTICIPO_SERVICIO, inicio, fin));
        BigDecimal aportesDuenoCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.APORTE_DUENO, inicio, fin));
        BigDecimal otrosIngresosCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.OTRO_INGRESO, inicio, fin));
        BigDecimal comprasMercaderiaCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.COMPRA_MERCADERIA, inicio, fin));
        BigDecimal devolucionesCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.DEVOLUCION, inicio, fin));
        BigDecimal otrosEgresosCaja = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.OTRO_EGRESO, inicio, fin));
        BigDecimal gastosVariables = devolucionesCaja.add(otrosEgresosCaja).setScale(2, RoundingMode.HALF_UP);

        // ── Gastos Operativos y Retiros ─────────────────────────────
        BigDecimal gastosOperativos = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.GASTO_OPERATIVO, inicio, fin));
        BigDecimal retiroDueno      = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.RETIRO_DUENO, inicio, fin));
        BigDecimal utilidadNeta     = ganancia.subtract(gastosOperativos);
        BigDecimal saldoReinversion = utilidadNeta.subtract(retiroDueno);
        BigDecimal flujoOperativo   = ingresosCaja.subtract(gastosOperativos);
        BigDecimal egresosNoOperativos = maxCero(egresosCaja.subtract(gastosOperativos).subtract(retiroDueno));
        List<ConfigCuentaFija> cuentasFijasActivas = listaSegura(cuentaFijaService.listarActivas());
        BigDecimal metaCostosFijos = cuentasFijasActivas.stream()
                .map(ConfigCuentaFija::getMontoMensual)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth mesReferencia = YearMonth.from(fin);
        BigDecimal diasMes = BigDecimal.valueOf(mesReferencia.lengthOfMonth());
        BigDecimal costoFijoDiario = dividir(metaCostosFijos, diasMes, 2);
        BigDecimal costoFijoHora = dividir(costoFijoDiario, HORAS_DIA_REFERENCIA, 2);
        BigDecimal costoFijoPeriodo = costoFijoDiario.multiply(diasPeriodoBd).setScale(2, RoundingMode.HALF_UP);
        List<Map<String, Object>> cuentasFijasResumen = construirCuentasFijasResumen(cuentasFijasActivas, diasMes, diasPeriodoBd);
        BigDecimal prestamosBancosMensual = sumarCuentasPorGrupo(cuentasFijasActivas, "PRESTAMOS_BANCOS");
        BigDecimal prestamosBancosPeriodo = montoMensualAlPeriodo(prestamosBancosMensual, diasMes, diasPeriodoBd);
        BigDecimal alquilerMensual = sumarCuentasPorGrupo(cuentasFijasActivas, "ALQUILER");
        BigDecimal alquilerPeriodo = montoMensualAlPeriodo(alquilerMensual, diasMes, diasPeriodoBd);
        BigDecimal serviciosMensual = sumarCuentasPorGrupo(cuentasFijasActivas, "SERVICIOS");
        BigDecimal serviciosPeriodo = montoMensualAlPeriodo(serviciosMensual, diasMes, diasPeriodoBd);
        BigDecimal internetMensual = sumarCuentasPorTexto(cuentasFijasActivas, "INTERNET");
        BigDecimal internetPeriodo = montoMensualAlPeriodo(internetMensual, diasMes, diasPeriodoBd);
        BigDecimal puntoEquilibrio = margenFactor.compareTo(BigDecimal.ZERO) > 0
                ? dividir(costoFijoPeriodo, margenFactor, 2)
                : BigDecimal.ZERO;
        BigDecimal faltaParaEquilibrio = maxCero(puntoEquilibrio.subtract(vendido));
        BigDecimal avanceEquilibrioPct = porcentaje(vendido, puntoEquilibrio, 1);
        BigDecimal metaDiaria = dividir(puntoEquilibrio, diasPeriodoBd, 2);
        BigDecimal metaDiariaSana = dividir(costoMercaderiaVendida.add(puntoEquilibrio), diasPeriodoBd, 2);
        BigDecimal ventasPromedioDiario = dividir(vendido, diasPeriodoBd, 2);
        BigDecimal gananciaPromedioDiaria = dividir(ganancia, diasPeriodoBd, 2);
        BigDecimal utilidadPromedioDiaria = dividir(utilidadNeta, diasPeriodoBd, 2);
        BigDecimal flujoCajaPromedioDiario = dividir(flujoCaja, diasPeriodoBd, 2);
        BigDecimal proyeccionVentas3Meses = ventasPromedioDiario.multiply(NOVENTA_DIAS).setScale(2, RoundingMode.HALF_UP);
        BigDecimal proyeccionGanancia3Meses = gananciaPromedioDiaria.multiply(NOVENTA_DIAS).setScale(2, RoundingMode.HALF_UP);
        BigDecimal proyeccionUtilidad3Meses = utilidadPromedioDiaria.multiply(NOVENTA_DIAS).setScale(2, RoundingMode.HALF_UP);
        BigDecimal proyeccionFlujoCaja3Meses = flujoCajaPromedioDiario.multiply(NOVENTA_DIAS).setScale(2, RoundingMode.HALF_UP);
        BigDecimal margenNetoPct = porcentaje(utilidadNeta, vendido, 1);
        BigDecimal retornoPorSolInvertido = dividir(vendido, invertido, 2);
        BigDecimal gananciaPorSolInvertido = dividir(ganancia, invertido, 2);
        BigDecimal utilidadPorSolVendido = dividir(utilidadNeta, vendido, 2);
        BigDecimal utilidadAdministrativa = ganancia.subtract(costoFijoPeriodo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal utilidadLibreDespuesRetiros = utilidadAdministrativa.subtract(retiroDueno).setScale(2, RoundingMode.HALF_UP);
        BigDecimal cajaLibreTrasReposicion = flujoCaja.subtract(costoMercaderiaVendida).setScale(2, RoundingMode.HALF_UP);
        BigDecimal faltanteReposicion = maxCero(costoMercaderiaVendida.subtract(maxCero(flujoCaja)));
        BigDecimal reposicionCubiertaPct = porcentaje(maxCero(flujoCaja), costoMercaderiaVendida, 1);
        BigDecimal capitalTrabajoSugerido = costoMercaderiaVendida.add(costoFijoPeriodo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal capitalTrabajoCubiertoPct = porcentaje(maxCero(flujoCaja), capitalTrabajoSugerido, 1);
        BigDecimal liquidezDias = costoFijoDiario.compareTo(BigDecimal.ZERO) > 0
                ? dividir(maxCero(flujoCaja), costoFijoDiario, 1)
                : BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        BigDecimal pesoPrestamosSobreVentasPct = porcentaje(prestamosBancosPeriodo, vendido, 1);

        // ── Inventario (valor actual, no por período) ───────────────
        BigDecimal valorInventario = listaSegura(productoRepository.findByActivoTrue()).stream()
                .map(p -> {
                    BigDecimal costo = p.getPrecioCompra() != null ? p.getPrecioCompra() : BigDecimal.ZERO;
                    BigDecimal stock = BigDecimal.valueOf(p.getStockActual() != null ? p.getStockActual() : 0);
                    return costo.multiply(stock);
                })
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        LocalDate fechaInventarioEstancado = fin.minusDays(DIAS_INVENTARIO_ESTANCADO);
        long productosEstancados = productoRepository.countProductosSinMovimiento(fechaInventarioEstancado);
        BigDecimal capitalEstancado = safe(productoRepository.calcularCapitalEstancado(fechaInventarioEstancado));
        BigDecimal capitalEstancadoPct = porcentaje(capitalEstancado, valorInventario, 1);
        List<Map<String, Object>> inventarioEstancado = construirInventarioEstancado(fechaInventarioEstancado);
        BigDecimal coberturaInventarioDias = ventasPromedioDiario.compareTo(BigDecimal.ZERO) > 0
                ? dividir(valorInventario, ventasPromedioDiario, 1)
                : BigDecimal.ZERO;
        BigDecimal inventarioSobreVentasPct = porcentaje(valorInventario, vendido, 1);
        String estadoNegocio = calcularEstadoNegocio(utilidadNeta, flujoCaja, vendido, puntoEquilibrio, costoFijoPeriodo);
        String diagnosticoPrincipal = construirDiagnosticoPrincipal(estadoNegocio, vendido, puntoEquilibrio,
                flujoCaja, cajaLibreTrasReposicion, utilidadNeta);
        List<Map<String, Object>> alertasFinancieras = construirAlertasFinancieras(
                vendido, margenPct, utilidadNeta, flujoCaja, puntoEquilibrio, faltaParaEquilibrio,
                cajaLibreTrasReposicion, faltanteReposicion, capitalEstancado, capitalEstancadoPct,
                metaCostosFijos, prestamosBancosPeriodo, pesoPrestamosSobreVentasPct);

        // ── Top 10 productos más rentables ─────────────────────────
        List<Object[]> topRaw = detalleVentaRepository.topProductosPorUtilidad(
                inicio, fin, PageRequest.of(0, 10));
        List<Map<String, Object>> topProductos = new ArrayList<>();
        for (Object[] row : topRaw) {
            BigDecimal ingreso   = row[5] != null ? (BigDecimal) row[5] : BigDecimal.ZERO;
            BigDecimal utilidad  = row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO;
            BigDecimal margenProd = ingreso.compareTo(BigDecimal.ZERO) > 0
                    ? utilidad.divide(ingreso, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("nombre",    row[1]);
            item.put("categoria", row[2]);
            item.put("cantidad",  row[3]);
            item.put("ingreso",   ingreso);
            item.put("utilidad",  utilidad);
            item.put("margen",    margenProd);
            topProductos.add(item);
        }

        // ── Período anterior (misma duración) para comparación ─────
        LocalDate inicioAnt = inicio.minusDays(diasPeriodo);
        LocalDate finAnt    = fin.minusDays(diasPeriodo);
        BigDecimal vendidoAnt  = safe(ventaRepository.sumVentasValidasByPeriodo(inicioAnt, finAnt));
        BigDecimal invertidoAnt = safe(compraRepository.sumTotalByPeriodo(
                inicioAnt.atStartOfDay(), finAnt.atTime(23, 59, 59)));
        BigDecimal gananciaAnt  = safe(detalleVentaRepository.sumarUtilidadPorPeriodo(inicioAnt, finAnt));

        BigDecimal gastosOperativosAnt = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.GASTO_OPERATIVO, inicioAnt, finAnt));
        BigDecimal retiroDuenoAnt      = safe(cajaRepository.sumarPorCategoriaYFechas(CategoriaMovimiento.RETIRO_DUENO, inicioAnt, finAnt));
        BigDecimal utilidadNetaAnt     = gananciaAnt.subtract(gastosOperativosAnt);
        BigDecimal saldoReinversionAnt = utilidadNetaAnt.subtract(retiroDuenoAnt);

        r.put("vendido",            vendido);
        r.put("numVentas",          numVentas);
        r.put("invertido",          invertido);
        r.put("ganancia",           ganancia);
        r.put("margenPct",          margenPct);
        r.put("ingresosCaja",       ingresosCaja);
        r.put("egresosCaja",        egresosCaja);
        r.put("flujoCaja",          flujoCaja);
        r.put("ratioLiquidez",      ratioLiquidez);
        r.put("ventasCobradasCaja", ventasCobradasCaja);
        r.put("cobranzasCaja",      cobranzasCaja);
        r.put("anticiposServicioCaja", anticiposServicioCaja);
        r.put("aportesDuenoCaja",   aportesDuenoCaja);
        r.put("otrosIngresosCaja",  otrosIngresosCaja);
        r.put("comprasMercaderiaCaja", comprasMercaderiaCaja);
        r.put("devolucionesCaja",   devolucionesCaja);
        r.put("otrosEgresosCaja",   otrosEgresosCaja);
        r.put("gastosVariables",    gastosVariables);
        r.put("gastosOperativos",   gastosOperativos);
        r.put("retiroDueno",        retiroDueno);
        r.put("utilidadNeta",       utilidadNeta);
        r.put("saldoReinversion",   saldoReinversion);
        r.put("flujoOperativo",     flujoOperativo);
        r.put("egresosNoOperativos", egresosNoOperativos);
        r.put("metaCostosFijos",    metaCostosFijos);
        r.put("cuentasFijasResumen", cuentasFijasResumen);
        r.put("prestamosBancosMensual", prestamosBancosMensual);
        r.put("prestamosBancosPeriodo", prestamosBancosPeriodo);
        r.put("pesoPrestamosSobreVentasPct", pesoPrestamosSobreVentasPct);
        r.put("alquilerMensual",    alquilerMensual);
        r.put("alquilerPeriodo",    alquilerPeriodo);
        r.put("serviciosMensual",   serviciosMensual);
        r.put("serviciosPeriodo",   serviciosPeriodo);
        r.put("internetMensual",    internetMensual);
        r.put("internetPeriodo",    internetPeriodo);
        r.put("valorInventario",    valorInventario);
        r.put("productosEstancados", productosEstancados);
        r.put("capitalEstancado",   capitalEstancado);
        r.put("capitalEstancadoPct", capitalEstancadoPct);
        r.put("inventarioEstancado", inventarioEstancado);
        r.put("fechaInventarioEstancado", fechaInventarioEstancado);
        r.put("diasInventarioEstancado", DIAS_INVENTARIO_ESTANCADO);
        r.put("topProductos",       topProductos);
        r.put("diasPeriodo",        diasPeriodo);
        r.put("ticketPromedio",     ticketPromedio);
        r.put("costoMercaderiaVendida", costoMercaderiaVendida);
        r.put("dineroReposicionSugerido", costoMercaderiaVendida);
        r.put("cajaLibreTrasReposicion", cajaLibreTrasReposicion);
        r.put("faltanteReposicion", faltanteReposicion);
        r.put("reposicionCubiertaPct", reposicionCubiertaPct);
        r.put("capitalTrabajoSugerido", capitalTrabajoSugerido);
        r.put("capitalTrabajoCubiertoPct", capitalTrabajoCubiertoPct);
        r.put("margenBrutoPct",     margenPct);
        r.put("margenNetoPct",      margenNetoPct);
        r.put("retornoPorSolInvertido", retornoPorSolInvertido);
        r.put("gananciaPorSolInvertido", gananciaPorSolInvertido);
        r.put("utilidadPorSolVendido", utilidadPorSolVendido);
        r.put("utilidadOperativa",  utilidadNeta);
        r.put("ebitdaSimplificado", utilidadNeta);
        r.put("utilidadAdministrativa", utilidadAdministrativa);
        r.put("utilidadLibreDespuesRetiros", utilidadLibreDespuesRetiros);
        r.put("liquidezPeriodo",    flujoCaja);
        r.put("liquidezDias",       liquidezDias);
        r.put("costoFijoDiario",    costoFijoDiario);
        r.put("costoFijoHora",      costoFijoHora);
        r.put("horasDiaReferencia", HORAS_DIA_REFERENCIA);
        r.put("costoFijoPeriodo",   costoFijoPeriodo);
        r.put("puntoEquilibrio",    puntoEquilibrio);
        r.put("breakEven",          puntoEquilibrio);
        r.put("faltaParaEquilibrio", faltaParaEquilibrio);
        r.put("avanceEquilibrioPct", avanceEquilibrioPct);
        r.put("metaDiaria",         metaDiaria);
        r.put("metaDiariaSana",     metaDiariaSana);
        r.put("ventasPromedioDiario", ventasPromedioDiario);
        r.put("gananciaPromedioDiaria", gananciaPromedioDiaria);
        r.put("utilidadPromedioDiaria", utilidadPromedioDiaria);
        r.put("flujoCajaPromedioDiario", flujoCajaPromedioDiario);
        r.put("proyeccionVentas3Meses", proyeccionVentas3Meses);
        r.put("proyeccionGanancia3Meses", proyeccionGanancia3Meses);
        r.put("proyeccionUtilidad3Meses", proyeccionUtilidad3Meses);
        r.put("proyeccionFlujoCaja3Meses", proyeccionFlujoCaja3Meses);
        r.put("coberturaInventarioDias", coberturaInventarioDias);
        r.put("inventarioSobreVentasPct", inventarioSobreVentasPct);
        r.put("estadoNegocio",      estadoNegocio);
        r.put("diagnosticoPrincipal", diagnosticoPrincipal);
        r.put("alertasFinancieras", alertasFinancieras);

        r.put("varVendido",          variacion(vendido, vendidoAnt));
        r.put("varInvertido",        variacion(invertido, invertidoAnt));
        r.put("varGanancia",         variacion(ganancia, gananciaAnt));
        r.put("varGastosOperativos", variacion(gastosOperativos, gastosOperativosAnt));
        r.put("varUtilidadNeta",     variacion(utilidadNeta, utilidadNetaAnt));
        r.put("varRetiroDueno",      variacion(retiroDueno, retiroDuenoAnt));
        r.put("varSaldoReinversion", variacion(saldoReinversion, saldoReinversionAnt));

        r.put("vendidoAnt",          vendidoAnt);
        r.put("invertidoAnt",        invertidoAnt);
        r.put("gananciaAnt",         gananciaAnt);
        r.put("gastosOperativosAnt", gastosOperativosAnt);
        r.put("retiroDuenoAnt",      retiroDuenoAnt);
        r.put("utilidadNetaAnt",     utilidadNetaAnt);
        r.put("saldoReinversionAnt", saldoReinversionAnt);
        return r;
    }

    @Transactional(readOnly = true)
    public byte[] exportarCierreMensualExcel(LocalDate inicio, LocalDate fin) throws IOException {
        Map<String, Object> datos = obtenerAnalisis(inicio, fin);
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            CellStyle header = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            Sheet resumen = workbook.createSheet("Cierre contador");
            resumen.createRow(0).createCell(0).setCellValue("Cierre mensual para contador");
            resumen.createRow(1).createCell(0).setCellValue("Periodo: " + inicio + " al " + fin);
            Row head = resumen.createRow(3);
            head.createCell(0).setCellValue("Indicador");
            head.createCell(1).setCellValue("Monto / valor");
            head.createCell(2).setCellValue("Lectura");
            head.getCell(0).setCellStyle(header);
            head.getCell(1).setCellStyle(header);
            head.getCell(2).setCellStyle(header);

            int row = 4;
            row = filaResumen(resumen, row, "Ventas del periodo", datos.get("vendido"), "Ingreso emitido por ventas validas");
            row = filaResumen(resumen, row, "Compras del periodo", datos.get("invertido"), "Mercaderia/insumos comprados");
            row = filaResumen(resumen, row, "Gastos operativos", datos.get("gastosOperativos"), "Salidas por operacion diaria");
            row = filaResumen(resumen, row, "Gastos variables / otros egresos", datos.get("gastosVariables"), "Devoluciones y otros egresos separados de los gastos fijos");
            row = filaResumen(resumen, row, "Gastos fijos prorrateados", datos.get("costoFijoPeriodo"), "Alquiler, servicios, sueldos, bancos configurados");
            row = filaResumen(resumen, row, "Prestamos/bancos", datos.get("prestamosBancosPeriodo"), "Carga financiera del periodo");
            row = filaResumen(resumen, row, "Retiros personales", datos.get("retiroDueno"), "Dinero retirado del negocio");
            row = filaResumen(resumen, row, "Dinero para reposicion", datos.get("dineroReposicionSugerido"), "Costo de lo vendido que conviene separar");
            row = filaResumen(resumen, row, "Utilidad bruta", datos.get("ganancia"), "Venta menos costo directo registrado");
            row = filaResumen(resumen, row, "Utilidad neta", datos.get("utilidadNeta"), "Utilidad bruta menos gastos operativos");
            row = filaResumen(resumen, row, "Flujo de caja", datos.get("flujoCaja"), "Ingresos caja menos egresos caja");
            row = filaResumen(resumen, row, "Inventario valorizado", datos.get("valorInventario"), "Stock actual valorizado al costo");
            row = filaResumen(resumen, row, "Inventario estancado", datos.get("capitalEstancado"), "Capital sin movimiento reciente");
            row = filaResumen(resumen, row, "Ticket promedio", datos.get("ticketPromedio"), "Venta promedio por comprobante");
            row = filaResumen(resumen, row, "Punto de equilibrio", datos.get("puntoEquilibrio"), "Venta minima para cubrir costos fijos segun margen");
            row = filaResumen(resumen, row, "Meta diaria sana", datos.get("metaDiariaSana"), "Venta diaria que repone mercaderia y cubre fijos");
            filaResumen(resumen, row, "Proyeccion utilidad 3 meses", datos.get("proyeccionUtilidad3Meses"), "Proyeccion lineal con el ritmo actual");
            autoSize(resumen, 3);

            Sheet alertas = workbook.createSheet("Alertas");
            escribirListaMap(alertas, header, listaMap(datos.get("alertasFinancieras")),
                    List.of("tipo", "titulo", "mensaje", "accion"));

            Sheet top = workbook.createSheet("Top productos");
            escribirListaMap(top, header, listaMap(datos.get("topProductos")),
                    List.of("nombre", "categoria", "cantidad", "ingreso", "utilidad", "margen"));

            Sheet estancado = workbook.createSheet("Inventario estancado");
            escribirListaMap(estancado, header, listaMap(datos.get("inventarioEstancado")),
                    List.of("nombre", "categoria", "stock", "costo", "valor", "ultimaVenta"));

            workbook.write(out);
            return out.toByteArray();
        }
    }

    @Transactional(readOnly = true)
    public void exportarCierreMensualPdf(LocalDate inicio, LocalDate fin, OutputStream out) throws DocumentException {
        Map<String, Object> datos = obtenerAnalisis(inicio, fin);
        Document doc = new Document(PageSize.A4, 28, 28, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 9);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 8);

        Paragraph titulo = new Paragraph("Cierre mensual para contador", title);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);
        doc.add(new Paragraph("Periodo: " + inicio.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " al " + fin.format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), small));
        doc.add(new Paragraph("Diagnostico: " + textoObj(datos.get("diagnosticoPrincipal")), small));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(3);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{2.4f, 1.2f, 3.2f});
        addHead(table, "Indicador", head);
        addHead(table, "Valor", head);
        addHead(table, "Lectura", head);
        addPdfRow(table, "Ventas del periodo", moneda(datos.get("vendido")), "Ingreso emitido por ventas validas", body);
        addPdfRow(table, "Compras del periodo", moneda(datos.get("invertido")), "Mercaderia/insumos comprados", body);
        addPdfRow(table, "Gastos operativos", moneda(datos.get("gastosOperativos")), "Salidas por operacion diaria", body);
        addPdfRow(table, "Gastos variables / otros egresos", moneda(datos.get("gastosVariables")), "Devoluciones y otros egresos separados de los gastos fijos", body);
        addPdfRow(table, "Gastos fijos prorrateados", moneda(datos.get("costoFijoPeriodo")), "Alquiler, servicios, sueldos y bancos configurados", body);
        addPdfRow(table, "Prestamos/bancos", moneda(datos.get("prestamosBancosPeriodo")), "Carga financiera del periodo", body);
        addPdfRow(table, "Retiros personales", moneda(datos.get("retiroDueno")), "Dinero retirado del negocio", body);
        addPdfRow(table, "Dinero para reposicion", moneda(datos.get("dineroReposicionSugerido")), "Costo de lo vendido que conviene separar", body);
        addPdfRow(table, "Utilidad bruta", moneda(datos.get("ganancia")), "Venta menos costo directo registrado", body);
        addPdfRow(table, "Utilidad neta", moneda(datos.get("utilidadNeta")), "Utilidad bruta menos gastos operativos", body);
        addPdfRow(table, "Flujo de caja", moneda(datos.get("flujoCaja")), "Ingresos caja menos egresos caja", body);
        addPdfRow(table, "Inventario valorizado", moneda(datos.get("valorInventario")), "Stock actual valorizado al costo", body);
        addPdfRow(table, "Inventario estancado", moneda(datos.get("capitalEstancado")), "Capital sin movimiento reciente", body);
        addPdfRow(table, "Ticket promedio", moneda(datos.get("ticketPromedio")), "Venta promedio por comprobante", body);
        addPdfRow(table, "Punto de equilibrio", moneda(datos.get("puntoEquilibrio")), "Venta minima para cubrir costos fijos segun margen", body);
        addPdfRow(table, "Meta diaria sana", moneda(datos.get("metaDiariaSana")), "Venta diaria que repone mercaderia y cubre fijos", body);
        doc.add(table);
        doc.close();
    }

    private int filaResumen(Sheet sheet, int rowIndex, String indicador, Object valor, String lectura) {
        Row row = sheet.createRow(rowIndex++);
        row.createCell(0).setCellValue(indicador);
        if (valor instanceof Number number) {
            row.createCell(1).setCellValue(number.doubleValue());
        } else {
            row.createCell(1).setCellValue(textoObj(valor));
        }
        row.createCell(2).setCellValue(lectura);
        return rowIndex;
    }

    private void escribirListaMap(Sheet sheet, CellStyle header, List<Map<String, Object>> datos, List<String> columnas) {
        Row head = sheet.createRow(0);
        for (int i = 0; i < columnas.size(); i++) {
            head.createCell(i).setCellValue(columnas.get(i));
            head.getCell(i).setCellStyle(header);
        }

        int rowIndex = 1;
        for (Map<String, Object> item : listaSegura(datos)) {
            Row row = sheet.createRow(rowIndex++);
            for (int i = 0; i < columnas.size(); i++) {
                Object valor = item.get(columnas.get(i));
                if (valor == null && "tipo".equals(columnas.get(i))) {
                    valor = item.get("nivel");
                }
                if (valor == null && "costo".equals(columnas.get(i))) {
                    valor = item.get("costoUnitario");
                }
                if (valor == null && "valor".equals(columnas.get(i))) {
                    valor = item.get("valorStock");
                }
                if (valor instanceof Number number) {
                    row.createCell(i).setCellValue(number.doubleValue());
                } else {
                    row.createCell(i).setCellValue(textoObj(valor));
                }
            }
        }
        autoSize(sheet, columnas.size());
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listaMap(Object value) {
        if (!(value instanceof List<?> raw)) {
            return List.of();
        }
        return raw.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private void autoSize(Sheet sheet, int columnas) {
        for (int i = 0; i < columnas; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String textoObj(Object value) {
        return value != null ? value.toString() : "";
    }

    private String moneda(Object value) {
        BigDecimal monto = BigDecimal.ZERO;
        if (value instanceof BigDecimal bd) {
            monto = bd;
        } else if (value instanceof Number number) {
            monto = BigDecimal.valueOf(number.doubleValue());
        }
        return "S/ " + monto.setScale(2, RoundingMode.HALF_UP);
    }

    private void addHead(PdfPTable table, String texto, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(texto, font));
        cell.setBackgroundColor(new Color(0, 102, 102));
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(5);
        table.addCell(cell);
    }

    private void addPdfRow(PdfPTable table, String indicador, String valor, String lectura, Font font) {
        addPdfCell(table, indicador, font, Element.ALIGN_LEFT);
        addPdfCell(table, valor, font, Element.ALIGN_RIGHT);
        addPdfCell(table, lectura, font, Element.ALIGN_LEFT);
    }

    private void addPdfCell(PdfPTable table, String texto, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(texto != null ? texto : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private BigDecimal safe(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private <T> List<T> listaSegura(List<T> lista) {
        return lista != null ? lista : List.of();
    }

    private BigDecimal dividir(BigDecimal numerador, BigDecimal denominador, int escala) {
        if (denominador == null || denominador.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(escala, RoundingMode.HALF_UP);
        }
        return safe(numerador).divide(denominador, escala, RoundingMode.HALF_UP);
    }

    private BigDecimal factor(BigDecimal parte, BigDecimal total) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return safe(parte).divide(total, 6, RoundingMode.HALF_UP);
    }

    private BigDecimal porcentaje(BigDecimal parte, BigDecimal total, int escala) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(escala, RoundingMode.HALF_UP);
        }
        return safe(parte)
                .divide(total, 6, RoundingMode.HALF_UP)
                .multiply(CIEN)
                .setScale(escala, RoundingMode.HALF_UP);
    }

    private BigDecimal maxCero(BigDecimal valor) {
        return safe(valor).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal montoMensualAlPeriodo(BigDecimal montoMensual, BigDecimal diasMes, BigDecimal diasPeriodo) {
        return dividir(safe(montoMensual), diasMes, 4)
                .multiply(diasPeriodo)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumarCuentasPorGrupo(List<ConfigCuentaFija> cuentas, String grupoBuscado) {
        return listaSegura(cuentas).stream()
                .filter(c -> grupoBuscado.equals(clasificarCuentaFija(c)))
                .map(ConfigCuentaFija::getMontoMensual)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal sumarCuentasPorTexto(List<ConfigCuentaFija> cuentas, String textoBuscado) {
        String buscado = normalizarTexto(textoBuscado);
        return listaSegura(cuentas).stream()
                .filter(c -> textoCuenta(c).contains(buscado))
                .map(ConfigCuentaFija::getMontoMensual)
                .map(this::safe)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private List<Map<String, Object>> construirCuentasFijasResumen(List<ConfigCuentaFija> cuentas,
                                                                   BigDecimal diasMes,
                                                                   BigDecimal diasPeriodo) {
        Map<String, Map<String, Object>> grupos = new LinkedHashMap<>();
        for (String codigo : List.of("ALQUILER", "SERVICIOS", "PRESTAMOS_BANCOS", "PERSONAL", "OTROS")) {
            grupos.put(codigo, crearGrupoCuenta(codigo));
        }

        for (ConfigCuentaFija cuenta : listaSegura(cuentas)) {
            String codigo = clasificarCuentaFija(cuenta);
            Map<String, Object> grupo = grupos.computeIfAbsent(codigo, this::crearGrupoCuenta);
            BigDecimal mensual = safe(cuenta.getMontoMensual());
            BigDecimal periodo = montoMensualAlPeriodo(mensual, diasMes, diasPeriodo);
            grupo.put("mensual", ((BigDecimal) grupo.get("mensual")).add(mensual).setScale(2, RoundingMode.HALF_UP));
            grupo.put("periodo", ((BigDecimal) grupo.get("periodo")).add(periodo).setScale(2, RoundingMode.HALF_UP));
            grupo.put("cantidad", ((Integer) grupo.get("cantidad")) + 1);
            @SuppressWarnings("unchecked")
            List<String> nombres = (List<String>) grupo.get("nombres");
            if (cuenta.getNombre() != null && !cuenta.getNombre().isBlank() && nombres.size() < 3) {
                nombres.add(cuenta.getNombre().trim());
            }
        }

        return grupos.values().stream()
                .filter(g -> ((BigDecimal) g.get("mensual")).compareTo(BigDecimal.ZERO) > 0)
                .toList();
    }

    private Map<String, Object> crearGrupoCuenta(String codigo) {
        Map<String, Object> grupo = new LinkedHashMap<>();
        grupo.put("codigo", codigo);
        grupo.put("nombre", nombreGrupoCuenta(codigo));
        grupo.put("icono", iconoGrupoCuenta(codigo));
        grupo.put("badgeClass", badgeGrupoCuenta(codigo));
        grupo.put("mensual", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        grupo.put("periodo", BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        grupo.put("cantidad", 0);
        grupo.put("nombres", new ArrayList<String>());
        return grupo;
    }

    private String clasificarCuentaFija(ConfigCuentaFija cuenta) {
        String texto = textoCuenta(cuenta);
        if (contieneAlguno(texto, "BANCO", "PRESTAMO", "CREDITO", "CUOTA", "FINANCIAMIENTO", "DEUDA")) {
            return "PRESTAMOS_BANCOS";
        }
        if (contieneAlguno(texto, "ALQUILER", "RENTA", "LOCAL")) {
            return "ALQUILER";
        }
        if (contieneAlguno(texto, "INTERNET", "LUZ", "AGUA", "TELEFONO", "CELULAR", "SERVICIO")) {
            return "SERVICIOS";
        }
        if (contieneAlguno(texto, "SUELDO", "PLANILLA", "PERSONAL", "AYUDANTE")) {
            return "PERSONAL";
        }
        return "OTROS";
    }

    private String textoCuenta(ConfigCuentaFija cuenta) {
        if (cuenta == null) {
            return "";
        }
        return normalizarTexto((cuenta.getCategoria() != null ? cuenta.getCategoria() : "") + " "
                + (cuenta.getNombre() != null ? cuenta.getNombre() : ""));
    }

    private boolean contieneAlguno(String texto, String... palabras) {
        for (String palabra : palabras) {
            if (texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private String normalizarTexto(String texto) {
        if (texto == null) {
            return "";
        }
        return Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .trim();
    }

    private String nombreGrupoCuenta(String codigo) {
        return switch (codigo) {
            case "ALQUILER" -> "Alquiler/local";
            case "SERVICIOS" -> "Servicios";
            case "PRESTAMOS_BANCOS" -> "Prestamos/bancos";
            case "PERSONAL" -> "Personal";
            default -> "Otros fijos";
        };
    }

    private String iconoGrupoCuenta(String codigo) {
        return switch (codigo) {
            case "ALQUILER" -> "fa-store";
            case "SERVICIOS" -> "fa-bolt";
            case "PRESTAMOS_BANCOS" -> "fa-university";
            case "PERSONAL" -> "fa-user-clock";
            default -> "fa-receipt";
        };
    }

    private String badgeGrupoCuenta(String codigo) {
        return switch (codigo) {
            case "ALQUILER" -> "badge-primary";
            case "SERVICIOS" -> "badge-info";
            case "PRESTAMOS_BANCOS" -> "badge-danger";
            case "PERSONAL" -> "badge-warning";
            default -> "badge-secondary";
        };
    }

    private List<Map<String, Object>> construirInventarioEstancado(LocalDate fechaSinMovimiento) {
        return listaSegura(productoRepository.obtenerProductosSinMovimiento(fechaSinMovimiento)).stream()
                .filter(p -> !p.esLamina())
                .map(this::mapearInventarioEstancado)
                .sorted(Comparator.comparing((Map<String, Object> p) -> (BigDecimal) p.get("valorStock")).reversed())
                .limit(8)
                .toList();
    }

    private Map<String, Object> mapearInventarioEstancado(Producto producto) {
        BigDecimal costo = safe(producto.getPrecioCompra()).setScale(2, RoundingMode.HALF_UP);
        int stockActual = producto.getStockActual() != null ? producto.getStockActual() : 0;
        BigDecimal valorStock = costo.multiply(BigDecimal.valueOf(stockActual)).setScale(2, RoundingMode.HALF_UP);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("codigo", producto.getCodigoInterno());
        item.put("nombre", producto.getNombre());
        item.put("categoria", producto.getCategoria());
        item.put("stock", stockActual);
        item.put("costoUnitario", costo);
        item.put("valorStock", valorStock);
        return item;
    }

    private String construirDiagnosticoPrincipal(String estadoNegocio,
                                                BigDecimal vendido,
                                                BigDecimal puntoEquilibrio,
                                                BigDecimal flujoCaja,
                                                BigDecimal cajaLibreTrasReposicion,
                                                BigDecimal utilidadNeta) {
        if ("RIESGO".equals(estadoNegocio)) {
            return "La operacion necesita ajuste: la utilidad o la caja del periodo estan en negativo.";
        }
        if (puntoEquilibrio.compareTo(BigDecimal.ZERO) > 0 && vendido.compareTo(puntoEquilibrio) < 0) {
            return "Todavia falta vender para cubrir los costos fijos del periodo.";
        }
        if (cajaLibreTrasReposicion.compareTo(BigDecimal.ZERO) < 0) {
            return "La venta deja ganancia, pero la caja no alcanza para guardar toda la reposicion.";
        }
        if (utilidadNeta.compareTo(BigDecimal.ZERO) > 0 && flujoCaja.compareTo(BigDecimal.ZERO) > 0) {
            return "El periodo va positivo: hay utilidad y la caja cerro por encima de sus salidas.";
        }
        return "El negocio esta estable, pero conviene revisar margen, reposicion y costos fijos.";
    }

    private List<Map<String, Object>> construirAlertasFinancieras(BigDecimal vendido,
                                                                  BigDecimal margenPct,
                                                                  BigDecimal utilidadNeta,
                                                                  BigDecimal flujoCaja,
                                                                  BigDecimal puntoEquilibrio,
                                                                  BigDecimal faltaParaEquilibrio,
                                                                  BigDecimal cajaLibreTrasReposicion,
                                                                  BigDecimal faltanteReposicion,
                                                                  BigDecimal capitalEstancado,
                                                                  BigDecimal capitalEstancadoPct,
                                                                  BigDecimal metaCostosFijos,
                                                                  BigDecimal prestamosBancosPeriodo,
                                                                  BigDecimal pesoPrestamosSobreVentasPct) {
        List<Map<String, Object>> alertas = new ArrayList<>();

        if (vendido.compareTo(BigDecimal.ZERO) == 0) {
            agregarAlerta(alertas, "info", "Sin ventas en el periodo",
                    "No hay base suficiente para leer margen, ticket o punto de equilibrio.",
                    "Revisa otro periodo o empieza registrando todas las ventas del dia.", "fa-info-circle");
        }
        if (metaCostosFijos.compareTo(BigDecimal.ZERO) == 0) {
            agregarAlerta(alertas, "info", "Faltan cuentas fijas",
                    "Sin alquiler, internet, bancos u otros pagos mensuales, el punto de equilibrio queda incompleto.",
                    "Configura las cuentas fijas desde Configuracion > Control Financiero.", "fa-cog");
        }
        if (vendido.compareTo(BigDecimal.ZERO) > 0 && margenPct.compareTo(new BigDecimal("15")) < 0) {
            agregarAlerta(alertas, "warning", "Margen bruto bajo",
                    "Por cada venta queda poca ganancia antes de pagar local, bancos y otros gastos.",
                    "Revisa precios de productos rapidos, impresiones y trabajos con insumos caros.", "fa-percentage");
        }
        if (puntoEquilibrio.compareTo(BigDecimal.ZERO) > 0 && faltaParaEquilibrio.compareTo(BigDecimal.ZERO) > 0) {
            agregarAlerta(alertas, "warning", "Aun no cubres el punto de equilibrio",
                    "Falta vender S/ " + faltaParaEquilibrio + " para cubrir los costos fijos del periodo.",
                    "Usa la meta diaria como piso minimo, no como ganancia.", "fa-bullseye");
        }
        if (utilidadNeta.compareTo(BigDecimal.ZERO) < 0) {
            agregarAlerta(alertas, "danger", "Utilidad operativa negativa",
                    "La ganancia bruta no alcanza para cubrir los gastos operativos registrados.",
                    "Reduce gastos, sube precios con bajo margen o prioriza trabajos de mayor utilidad.", "fa-exclamation-triangle");
        }
        if (flujoCaja.compareTo(BigDecimal.ZERO) < 0) {
            agregarAlerta(alertas, "danger", "Flujo de caja negativo",
                    "En este periodo salio mas dinero del que entro por caja.",
                    "Separa compras, retiros y pagos de banco para ver donde se esta yendo el efectivo.", "fa-water");
        }
        if (cajaLibreTrasReposicion.compareTo(BigDecimal.ZERO) < 0 && faltanteReposicion.compareTo(BigDecimal.ZERO) > 0) {
            agregarAlerta(alertas, "warning", "Descalce de reposicion",
                    "Segun el costo de lo vendido, faltan S/ " + faltanteReposicion + " para reponer mercaderia.",
                    "Aparta primero el costo de reposicion antes de usar la caja para otros pagos.", "fa-box-open");
        }
        if (capitalEstancado.compareTo(BigDecimal.ZERO) > 0 && capitalEstancadoPct.compareTo(new BigDecimal("25")) >= 0) {
            agregarAlerta(alertas, "warning", "Inventario estancado alto",
                    "El " + capitalEstancadoPct + "% del capital en stock esta sin venta reciente.",
                    "Promociona, cambia de ubicacion o evita recomprar esos productos.", "fa-warehouse");
        }
        if (prestamosBancosPeriodo.compareTo(BigDecimal.ZERO) > 0 && pesoPrestamosSobreVentasPct.compareTo(new BigDecimal("20")) >= 0) {
            agregarAlerta(alertas, "warning", "Bancos pesan demasiado",
                    "Las cuotas del periodo equivalen al " + pesoPrestamosSobreVentasPct + "% de tus ventas.",
                    "La meta diaria debe considerar bancos, no solo mercaderia.", "fa-university");
        }
        if (alertas.isEmpty()) {
            agregarAlerta(alertas, "success", "Lectura estable",
                    "Ventas, utilidad y caja no muestran una alerta critica para este periodo.",
                    "Mantén el registro diario para que la proyeccion de 3 meses sea mas confiable.", "fa-check-circle");
        }
        return alertas;
    }

    private void agregarAlerta(List<Map<String, Object>> alertas,
                               String nivel,
                               String titulo,
                               String mensaje,
                               String accion,
                               String icono) {
        Map<String, Object> alerta = new LinkedHashMap<>();
        alerta.put("nivel", nivel);
        alerta.put("titulo", titulo);
        alerta.put("mensaje", mensaje);
        alerta.put("accion", accion);
        alerta.put("icono", icono);
        alertas.add(alerta);
    }

    private String calcularEstadoNegocio(BigDecimal utilidadNeta,
                                         BigDecimal flujoCaja,
                                         BigDecimal vendido,
                                         BigDecimal puntoEquilibrio,
                                         BigDecimal costoFijoPeriodo) {
        if (utilidadNeta.compareTo(BigDecimal.ZERO) < 0 || flujoCaja.compareTo(BigDecimal.ZERO) < 0) {
            return "RIESGO";
        }
        if (costoFijoPeriodo.compareTo(BigDecimal.ZERO) > 0 && vendido.compareTo(BigDecimal.ZERO) == 0) {
            return "AJUSTADO";
        }
        if (puntoEquilibrio.compareTo(BigDecimal.ZERO) > 0 && vendido.compareTo(puntoEquilibrio) < 0) {
            return "AJUSTADO";
        }
        return "SALUDABLE";
    }

    private BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior == null || anterior.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
        return actual.subtract(anterior)
                .divide(anterior, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
    }
}
