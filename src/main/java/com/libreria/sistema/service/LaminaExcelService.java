package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.LaminaFormDTO;
import com.libreria.sistema.repository.ProductoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LaminaExcelService {

    private static final String[] COLUMNAS = {
            "NUMERO_LAMINA",
            "TITULO",
            "MARCA",
            "CATEGORIA",
            "BOLSA",
            "STOCK",
            "PRECIO_VENTA"
    };

    private final ProductoRepository productoRepository;
    private final LaminaService laminaService;

    public byte[] generarPlantilla() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Laminas");
            Sheet instrucciones = workbook.createSheet("Instrucciones");

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row header = sheet.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(COLUMNAS[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, 6000);
            }

            instrucciones.setColumnWidth(0, 5000);
            instrucciones.setColumnWidth(1, 12000);

            Row titulo = instrucciones.createRow(0);
            titulo.createCell(0).setCellValue("CAMPO");
            titulo.createCell(1).setCellValue("DESCRIPCION");
            titulo.getCell(0).setCellStyle(headerStyle);
            titulo.getCell(1).setCellStyle(headerStyle);

            Row r1 = instrucciones.createRow(1);
            r1.createCell(0).setCellValue("NUMERO_LAMINA");
            r1.createCell(1).setCellValue("Numero visible de la lamina. Ej: 1083");

            Row r2 = instrucciones.createRow(2);
            r2.createCell(0).setCellValue("TITULO");
            r2.createCell(1).setCellValue("Titulo o tema escolar de la lamina.");

            Row r3 = instrucciones.createRow(3);
            r3.createCell(0).setCellValue("MARCA");
            r3.createCell(1).setCellValue("Marca editorial o fabricante. Opcional.");

            Row r4 = instrucciones.createRow(4);
            r4.createCell(0).setCellValue("CATEGORIA");
            r4.createCell(1).setCellValue("Curso o agrupacion. Ej: BIOLOGIA, HISTORIA.");

            Row r5 = instrucciones.createRow(5);
            r5.createCell(0).setCellValue("BOLSA");
            r5.createCell(1).setCellValue("Bolsa, folder o contenedor donde la guardaras.");

            Row r6 = instrucciones.createRow(6);
            r6.createCell(0).setCellValue("STOCK");
            r6.createCell(1).setCellValue("Cantidad disponible de esa lamina.");

            Row r7 = instrucciones.createRow(7);
            r7.createCell(0).setCellValue("PRECIO_VENTA");
            r7.createCell(1).setCellValue("Opcional. Si se deja vacio, usa S/ 0.50.");

            ByteArrayOutputStream output = new ByteArrayOutputStream();
            workbook.write(output);
            return output.toByteArray();
        }
    }

    @Transactional
    public Map<String, Object> importarLaminas(MultipartFile archivo, boolean actualizarExistentes) throws IOException {
        int creadas = 0;
        int actualizadas = 0;
        int omitidas = 0;
        List<String> errores = new ArrayList<>();
        List<String> actualizadasDetalle = new ArrayList<>();
        List<String> omitidasDetalle = new ArrayList<>();

        try (InputStream is = archivo.getInputStream(); Workbook workbook = WorkbookFactory.create(is)) {
            Sheet sheet = workbook.getSheetAt(0);

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || filaVacia(row)) {
                    continue;
                }

                try {
                    LaminaFormDTO dto = new LaminaFormDTO();
                    dto.setLaminaNumero(getCellStringValue(row.getCell(0)));
                    dto.setLaminaTitulo(getCellStringValue(row.getCell(1)));
                    dto.setLaminaMarca(getCellStringValue(row.getCell(2)));
                    dto.setLaminaCategoria(getCellStringValue(row.getCell(3)));
                    dto.setLaminaContenedor(getCellStringValue(row.getCell(4)));
                    dto.setStockActual(getCellIntegerValue(row.getCell(5), 1));
                    dto.setPrecioVenta(getCellBigDecimalValue(row.getCell(6), LaminaService.PRECIO_VENTA_DEFAULT));
                    dto.setActivo(true);

                    Optional<Producto> existente = buscarExistente(dto);
                    if (existente.isPresent()) {
                        if (!actualizarExistentes) {
                            omitidas++;
                            Producto productoExistente = existente.get();
                            omitidasDetalle.add("Fila " + (i + 1) + ": "
                                    + productoExistente.getLaminaEtiquetaTexto()
                                    + " en "
                                    + productoExistente.getLaminaUbicacionTexto()
                                    + " ya existe y se omitio.");
                            continue;
                        }
                        dto.setId(existente.get().getId());
                        dto.setCodigoInterno(existente.get().getCodigoInterno());
                    }

                    Producto guardada = laminaService.guardarLamina(dto, false);
                    if (existente.isPresent()) {
                        actualizadas++;
                        actualizadasDetalle.add("Fila " + (i + 1) + ": "
                                + guardada.getLaminaEtiquetaTexto()
                                + " en "
                                + guardada.getLaminaUbicacionTexto()
                                + " actualizada; stock actual " + (guardada.getStockActual() != null ? guardada.getStockActual() : 0) + ".");
                    } else {
                        creadas++;
                    }
                } catch (Exception e) {
                    errores.add("Fila " + (i + 1) + ": " + e.getMessage());
                }
            }
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("creadas", creadas);
        resultado.put("actualizadas", actualizadas);
        resultado.put("omitidas", omitidas);
        resultado.put("actualizadasDetalle", actualizadasDetalle);
        resultado.put("omitidasDetalle", omitidasDetalle);
        resultado.put("errores", errores);
        resultado.put("success", errores.isEmpty());
        return resultado;
    }

    private Optional<Producto> buscarExistente(LaminaFormDTO dto) {
        return productoRepository.findLaminasCoincidenciaExacta(
                laminaService.normalizarTexto(dto.getLaminaNumero()),
                laminaService.normalizarTexto(dto.getLaminaTitulo()),
                laminaService.normalizarTexto(dto.getLaminaContenedor())
        ).stream().findFirst();
    }

    private boolean filaVacia(Row row) {
        for (int i = 0; i < COLUMNAS.length; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && !getCellStringValue(cell).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return "";
        }
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> {
                double value = cell.getNumericCellValue();
                if (Math.floor(value) == value) {
                    yield String.valueOf((long) value);
                }
                yield BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
            }
            case BOOLEAN -> cell.getBooleanCellValue() ? "SI" : "NO";
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private Integer getCellIntegerValue(Cell cell, int defaultValue) {
        String text = getCellStringValue(cell);
        if (text.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(text.replaceAll("[^0-9-]", ""));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private BigDecimal getCellBigDecimalValue(Cell cell, BigDecimal defaultValue) {
        String text = getCellStringValue(cell);
        if (text.isBlank()) {
            return defaultValue;
        }
        try {
            return new BigDecimal(text.replace(",", "."));
        } catch (Exception e) {
            return defaultValue;
        }
    }
}
