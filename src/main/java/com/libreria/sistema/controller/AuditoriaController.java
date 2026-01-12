package com.libreria.sistema.controller;

import com.libreria.sistema.model.AuditoriaLog;
import com.libreria.sistema.service.AuditoriaService;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/auditoria")
@PreAuthorize("hasPermission(null, 'AUDITORIA_VER')")
public class AuditoriaController {

    @Autowired
    private AuditoriaService auditoriaService;

    @GetMapping
    public String index(Model model) {
        model.addAttribute("titulo", "Auditoría del Sistema");
        return "auditoria/index";
    }

    @GetMapping("/api/buscar")
    @ResponseBody
    public Map<String, Object> buscar(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        // Limpieza de strings vacíos
        if (usuario != null && usuario.trim().isEmpty()) usuario = null;
        if (modulo != null && modulo.trim().isEmpty()) modulo = null;
        if (accion != null && accion.trim().isEmpty()) accion = null;

        Pageable pageable = PageRequest.of(page, size, Sort.by("fechaHora").descending());
        Page<AuditoriaLog> resultado = auditoriaService.obtenerHistorial(
            usuario, modulo, accion, fechaInicio, fechaFin, pageable
        );

        Map<String, Object> response = new HashMap<>();
        response.put("content", resultado.getContent());
        response.put("totalElements", resultado.getTotalElements());
        response.put("totalPages", resultado.getTotalPages());
        response.put("currentPage", resultado.getNumber());
        response.put("first", resultado.isFirst());
        response.put("last", resultado.isLast());

        return response;
    }

    @GetMapping("/api/detalle/{id}")
    @ResponseBody
    public ResponseEntity<AuditoriaLog> obtenerDetalle(@PathVariable Long id) {
        // Enfoque simplificado usando el repositorio directamente si fuera necesario, 
        // pero usaremos el filtro para ser consistentes.
        // Lo ideal sería un método findById en el servicio, pero esto funciona:
        return auditoriaService.obtenerHistorial(null, null, null, null, null, Pageable.unpaged())
                .stream()
                .filter(log -> log.getId().equals(id))
                .findFirst()
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportarExcel(
            @RequestParam(required = false) String usuario,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fechaFin) {

        try {
            if (usuario != null && usuario.trim().isEmpty()) usuario = null;
            if (modulo != null && modulo.trim().isEmpty()) modulo = null;
            if (accion != null && accion.trim().isEmpty()) accion = null;

            Pageable pageable = PageRequest.of(0, 5000, Sort.by("fechaHora").descending());
            Page<AuditoriaLog> auditorias = auditoriaService.obtenerHistorial(
                usuario, modulo, accion, fechaInicio, fechaFin, pageable
            );

            Workbook workbook = new XSSFWorkbook();
            Sheet sheet = workbook.createSheet("Auditoría");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row headerRow = sheet.createRow(0);
            String[] columnas = {"ID", "Fecha/Hora", "Usuario", "Módulo", "Acción", "Entidad", "ID Entidad", "IP", "Detalles"};
            for (int i = 0; i < columnas.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columnas[i]);
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");
            for (AuditoriaLog log : auditorias.getContent()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(log.getId());
                row.createCell(1).setCellValue(log.getFechaHora().format(formatter));
                row.createCell(2).setCellValue(log.getUsuario());
                row.createCell(3).setCellValue(log.getModulo());
                row.createCell(4).setCellValue(log.getAccion());
                row.createCell(5).setCellValue(log.getEntidad());
                row.createCell(6).setCellValue(log.getEntidadId() != null ? log.getEntidadId().toString() : "");
                row.createCell(7).setCellValue(log.getIpAddress());
                row.createCell(8).setCellValue(log.getDetalles() != null ? log.getDetalles() : "");
            }

            for (int i = 0; i < columnas.length; i++) {
                sheet.autoSizeColumn(i);
            }

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            String nombreArchivo = "auditoria_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", nombreArchivo);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(outputStream.toByteArray());

        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}