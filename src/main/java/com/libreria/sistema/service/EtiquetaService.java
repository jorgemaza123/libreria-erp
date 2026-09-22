package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.EtiquetaPdfOpcionesDTO;
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
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private static final float MARGEN_A4 = 8f; // milimetros
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
        return generarPdfEtiquetas(productoIds, cantidadPorProducto, null);
    }

    public byte[] generarPdfEtiquetas(List<Long> productoIds,
                                      int cantidadPorProducto,
                                      EtiquetaPdfOpcionesDTO opcionesRequest) throws IOException, DocumentException {
        // Validación de entrada
        if (productoIds == null || productoIds.isEmpty()) {
            throw new IllegalArgumentException("Debe seleccionar al menos un producto");
        }

        Configuracion config = configuracionService.obtenerConfiguracion();
        EtiquetaOpciones opciones = EtiquetaOpciones.from(config, opcionesRequest, cantidadPorProducto);

        List<Producto> productos = ordenarSegunSolicitud(productoIds, productoRepository.findAllById(productoIds));

        if (productos.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron productos con los IDs proporcionados");
        }

        log.info("Generando PDF de etiquetas para {} productos en formato {}", productos.size(), opciones.formato);

        try {
            if ("TICKET".equalsIgnoreCase(opciones.formato)) {
                return generarPdfTicket(productos, opciones, config);
            } else {
                return generarPdfA4(productos, opciones, config);
            }
        } catch (Exception e) {
            log.error("Error fatal generando PDF de etiquetas: {}", e.getMessage(), e);
            throw new RuntimeException("Error al generar el PDF: " + e.getMessage(), e);
        }
    }

    private List<Producto> ordenarSegunSolicitud(List<Long> productoIds, List<Producto> productos) {
        Map<Long, Producto> porId = new LinkedHashMap<>();
        for (Producto producto : productos) {
            porId.put(producto.getId(), producto);
        }
        List<Producto> ordenados = new ArrayList<>();
        for (Long productoId : productoIds) {
            Producto producto = porId.get(productoId);
            if (producto != null) {
                ordenados.add(producto);
            }
        }
        return ordenados;
    }

    /**
     * Genera PDF en formato A4 con etiquetas en grid 3x8.
     * Diseñado para papel adhesivo estándar (ej. Avery L7160).
     */
    private byte[] generarPdfA4(List<Producto> productos, EtiquetaOpciones opciones, Configuracion config)
            throws IOException, DocumentException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        float margenHorizontal = mmToPts(opciones.margenHorizontalMm);
        float margenVertical = mmToPts(opciones.margenVerticalMm);
        float anchoDisponible = PageSize.A4.getWidth() - (margenHorizontal * 2);
        float altoDisponible = PageSize.A4.getHeight() - (margenVertical * 2);
        float anchoEtiqueta = mmToPts(opciones.etiquetaAnchoMm);
        float altoEtiqueta = mmToPts(opciones.etiquetaAltoMm);
        float anchoMaximoEtiqueta = anchoDisponible / opciones.columnasA4;
        float altoMaximoEtiqueta = altoDisponible / opciones.filasA4;
        if (anchoEtiqueta > anchoMaximoEtiqueta) {
            anchoEtiqueta = anchoMaximoEtiqueta;
        }
        if (altoEtiqueta > altoMaximoEtiqueta) {
            altoEtiqueta = altoMaximoEtiqueta;
        }

        // Página A4 con márgenes reducidos para maximizar etiquetas
        Document document = new Document(PageSize.A4,
                margenHorizontal, margenHorizontal,
                margenVertical, margenVertical);

        PdfWriter.getInstance(document, baos);
        document.open();

        float[] anchos = repetirAncho(opciones.columnasA4, anchoEtiqueta);
        String moneda = (config != null && config.getFormatoMoneda() != null) ? config.getFormatoMoneda() : "S/";
        int etiquetasPorPagina = opciones.capacidadA4();
        List<EtiquetaA4Item> etiquetas = expandirEtiquetasA4(productos, opciones);
        int saltarEtiquetasPrimeraPagina = Math.min(opciones.saltarEtiquetasA4, etiquetasPorPagina - 1);

        int indice = 0;
        while (indice < etiquetas.size()) {
            PdfPTable tabla = crearTablaA4(opciones.columnasA4, anchos);
            int etiquetasEnPagina = 0;

            if (indice == 0 && saltarEtiquetasPrimeraPagina > 0) {
                completarCeldasVaciasA4(tabla, saltarEtiquetasPrimeraPagina, altoEtiqueta);
                etiquetasEnPagina += saltarEtiquetasPrimeraPagina;
            }

            while (indice < etiquetas.size() && etiquetasEnPagina < etiquetasPorPagina) {
                EtiquetaA4Item etiqueta = etiquetas.get(indice);
                PdfPCell celda = crearCeldaEtiquetaA4(etiqueta.producto(), moneda, etiqueta.opciones(), altoEtiqueta);
                tabla.addCell(celda);
                etiquetasEnPagina++;
                indice++;
            }

            completarCeldasVaciasA4(tabla, etiquetasPorPagina - etiquetasEnPagina, altoEtiqueta);
            document.add(tabla);

            if (indice < etiquetas.size()) {
                document.newPage();
            }
        }

        document.close();
        return baos.toByteArray();
    }

    private List<EtiquetaA4Item> expandirEtiquetasA4(List<Producto> productos, EtiquetaOpciones opciones) {
        List<EtiquetaA4Item> etiquetas = new ArrayList<>();
        for (Producto producto : productos) {
            EtiquetaProductoOpciones productoOpciones = opciones.paraProducto(producto);
            for (int i = 0; i < productoOpciones.cantidad; i++) {
                etiquetas.add(new EtiquetaA4Item(producto, productoOpciones));
            }
        }
        return etiquetas;
    }

    private PdfPTable crearTablaA4(int columnas, float[] anchos) throws DocumentException {
        PdfPTable tabla = new PdfPTable(columnas);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(0);
        tabla.setSpacingAfter(0);
        tabla.setTotalWidth(anchos);
        tabla.setLockedWidth(true);
        return tabla;
    }

    private void completarCeldasVaciasA4(PdfPTable tabla, int celdasVacias, float altoEtiqueta) {
        for (int i = 0; i < celdasVacias; i++) {
            PdfPCell celdaVacia = new PdfPCell();
            celdaVacia.setBorder(Rectangle.NO_BORDER);
            celdaVacia.setFixedHeight(altoEtiqueta);
            tabla.addCell(celdaVacia);
        }
    }

    private float[] repetirAncho(int columnas, float anchoColumna) {
        float[] anchos = new float[columnas];
        for (int i = 0; i < columnas; i++) {
            anchos[i] = anchoColumna;
        }
        return anchos;
    }

    /**
     * Crea una celda de etiqueta para formato A4.
     * BLINDADO: Maneja nulls y errores de código de barras gracefully.
     */
    private PdfPCell crearCeldaEtiquetaA4(Producto producto,
                                          String moneda,
                                          EtiquetaProductoOpciones opciones,
                                          float altoEtiqueta) {
        PdfPTable contenido = new PdfPTable(1);
        contenido.setWidthPercentage(100);

        // Fuentes
        Font fontNombre = FontFactory.getFont(opciones.fuente, opciones.nombreFontSize, Font.BOLD, opciones.colorTexto);
        Font fontPrecio = FontFactory.getFont(opciones.fuente, opciones.precioFontSize, Font.BOLD, opciones.colorPrecio);
        Font fontCodigo = FontFactory.getFont(opciones.fuente, opciones.codigoFontSize, Font.NORMAL, opciones.colorCodigo);
        Font fontCategoria = FontFactory.getFont(opciones.fuente, opciones.categoriaFontSize, Font.NORMAL, opciones.colorTexto);

        // NULL SAFETY: Nombre del producto (truncado)
        String nombre = textoEtiqueta(opciones.nombrePersonalizado, producto.getNombre(), "Sin nombre");
        if (nombre.length() > opciones.maxNombreCaracteres) {
            nombre = nombre.substring(0, Math.max(1, opciones.maxNombreCaracteres - 3)) + "...";
        }
        if (opciones.mostrarNombre) {
            PdfPCell celdaNombre = new PdfPCell(new Phrase(nombre, fontNombre));
            celdaNombre.setBorder(Rectangle.NO_BORDER);
            celdaNombre.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaNombre.setPaddingTop(2);
            celdaNombre.setPaddingBottom(1);
            contenido.addCell(celdaNombre);
        }

        if (opciones.mostrarCategoria) {
            String categoria = textoEtiqueta(producto.getCategoria(), "", "SIN CATEGORIA");
            PdfPCell celdaCategoria = new PdfPCell(new Phrase(categoria, fontCategoria));
            celdaCategoria.setBorder(Rectangle.NO_BORDER);
            celdaCategoria.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaCategoria.setPaddingBottom(1);
            contenido.addCell(celdaCategoria);
        }

        // Código de barras con FALLBACK a texto plano si falla la generación
        String codigoBarra = producto.getCodigoBarra();
        if (codigoBarra != null && !codigoBarra.trim().isEmpty()) {
            boolean codigoGeneradoExitosamente = false;

            try {
                byte[] barcodeImg = generarImagenCodigoBarras(codigoBarra,
                        Math.round(opciones.codigoAnchoMm),
                        Math.round(opciones.codigoAltoMm));
                Image img = Image.getInstance(barcodeImg);
                img.scaleToFit(mmToPts(opciones.codigoAnchoMm), mmToPts(opciones.codigoAltoMm));

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
            if (opciones.mostrarCodigoTexto || !codigoGeneradoExitosamente) {
                Font fontCodigoTexto = codigoGeneradoExitosamente ? fontCodigo :
                        FontFactory.getFont(opciones.fuente, Math.max(opciones.codigoFontSize, 8f), Font.BOLD, opciones.colorCodigo);
                String textoMostrar = codigoGeneradoExitosamente ? codigoBarra : "COD: " + codigoBarra;

                PdfPCell celdaCodigo = new PdfPCell(new Phrase(textoMostrar, fontCodigoTexto));
                celdaCodigo.setBorder(Rectangle.NO_BORDER);
                celdaCodigo.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaCodigo.setPaddingBottom(1);
                if (!codigoGeneradoExitosamente) {
                    celdaCodigo.setPaddingTop(8); // Más espacio si no hay imagen
                }
                contenido.addCell(celdaCodigo);
            }
        } else {
            PdfPCell celdaSinCodigo = new PdfPCell(new Phrase("Sin código de barras", fontCodigo));
            celdaSinCodigo.setBorder(Rectangle.NO_BORDER);
            celdaSinCodigo.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaSinCodigo.setPaddingTop(5);
            contenido.addCell(celdaSinCodigo);
        }

        if (opciones.mostrarCodigoInterno) {
            String codigoInterno = textoEtiqueta(producto.getCodigoInterno(), "", "");
            if (!codigoInterno.isBlank()) {
                PdfPCell celdaCodigoInterno = new PdfPCell(new Phrase("INT: " + codigoInterno, fontCodigo));
                celdaCodigoInterno.setBorder(Rectangle.NO_BORDER);
                celdaCodigoInterno.setHorizontalAlignment(Element.ALIGN_CENTER);
                celdaCodigoInterno.setPaddingBottom(1);
                contenido.addCell(celdaCodigoInterno);
            }
        }

        // NULL SAFETY: Precio con formato seguro
        String monedaSegura = Objects.toString(moneda, "S/");
        String precioFormateado;
        try {
            BigDecimal precioVenta = opciones.precioPersonalizado != null
                    ? opciones.precioPersonalizado
                    : producto.getPrecioVenta();
            precioFormateado = monedaSegura + " " + (precioVenta != null ?
                    String.format("%.2f", precioVenta) : "0.00");
        } catch (Exception e) {
            log.warn("Error formateando precio del producto {}: {}", producto.getId(), e.getMessage());
            precioFormateado = monedaSegura + " 0.00";
        }

        if (opciones.mostrarPrecio) {
            PdfPCell celdaPrecio = new PdfPCell(new Phrase(precioFormateado, fontPrecio));
            celdaPrecio.setBorder(Rectangle.NO_BORDER);
            celdaPrecio.setHorizontalAlignment(Element.ALIGN_CENTER);
            celdaPrecio.setPaddingTop(2);
            celdaPrecio.setPaddingBottom(3);
            contenido.addCell(celdaPrecio);
        }

        // Celda principal con borde punteado (guía de corte)
        PdfPCell celdaPrincipal = new PdfPCell(contenido);
        celdaPrincipal.setBorder(opciones.mostrarBorde ? Rectangle.BOX : Rectangle.NO_BORDER);
        celdaPrincipal.setBorderColor(opciones.colorBorde);
        celdaPrincipal.setBorderWidth(0.5f);
        celdaPrincipal.setFixedHeight(Math.min(mmToPts(opciones.etiquetaAltoMm), altoEtiqueta));
        celdaPrincipal.setVerticalAlignment(Element.ALIGN_MIDDLE);
        celdaPrincipal.setPadding(2);

        return celdaPrincipal;
    }

    /**
     * Genera PDF en formato Ticket para impresora térmica.
     * BLINDADO: Maneja nulls y errores de código de barras gracefully.
     */
    private byte[] generarPdfTicket(List<Producto> productos, EtiquetaOpciones opciones, Configuracion config)
            throws IOException, DocumentException {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        // Determinar ancho según configuración (58mm o 80mm) con NULL SAFETY
        int anchoMm = opciones.anchoTicketMm;
        float anchoTicket = anchoMm == 58 ? ANCHO_TICKET_58 : ANCHO_TICKET_80;

        // Calcular alto total: cada etiqueta ~28mm
        int totalEtiquetas = productos.stream()
                .mapToInt(producto -> opciones.paraProducto(producto).cantidad)
                .sum();
        float altoEtiqueta = mmToPts(Math.max(28f, opciones.etiquetaAltoMm));
        float altoTotal = totalEtiquetas * altoEtiqueta + mmToPts(10); // margen extra

        Rectangle pageSize = new Rectangle(anchoTicket, altoTotal);
        Document document = new Document(pageSize, 5, 5, 5, 5);

        PdfWriter.getInstance(document, baos);
        document.open();

        // NULL SAFETY: Moneda
        String moneda = (config != null && config.getFormatoMoneda() != null)
                ? config.getFormatoMoneda() : "S/";

        for (Producto producto : productos) {
            EtiquetaProductoOpciones productoOpciones = opciones.paraProducto(producto);
            Font fontNombre = FontFactory.getFont(productoOpciones.fuente, productoOpciones.nombreFontSize, Font.BOLD, productoOpciones.colorTexto);
            Font fontPrecio = FontFactory.getFont(productoOpciones.fuente, productoOpciones.precioFontSize, Font.BOLD, productoOpciones.colorPrecio);
            Font fontCodigo = FontFactory.getFont(productoOpciones.fuente, productoOpciones.codigoFontSize, Font.NORMAL, productoOpciones.colorCodigo);
            Font fontCodigoFallback = FontFactory.getFont(productoOpciones.fuente, Math.max(productoOpciones.codigoFontSize, 9f), Font.BOLD, productoOpciones.colorCodigo);
            Font fontCategoria = FontFactory.getFont(productoOpciones.fuente, productoOpciones.categoriaFontSize, Font.NORMAL, productoOpciones.colorTexto);

            for (int i = 0; i < productoOpciones.cantidad; i++) {
                try {
                    // NULL SAFETY: Nombre truncado
                    String nombre = textoEtiqueta(productoOpciones.nombrePersonalizado, producto.getNombre(), "Sin nombre");
                    if (nombre.length() > productoOpciones.maxNombreCaracteresTicket) {
                        nombre = nombre.substring(0, Math.max(1, productoOpciones.maxNombreCaracteresTicket - 3)) + "...";
                    }

                    if (productoOpciones.mostrarNombre) {
                        Paragraph pNombre = new Paragraph(nombre, fontNombre);
                        pNombre.setAlignment(Element.ALIGN_CENTER);
                        document.add(pNombre);
                    }

                    if (productoOpciones.mostrarCategoria) {
                        Paragraph pCategoria = new Paragraph(textoEtiqueta(producto.getCategoria(), "", "SIN CATEGORIA"), fontCategoria);
                        pCategoria.setAlignment(Element.ALIGN_CENTER);
                        document.add(pCategoria);
                    }

                    // Código de barras con FALLBACK
                    String codigoBarra = producto.getCodigoBarra();
                    if (codigoBarra != null && !codigoBarra.trim().isEmpty()) {
                        boolean imagenGenerada = false;

                        try {
                            byte[] barcodeImg = generarImagenCodigoBarras(codigoBarra,
                                    Math.round(productoOpciones.codigoAnchoMm),
                                    Math.round(productoOpciones.codigoAltoMm));
                            Image img = Image.getInstance(barcodeImg);
                            img.scaleToFit(Math.min(anchoTicket - 20, mmToPts(productoOpciones.codigoAnchoMm)),
                                    mmToPts(productoOpciones.codigoAltoMm));
                            img.setAlignment(Element.ALIGN_CENTER);
                            document.add(img);
                            imagenGenerada = true;
                        } catch (Exception e) {
                            log.warn("Fallback texto para ticket, código '{}': {}", codigoBarra, e.getMessage());
                        }

                        // Texto del código (siempre visible)
                        if (productoOpciones.mostrarCodigoTexto || !imagenGenerada) {
                            Font fontTexto = imagenGenerada ? fontCodigo : fontCodigoFallback;
                            String textoMostrar = imagenGenerada ? codigoBarra : "COD: " + codigoBarra;
                            Paragraph pCodigo = new Paragraph(textoMostrar, fontTexto);
                            pCodigo.setAlignment(Element.ALIGN_CENTER);
                            document.add(pCodigo);
                        }
                    }

                    if (productoOpciones.mostrarCodigoInterno) {
                        String codigoInterno = textoEtiqueta(producto.getCodigoInterno(), "", "");
                        if (!codigoInterno.isBlank()) {
                            Paragraph pCodigoInterno = new Paragraph("INT: " + codigoInterno, fontCodigo);
                            pCodigoInterno.setAlignment(Element.ALIGN_CENTER);
                            document.add(pCodigoInterno);
                        }
                    }

                    // NULL SAFETY: Precio
                    String precioFormateado;
                    try {
                        BigDecimal precioVenta = productoOpciones.precioPersonalizado != null
                                ? productoOpciones.precioPersonalizado
                                : producto.getPrecioVenta();
                        precioFormateado = moneda + " " + (precioVenta != null ?
                                String.format("%.2f", precioVenta) : "0.00");
                    } catch (Exception e) {
                        precioFormateado = moneda + " 0.00";
                    }

                    if (productoOpciones.mostrarPrecio) {
                        Paragraph pPrecio = new Paragraph(precioFormateado, fontPrecio);
                        pPrecio.setAlignment(Element.ALIGN_CENTER);
                        pPrecio.setSpacingAfter(5);
                        document.add(pPrecio);
                    }

                    // Línea separadora punteada
                    if (productoOpciones.mostrarBorde) {
                        Paragraph separador = new Paragraph("- - - - - - - - - - - - - - -", fontCodigo);
                        separador.setAlignment(Element.ALIGN_CENTER);
                        separador.setSpacingAfter(3);
                        document.add(separador);
                    }

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
        bean.setBarHeight(Math.max(6, Math.min(22, alto)));    // Altura de las barras en mm
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

    private String textoEtiqueta(String preferido, String alterno, String fallback) {
        if (preferido != null && !preferido.trim().isEmpty()) {
            return preferido.trim();
        }
        if (alterno != null && !alterno.trim().isEmpty()) {
            return alterno.trim();
        }
        return fallback != null ? fallback : "";
    }

    private static boolean boolOrDefault(Boolean valor, boolean defecto) {
        return valor != null ? valor : defecto;
    }

    private static int intOrDefault(Integer valor, int defecto, int minimo, int maximo) {
        int normalizado = valor != null ? valor : defecto;
        return Math.max(minimo, Math.min(maximo, normalizado));
    }

    private static float decimalOrDefault(BigDecimal valor, float defecto, float minimo, float maximo) {
        float normalizado = valor != null ? valor.floatValue() : defecto;
        return Math.max(minimo, Math.min(maximo, normalizado));
    }

    private static Color colorSeguro(String valor, Color defecto) {
        if (valor == null || valor.trim().isEmpty()) {
            return defecto;
        }

        String limpio = valor.trim();
        if (!limpio.startsWith("#")) {
            limpio = "#" + limpio;
        }

        try {
            return Color.decode(limpio);
        } catch (NumberFormatException ex) {
            return defecto;
        }
    }

    private static String fuenteSegura(String fuente) {
        if (fuente == null || fuente.trim().isEmpty()) {
            return FontFactory.HELVETICA;
        }

        String normalizada = fuente.trim().toUpperCase();
        if ("COURIER".equals(normalizada)) {
            return FontFactory.COURIER;
        }
        if ("TIMES".equals(normalizada) || "TIMES_ROMAN".equals(normalizada)) {
            return FontFactory.TIMES_ROMAN;
        }
        return FontFactory.HELVETICA;
    }

    private static String formatoSeguro(String formato, Configuracion config) {
        String solicitado = formato;
        if (solicitado == null || solicitado.trim().isEmpty()) {
            solicitado = config != null ? config.getFormatoImpresion() : null;
        }
        if (solicitado == null || solicitado.trim().isEmpty()) {
            return "A4";
        }
        return "TICKET".equalsIgnoreCase(solicitado) ? "TICKET" : "A4";
    }

    private record EtiquetaA4Item(Producto producto, EtiquetaProductoOpciones opciones) {
    }

    private static class EtiquetaOpciones {
        String formato;
        String plantilla;
        int anchoTicketMm;
        int cantidad;
        int columnasA4;
        int filasA4;
        int maxNombreCaracteres;
        int maxNombreCaracteresTicket;
        float nombreFontSize;
        float precioFontSize;
        float codigoFontSize;
        float categoriaFontSize;
        float codigoAnchoMm;
        float codigoAltoMm;
        float etiquetaAnchoMm;
        float etiquetaAltoMm;
        float margenHorizontalMm;
        float margenVerticalMm;
        int saltarEtiquetasA4;
        boolean mostrarNombre;
        boolean mostrarPrecio;
        boolean mostrarCodigoTexto;
        boolean mostrarCategoria;
        boolean mostrarCodigoInterno;
        boolean mostrarBorde;
        String fuente;
        Color colorTexto;
        Color colorPrecio;
        Color colorCodigo;
        Color colorBorde;
        Map<Long, EtiquetaPdfOpcionesDTO.ProductoEtiquetaOverrideDTO> overrides = new LinkedHashMap<>();

        static EtiquetaOpciones from(Configuracion config,
                                     EtiquetaPdfOpcionesDTO request,
                                     int cantidadPorProducto) {
            EtiquetaOpciones opciones = new EtiquetaOpciones();
            opciones.formato = formatoSeguro(request != null ? request.getFormato() : null, config);
            opciones.plantilla = "UTIL_CHICO";
            opciones.anchoTicketMm = intOrDefault(request != null ? request.getAnchoTicketMm() : null,
                    config != null && config.getAnchoTicketMm() != null ? config.getAnchoTicketMm() : 80,
                    58, 80);
            if (opciones.anchoTicketMm != 58) {
                opciones.anchoTicketMm = 80;
            }

            opciones.cantidad = intOrDefault(request != null ? request.getCantidad() : null,
                    cantidadPorProducto > 0 ? cantidadPorProducto : 1,
                    1, 100);
            opciones.columnasA4 = COLUMNAS_A4;
            opciones.filasA4 = FILAS_A4;
            opciones.maxNombreCaracteres = 30;
            opciones.maxNombreCaracteresTicket = 25;
            opciones.nombreFontSize = 7f;
            opciones.precioFontSize = 12f;
            opciones.codigoFontSize = 6f;
            opciones.categoriaFontSize = 5.5f;
            opciones.codigoAnchoMm = 50f;
            opciones.codigoAltoMm = 12f;
            opciones.etiquetaAnchoMm = ANCHO_ETIQUETA_A4;
            opciones.etiquetaAltoMm = ALTO_ETIQUETA_A4;
            opciones.margenHorizontalMm = MARGEN_A4;
            opciones.margenVerticalMm = MARGEN_A4;
            opciones.mostrarNombre = true;
            opciones.mostrarPrecio = true;
            opciones.mostrarCodigoTexto = true;
            opciones.mostrarCategoria = false;
            opciones.mostrarCodigoInterno = false;
            opciones.mostrarBorde = true;
            opciones.fuente = FontFactory.HELVETICA;
            opciones.colorTexto = new Color(17, 24, 39);
            opciones.colorPrecio = new Color(17, 24, 39);
            opciones.colorCodigo = new Color(55, 65, 81);
            opciones.colorBorde = new Color(209, 213, 219);

            if (request != null) {
                opciones.aplicarPlantilla(request.getPlantilla());
                opciones.plantilla = normalizarPlantilla(request.getPlantilla());

                opciones.mostrarNombre = boolOrDefault(request.getMostrarNombre(), opciones.mostrarNombre);
                opciones.mostrarPrecio = boolOrDefault(request.getMostrarPrecio(), opciones.mostrarPrecio);
                opciones.mostrarCodigoTexto = boolOrDefault(request.getMostrarCodigoTexto(), opciones.mostrarCodigoTexto);
                opciones.mostrarCategoria = boolOrDefault(request.getMostrarCategoria(), opciones.mostrarCategoria);
                opciones.mostrarCodigoInterno = boolOrDefault(request.getMostrarCodigoInterno(), opciones.mostrarCodigoInterno);
                opciones.mostrarBorde = boolOrDefault(request.getMostrarBorde(), opciones.mostrarBorde);

                opciones.fuente = fuenteSegura(request.getFuente());
                opciones.colorTexto = colorSeguro(request.getColorTexto(), opciones.colorTexto);
                opciones.colorPrecio = colorSeguro(request.getColorPrecio(), opciones.colorPrecio);
                opciones.colorCodigo = colorSeguro(request.getColorCodigo(), opciones.colorCodigo);
                opciones.colorBorde = colorSeguro(request.getColorBorde(), opciones.colorBorde);

                opciones.nombreFontSize = decimalOrDefault(request.getNombreFontSize(), opciones.nombreFontSize, 4f, 18f);
                opciones.precioFontSize = decimalOrDefault(request.getPrecioFontSize(), opciones.precioFontSize, 5f, 26f);
                opciones.codigoFontSize = decimalOrDefault(request.getCodigoFontSize(), opciones.codigoFontSize, 4f, 14f);
                opciones.categoriaFontSize = decimalOrDefault(request.getCategoriaFontSize(), opciones.categoriaFontSize, 4f, 12f);
                opciones.codigoAnchoMm = decimalOrDefault(request.getCodigoAnchoMm(), opciones.codigoAnchoMm, 20f, 80f);
                opciones.codigoAltoMm = decimalOrDefault(request.getCodigoAltoMm(), opciones.codigoAltoMm, 6f, 24f);
                opciones.etiquetaAnchoMm = decimalOrDefault(request.getEtiquetaAnchoMm(), opciones.etiquetaAnchoMm, 25f, 90f);
                opciones.etiquetaAltoMm = decimalOrDefault(request.getEtiquetaAltoMm(), opciones.etiquetaAltoMm, 18f, 70f);
                opciones.margenHorizontalMm = decimalOrDefault(request.getMargenHorizontalMm(), opciones.margenHorizontalMm, 2f, 30f);
                opciones.margenVerticalMm = decimalOrDefault(request.getMargenVerticalMm(), opciones.margenVerticalMm, 2f, 30f);
                opciones.columnasA4 = intOrDefault(request.getColumnasA4(), opciones.columnasA4, 1, 5);
                opciones.filasA4 = intOrDefault(request.getFilasA4(), opciones.filasA4, 1, 12);
                opciones.saltarEtiquetasA4 = intOrDefault(request.getSaltarEtiquetasA4(), 0, 0, opciones.capacidadA4() - 1);

                if (request.getProductos() != null) {
                    for (EtiquetaPdfOpcionesDTO.ProductoEtiquetaOverrideDTO override : request.getProductos()) {
                        if (override != null && override.getProductoId() != null) {
                            opciones.overrides.put(override.getProductoId(), override);
                        }
                    }
                }
            }

            return opciones;
        }

        int capacidadA4() {
            return Math.max(1, columnasA4 * filasA4);
        }

        EtiquetaProductoOpciones paraProducto(Producto producto) {
            EtiquetaProductoOpciones productoOpciones = EtiquetaProductoOpciones.from(this);
            if (producto == null || producto.getId() == null) {
                return productoOpciones;
            }

            EtiquetaPdfOpcionesDTO.ProductoEtiquetaOverrideDTO override = overrides.get(producto.getId());
            if (override == null) {
                return productoOpciones;
            }

            if (override.getPlantilla() != null && !override.getPlantilla().trim().isEmpty()
                    && !"GLOBAL".equalsIgnoreCase(override.getPlantilla())) {
                productoOpciones.aplicarPlantilla(override.getPlantilla());
            }

            productoOpciones.cantidad = intOrDefault(override.getCantidad(), productoOpciones.cantidad, 1, 100);
            productoOpciones.nombrePersonalizado = override.getNombrePersonalizado();
            productoOpciones.precioPersonalizado = override.getPrecioPersonalizado();
            productoOpciones.mostrarNombre = boolOrDefault(override.getMostrarNombre(), productoOpciones.mostrarNombre);
            productoOpciones.mostrarPrecio = boolOrDefault(override.getMostrarPrecio(), productoOpciones.mostrarPrecio);
            productoOpciones.mostrarCategoria = boolOrDefault(override.getMostrarCategoria(), productoOpciones.mostrarCategoria);
            productoOpciones.mostrarCodigoInterno = boolOrDefault(override.getMostrarCodigoInterno(), productoOpciones.mostrarCodigoInterno);
            productoOpciones.mostrarCodigoTexto = boolOrDefault(override.getMostrarCodigoTexto(), productoOpciones.mostrarCodigoTexto);
            productoOpciones.mostrarBorde = boolOrDefault(override.getMostrarBorde(), productoOpciones.mostrarBorde);
            if (override.getFuente() != null && !override.getFuente().trim().isEmpty()) {
                productoOpciones.fuente = fuenteSegura(override.getFuente());
            }
            productoOpciones.nombreFontSize = decimalOrDefault(override.getNombreFontSize(), productoOpciones.nombreFontSize, 4f, 18f);
            productoOpciones.precioFontSize = decimalOrDefault(override.getPrecioFontSize(), productoOpciones.precioFontSize, 5f, 26f);
            productoOpciones.codigoFontSize = decimalOrDefault(override.getCodigoFontSize(), productoOpciones.codigoFontSize, 4f, 14f);
            productoOpciones.categoriaFontSize = decimalOrDefault(override.getCategoriaFontSize(), productoOpciones.categoriaFontSize, 4f, 12f);
            productoOpciones.codigoAnchoMm = decimalOrDefault(override.getCodigoAnchoMm(), productoOpciones.codigoAnchoMm, 20f, 80f);
            productoOpciones.codigoAltoMm = decimalOrDefault(override.getCodigoAltoMm(), productoOpciones.codigoAltoMm, 6f, 24f);
            productoOpciones.etiquetaAltoMm = decimalOrDefault(override.getEtiquetaAltoMm(), productoOpciones.etiquetaAltoMm, 18f, 70f);
            productoOpciones.colorTexto = colorSeguro(override.getColorTexto(), productoOpciones.colorTexto);
            productoOpciones.colorPrecio = colorSeguro(override.getColorPrecio(), productoOpciones.colorPrecio);
            productoOpciones.colorCodigo = colorSeguro(override.getColorCodigo(), productoOpciones.colorCodigo);
            productoOpciones.colorBorde = colorSeguro(override.getColorBorde(), productoOpciones.colorBorde);
            return productoOpciones;
        }

        private void aplicarPlantilla(String plantillaRequest) {
            String plantillaNormalizada = normalizarPlantilla(plantillaRequest);
            switch (plantillaNormalizada) {
                case "JUGUETE_GRANDE":
                    nombreFontSize = 8f;
                    precioFontSize = 14f;
                    codigoFontSize = 6f;
                    categoriaFontSize = 5.5f;
                    codigoAnchoMm = 54f;
                    codigoAltoMm = 13f;
                    etiquetaAltoMm = 38f;
                    filasA4 = 7;
                    mostrarPrecio = true;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = false;
                    break;
                case "SIN_PRECIO":
                    nombreFontSize = 8f;
                    precioFontSize = 10f;
                    codigoFontSize = 6f;
                    categoriaFontSize = 5.5f;
                    codigoAnchoMm = 52f;
                    codigoAltoMm = 14f;
                    etiquetaAltoMm = 33.9f;
                    mostrarPrecio = false;
                    mostrarCategoria = true;
                    mostrarCodigoInterno = false;
                    break;
                case "SOLO_CODIGO":
                    nombreFontSize = 6f;
                    precioFontSize = 10f;
                    codigoFontSize = 7f;
                    categoriaFontSize = 5f;
                    codigoAnchoMm = 56f;
                    codigoAltoMm = 16f;
                    etiquetaAltoMm = 28f;
                    mostrarNombre = false;
                    mostrarPrecio = false;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = true;
                    mostrarCodigoTexto = true;
                    break;
                case "UTIL_CHICO":
                default:
                    nombreFontSize = 7f;
                    precioFontSize = 12f;
                    codigoFontSize = 6f;
                    categoriaFontSize = 5.5f;
                    codigoAnchoMm = 50f;
                    codigoAltoMm = 12f;
                    etiquetaAnchoMm = ANCHO_ETIQUETA_A4;
                    etiquetaAltoMm = ALTO_ETIQUETA_A4;
                    columnasA4 = COLUMNAS_A4;
                    filasA4 = FILAS_A4;
                    mostrarNombre = true;
                    mostrarPrecio = true;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = false;
                    mostrarCodigoTexto = true;
                    break;
            }
        }

        private static String normalizarPlantilla(String plantilla) {
            if (plantilla == null || plantilla.trim().isEmpty()) {
                return "UTIL_CHICO";
            }
            String normalizada = plantilla.trim().toUpperCase();
            if ("JUGUETE_GRANDE".equals(normalizada)
                    || "SIN_PRECIO".equals(normalizada)
                    || "SOLO_CODIGO".equals(normalizada)) {
                return normalizada;
            }
            return "UTIL_CHICO";
        }
    }

    private static class EtiquetaProductoOpciones {
        int cantidad;
        int maxNombreCaracteres;
        int maxNombreCaracteresTicket;
        float nombreFontSize;
        float precioFontSize;
        float codigoFontSize;
        float categoriaFontSize;
        float codigoAnchoMm;
        float codigoAltoMm;
        float etiquetaAltoMm;
        boolean mostrarNombre;
        boolean mostrarPrecio;
        boolean mostrarCodigoTexto;
        boolean mostrarCategoria;
        boolean mostrarCodigoInterno;
        boolean mostrarBorde;
        String fuente;
        String nombrePersonalizado;
        BigDecimal precioPersonalizado;
        Color colorTexto;
        Color colorPrecio;
        Color colorCodigo;
        Color colorBorde;

        static EtiquetaProductoOpciones from(EtiquetaOpciones opciones) {
            EtiquetaProductoOpciones productoOpciones = new EtiquetaProductoOpciones();
            productoOpciones.cantidad = opciones.cantidad;
            productoOpciones.maxNombreCaracteres = opciones.maxNombreCaracteres;
            productoOpciones.maxNombreCaracteresTicket = opciones.maxNombreCaracteresTicket;
            productoOpciones.nombreFontSize = opciones.nombreFontSize;
            productoOpciones.precioFontSize = opciones.precioFontSize;
            productoOpciones.codigoFontSize = opciones.codigoFontSize;
            productoOpciones.categoriaFontSize = opciones.categoriaFontSize;
            productoOpciones.codigoAnchoMm = opciones.codigoAnchoMm;
            productoOpciones.codigoAltoMm = opciones.codigoAltoMm;
            productoOpciones.etiquetaAltoMm = opciones.etiquetaAltoMm;
            productoOpciones.mostrarNombre = opciones.mostrarNombre;
            productoOpciones.mostrarPrecio = opciones.mostrarPrecio;
            productoOpciones.mostrarCodigoTexto = opciones.mostrarCodigoTexto;
            productoOpciones.mostrarCategoria = opciones.mostrarCategoria;
            productoOpciones.mostrarCodigoInterno = opciones.mostrarCodigoInterno;
            productoOpciones.mostrarBorde = opciones.mostrarBorde;
            productoOpciones.fuente = opciones.fuente;
            productoOpciones.colorTexto = opciones.colorTexto;
            productoOpciones.colorPrecio = opciones.colorPrecio;
            productoOpciones.colorCodigo = opciones.colorCodigo;
            productoOpciones.colorBorde = opciones.colorBorde;
            return productoOpciones;
        }

        private void aplicarPlantilla(String plantillaRequest) {
            String plantillaNormalizada = EtiquetaOpciones.normalizarPlantilla(plantillaRequest);
            switch (plantillaNormalizada) {
                case "JUGUETE_GRANDE":
                    nombreFontSize = 8f;
                    precioFontSize = 14f;
                    codigoFontSize = 6f;
                    codigoAnchoMm = 54f;
                    codigoAltoMm = 13f;
                    etiquetaAltoMm = 38f;
                    mostrarPrecio = true;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = false;
                    break;
                case "SIN_PRECIO":
                    nombreFontSize = 8f;
                    precioFontSize = 10f;
                    codigoFontSize = 6f;
                    codigoAnchoMm = 52f;
                    codigoAltoMm = 14f;
                    mostrarPrecio = false;
                    mostrarCategoria = true;
                    mostrarCodigoInterno = false;
                    break;
                case "SOLO_CODIGO":
                    nombreFontSize = 6f;
                    precioFontSize = 10f;
                    codigoFontSize = 7f;
                    codigoAnchoMm = 56f;
                    codigoAltoMm = 16f;
                    etiquetaAltoMm = 28f;
                    mostrarNombre = false;
                    mostrarPrecio = false;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = true;
                    mostrarCodigoTexto = true;
                    break;
                case "UTIL_CHICO":
                default:
                    nombreFontSize = 7f;
                    precioFontSize = 12f;
                    codigoFontSize = 6f;
                    codigoAnchoMm = 50f;
                    codigoAltoMm = 12f;
                    etiquetaAltoMm = ALTO_ETIQUETA_A4;
                    mostrarNombre = true;
                    mostrarPrecio = true;
                    mostrarCategoria = false;
                    mostrarCodigoInterno = false;
                    mostrarCodigoTexto = true;
                    break;
            }
        }
    }

    /**
     * Convierte milímetros a puntos PDF (1 punto = 1/72 pulgadas).
     */
    private float mmToPts(float mm) {
        return mm * 72f / 25.4f;
    }
}
