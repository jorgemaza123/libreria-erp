package com.libreria.sistema.controller;

import com.libreria.sistema.model.Kardex;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.KardexRepository;
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
    private final KardexRepository kardexRepository; // Necesario para registrar el ajuste

    public InventarioController(ProductoRepository productoRepository, KardexRepository kardexRepository) {
        this.productoRepository = productoRepository;
        this.kardexRepository = kardexRepository;
    }

    // --- IMPORTACIÓN EXCEL (YA EXISTENTE) ---
    @GetMapping("/importar")
    public String vistaImportar() {
        return "inventario/importar";
    }

    @PostMapping("/upload")
    public String subirExcel(@RequestParam("file") MultipartFile file, RedirectAttributes attr) {
        // ... (TU CÓDIGO DE IMPORTACIÓN EXCEL EXISTENTE SE MANTIENE IGUAL) ...
        // Si no lo tienes a mano, avísame para reenviarlo, pero asumo que ya está.
        if (file.isEmpty()) {
            attr.addFlashAttribute("error", "Por favor seleccione un archivo Excel.");
            return "redirect:/inventario/importar";
        }
        try (Workbook workbook = new XSSFWorkbook(file.getInputStream())) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Producto> nuevosProductos = new ArrayList<>();
            int filasProcesadas = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue; 
                String nombre = "";
                Cell cellName = row.getCell(0);
                if(cellName != null) nombre = cellName.getStringCellValue();
                if (nombre.isEmpty()) continue;

                Producto p = new Producto();
                p.setNombre(nombre.toUpperCase());
                // ... lógica simple para evitar errores si celdas vacías ...
                p.setStockActual((int)(row.getCell(4) != null ? row.getCell(4).getNumericCellValue() : 0));
                p.setPrecioVenta(BigDecimal.valueOf(row.getCell(3) != null ? row.getCell(3).getNumericCellValue() : 0));
                p.setPrecioCompra(BigDecimal.valueOf(row.getCell(2) != null ? row.getCell(2).getNumericCellValue() : 0));
                p.setActivo(true);
                nuevosProductos.add(p);
                filasProcesadas++;
            }
            productoRepository.saveAll(nuevosProductos);
            attr.addFlashAttribute("success", "Carga completada: " + filasProcesadas);
        } catch (Exception e) {
            attr.addFlashAttribute("error", "Error: " + e.getMessage());
        }
        return "redirect:/inventario/importar";
    }

    // --- ETIQUETAS (YA EXISTENTE) ---
    @GetMapping("/etiquetas/{id}")
    public String generarEtiquetas(@PathVariable Long id, Model model) {
        Producto p = productoRepository.findById(id).orElse(null);
        if(p == null) return "redirect:/productos";
        model.addAttribute("producto", p);
        return "inventario/etiquetas";
    }

    // ==========================================
    // NUEVO: MÓDULO DE AJUSTE DE INVENTARIO
    // ==========================================

    @GetMapping("/ajuste")
    public String vistaAjuste(Model model) {
        // Enviamos la lista para el select buscador
        model.addAttribute("productos", productoRepository.findAll());
        return "inventario/ajuste";
    }

    @PostMapping("/ajustar")
    public String procesarAjuste(@RequestParam Long productoId,
                                 @RequestParam Integer stockReal,
                                 @RequestParam String motivo,
                                 RedirectAttributes attr) {
        try {
            Producto prod = productoRepository.findById(productoId)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

            int stockSistema = prod.getStockActual();
            int diferencia = stockReal - stockSistema;

            if (diferencia == 0) {
                attr.addFlashAttribute("info", "El stock real es igual al del sistema. No se hicieron cambios.");
                return "redirect:/inventario/ajuste";
            }

            // Registrar en KARDEX
            Kardex k = new Kardex();
            k.setProducto(prod);
            k.setStockAnterior(stockSistema);
            k.setStockActual(stockReal);
            k.setCantidad(Math.abs(diferencia)); // Cantidad movida (valor absoluto)
            
            if (diferencia > 0) {
                k.setTipo("ENTRADA (AJUSTE)");
                k.setMotivo("SOBRANTE INVENTARIO: " + motivo);
            } else {
                k.setTipo("SALIDA (AJUSTE)");
                k.setMotivo("MERMA/FALTANTE: " + motivo);
            }
            
            kardexRepository.save(k);

            // Actualizar Producto
            prod.setStockActual(stockReal);
            productoRepository.save(prod);

            attr.addFlashAttribute("success", "Stock ajustado correctamente. Nuevo stock: " + stockReal);

        } catch (Exception e) {
            attr.addFlashAttribute("error", "Error al ajustar: " + e.getMessage());
        }
        return "redirect:/inventario/ajuste";
    }
}