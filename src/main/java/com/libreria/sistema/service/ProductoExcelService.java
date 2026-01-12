package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Servicio para importación y exportación masiva de productos vía Excel.
 * Permite cargar inventarios completos de forma rápida.
 */
@Service
@Slf4j
public class ProductoExcelService {

    private final ProductoRepository productoRepository;

    // Columnas de la plantilla Excel
    private static final String[] COLUMNAS = {
        "CODIGO_BARRAS",      // 0 - Opcional
        "CODIGO_INTERNO",     // 1 - Opcional (SKU)
        "NOMBRE",             // 2 - OBLIGATORIO
        "CATEGORIA",          // 3 - Opcional
        "MARCA",              // 4 - Opcional
        "MODELO",             // 5 - Opcional
        "DESCRIPCION",        // 6 - Opcional
        "PRECIO_COMPRA",      // 7 - Opcional
        "PRECIO_VENTA",       // 8 - Opcional
        "PRECIO_MAYORISTA",   // 9 - Opcional
        "STOCK_ACTUAL",       // 10 - Opcional (default 0)
        "STOCK_MINIMO",       // 11 - Opcional (default 5)
        "UNIDAD_MEDIDA",      // 12 - Opcional (UNIDAD, CAJA, KG, LITRO, METRO)
        "UBICACION_FILA",     // 13 - Opcional
        "UBICACION_COLUMNA",  // 14 - Opcional
        "UBICACION_ESTANTE"   // 15 - Opcional
    };

    public ProductoExcelService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    /**
     * Genera la plantilla Excel vacía para descargar
     */
    public byte[] generarPlantilla() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Productos");

            // Estilo para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            // Estilo para campo obligatorio
            CellStyle requiredStyle = workbook.createCellStyle();
            requiredStyle.setFillForegroundColor(IndexedColors.RED.getIndex());
            requiredStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font reqFont = workbook.createFont();
            reqFont.setColor(IndexedColors.WHITE.getIndex());
            reqFont.setBold(true);
            requiredStyle.setFont(reqFont);

            // Crear encabezados
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUMNAS[i]);
                // El NOMBRE (columna 2) es obligatorio - resaltarlo
                cell.setCellStyle(i == 2 ? requiredStyle : headerStyle);
                sheet.setColumnWidth(i, 5000);
            }

            // Fila de instrucciones
            Row instrRow = sheet.createRow(1);
            CellStyle instrStyle = workbook.createCellStyle();
            instrStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
            instrStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font instrFont = workbook.createFont();
            instrFont.setItalic(true);
            instrFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            instrStyle.setFont(instrFont);

            String[] instrucciones = {
                "Opcional",           // CODIGO_BARRAS
                "Opcional",           // CODIGO_INTERNO
                "**OBLIGATORIO**",    // NOMBRE
                "Opcional",           // CATEGORIA
                "Opcional",           // MARCA
                "Opcional",           // MODELO
                "Opcional",           // DESCRIPCION
                "Ej: 10.50",          // PRECIO_COMPRA
                "Ej: 15.00",          // PRECIO_VENTA
                "Ej: 12.00",          // PRECIO_MAYORISTA
                "Número entero",      // STOCK_ACTUAL
                "Número entero",      // STOCK_MINIMO
                "UNIDAD/CAJA/KG/L/M", // UNIDAD_MEDIDA
                "Opcional",           // UBICACION_FILA
                "Opcional",           // UBICACION_COLUMNA
                "Opcional"            // UBICACION_ESTANTE
            };

            for (int i = 0; i < instrucciones.length; i++) {
                Cell cell = instrRow.createCell(i);
                cell.setCellValue(instrucciones[i]);
                cell.setCellStyle(instrStyle);
            }

            // Fila de ejemplo
            Row ejemploRow = sheet.createRow(2);
            String[] ejemplo = {
                "7751234567890",       // CODIGO_BARRAS
                "FILT-001",            // CODIGO_INTERNO
                "FILTRO DE ACEITE TOYOTA",  // NOMBRE
                "FILTROS",             // CATEGORIA
                "TOYOTA",              // MARCA
                "90915-YZZD4",         // MODELO
                "Filtro original Toyota", // DESCRIPCION
                "25.00",               // PRECIO_COMPRA
                "45.00",               // PRECIO_VENTA
                "38.00",               // PRECIO_MAYORISTA
                "50",                  // STOCK_ACTUAL
                "10",                  // STOCK_MINIMO
                "UNIDAD",              // UNIDAD_MEDIDA
                "A",                   // UBICACION_FILA
                "1",                   // UBICACION_COLUMNA
                "EST-01"               // UBICACION_ESTANTE
            };

            CellStyle exampleStyle = workbook.createCellStyle();
            exampleStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            exampleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            for (int i = 0; i < ejemplo.length; i++) {
                Cell cell = ejemploRow.createCell(i);
                cell.setCellValue(ejemplo[i]);
                cell.setCellStyle(exampleStyle);
            }

            // Ajustar ancho de columnas
            sheet.setColumnWidth(2, 10000);  // NOMBRE más ancho
            sheet.setColumnWidth(6, 8000);   // DESCRIPCION más ancho

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Exporta todos los productos actuales a Excel
     */
    public byte[] exportarProductos() throws IOException {
        List<Producto> productos = productoRepository.findByActivoTrue();

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Productos");

            // Estilo para encabezados
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            Font headerFont = workbook.createFont();
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            // Crear encabezados
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < COLUMNAS.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(COLUMNAS[i]);
                cell.setCellStyle(headerStyle);
            }

            // Llenar datos
            int rowNum = 1;
            for (Producto p : productos) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(p.getCodigoBarra() != null ? p.getCodigoBarra() : "");
                row.createCell(1).setCellValue(p.getCodigoInterno() != null ? p.getCodigoInterno() : "");
                row.createCell(2).setCellValue(p.getNombre() != null ? p.getNombre() : "");
                row.createCell(3).setCellValue(p.getCategoria() != null ? p.getCategoria() : "");
                row.createCell(4).setCellValue(p.getMarca() != null ? p.getMarca() : "");
                row.createCell(5).setCellValue(p.getModelo() != null ? p.getModelo() : "");
                row.createCell(6).setCellValue(p.getDescripcion() != null ? p.getDescripcion() : "");
                row.createCell(7).setCellValue(p.getPrecioCompra() != null ? p.getPrecioCompra().doubleValue() : 0);
                row.createCell(8).setCellValue(p.getPrecioVenta() != null ? p.getPrecioVenta().doubleValue() : 0);
                row.createCell(9).setCellValue(p.getPrecioMayorista() != null ? p.getPrecioMayorista().doubleValue() : 0);
                row.createCell(10).setCellValue(p.getStockActual() != null ? p.getStockActual() : 0);
                row.createCell(11).setCellValue(p.getStockMinimo() != null ? p.getStockMinimo() : 5);
                row.createCell(12).setCellValue(p.getUnidadMedida() != null ? p.getUnidadMedida() : "UNIDAD");
                row.createCell(13).setCellValue(p.getUbicacionFila() != null ? p.getUbicacionFila() : "");
                row.createCell(14).setCellValue(p.getUbicacionColumna() != null ? p.getUbicacionColumna() : "");
                row.createCell(15).setCellValue(p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "");
            }

            // Auto-ajustar columnas
            for (int i = 0; i < COLUMNAS.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    /**
     * Obtiene el siguiente número de SKU disponible
     */
    private int obtenerSiguienteNumeroSku() {
        return productoRepository.findUltimoSku()
            .map(ultimo -> {
                try {
                    return Integer.parseInt(ultimo.replace("SKU-", "")) + 1;
                } catch (NumberFormatException e) {
                    return 1;
                }
            })
            .orElse(1);
    }

    /**
     * Importa productos desde un archivo Excel.
     * Retorna un mapa con estadísticas y errores.
     */
    @Transactional
    public Map<String, Object> importarProductos(MultipartFile file, boolean actualizarExistentes) throws IOException {
        Map<String, Object> resultado = new HashMap<>();
        List<String> errores = new ArrayList<>();
        List<String> advertencias = new ArrayList<>();
        int creados = 0;
        int actualizados = 0;
        int omitidos = 0;

        // Contador para SKU autogenerado durante la importación
        int siguienteNumeroSku = obtenerSiguienteNumeroSku();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            int totalFilas = sheet.getLastRowNum();

            // Empezar desde fila 3 (0=encabezado, 1=instrucciones, 2=ejemplo)
            for (int i = 3; i <= totalFilas; i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) continue;

                try {
                    // Leer nombre (OBLIGATORIO)
                    String nombre = getCellStringValue(row.getCell(2));
                    if (nombre == null || nombre.trim().isEmpty()) {
                        errores.add("Fila " + (i + 1) + ": El NOMBRE es obligatorio");
                        omitidos++;
                        continue;
                    }

                    String codigoBarra = getCellStringValue(row.getCell(0));
                    String codigoInterno = getCellStringValue(row.getCell(1));

                    // Buscar si ya existe el producto
                    Producto productoExistente = null;

                    // Primero buscar por código de barras
                    if (codigoBarra != null && !codigoBarra.isEmpty()) {
                        productoExistente = productoRepository.findByCodigoBarra(codigoBarra).orElse(null);
                    }

                    // Si no encontró, buscar por código interno
                    if (productoExistente == null && codigoInterno != null && !codigoInterno.isEmpty()) {
                        productoExistente = productoRepository.findByCodigoInterno(codigoInterno).orElse(null);
                    }

                    if (productoExistente != null) {
                        if (actualizarExistentes) {
                            // Actualizar producto existente
                            actualizarProductoDesdeRow(productoExistente, row);
                            productoRepository.save(productoExistente);
                            actualizados++;
                            log.debug("Producto actualizado: {}", nombre);
                        } else {
                            advertencias.add("Fila " + (i + 1) + ": Producto '" + nombre + "' ya existe (omitido)");
                            omitidos++;
                        }
                    } else {
                        // Crear nuevo producto
                        Producto nuevo = crearProductoDesdeRow(row);

                        // Autogenerar SKU si no viene en el Excel
                        if (nuevo.getCodigoInterno() == null || nuevo.getCodigoInterno().isBlank()) {
                            nuevo.setCodigoInterno(String.format("SKU-%05d", siguienteNumeroSku++));
                        }

                        productoRepository.save(nuevo);
                        creados++;
                        log.debug("Producto creado: {}", nombre);
                    }

                } catch (Exception e) {
                    errores.add("Fila " + (i + 1) + ": " + e.getMessage());
                    omitidos++;
                    log.warn("Error procesando fila {}: {}", i + 1, e.getMessage());
                }
            }
        }

        resultado.put("creados", creados);
        resultado.put("actualizados", actualizados);
        resultado.put("omitidos", omitidos);
        resultado.put("errores", errores);
        resultado.put("advertencias", advertencias);
        resultado.put("success", errores.isEmpty() || creados > 0 || actualizados > 0);

        log.info("Importación completada: {} creados, {} actualizados, {} omitidos", creados, actualizados, omitidos);

        return resultado;
    }

    private Producto crearProductoDesdeRow(Row row) {
        Producto p = new Producto();
        actualizarProductoDesdeRow(p, row);
        p.setActivo(true);
        return p;
    }

    private void actualizarProductoDesdeRow(Producto p, Row row) {
        String codigoBarra = getCellStringValue(row.getCell(0));
        String codigoInterno = getCellStringValue(row.getCell(1));

        // Solo actualizar códigos si no están vacíos
        if (codigoBarra != null && !codigoBarra.isEmpty()) {
            p.setCodigoBarra(codigoBarra);
        }
        if (codigoInterno != null && !codigoInterno.isEmpty()) {
            p.setCodigoInterno(codigoInterno);
        }

        p.setNombre(getCellStringValue(row.getCell(2)).toUpperCase());

        String categoria = getCellStringValue(row.getCell(3));
        if (categoria != null && !categoria.isEmpty()) {
            p.setCategoria(categoria.toUpperCase());
        }

        String marca = getCellStringValue(row.getCell(4));
        if (marca != null && !marca.isEmpty()) {
            p.setMarca(marca.toUpperCase());
        }

        p.setModelo(getCellStringValue(row.getCell(5)));
        p.setDescripcion(getCellStringValue(row.getCell(6)));

        BigDecimal precioCompra = getCellDecimalValue(row.getCell(7));
        if (precioCompra != null) p.setPrecioCompra(precioCompra);

        BigDecimal precioVenta = getCellDecimalValue(row.getCell(8));
        if (precioVenta != null) p.setPrecioVenta(precioVenta);

        BigDecimal precioMayorista = getCellDecimalValue(row.getCell(9));
        if (precioMayorista != null) p.setPrecioMayorista(precioMayorista);

        Integer stockActual = getCellIntValue(row.getCell(10));
        p.setStockActual(stockActual != null ? stockActual : 0);

        Integer stockMinimo = getCellIntValue(row.getCell(11));
        p.setStockMinimo(stockMinimo != null ? stockMinimo : 5);

        String unidadMedida = getCellStringValue(row.getCell(12));
        p.setUnidadMedida(unidadMedida != null && !unidadMedida.isEmpty() ? unidadMedida.toUpperCase() : "UNIDAD");

        p.setUbicacionFila(getCellStringValue(row.getCell(13)));
        p.setUbicacionColumna(getCellStringValue(row.getCell(14)));
        p.setUbicacionEstante(getCellStringValue(row.getCell(15)));
    }

    private boolean isRowEmpty(Row row) {
        for (int i = 0; i < COLUMNAS.length; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.trim().isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                // Si es un número, convertirlo a string sin decimales si es entero
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return cell.getStringCellValue();
                } catch (Exception e) {
                    return String.valueOf(cell.getNumericCellValue());
                }
            default:
                return null;
        }
    }

    private BigDecimal getCellDecimalValue(Cell cell) {
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return BigDecimal.valueOf(cell.getNumericCellValue());
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) return null;
                    // Reemplazar comas por puntos para formato español
                    value = value.replace(",", ".");
                    return new BigDecimal(value);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Integer getCellIntValue(Cell cell) {
        if (cell == null) return null;

        try {
            switch (cell.getCellType()) {
                case NUMERIC:
                    return (int) cell.getNumericCellValue();
                case STRING:
                    String value = cell.getStringCellValue().trim();
                    if (value.isEmpty()) return null;
                    return Integer.parseInt(value);
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
