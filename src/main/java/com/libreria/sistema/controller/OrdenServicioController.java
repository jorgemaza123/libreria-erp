package com.libreria.sistema.controller;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.OrdenItem;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.dto.OrdenDTO;
import com.libreria.sistema.repository.CajaRepository;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.service.ConfiguracionService; // IMPORTANTE

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
    private final ConfiguracionService configuracionService; // Inyectado

    public OrdenServicioController(OrdenServicioRepository ordenRepository, 
                                   CajaRepository cajaRepository,
                                   ConfiguracionService configuracionService) {
        this.ordenRepository = ordenRepository;
        this.cajaRepository = cajaRepository;
        this.configuracionService = configuracionService;
    }

    @GetMapping("/nueva")
public String nuevaOrden(Model model) { 
    // ESTA LÍNEA ES VITAL PARA QUE EL DATALIST FUNCIONE
    model.addAttribute("tipos", ordenRepository.findTiposServicio()); 
    return "ordenes/formulario"; 
}

    @GetMapping("/lista")
    public String listaOrdenes(Model model) {
        model.addAttribute("ordenes", ordenRepository.findAll());
        return "ordenes/lista";
    }

    // API GUARDAR
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

    // API FINALIZAR
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
    //        GENERADOR PDF (CON LOGO)
    // ==========================================
    @GetMapping("/pdf/{id}")
    public void descargarPdf(@PathVariable Long id, HttpServletResponse response) throws IOException, DocumentException {
        OrdenServicio orden = ordenRepository.findById(id).orElse(null);
        if(orden == null) return;

        Configuracion config = configuracionService.obtenerConfiguracion(); // DATOS EMPRESA

        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline; filename=Orden_" + id + ".pdf");

        Document document = new Document(PageSize.A4, 30, 30, 30, 30);
        PdfWriter.getInstance(document, response.getOutputStream());
        document.open();

        // 1. CABECERA
        PdfPTable headerTable = new PdfPTable(3);
        headerTable.setWidthPercentage(100);
        headerTable.setWidths(new float[]{5, 1, 3});

        // Logo y Datos Empresa
        PdfPCell cellEmpresa = new PdfPCell();
        cellEmpresa.setBorder(Rectangle.NO_BORDER);
        
        // Intentar poner logo si existe
        if (config.getLogoBase64() != null && !config.getLogoBase64().isEmpty()) {
            try {
                byte[] imageBytes = java.util.Base64.getDecoder().decode(config.getLogoBase64());
                Image logo = Image.getInstance(imageBytes);
                logo.scaleToFit(120, 60);
                logo.setAlignment(Element.ALIGN_LEFT);
                cellEmpresa.addElement(logo);
            } catch (Exception e) { /* Ignorar error de logo */ }
        }

        Font fontEmpresa = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14);
        cellEmpresa.addElement(new Paragraph(config.getNombreEmpresa(), fontEmpresa));
        cellEmpresa.addElement(new Paragraph(config.getDireccion(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
        cellEmpresa.addElement(new Paragraph("Telf: " + config.getTelefono(), FontFactory.getFont(FontFactory.HELVETICA, 9)));
        headerTable.addCell(cellEmpresa);

        // Espacio
        PdfPCell cellVacia = new PdfPCell();
        cellVacia.setBorder(Rectangle.NO_BORDER);
        headerTable.addCell(cellVacia);

        // Cuadro RUC
        PdfPCell cellRuc = new PdfPCell();
        cellRuc.setBorder(Rectangle.BOX);
        cellRuc.setBorderWidth(1.5f);
        cellRuc.setPadding(8);
        
        Paragraph pRuc = new Paragraph("R.U.C. " + config.getRuc(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12));
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

        document.add(new Paragraph(" "));

        // 2. DATOS CLIENTE
        PdfPTable infoTable = new PdfPTable(2);
        infoTable.setWidthPercentage(100);
        infoTable.setWidths(new float[]{1, 1});

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

        // 3. TABLA ITEMS
        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setWidths(new float[]{4, 1});

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

        Font fontData = FontFactory.getFont(FontFactory.HELVETICA, 10);
        for(OrdenItem item : orden.getItems()) {
            PdfPCell cellDesc = new PdfPCell(new Phrase(item.getDescripcion(), fontData));
            cellDesc.setPadding(5);
            table.addCell(cellDesc);

            PdfPCell cellMonto = new PdfPCell(new Phrase(config.getMoneda() + " " + item.getCosto(), fontData));
            cellMonto.setHorizontalAlignment(Element.ALIGN_RIGHT);
            cellMonto.setPadding(5);
            table.addCell(cellMonto);
        }
        document.add(table);

        // 4. TOTALES
        document.add(new Paragraph(" "));
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(40);
        totalTable.setHorizontalAlignment(Element.ALIGN_RIGHT);
        
        agregarFilaTotal(totalTable, "TOTAL:", orden.getTotal(), true, config.getMoneda());
        agregarFilaTotal(totalTable, "A CUENTA:", orden.getACuenta(), false, config.getMoneda());
        agregarFilaTotal(totalTable, "SALDO:", orden.getSaldo(), true, config.getMoneda());
        
        document.add(totalTable);

        document.add(new Paragraph(" "));
        document.add(new Paragraph("OBSERVACIONES: " + (orden.getObservaciones() != null ? orden.getObservaciones() : "Ninguna"), FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 9)));
        document.add(new Paragraph("---------------------------------------------------------------------------------------------------"));
        document.add(new Paragraph("Nota: Pasados 30 días no nos responsabilizamos por equipos olvidados.", FontFactory.getFont(FontFactory.HELVETICA, 8)));

        document.close();
    }

    private void agregarFilaTotal(PdfPTable table, String label, BigDecimal valor, boolean bold, String moneda) {
        Font f = bold ? FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10) : FontFactory.getFont(FontFactory.HELVETICA, 10);
        PdfPCell cLabel = new PdfPCell(new Phrase(label, f));
        cLabel.setBorder(Rectangle.NO_BORDER);
        cLabel.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(cLabel);

        PdfPCell cVal = new PdfPCell(new Phrase(moneda + " " + valor, f));
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

    @GetMapping("/editar/{id}")
public String editarOrden(@PathVariable Long id, Model model) {
    OrdenServicio orden = ordenRepository.findById(id).orElse(null);
    if(orden == null) return "redirect:/ordenes/lista";
    
    model.addAttribute("ordenEdicion", orden);
    // AQUÍ TAMBIÉN:
    model.addAttribute("tipos", ordenRepository.findTiposServicio()); 
    return "ordenes/formulario";
}
}