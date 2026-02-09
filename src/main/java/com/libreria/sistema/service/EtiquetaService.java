package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import org.krysalis.barcode4j.impl.code128.Code128Bean;
import org.krysalis.barcode4j.output.bitmap.BitmapCanvasProvider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Servicio para generación de etiquetas con códigos de barras en PDF.
 * Soporta formato A4 (3x8 etiquetas) y Ticket (impresora térmica).
 */
@Service
@Slf4j
public class EtiquetaService {

    private final ProductoRepository productoRepository;
    private final ConfiguracionService configuracionService;

    // Constantes para formato A4 (hoja estándar de etiquetas adhesivas)
    private static final int COLUMNAS_A4 = 3;
    private static final int FILAS_A4 = 8;
    private static final float MARGEN_A4 = 15f; // mm convertidos a puntos
    private static final float ANCHO_ETIQUETA_A4 = 63.5f; // mm (estándar Avery)
    private static final float ALTO_ETIQUETA_A4 = 33.9f;  // mm (estándar Avery)

    // Constantes para formato Ticket (58mm o 80mm)
    private static final float ANCHO_TICKET_58 = 164f;  // puntos (58mm)
    private static final float ANCHO_TICKET_80 = 226f;  // puntos (80mm)

    public EtiquetaService(ProductoRepository productoRepository, ConfiguracionService configuracionService) {
        this.productoRepository = productoRepository;
        this.configuracionService = configuracionService;
    }

    /**
     * Genera PDF con etiquetas para los productos seleccionados.
     * El formato se determina según la configuración del sistema.
     * BLINDADO: Maneja productos sin datos, códigos inválidos y errores de renderizado.
     *
     * @param productoIds Lista de IDs de productos
     * @param cantidadPorProducto Cantidad de etiquetas por cada producto
     * @return byte[] con el contenido del PDF
     */
    public byte[] generarPdfEtiquetas(List<Long> productoIds, int cantidadPorProducto) throws IOException, DocumentException {
        // Validación de entrada
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un producto");
        }

        Configuracion config = configuracionService.obtenerConfiguracion();
        String formato = config != null && config.getFormatoImpresion() != null
                ? config.getFormatoImpresion() : "A4";

        List<Producto> productos = productoRepository.findAllById(productoIds);

        if (productos.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron productos con los IDs proporcionados");
        }

        log.info("Generando PDF de etiquetas para {} productos en formato {}", productos.size(), formato);

        try {
            if ("TICKET".equalsIgnoreCase(formato)) {
                return generarPdfTicket(productos, cantidadPorProducto, config);
            } else {
                return generarPdfA4(productos, cantidadPorProducto, config);
            }
        } catch (Exception e) {
            log.error("Error fatal generando PDF de etiquetas: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    /**
     * Genera PDF en formato A4 con etiquetas en grid 3x8.
     * Diseñado para papel adhesivo estándar (ej. Avery L7160).
     */
    private byte[] generarPdfA4(List<Producto> productos, int cantidadPorProducto, Configuracion config)
            throws IOException, DocumentException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Página A4 con márgenes reducidos para maximizar etiquetas
        Document document = new Document(PageSize.A4,
                mmToPts(7), mmToPts(7),  // márgenes izq/der
                mmToPts(13), mmToPts(13)); // márgenes sup/inf

        PdfWriter.getInstance(document, baos);
        document.open();

        // Tabla principal: 3 columnas
        PdfPTable tabla = new PdfPTable(COLUMNAS_A4);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0);
        tabla.setSpacingAfter(0);

        // Ancho fijo para cada columna
        float anchoColumna = mmToPts(ANCHO_ETIQUETA_A4);
        tabla.setTotalWidth(new float[]{anchoColumna, anchoColumna, anchoColumna});
        tabla.setLockedWidth(true);

        String moneda = config.getFormatoMoneda() != null ? config.getFormatoMoneda() : "S/";
        int etiquetasEnPagina = 0;

        for (Producto producto : productos) {
            for (int i = 0; i < cantidadPorProducto; i++) {
                PdfPCell celda = crearCeldaEtiquetaA4(producto, moneda);
                tabla.addCell(celda);
                etiquetasEnPagina++;

                // Cada 24 etiquetas (3x8), nueva página
                if (etiquetasEnPagina == COLUMNAS_A4 * FILAS_A4) {
                    document.add(tabla);
                    document.newPage();
                    tabla = new PdfPTable(COLUMNAS_A4);
                    tabla.setWidthPercentage(100);
                    tabla.setTotalWidth(new float[]{anchoColumna, anchoColumna, anchoColumna});
                    tabla.setLockedWidth(true);
                    etiquetasEnPagina = 0;
                }
            }
        }

        // Completar última fila con celdas vacías si es necesario
        int restantes = etiquetasEnPagina % COLUMNAS_A4;
        if (restantes > 0) {
            for (int i = restantes; i < COLUMNAS_A4; i++) {
                PdfPCell celdaVacia = new PdfPCell();
                celdaVacia.setBorder(Rectangle.NO_BORDER);
                celdaVacia.setFixedHeight(mmToPts(ALTO_ETIQUETA_A4));
                tabla.addCell(celdaVacia);
            }
        }

        if (etiquetasEnPagina > 0) {
            document.add(tabla);
        }

        document.close();
        return baos.toByteArray();
    }

    /**
     * Crea una celda de etiqueta para formato A4.
     * BLINDADO: Maneja nulls y errores de código de barras gracefully.
     */
    private PdfPCell crearCeldaEtiquetaA4(Producto producto, String moneda) {
        PdfPTable contenido = new PdfPTable(1);
        contenido.setWidthPercentage(100);

        // Fuentes
        Font fontNombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 7, Color.BLACK);
        Font fontPrecio = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12, Color.BLACK);
        Font fontCodigo = FontFactory.getFont(FontFactory.HELVETICA, 6, Color.DARK_GRAY);

        // NULL SAFETY: Nombre del producto (truncado)
        String nombre = java.util.Objects.toString(producto.getNombre(), "Sin nombre");
        if (nombre.length() > 30) {
            nombre = nombre.substring(0, 27) + "...";
        }
        PdfPCell celdaNombre = new PdfPCell(new Phrase(nombre, fontNombre));
        celdaNombre.setBorder(Rectangle.NO_BORDER);
        celdaNombre.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaNombre.setPaddingTop(2);
        celdaNombre.setPaddingBottom(1);
        contenido.addCell(celdaNombre);

        // Código de barras con FALLBACK a texto plano si falla la generación
        String codigoBarra = producto.getCodigoBarra();
        if (codigoBarra != null && !codigoBarra.trim().isEmpty()) {
            boolean codigoGeneradoExitosamente = false;

            try {
                byte[] barcodeImg = generarImagenCodigoBarras(codigoBarra, 120, 35);
                Image img = Image.getInstance(barcodeImg);
                img.scaleToFit(mmToPts(50), mmToPts(12));

                PdfPCell celdaBarcode = new PdfPCell(img);
                celdaBarcode.setBorder(Rectangle.NO_BORDER);
                celdaBarcode.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaBarcode.setPaddingTop(1);
                celdaBarcode.setPaddingBottom(1);
                contenido.addCell(celdaBarcode);
                codigoGeneradoExitosamente = true;
            } catch (Exception e) {
                // FALLBACK: Si no se puede generar el gráfico, mostrar texto prominente
                log.warn("Fallback a texto para código '{}' del producto {}: {}",
                        codigoBarra, producto.getId(), e.getMessage());
            }

            // Texto del código debajo (siempre se muestra)
            Font fontCodigoTexto = codigoGeneradoExitosamente ? fontCodigo :
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
            String textoMostrar = codigoGeneradoExitosamente ? codigoBarra : "COD: " + codigoBarra;

            PdfPCell celdaCodigo = new PdfPCell(new Phrase(textoMostrar, fontCodigoTexto));
            celdaCodigo.setBorder(Rectangle.NO_BORDER);
            celdaCodigo.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaCodigo.setPaddingBottom(1);
            if (!codigoGeneradoExitosamente) {
                celdaCodigo.setPaddingTop(8); // Más espacio si no hay imagen
            }
            contenido.addCell(celdaCodigo);
        } else {
            PdfPCell celdaSinCodigo = new PdfPCell(new Phrase("Sin código de barras", fontCodigo));
            celdaSinCodigo.setBorder(Rectangle.NO_BORDER);
            celdaSinCodigo.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaSinCodigo.setPaddingTop(5);
            contenido.addCell(celdaSinCodigo);
        }

        // NULL SAFETY: Precio con formato seguro
        String monedaSegura = java.util.Objects.toString(moneda, "S/");
        String precioFormateado;
        try {
            java.math.BigDecimal precioVenta = producto.getPrecioVenta();
            precioFormateado = monedaSegura + " " + (precioVenta != null ?
                    String.format("%.2f", precioVenta) : "0.00");
        } catch (Exception e) {
            log.warn("Error formateando precio del producto {}: {}", producto.getId(), e.getMessage());
            precioFormateado = monedaSegura + " 0.00";
        }

        PdfPCell celdaPrecio = new PdfPCell(new Phrase(precioFormateado, fontPrecio));
        celdaPrecio.setBorder(Rectangle.NO_BORDER);
        celdaPrecio.setHorizontalAlignment(Element.ALIGN_CENTER);
        celdaPrecio.setPaddingTop(2);
        celdaPrecio.setPaddingBottom(3);
        contenido.addCell(celdaPrecio);

        // Celda principal con borde punteado (guía de corte)
        PdfPCell celdaPrincipal = new PdfPCell(contenido);
        celdaPrincipal.setBorder(Rectangle.BOX);
        celdaPrincipal.setBorderColor(Color.LIGHT_GRAY);
        celdaPrincipal.setBorderWidth(0.5f);
        celdaPrincipal.setFixedHeight(mmToPts(ALTO_ETIQUETA_A4));
        celdaPrincipal.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celdaPrincipal.setPadding(2);

        return celdaPrincipal;
    }

    /**
     * Genera PDF en formato Ticket para impresora térmica.
     * BLINDADO: Maneja nulls y errores de código de barras gracefully.
     */
    private byte[] generarPdfTicket(List<Producto> productos, int cantidadPorProducto, Configuracion config)
            throws IOException, DocumentException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Determinar ancho según configuración (58mm o 80mm) con NULL SAFETY
        int anchoMm = (config != null && config.getAnchoTicketMm() != null) ? config.getAnchoTicketMm() : 80;
        float anchoTicket = anchoMm == 58 ? ANCHO_TICKET_58 : ANCHO_TICKET_80;

        // Calcular alto total: cada etiqueta ~28mm
        int totalEtiquetas = productos.size() * cantidadPorProducto;
        float altoEtiqueta = mmToPts(28);
        float altoTotal = totalEtiquetas * altoEtiqueta + mmToPts(10); // margen extra

        Rectangle pageSize = new Rectangle(anchoTicket, altoTotal);
        Document document = new Document(pageSize, 5, 5, 5, 5);

        PdfWriter.getInstance(document, baos);
        document.open();

        // NULL SAFETY: Moneda
        String moneda = (config != null && config.getFormatoMoneda() != null)
                ? config.getFormatoMoneda() : "S/";

        // Fuentes para ticket
        Font fontNombre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8, Color.BLACK);
        Font fontPrecio = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, Color.BLACK);
        Font fontCodigo = FontFactory.getFont(FontFactory.HELVETICA, 7, Color.BLACK);
        Font fontCodigoFallback = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 9, Color.BLACK);

        for (Producto producto : productos) {
            for (int i = 0; i < cantidadPorProducto; i++) {
                try {
                    // NULL SAFETY: Nombre truncado
                    String nombre = java.util.Objects.toString(producto.getNombre(), "Sin nombre");
                    if (nombre.length() > 25) {
                        nombre = nombre.substring(0, 22) + "...";
                    }

                    Paragraph pNombre = new Paragraph(nombre, fontNombre);
                    pNombre.setAlignment(Element.ALIGN_CENTER);
                    document.add(pNombre);

                    // Código de barras con FALLBACK
                    String codigoBarra = producto.getCodigoBarra();
                    if (codigoBarra != null && !codigoBarra.trim().isEmpty()) {
                        boolean imagenGenerada = false;

                        try {
                            byte[] barcodeImg = generarImagenCodigoBarras(codigoBarra, 150, 40);
                            Image img = Image.getInstance(barcodeImg);
                            img.scaleToFit(anchoTicket - 20, mmToPts(12));
                            img.setAlignment(Element.ALIGN_CENTER);
                            document.add(img);
                            imagenGenerada = true;
                        } catch (Exception e) {
                            log.warn("Fallback texto para ticket, código '{}': {}", codigoBarra, e.getMessage());
                        }

                        // Texto del código (siempre visible)
                        Font fontTexto = imagenGenerada ? fontCodigo : fontCodigoFallback;
                        String textoMostrar = imagenGenerada ? codigoBarra : "COD: " + codigoBarra;
                        Paragraph pCodigo = new Paragraph(textoMostrar, fontTexto);
                        pCodigo.setAlignment(Element.ALIGN_CENTER);
                        document.add(pCodigo);
                    }

                    // NULL SAFETY: Precio
                    String precioFormateado;
                    try {
                        java.math.BigDecimal precioVenta = producto.getPrecioVenta();
                        precioFormateado = moneda + " " + (precioVenta != null ?
                                String.format("%.2f", precioVenta) : "0.00");
                    } catch (Exception e) {
                        precioFormateado = moneda + " 0.00";
                    }

                    Paragraph pPrecio = new Paragraph(precioFormateado, fontPrecio);
                    pPrecio.setAlignment(Element.ALIGN_CENTER);
                    pPrecio.setSpacingAfter(5);
                    document.add(pPrecio);

                    // Línea separadora punteada
                    Paragraph separador = new Paragraph("- - - - - - - - - - - - - - -", fontCodigo);
                    separador.setAlignment(Element.ALIGN_CENTER);
                    separador.setSpacingAfter(3);
                    document.add(separador);

                } catch (Exception e) {
                    // Si falla un producto individual, loguear y continuar con el siguiente
                    log.error("Error procesando etiqueta ticket para producto {}: {}",
                            producto.getId(), e.getMessage());
                }
            }
        }

        document.close();
        return baos.toByteArray();
    }

    /**
     * Genera imagen PNG del código de barras usando barcode4j (Code128).
     * BLINDADO: Valida el código antes de generar y maneja errores gracefully.
     *
     * @param codigo Código a convertir en barras
     * @param ancho Ancho deseado (no usado directamente, controlado por moduleWidth)
     * @param alto Alto deseado (no usado directamente, controlado por barHeight)
     * @return byte[] con la imagen PNG del código de barras
     * @throws IOException Si hay error de I/O
     * @throws IllegalArgumentException Si el código es inválido para Code128
     */
    private byte[] generarImagenCodigoBarras(String codigo, int ancho, int alto) throws IOException {
        // Validación defensiva del código
        if (codigo == null || codigo.trim().isEmpty()) {
            throw new IllegalArgumentException("El código de barras no puede estar vacío");
        }

        // Limpiar el código: remover caracteres no imprimibles
        String codigoLimpio = codigo.trim().replaceAll("[^\\x20-\\x7E]", "");

        if (codigoLimpio.isEmpty()) {
            throw new IllegalArgumentException("El código de barras contiene solo caracteres inválidos");
        }

        // Code128 acepta caracteres ASCII 0-127, pero para mejor compatibilidad
        // limitamos a caracteres imprimibles estándar
        log.debug("Generando código de barras Code128 para: {}", codigoLimpio);

        Code128Bean bean = new Code128Bean();

        // Configuración del código de barras
        bean.setModuleWidth(0.21); // Ancho de módulo estándar
        bean.setBarHeight(8.0);    // Altura de las barras en mm
        bean.doQuietZone(false);   // Sin zona silenciosa (ya tenemos márgenes)
        bean.setFontSize(0);       // Sin texto debajo (lo ponemos aparte)

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try {
            // Generar imagen en memoria
            BitmapCanvasProvider canvas = new BitmapCanvasProvider(
                    baos, "image/png", 150, // DPI
                    BufferedImage.TYPE_BYTE_BINARY, false, 0);

            bean.generateBarcode(canvas, codigoLimpio);
            canvas.finish();

            return baos.toByteArray();
        } catch (Exception e) {
            log.error("Error generando imagen de código de barras para '{}': {}", codigoLimpio, e.getMessage());
            throw new IOException("No se pudo generar el código de barras: " + e.getMessage(), e);
        }
    }

    /**
     * Convierte milímetros a puntos PDF (1 punto = 1/72 pulgadas).
     */
    private float mmToPts(float mm) {
        return mm * 72f / 25.4f;
    }
}
