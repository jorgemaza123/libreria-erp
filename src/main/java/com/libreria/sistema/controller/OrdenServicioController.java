package com.libreria.sistema.controller;

import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.OrdenItem;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.dto.OrdenDTO;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.awt.Color;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Controller
@RequestMapping("/ordenes")
public class OrdenServicioController {

    private final OrdenServicioRepository ordenRepository;
    private final CajaRepository cajaRepository;

    public OrdenServicioController(OrdenServicioRepository ordenRepository, CajaRepository cajaRepository) {
        this.ordenRepository = ordenRepository;
        this.cajaRepository = cajaRepository;
    }

    // VISTAS
    @GetMapping("/nueva")
    public String nuevaOrden(Model model) { return "ordenes/formulario"; }

    @GetMapping("/lista")
    public String listaOrdenes(Model model) {
        model.addAttribute("ordenes", ordenRepository.findAll());
        return "ordenes/lista";
    }

    // API GUARDAR (Igual que antes)
    @PostMapping("/api/guardar")
    public ResponseEntity<?> guardarOrden(@RequestBody OrdenDTO dto) {
        try {
            OrdenServicio orden = new OrdenServicio();
            orden.setTipoServicio(dto.getTipoServicio());
            orden.setTituloTrabajo(dto.getTituloTrabajo());
            orden.setClienteNombre(dto.getClienteNombre());
            orden.setClienteTelefono(dto.getClienteTelefono());
            orden.setClienteDocumento(dto.getClienteDocumento());
            orden.setFechaEntregaEstimada(dto.getFechaEntrega());
            orden.setObservaciones(dto.getObservaciones());
            orden.setACuenta(dto.getACuenta() != null ? dto.getACuenta() : BigDecimal.ZERO);
            orden.setEstado("PENDIENTE");

            BigDecimal total = BigDecimal.ZERO;
            if (dto.getItems() != null) {
                for (OrdenDTO.ItemDTO itemDto : dto.getItems()) {
                    OrdenItem item = new OrdenItem();
                    item.setDescripcion(itemDto.getDescripcion());
                    item.setCosto(itemDto.getCosto());
                    item.setOrden(orden);
                    orden.getItems().add(item);
                    total = total.add(itemDto.getCosto());
                }
            }
            orden.setTotal(total);
            orden.setSaldo(total.subtract(orden.getACuenta()));

            OrdenServicio guardada = ordenRepository.save(orden);

            if (orden.getACuenta().compareTo(BigDecimal.ZERO) > 0) {
                registrarEnCaja("INGRESO", "ADELANTO SERV #" + guardada.getId(), orden.getACuenta());
            }
            return ResponseEntity.ok(Map.of("message", "Orden registrada", "id", guardada.getId()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }

    // API FINALIZAR (Igual que antes)
    @PostMapping("/api/finalizar/{id}")
    public ResponseEntity<?> finalizarOrden(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean cobrarSaldo) {
        return ordenRepository.findById(id).map(orden -> {
            if (cobrarSaldo && orden.getSaldo().compareTo(BigDecimal.ZERO) > 0) {
                registrarEnCaja("INGRESO", "SALDO FINAL SERV #" + orden.getId(), orden.getSaldo());
                orden.setACuenta(orden.getTotal());
                orden.setSaldo(BigDecimal.ZERO);
            }
            orden.setEstado("ENTREGADO");
            ordenRepository.save(orden);
            return ResponseEntity.ok("Orden finalizada");
        }).orElse(ResponseEntity.badRequest().body("No encontrado"));
    }

    // ==========================================
    //        GENERADOR PDF PROFESIONAL
    // ==========================================
    @GetMapping("/pdf/{id}")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {
        OrdenServicio orden = ordenRepository.findById(id).orElse(null);
        if(orden == null) return;

        response.setContentType("application/pdf");
        // 'inline' hace que se abra en el navegador en lugar de descargar
        response.setHeader("Content-Disposition", "inline; filename=Orden_" + id + ".pdf");

        // Usamos A4 Vertical para que parezca documento formal
        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        // 1. CABECERA TIPO SUNAT (Logo Izq, RUC Der)
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{5, 1, 3});

        // Datos Empresa
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        cellEmpresa.addElement(new Paragraph("LIBRERÍA & SERVICIOS ERP", fontEmpresa));
        cellEmpresa.addElement(new Paragraph("Av. Principal 123 - Lima", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellEmpresa.addElement(new Paragraph("Telf: (01) 555-0909", FontFactory.getFont(FontFactory.HELVETICA, 9)));
        headerTable.addCell(cellEmpresa);

        // Espacio
        PdfPCell cellVacia = new PdfPCell();
        cellVacia.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(cellVacia);

        // Cuadro RUC / Orden
        PdfPCell cellRuc = new PdfPCell();
        cellRuc.setBorder(Rectangle.BOX);
        cellRuc.setBorderWidth(1.5f);
        cellRuc.setPadding(8);
        
        Paragraph pRuc = new Paragraph("R.U.C. 20100000001", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
        pRuc.setAlignment(Element.ALIGN_CENTER);
        cellRuc.addElement(pRuc);
        
        Paragraph pTipo = new Paragraph("ORDEN DE SERVICIO", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10));
        pTipo.setAlignment(Element.ALIGN_CENTER);
        pTipo.setSpacingBefore(5);
        cellRuc.addElement(pTipo);

        Paragraph pNum = new Paragraph("Nº " + String.format("%06d", orden.getId()), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.RED));
        pNum.setAlignment(Element.ALIGN_CENTER);
        cellRuc.addElement(pNum);
        
        headerTable.addCell(cellRuc);
        document.add(headerTable);

        document.add(new Paragraph(" ")); // Separador

        // 2. DATOS DEL CLIENTE Y TRABAJO
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1}); // 50% - 50%

        PdfPCell cellInfoIzq = new PdfPCell();
        cellInfoIzq.setBorder(Rectangle.NO_BORDER);
        cellInfoIzq.addElement(new Paragraph("CLIENTE: " + orden.getClienteNombre(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellInfoIzq.addElement(new Paragraph("DOC: " + (orden.getClienteDocumento() != null ? orden.getClienteDocumento() : "-"), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellInfoIzq.addElement(new Paragraph("TELF: " + orden.getClienteTelefono(), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        infoTable.addCell(cellInfoIzq);

        PdfPCell cellInfoDer = new PdfPCell();
        cellInfoDer.setBorder(Rectangle.NO_BORDER);
        cellInfoDer.addElement(new Paragraph("FECHA: " + orden.getFechaRecepcion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellInfoDer.addElement(new Paragraph("ENTREGA EST.: " + (orden.getFechaEntregaEstimada() != null ? orden.getFechaEntregaEstimada().toString() : "-"), FontFactory.getFont(FontFactory.HELVETICA, 10)));
        cellInfoDer.addElement(new Paragraph("TIPO: " + orden.getTipoServicio(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10)));
        infoTable.addCell(cellInfoDer);

        document.add(infoTable);
        document.add(new Paragraph("TRABAJO: " + orden.getTituloTrabajo(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
        document.add(new Paragraph(" "));

        // 3. TABLA DE ITEMS (PROFESIONAL)
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 1}); // 80% Desc, 20% Precio

        // Cabecera Tabla
        Font fontHeader = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE);
        PdfPCell c1 = new PdfPCell(new Phrase("DESCRIPCIÓN / SERVICIO / FALLA", fontHeader));
        c1.setBackgroundColor(Color.DARK_GRAY);
        c1.setHorizontalAlignment(Element.ALIGN_CENTER);
        c1.setPadding(5);
        table.addCell(c1);

        PdfPCell c2 = new PdfPCell(new Phrase("IMPORTE", fontHeader));
        c2.setBackgroundColor(Color.DARK_GRAY);
        c2.setHorizontalAlignment(Element.ALIGN_CENTER);
        c2.setPadding(5);
        table.addCell(c2);

        // Datos Tabla
        Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 10);
        for(OrdenItem item : orden.getItems()) {
            PdfPCell cellDesc = new PdfPCell(new Phrase(item.getDescripcion(), fontData));
            cellDesc.setPadding(5);
            table.addCell(cellDesc);

            PdfPCell cellMonto = new PdfPCell(new Phrase("S/ " + item.getCosto(), fontData));
            cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellMonto.setPadding(5);
            table.addCell(cellMonto);
        }
        document.add(table);

        // 4. TOTALES
        document.add(new Paragraph(" "));
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(40); // Tabla pequeña a la derecha
        totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        agregarFilaTotal(totalTable, "TOTAL:", orden.getTotal(), true);
        agregarFilaTotal(totalTable, "A CUENTA:", orden.getACuenta(), false);
        agregarFilaTotal(totalTable, "SALDO:", orden.getSaldo(), true);
        
        document.add(totalTable);

        // Pie de página
        document.add(new Paragraph(" "));
        document.add(new Paragraph("OBSERVACIONES: " + (orden.getObservaciones() != null ? orden.getObservaciones() : "Ninguna"), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));
        document.add(new Paragraph("---------------------------------------------------------------------------------------------------"));
        document.add(new Paragraph("Nota: Pasados 30 días no nos responsabilizamos por equipos olvidados.", FontFactory.getFont(FontFactory.HELVETICA, 8)));

        document.close();
    }

    private void agregarFilaTotal(PdfPTable table, String label, BigDecimal valor, boolean bold) {
        Font f = bold ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10) : FontFactory.getFont(FontFactory.HELVETICA, 10);
        PdfPCell cLabel = new PdfPCell(new Phrase(label, f));
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cLabel);

        PdfPCell cVal = new PdfPCell(new Phrase("S/ " + valor, f));
        cVal.setBorder(Rectangle.BOX);
        cVal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cVal);
    }

    private void registrarEnCaja(String tipo, String concepto, BigDecimal monto) {
        MovimientoCaja mov = new MovimientoCaja();
        mov.setFecha(LocalDateTime.now());
        mov.setTipo(tipo);
        mov.setConcepto(concepto);
        mov.setMonto(monto);
        cajaRepository.save(mov);
    }
}