package com.libreria.sistema.controller;

import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.VentaRepository;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.io.IOException;
import java.util.List;

@Controller
@RequestMapping("/reportes")
public class ReporteController {

    private final VentaRepository ventaRepository;

    public ReporteController(VentaRepository ventaRepository) {
        this.ventaRepository = ventaRepository;
    }

    // Vista de Filtros (Simple)
    @GetMapping
    public String index() {
        return "reportes/index"; 
    }

    // EXPORTAR EXCEL (Ventas Totales)
    @GetMapping("/excel/ventas")
    public void exportarVentasExcel(HttpServletResponse response) throws IOException {
        List<Venta> ventas = ventaRepository.findAll(); // Aquí deberías filtrar por fecha

        // Crear Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Ventas");

        // Cabecera
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("ID");
        header.createCell(1).setCellValue("Fecha");
        header.createCell(2).setCellValue("Comprobante");
        header.createCell(3).setCellValue("Cliente");
        header.createCell(4).setCellValue("Total");
        header.createCell(5).setCellValue("Estado");

        // Datos
        int rowIdx = 1;
        for (Venta v : ventas) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(v.getId());
            row.createCell(1).setCellValue(v.getFechaEmision().toString());
            row.createCell(2).setCellValue(v.getSerie() + "-" + v.getNumero());
            row.createCell(3).setCellValue(v.getClienteDenominacion());
            row.createCell(4).setCellValue(v.getTotal().doubleValue());
            row.createCell(5).setCellValue(v.getEstado());
        }

        // Enviar respuesta
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=ventas.xlsx");
        workbook.write(response.getOutputStream());
        workbook.close();
    }
}