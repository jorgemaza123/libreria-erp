package com.libreria.sistema.controller;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Controller
@RequestMapping("/inventario")
public class InventarioController {

    private final ProductoRepository productoRepository;

    public InventarioController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    // --- 1. VISTA DE IMPORTACIÓN ---
    @GetMapping("/importar")
    public String vistaImportar() {
        return "inventario/importar";
    }

    // --- 2. PROCESAR EXCEL ---
    @PostMapping("/upload")
    public String subirExcel(@RequestParam("file") MultipartFile file, RedirectAttributes attr) {
        if (file.isEmpty()) {
            attr.addFlashAttribute("error", "Por favor seleccione un archivo Excel.");
            return "redirect:/inventario/importar";
        }

        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0); // Leer la primera hoja
            List<Producto> nuevosProductos = new ArrayList<>();
            int filasProcesadas = 0;
            int filasOmitidas = 0;

            // Iterar filas (saltando la cabecera index 0)
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 

                // Leer Celdas (Asumiendo orden: NOMBRE, CODIGO_BARRA, COSTO, PRECIO, STOCK)
                String nombre = getCellValue(row, 0);
                if (nombre == null || nombre.isEmpty()) continue; // Nombre obligatorio

                String codigo = getCellValue(row, 1); // Puede estar vacío y se genera uno
                BigDecimal costo = getDecimalValue(row, 2);
                BigDecimal precio = getDecimalValue(row, 3);
                int stock = (int) getNumericValue(row, 4);

                // Verificar si ya existe por código
                if (codigo != null && !codigo.isEmpty() && productoRepository.findByCodigoBarra(codigo).isPresent()) {
                    filasOmitidas++; // Ya existe, lo saltamos (o podrías actualizarlo)
                    continue;
                }

                Producto p = new Producto();
                p.setNombre(nombre.toUpperCase());
                p.setCodigoBarra(codigo != null && !codigo.isEmpty() ? codigo : "GEN-" + System.currentTimeMillis() + row.getRowNum());
                p.setPrecioCompra(costo);
                p.setPrecioVenta(precio);
                p.setStockActual(stock);
                p.setActivo(true);
                p.setStockMinimo(5);
                p.setUnidadMedida("UNIDAD");
                
                nuevosProductos.add(p);
                filasProcesadas++;
            }

            // Guardar todo de golpe
            productoRepository.saveAll(nuevosProductos);
            attr.addFlashAttribute("success", "Carga completada. Procesados: " + filasProcesadas + ". Omitidos (Duplicados): " + filasOmitidas);

        } catch (Exception e) {
            e.printStackTrace();
            attr.addFlashAttribute("error", "Error al procesar Excel: " + e.getMessage());
        }

        return "redirect:/inventario/importar";
    }

    // --- 3. GENERADOR DE ETIQUETAS ---
    @GetMapping("/etiquetas/{id}")
    public String generarEtiquetas(@PathVariable Long id, Model model) {
        Producto p = productoRepository.findById(id).orElse(null);
        if(p == null) return "redirect:/productos";
        
        model.addAttribute("producto", p);
        return "inventario/etiquetas";
    }

    // --- UTILITARIOS PARA EXCEL ---
    private String getCellValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            default -> "";
        };
    }

    private BigDecimal getDecimalValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return BigDecimal.ZERO;
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        return BigDecimal.ZERO;
    }
    
    private double getNumericValue(Row row, int index) {
        Cell cell = row.getCell(index);
        if (cell == null) return 0;
        if (cell.getCellType() == CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        return 0;
    }
}