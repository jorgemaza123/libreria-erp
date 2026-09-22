package com.libreria.sistema.service;

import com.libreria.sistema.model.DetalleCompra;
import com.libreria.sistema.model.DetalleVenta;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.ReposicionPendiente;
import com.libreria.sistema.model.dto.ReposicionPendienteDTO;
import com.libreria.sistema.repository.DetalleVentaRepository;
import com.libreria.sistema.repository.DetalleCompraRepository;
import com.libreria.sistema.repository.ReposicionPendienteRepository;
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
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class ReposicionPendienteService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ReposicionPendienteRepository reposicionRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final DetalleCompraRepository detalleCompraRepository;

    public ReposicionPendienteService(ReposicionPendienteRepository reposicionRepository,
                                      DetalleVentaRepository detalleVentaRepository,
                                      DetalleCompraRepository detalleCompraRepository) {
        this.reposicionRepository = reposicionRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.detalleCompraRepository = detalleCompraRepository;
    }

    @Transactional
    public void registrarVenta(DetalleVenta detalle) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            return;
        }
        Producto producto = detalle.getProducto();
        if (producto.getTipo() != null && "SERVICIO".equalsIgnoreCase(producto.getTipo())) {
            return;
        }

        BigDecimal cantidad = positivo(detalle.getCantidad()).setScale(3, RoundingMode.HALF_UP);
        if (cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        ReposicionPendiente pendiente = reposicionRepository.findByProductoId(producto.getId())
                .orElseGet(() -> {
                    ReposicionPendiente nuevo = new ReposicionPendiente();
                    nuevo.setProducto(producto);
                    nuevo.setPrimeraVenta(LocalDateTime.now());
                    return nuevo;
                });

        BigDecimal reposicion = positivo(detalle.getMontoReposicionTotal());
        if (reposicion.compareTo(BigDecimal.ZERO) <= 0) {
            reposicion = positivo(detalle.getCostoUnitario()).multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        }

        pendiente.setCantidadPendiente(positivo(pendiente.getCantidadPendiente()).add(cantidad));
        pendiente.setCantidadVendidaAcumulada(positivo(pendiente.getCantidadVendidaAcumulada()).add(cantidad));
        pendiente.setMontoVentaAcumulado(positivo(pendiente.getMontoVentaAcumulado()).add(positivo(detalle.getSubtotal()).setScale(2, RoundingMode.HALF_UP)));
        pendiente.setMontoReposicionPendiente(positivo(pendiente.getMontoReposicionPendiente()).add(reposicion));
        pendiente.setUtilidadBrutaAcumulada(positivo(pendiente.getUtilidadBrutaAcumulada()).add(positivo(detalle.getUtilidadTotal()).setScale(2, RoundingMode.HALF_UP)));

        BigDecimal indirectoTotal = positivo(detalle.getCostoIndirectoUnitario()).multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
        pendiente.setCostoIndirectoAcumulado(positivo(pendiente.getCostoIndirectoAcumulado()).add(indirectoTotal));
        pendiente.setUtilidadNetaEstimadaAcumulada(positivo(pendiente.getUtilidadNetaEstimadaAcumulada())
                .add(positivo(detalle.getUtilidadNetaTotal()).setScale(2, RoundingMode.HALF_UP)));
        pendiente.setVecesVendida((pendiente.getVecesVendida() != null ? pendiente.getVecesVendida() : 0) + 1);
        pendiente.setUltimaVenta(LocalDateTime.now());
        pendiente.setEstado("PENDIENTE");
        pendiente.setPrioridad(calcularPrioridad(producto, pendiente));
        pendiente.normalizar();
        reposicionRepository.save(pendiente);
    }

    @Transactional
    public void registrarCompra(DetalleCompra detalle) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            return;
        }
        reposicionRepository.findByProductoId(detalle.getProducto().getId()).ifPresent(pendiente -> {
            if (!"PENDIENTE".equalsIgnoreCase(pendiente.getEstado())) {
                return;
            }
            BigDecimal cantidadComprada = BigDecimal.valueOf(detalle.getCantidad() != null ? detalle.getCantidad() : 0)
                    .setScale(3, RoundingMode.HALF_UP);
            if (cantidadComprada.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal pendienteAntes = positivo(pendiente.getCantidadPendiente());
            BigDecimal aplicar = cantidadComprada.min(pendienteAntes).setScale(3, RoundingMode.HALF_UP);
            if (aplicar.compareTo(BigDecimal.ZERO) <= 0) {
                return;
            }

            BigDecimal proporcion = pendienteAntes.compareTo(BigDecimal.ZERO) > 0
                    ? aplicar.divide(pendienteAntes, 6, RoundingMode.HALF_UP)
                    : BigDecimal.ONE;
            pendiente.setCantidadPendiente(pendienteAntes.subtract(aplicar).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
            pendiente.setCantidadCompradaAplicada(positivo(pendiente.getCantidadCompradaAplicada()).add(aplicar));
            pendiente.setMontoReposicionPendiente(
                    positivo(pendiente.getMontoReposicionPendiente())
                            .subtract(positivo(pendiente.getMontoReposicionPendiente()).multiply(proporcion))
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP));
            pendiente.setVecesComprada((pendiente.getVecesComprada() != null ? pendiente.getVecesComprada() : 0) + 1);
            pendiente.setUltimaCompra(LocalDateTime.now());
            if (pendiente.getCantidadPendiente().compareTo(new BigDecimal("0.001")) < 0) {
                pendiente.setCantidadPendiente(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
                pendiente.setMontoReposicionPendiente(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                pendiente.setEstado("REPUESTO");
                pendiente.setPrioridad("BAJA");
            } else {
                pendiente.setPrioridad(calcularPrioridad(detalle.getProducto(), pendiente));
            }
            reposicionRepository.save(pendiente);
        });
    }

    @Transactional
    public void revertirCompra(DetalleCompra detalle) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            return;
        }
        Producto producto = detalle.getProducto();
        BigDecimal cantidad = BigDecimal.valueOf(detalle.getCantidad() != null ? detalle.getCantidad() : 0)
                .setScale(3, RoundingMode.HALF_UP);
        if (cantidad.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        ReposicionPendiente pendiente = reposicionRepository.findByProductoId(producto.getId())
                .orElseGet(() -> {
                    ReposicionPendiente nuevo = new ReposicionPendiente();
                    nuevo.setProducto(producto);
                    nuevo.setPrimeraVenta(LocalDateTime.now());
                    return nuevo;
                });
        BigDecimal costo = positivo(detalle.getCostoUnitarioReal());
        if (costo.compareTo(BigDecimal.ZERO) <= 0) {
            costo = positivo(detalle.getPrecioUnitario());
        }
        pendiente.setCantidadPendiente(positivo(pendiente.getCantidadPendiente()).add(cantidad).setScale(3, RoundingMode.HALF_UP));
        pendiente.setMontoReposicionPendiente(positivo(pendiente.getMontoReposicionPendiente())
                .add(costo.multiply(cantidad).setScale(2, RoundingMode.HALF_UP)));
        pendiente.setCantidadCompradaAplicada(positivo(pendiente.getCantidadCompradaAplicada()).subtract(cantidad).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
        pendiente.setEstado("PENDIENTE");
        pendiente.setPrioridad(calcularPrioridad(producto, pendiente));
        reposicionRepository.save(pendiente);
    }

    @Transactional
    public void revertirVenta(DetalleVenta detalle) {
        if (detalle == null || detalle.getProducto() == null || detalle.getProducto().getId() == null) {
            return;
        }
        Producto producto = detalle.getProducto();
        if (producto.getTipo() != null && "SERVICIO".equalsIgnoreCase(producto.getTipo())) {
            return;
        }
        reposicionRepository.findByProductoId(producto.getId()).ifPresent(pendiente -> {
            BigDecimal cantidad = positivo(detalle.getCantidad()).setScale(3, RoundingMode.HALF_UP);
            BigDecimal reposicion = positivo(detalle.getMontoReposicionTotal());
            if (reposicion.compareTo(BigDecimal.ZERO) <= 0) {
                reposicion = positivo(detalle.getCostoUnitario()).multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            }
            BigDecimal indirecto = positivo(detalle.getCostoIndirectoUnitario()).multiply(cantidad).setScale(2, RoundingMode.HALF_UP);
            pendiente.setCantidadPendiente(positivo(pendiente.getCantidadPendiente()).subtract(cantidad).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
            pendiente.setCantidadVendidaAcumulada(positivo(pendiente.getCantidadVendidaAcumulada()).subtract(cantidad).max(BigDecimal.ZERO).setScale(3, RoundingMode.HALF_UP));
            pendiente.setMontoVentaAcumulado(positivo(pendiente.getMontoVentaAcumulado()).subtract(positivo(detalle.getSubtotal())).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            pendiente.setMontoReposicionPendiente(positivo(pendiente.getMontoReposicionPendiente()).subtract(reposicion).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            pendiente.setUtilidadBrutaAcumulada(positivo(pendiente.getUtilidadBrutaAcumulada()).subtract(positivo(detalle.getUtilidadTotal())).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            pendiente.setCostoIndirectoAcumulado(positivo(pendiente.getCostoIndirectoAcumulado()).subtract(indirecto).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            pendiente.setUtilidadNetaEstimadaAcumulada(positivo(pendiente.getUtilidadNetaEstimadaAcumulada()).subtract(positivo(detalle.getUtilidadNetaTotal())).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP));
            if (pendiente.getCantidadPendiente().compareTo(BigDecimal.ZERO) == 0) {
                pendiente.setEstado("REPUESTO");
                pendiente.setPrioridad("BAJA");
            } else {
                pendiente.setPrioridad(calcularPrioridad(producto, pendiente));
            }
            reposicionRepository.save(pendiente);
        });
    }

    @Transactional(readOnly = true)
    public List<ReposicionPendienteDTO> listarPendientes() {
        return enriquecerDtos(reposicionRepository.findByEstadoOrderByPrioridadAscUltimaVentaDesc("PENDIENTE"));
    }

    @Transactional(readOnly = true)
    public List<ReposicionPendienteDTO> listarTodas() {
        return enriquecerDtos(reposicionRepository.findAllByOrderByEstadoAscPrioridadAscUltimaVentaDesc());
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> prepararItemsParaCompra(List<Long> reposicionIds) {
        if (reposicionIds == null || reposicionIds.isEmpty()) {
            return List.of();
        }
        return reposicionRepository.findAllById(reposicionIds).stream()
                .filter(r -> "PENDIENTE".equalsIgnoreCase(r.getEstado()))
                .filter(r -> r.getProducto() != null)
                .map(this::toCompraPrefill)
                .filter(item -> ((Number) item.get("cantidad")).intValue() > 0)
                .toList();
    }

    @Transactional
    public ReposicionPendienteDTO actualizarManual(Long id, BigDecimal cantidadPendiente, BigDecimal montoReposicion, String prioridad, String notas) {
        ReposicionPendiente pendiente = reposicionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reposicion no encontrada"));
        pendiente.setCantidadPendiente(positivo(cantidadPendiente).setScale(3, RoundingMode.HALF_UP));
        pendiente.setMontoReposicionPendiente(positivo(montoReposicion).setScale(2, RoundingMode.HALF_UP));
        pendiente.setPrioridad(prioridad == null || prioridad.isBlank() ? calcularPrioridad(pendiente.getProducto(), pendiente) : prioridad);
        pendiente.setNotas(notas);
        pendiente.setEstado(pendiente.getCantidadPendiente().compareTo(BigDecimal.ZERO) > 0 ? "PENDIENTE" : "REPUESTO");
        pendiente.normalizar();
        return toDto(reposicionRepository.save(pendiente));
    }

    @Transactional
    public void marcarRepuesto(Long id) {
        ReposicionPendiente pendiente = reposicionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Reposicion no encontrada"));
        pendiente.setCantidadPendiente(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP));
        pendiente.setMontoReposicionPendiente(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
        pendiente.setEstado("REPUESTO");
        pendiente.setPrioridad("BAJA");
        pendiente.setUltimaCompra(LocalDateTime.now());
        reposicionRepository.save(pendiente);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resumen(LocalDate inicio, LocalDate fin) {
        Object[] row = detalleVentaRepository.resumenCosteoVentas(inicio, fin);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ventaTotal", valor(row, 0));
        out.put("reposicionVendida", valor(row, 1));
        out.put("gananciaBruta", valor(row, 2));
        out.put("costosIndirectos", valor(row, 3));
        out.put("gananciaNetaEstimada", valor(row, 4));
        out.put("ventas", row != null && row.length > 5 && row[5] != null ? ((Number) row[5]).longValue() : 0L);

        Object[] pendientes = reposicionRepository.resumenPorEstado("PENDIENTE");
        out.put("cantidadPendiente", valor(pendientes, 0));
        out.put("montoPendienteReposicion", valor(pendientes, 1));
        out.put("ventaAcumuladaPendiente", valor(pendientes, 2));
        out.put("utilidadBrutaPendiente", valor(pendientes, 3));
        out.put("indirectosPendientes", valor(pendientes, 4));
        out.put("utilidadNetaPendiente", valor(pendientes, 5));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> ventasPorProducto(LocalDate inicio, LocalDate fin) {
        return detalleVentaRepository.resumenCosteoVentasPorProducto(inicio, fin).stream()
                .map(row -> {
                    BigDecimal venta = valor(row, 5);
                    BigDecimal reposicion = valor(row, 6);
                    BigDecimal bruta = valor(row, 7);
                    BigDecimal neta = valor(row, 9);
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("productoId", row[0]);
                    item.put("codigoInterno", row[1]);
                    item.put("nombre", row[2]);
                    item.put("categoria", row[3]);
                    item.put("cantidad", valor(row, 4));
                    item.put("venta", venta);
                    item.put("reposicion", reposicion);
                    item.put("gananciaBruta", bruta);
                    item.put("costosIndirectos", valor(row, 8));
                    item.put("gananciaNeta", neta);
                    item.put("margenBrutoPct", porcentaje(bruta, venta, 1));
                    item.put("margenNetoPct", porcentaje(neta, venta, 1));
                    item.put("ultimaVenta", row[10]);
                    return item;
                })
                .toList();
    }

    public byte[] exportarPendientesExcel(List<ReposicionPendienteDTO> pendientes) throws IOException {
        try (Workbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            List<ReposicionPendienteDTO> ordenados = ordenarPorProveedor(pendientes);
            Sheet sheet = workbook.createSheet("Reposicion pendiente");
            CellStyle header = workbook.createCellStyle();
            org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            header.setFont(headerFont);
            header.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setAlignment(HorizontalAlignment.CENTER);

            String[] cols = {"Proveedor sugerido", "Codigo", "Producto", "Categoria", "Stock", "Pendiente",
                    "Sugerido compra", "Presentacion", "Bloque", "S/ Reposicion", "Venta 7 dias", "Venta 30 dias",
                    "Venta acum.", "Ganancia bruta", "Indirectos", "Ganancia neta", "Prioridad", "Ultima venta", "Notas"};
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Reposicion y ganancia - pendientes de compra");
            Row h = sheet.createRow(2);
            for (int i = 0; i < cols.length; i++) {
                h.createCell(i).setCellValue(cols[i]);
                h.getCell(i).setCellStyle(header);
            }

            DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
            int idx = 3;
            for (ReposicionPendienteDTO p : ordenados) {
                Row r = sheet.createRow(idx++);
                r.createCell(0).setCellValue(texto(p.getProveedorSugerido()));
                r.createCell(1).setCellValue(texto(p.getCodigoInterno()));
                r.createCell(2).setCellValue(texto(p.getNombre()));
                r.createCell(3).setCellValue(texto(p.getCategoria()));
                r.createCell(4).setCellValue(p.getStockActual() != null ? p.getStockActual() : 0);
                r.createCell(5).setCellValue(p.getCantidadPendiente().doubleValue());
                r.createCell(6).setCellValue(p.getCantidadSugeridaCompra().doubleValue());
                r.createCell(7).setCellValue(texto(p.getPresentacionRecomendada()));
                r.createCell(8).setCellValue(texto(p.getBloqueCompra()));
                r.createCell(9).setCellValue(p.getMontoReposicionPendiente().doubleValue());
                r.createCell(10).setCellValue(p.getVenta7Dias().doubleValue());
                r.createCell(11).setCellValue(p.getVenta30Dias().doubleValue());
                r.createCell(12).setCellValue(p.getMontoVentaAcumulado().doubleValue());
                r.createCell(13).setCellValue(p.getUtilidadBrutaAcumulada().doubleValue());
                r.createCell(14).setCellValue(p.getCostoIndirectoAcumulado().doubleValue());
                r.createCell(15).setCellValue(p.getUtilidadNetaEstimadaAcumulada().doubleValue());
                r.createCell(16).setCellValue(texto(p.getPrioridad()));
                r.createCell(17).setCellValue(p.getUltimaVenta() != null ? p.getUltimaVenta().format(fmt) : "");
                r.createCell(18).setCellValue(texto(p.getNotas()));
            }
            for (int i = 0; i < cols.length; i++) {
                sheet.autoSizeColumn(i);
            }
            crearResumenProveedor(workbook, header, ordenados);
            workbook.write(out);
            return out.toByteArray();
        }
    }

    public void exportarPendientesPdf(List<ReposicionPendienteDTO> pendientes, OutputStream out) throws DocumentException {
        Document doc = new Document(PageSize.A4.rotate(), 24, 24, 28, 28);
        PdfWriter.getInstance(doc, out);
        doc.open();
        Font title = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 15);
        Font small = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font head = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.WHITE);
        Font body = FontFactory.getFont(FontFactory.HELVETICA, 8);
        Font group = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, new Color(30, 41, 59));

        Paragraph titulo = new Paragraph("Reposicion pendiente y utilidad acumulada", title);
        titulo.setAlignment(Element.ALIGN_CENTER);
        doc.add(titulo);
        doc.add(new Paragraph("Productos vendidos que deben reponerse, agrupados por proveedor sugerido. El monto de reposicion es el costo acumulado que conviene separar antes de usar la ganancia.", small));
        doc.add(new Paragraph(" "));

        PdfPTable table = new PdfPTable(11);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{1.7f, 1.1f, 2.6f, 1.3f, 1.0f, 1.1f, 1.1f, 1.3f, 1.2f, 1.2f, 1.1f});
        String[] headers = {"Proveedor", "Codigo", "Producto", "Categoria", "Stock", "Pend.", "Sug.", "Present.", "Reposicion", "G. Neta", "Bloque"};
        for (String h : headers) {
            PdfPCell cell = new PdfPCell(new Phrase(h, head));
            cell.setBackgroundColor(new Color(44, 62, 80));
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setPadding(5);
            table.addCell(cell);
        }
        String proveedorActual = null;
        for (ReposicionPendienteDTO p : ordenarPorProveedor(pendientes)) {
            String proveedor = proveedorNormalizado(p.getProveedorSugerido());
            if (!proveedor.equals(proveedorActual)) {
                proveedorActual = proveedor;
                PdfPCell grupo = new PdfPCell(new Phrase("Proveedor: " + proveedorActual, group));
                grupo.setColspan(11);
                grupo.setBackgroundColor(new Color(241, 245, 249));
                grupo.setPadding(5);
                table.addCell(grupo);
            }
            add(table, texto(p.getProveedorSugerido()), body, Element.ALIGN_LEFT);
            add(table, texto(p.getCodigoInterno()), body, Element.ALIGN_LEFT);
            add(table, texto(p.getNombre()), body, Element.ALIGN_LEFT);
            add(table, texto(p.getCategoria()), body, Element.ALIGN_LEFT);
            add(table, String.valueOf(p.getStockActual() != null ? p.getStockActual() : 0), body, Element.ALIGN_CENTER);
            add(table, p.getCantidadPendiente().setScale(2, RoundingMode.HALF_UP).toString(), body, Element.ALIGN_RIGHT);
            add(table, p.getCantidadSugeridaCompra().setScale(2, RoundingMode.HALF_UP).toString(), body, Element.ALIGN_RIGHT);
            add(table, texto(p.getPresentacionRecomendada()), body, Element.ALIGN_CENTER);
            add(table, moneda(p.getMontoReposicionPendiente()), body, Element.ALIGN_RIGHT);
            add(table, moneda(p.getUtilidadNetaEstimadaAcumulada()), body, Element.ALIGN_RIGHT);
            add(table, texto(p.getBloqueCompra()), body, Element.ALIGN_CENTER);
        }
        doc.add(table);
        doc.close();
    }

    private void crearResumenProveedor(Workbook workbook,
                                       CellStyle header,
                                       List<ReposicionPendienteDTO> pendientes) {
        Sheet sheet = workbook.createSheet("Resumen por proveedor");
        String[] cols = {"Proveedor", "Items", "Unidades sugeridas", "S/ reposicion", "Urgentes", "Cuando haya caja"};
        Row h = sheet.createRow(0);
        for (int i = 0; i < cols.length; i++) {
            h.createCell(i).setCellValue(cols[i]);
            h.getCell(i).setCellStyle(header);
        }

        Map<String, ProveedorResumen> resumen = new LinkedHashMap<>();
        for (ReposicionPendienteDTO p : pendientes) {
            String proveedor = proveedorNormalizado(p.getProveedorSugerido());
            ProveedorResumen r = resumen.computeIfAbsent(proveedor, key -> new ProveedorResumen());
            r.items++;
            r.unidades = r.unidades.add(positivo(p.getCantidadSugeridaCompra()));
            r.monto = r.monto.add(positivo(p.getMontoReposicionPendiente()));
            if ("REPONER_URGENTE".equalsIgnoreCase(p.getBloqueCompra())) {
                r.urgentes++;
            } else {
                r.caja++;
            }
        }

        int idx = 1;
        for (Map.Entry<String, ProveedorResumen> entry : resumen.entrySet()) {
            ProveedorResumen r = entry.getValue();
            Row row = sheet.createRow(idx++);
            row.createCell(0).setCellValue(entry.getKey());
            row.createCell(1).setCellValue(r.items);
            row.createCell(2).setCellValue(r.unidades.setScale(2, RoundingMode.HALF_UP).doubleValue());
            row.createCell(3).setCellValue(r.monto.setScale(2, RoundingMode.HALF_UP).doubleValue());
            row.createCell(4).setCellValue(r.urgentes);
            row.createCell(5).setCellValue(r.caja);
        }
        for (int i = 0; i < cols.length; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private List<ReposicionPendienteDTO> enriquecerDtos(List<ReposicionPendiente> registros) {
        List<ReposicionPendienteDTO> base = registros.stream()
                .map(this::toDto)
                .toList();
        List<Long> productoIds = base.stream()
                .map(ReposicionPendienteDTO::getProductoId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (productoIds.isEmpty()) {
            return base;
        }

        Map<Long, BigDecimal> ventas7 = ventasDesde(productoIds, LocalDate.now().minusDays(6));
        Map<Long, BigDecimal> ventas30 = ventasDesde(productoIds, LocalDate.now().minusDays(29));
        Map<Long, String> proveedores = proveedoresRecientes(productoIds);

        for (ReposicionPendienteDTO dto : base) {
            BigDecimal venta7 = positivo(ventas7.get(dto.getProductoId())).setScale(3, RoundingMode.HALF_UP);
            BigDecimal venta30 = positivo(ventas30.get(dto.getProductoId())).setScale(3, RoundingMode.HALF_UP);
            BigDecimal sugerida = sugerirCantidadCompra(dto, venta7, venta30);
            dto.setVenta7Dias(venta7);
            dto.setVenta30Dias(venta30);
            dto.setCantidadSugeridaCompra(sugerida);
            dto.setPresentacionRecomendada(sugerirPresentacion(sugerida.setScale(0, RoundingMode.CEILING).intValue()));
            dto.setProveedorSugerido(proveedores.getOrDefault(dto.getProductoId(), "Sin proveedor reciente"));
            dto.setBloqueCompra(esCompraUrgente(dto) ? "REPONER_URGENTE" : "CUANDO_HAYA_CAJA");
        }
        return base.stream()
                .sorted(java.util.Comparator
                        .comparing(ReposicionPendienteDTO::getProveedorSugerido, java.util.Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ReposicionPendienteDTO::getPrioridad, java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .collect(Collectors.toList());
    }

    private Map<Long, BigDecimal> ventasDesde(List<Long> productoIds, LocalDate fecha) {
        Map<Long, BigDecimal> ventas = new LinkedHashMap<>();
        for (Object[] row : detalleVentaRepository.resumenVentasPorProductoDesde(productoIds, fecha)) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            ventas.put(((Number) row[0]).longValue(), valor(row, 1).setScale(3, RoundingMode.HALF_UP));
        }
        return ventas;
    }

    private Map<Long, String> proveedoresRecientes(List<Long> productoIds) {
        Map<Long, String> proveedores = new LinkedHashMap<>();
        for (Object[] row : detalleCompraRepository.ultimosProveedoresPorProducto(productoIds)) {
            if (row == null || row.length < 2 || row[0] == null || proveedores.containsKey(((Number) row[0]).longValue())) {
                continue;
            }
            String proveedor = row[1] != null ? row[1].toString() : "Sin proveedor";
            proveedores.put(((Number) row[0]).longValue(), proveedor);
        }
        return proveedores;
    }

    private BigDecimal sugerirCantidadCompra(ReposicionPendienteDTO dto, BigDecimal venta7, BigDecimal venta30) {
        BigDecimal pendiente = positivo(dto.getCantidadPendiente());
        BigDecimal cobertura7 = positivo(venta7).multiply(new BigDecimal("1.50"));
        BigDecimal cobertura30 = positivo(venta30).divide(new BigDecimal("30"), 6, RoundingMode.HALF_UP)
                .multiply(new BigDecimal("14"));
        BigDecimal objetivoStockMinimo = BigDecimal.valueOf(dto.getStockMinimo() != null ? dto.getStockMinimo() : 0)
                .subtract(BigDecimal.valueOf(dto.getStockActual() != null ? dto.getStockActual() : 0))
                .max(BigDecimal.ZERO);
        BigDecimal sugerida = pendiente.max(cobertura7).max(cobertura30).max(objetivoStockMinimo);
        return sugerida.setScale(0, RoundingMode.CEILING);
    }

    private boolean esCompraUrgente(ReposicionPendienteDTO dto) {
        String prioridad = dto.getPrioridad() != null ? dto.getPrioridad() : "";
        return "URGENTE".equalsIgnoreCase(prioridad) || "ALTA".equalsIgnoreCase(prioridad);
    }

    private List<ReposicionPendienteDTO> ordenarPorProveedor(List<ReposicionPendienteDTO> pendientes) {
        if (pendientes == null || pendientes.isEmpty()) {
            return List.of();
        }
        List<ReposicionPendienteDTO> ordenados = new ArrayList<>(pendientes);
        ordenados.sort(Comparator
                .comparing((ReposicionPendienteDTO p) -> proveedorNormalizado(p.getProveedorSugerido()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> texto(p.getBloqueCompra()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> texto(p.getCategoria()), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(p -> texto(p.getNombre()), String.CASE_INSENSITIVE_ORDER));
        return ordenados;
    }

    private String proveedorNormalizado(String proveedor) {
        String value = texto(proveedor);
        return value.isBlank() ? "Sin proveedor reciente" : value;
    }

    private ReposicionPendienteDTO toDto(ReposicionPendiente r) {
        Producto p = r.getProducto();
        return ReposicionPendienteDTO.builder()
                .id(r.getId())
                .productoId(p != null ? p.getId() : null)
                .codigoInterno(p != null ? p.getCodigoInterno() : null)
                .nombre(p != null ? p.getNombre() : null)
                .categoria(p != null ? p.getCategoria() : null)
                .stockActual(p != null ? p.getStockActual() : 0)
                .stockMinimo(p != null ? p.getStockMinimo() : 0)
                .cantidadPendiente(positivo(r.getCantidadPendiente()).setScale(3, RoundingMode.HALF_UP))
                .cantidadVendidaAcumulada(positivo(r.getCantidadVendidaAcumulada()).setScale(3, RoundingMode.HALF_UP))
                .cantidadCompradaAplicada(positivo(r.getCantidadCompradaAplicada()).setScale(3, RoundingMode.HALF_UP))
                .montoVentaAcumulado(positivo(r.getMontoVentaAcumulado()).setScale(2, RoundingMode.HALF_UP))
                .montoReposicionPendiente(positivo(r.getMontoReposicionPendiente()).setScale(2, RoundingMode.HALF_UP))
                .venta7Dias(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP))
                .venta30Dias(BigDecimal.ZERO.setScale(3, RoundingMode.HALF_UP))
                .cantidadSugeridaCompra(positivo(r.getCantidadPendiente()).setScale(0, RoundingMode.CEILING))
                .utilidadBrutaAcumulada(positivo(r.getUtilidadBrutaAcumulada()).setScale(2, RoundingMode.HALF_UP))
                .costoIndirectoAcumulado(positivo(r.getCostoIndirectoAcumulado()).setScale(2, RoundingMode.HALF_UP))
                .utilidadNetaEstimadaAcumulada(positivo(r.getUtilidadNetaEstimadaAcumulada()).setScale(2, RoundingMode.HALF_UP))
                .vecesVendida(r.getVecesVendida())
                .vecesComprada(r.getVecesComprada())
                .estado(r.getEstado())
                .prioridad(r.getPrioridad())
                .bloqueCompra("MEDIA".equalsIgnoreCase(r.getPrioridad()) || "BAJA".equalsIgnoreCase(r.getPrioridad()) ? "CUANDO_HAYA_CAJA" : "REPONER_URGENTE")
                .presentacionRecomendada(sugerirPresentacion(positivo(r.getCantidadPendiente()).setScale(0, RoundingMode.CEILING).intValue()))
                .proveedorSugerido("Sin proveedor reciente")
                .notas(r.getNotas())
                .primeraVenta(r.getPrimeraVenta())
                .ultimaVenta(r.getUltimaVenta())
                .ultimaCompra(r.getUltimaCompra())
                .build();
    }

    private Map<String, Object> toCompraPrefill(ReposicionPendiente r) {
        Producto p = r.getProducto();
        BigDecimal pendiente = positivo(r.getCantidadPendiente()).setScale(3, RoundingMode.HALF_UP);
        int cantidad = pendiente.compareTo(BigDecimal.ZERO) > 0
                ? pendiente.setScale(0, RoundingMode.CEILING).intValue()
                : 0;
        BigDecimal costo = positivo(p.getPrecioCompra()).setScale(4, RoundingMode.HALF_UP);
        if (costo.compareTo(BigDecimal.ZERO) <= 0 && pendiente.compareTo(BigDecimal.ZERO) > 0) {
            costo = positivo(r.getMontoReposicionPendiente())
                    .divide(pendiente, 4, RoundingMode.HALF_UP);
        }
        BigDecimal total = costo.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("productoId", p.getId());
        item.put("nombre", etiquetaProducto(p));
        item.put("cantidad", cantidad);
        item.put("costo", costo);
        item.put("totalPagado", total);
        item.put("stockActual", p.getStockActual() != null ? p.getStockActual() : 0);
        item.put("costoAnterior", costo);
        item.put("precioVentaActual", p.getPrecioVenta() != null ? p.getPrecioVenta() : BigDecimal.ZERO);
        item.put("clasificacion", p.getClasificacion() != null ? p.getClasificacion() : Producto.CLASIFICACION_MERCADERIA);
        item.put("usarReglaManualPrecio", Boolean.TRUE.equals(p.getUsarReglaManualPrecio()));
        item.put("gananciaObjetivoPct", p.getGananciaObjetivoPct());
        item.put("gananciaMinimaPct", p.getGananciaMinimaPct());
        item.put("prioridadReposicion", r.getPrioridad());
        item.put("presentacionSugerida", sugerirPresentacion(cantidad));
        item.put("notasReposicion", r.getNotas());
        return item;
    }

    private String etiquetaProducto(Producto p) {
        if (p.getCodigoBarra() != null && !p.getCodigoBarra().isBlank()) {
            return p.getCodigoBarra() + " - " + p.getNombre();
        }
        if (p.getCodigoInterno() != null && !p.getCodigoInterno().isBlank()) {
            return p.getCodigoInterno() + " - " + p.getNombre();
        }
        return p.getNombre();
    }

    private String sugerirPresentacion(int cantidad) {
        if (cantidad >= 100) return "CIENTO";
        if (cantidad >= 12) return "DOCENA";
        if (cantidad >= 6) return "MEDIA DOCENA";
        return "UNIDAD";
    }

    private String calcularPrioridad(Producto producto, ReposicionPendiente pendiente) {
        int stock = producto != null && producto.getStockActual() != null ? producto.getStockActual() : 0;
        int minimo = producto != null && producto.getStockMinimo() != null ? producto.getStockMinimo() : 0;
        if (stock <= 0) return "URGENTE";
        if (minimo > 0 && stock <= minimo) return "ALTA";
        if (positivo(pendiente.getMontoReposicionPendiente()).compareTo(new BigDecimal("100.00")) >= 0) return "ALTA";
        return "MEDIA";
    }

    private BigDecimal valor(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        Object value = row[index];
        if (value instanceof BigDecimal bd) {
            return bd.setScale(2, RoundingMode.HALF_UP);
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue()).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal positivo(BigDecimal valor) {
        if (valor == null || valor.compareTo(BigDecimal.ZERO) < 0) {
            return BigDecimal.ZERO;
        }
        return valor;
    }

    private BigDecimal porcentaje(BigDecimal parte, BigDecimal total, int escala) {
        if (total == null || total.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO.setScale(escala, RoundingMode.HALF_UP);
        }
        return positivo(parte).divide(total, 6, RoundingMode.HALF_UP).multiply(CIEN).setScale(escala, RoundingMode.HALF_UP);
    }

    public LocalDate[] rangoMesActual() {
        YearMonth mes = YearMonth.now();
        return new LocalDate[]{mes.atDay(1), mes.atEndOfMonth()};
    }

    private String texto(String value) {
        return value != null ? value : "";
    }

    private String moneda(BigDecimal value) {
        return "S/ " + positivo(value).setScale(2, RoundingMode.HALF_UP);
    }

    private void add(PdfPTable table, String text, Font font, int align) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(align);
        cell.setPadding(4);
        table.addCell(cell);
    }

    private static class ProveedorResumen {
        private int items;
        private int urgentes;
        private int caja;
        private BigDecimal unidades = BigDecimal.ZERO;
        private BigDecimal monto = BigDecimal.ZERO;
    }
}
