package com.libreria.sistema.service;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parser de texto OCR para extraer items de listas escolares.
 * Convierte el texto crudo de OCR en items estructurados con cantidad y descripción.
 */
@Service
@Slf4j
public class TextoListaParser {

    // Patrones para detectar cantidades al inicio de línea
    private static final Pattern PATRON_CANTIDAD_INICIO = Pattern.compile(
        "^\\s*(\\d+)\\s*[.\\-\\)\\s]+(.+)$",
        Pattern.CASE_INSENSITIVE
    );

    // Patrones para detectar cantidades con palabras
    private static final Pattern PATRON_CANTIDAD_PALABRA = Pattern.compile(
        "^\\s*(uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|" +
        "un|1|2|3|4|5|6|7|8|9|10|11|12)\\s+(.+)$",
        Pattern.CASE_INSENSITIVE
    );

    // Patrón para líneas que parecen títulos/encabezados
    private static final Pattern PATRON_TITULO = Pattern.compile(
        "^\\s*(lista|grado|nivel|colegio|año|materiales|utiles|escolar|primaria|secundaria).*$",
        Pattern.CASE_INSENSITIVE
    );

    // Patrón para líneas vacías o solo símbolos
    private static final Pattern PATRON_IGNORAR = Pattern.compile(
        "^[\\s\\-\\*\\.•○◦\\[\\]\\(\\)]+$"
    );

    /**
     * Resultado del parsing de un item.
     */
    @Data
    public static class ItemParseado {
        private String textoOriginal;
        private String textoNormalizado;
        private int cantidad;
        private int lineaNumero;
        private double confianza; // 0.0 - 1.0

        public ItemParseado(String textoOriginal, String textoNormalizado, int cantidad, int lineaNumero) {
            this.textoOriginal = textoOriginal;
            this.textoNormalizado = textoNormalizado;
            this.cantidad = cantidad;
            this.lineaNumero = lineaNumero;
            this.confianza = 1.0;
        }
    }

    /**
     * Parsea el texto completo de una lista escolar.
     *
     * @param textoOCR Texto crudo del OCR
     * @return Lista de items parseados
     */
    public List<ItemParseado> parsear(String textoOCR) {
        List<ItemParseado> items = new ArrayList<>();

        if (textoOCR == null || textoOCR.isBlank()) {
            return items;
        }

        String[] lineas = textoOCR.split("\\r?\\n");
        int numeroLinea = 0;

        for (String linea : lineas) {
            numeroLinea++;
            String lineaLimpia = limpiarLinea(linea);

            // Ignorar líneas vacías
            if (lineaLimpia.isEmpty()) {
                continue;
            }

            // Ignorar títulos y encabezados
            if (esTitulo(lineaLimpia)) {
                log.debug("Línea {} ignorada (título): {}", numeroLinea, linea);
                continue;
            }

            // Ignorar líneas con solo símbolos
            if (PATRON_IGNORAR.matcher(lineaLimpia).matches()) {
                continue;
            }

            // Intentar extraer cantidad e item
            ItemParseado item = extraerItem(linea, lineaLimpia, numeroLinea);
            if (item != null) {
                items.add(item);
                log.debug("Línea {} parseada: {} x{}", numeroLinea, item.getTextoNormalizado(), item.getCantidad());
            }
        }

        log.info("Texto parseado: {} items extraídos de {} líneas", items.size(), numeroLinea);
        return items;
    }

    /**
     * Limpia una línea de caracteres innecesarios.
     */
    private String limpiarLinea(String linea) {
        if (linea == null) return "";

        // Eliminar caracteres de viñeta comunes
        String limpia = linea.replaceAll("^[\\s\\-\\*•○◦►▶\\[\\]\\(\\)]+", "");

        // Eliminar espacios extra
        limpia = limpia.replaceAll("\\s+", " ").trim();

        return limpia;
    }

    /**
     * Verifica si una línea parece ser un título o encabezado.
     */
    private boolean esTitulo(String linea) {
        return PATRON_TITULO.matcher(linea).matches();
    }

    /**
     * Extrae un item con su cantidad de una línea.
     */
    private ItemParseado extraerItem(String lineaOriginal, String lineaLimpia, int numeroLinea) {
        int cantidad = 1;
        String descripcion = lineaLimpia;

        // Intentar extraer cantidad numérica al inicio
        Matcher matcherNumero = PATRON_CANTIDAD_INICIO.matcher(lineaLimpia);
        if (matcherNumero.matches()) {
            try {
                cantidad = Integer.parseInt(matcherNumero.group(1));
                descripcion = matcherNumero.group(2).trim();
            } catch (NumberFormatException e) {
                // Mantener cantidad = 1
            }
        } else {
            // Intentar con palabras (uno, dos, etc.)
            Matcher matcherPalabra = PATRON_CANTIDAD_PALABRA.matcher(lineaLimpia);
            if (matcherPalabra.matches()) {
                cantidad = palabraACantidad(matcherPalabra.group(1));
                descripcion = matcherPalabra.group(2).trim();
            }
        }

        // Validar que la descripción no esté vacía
        if (descripcion.isEmpty() || descripcion.length() < 3) {
            return null;
        }

        // Normalizar la descripción para búsqueda
        String descripcionNormalizada = normalizarParaBusqueda(descripcion);

        return new ItemParseado(lineaOriginal.trim(), descripcionNormalizada, cantidad, numeroLinea);
    }

    /**
     * Convierte palabra de cantidad a número.
     */
    private int palabraACantidad(String palabra) {
        if (palabra == null) return 1;

        return switch (palabra.toLowerCase()) {
            case "un", "uno", "una", "1" -> 1;
            case "dos", "2" -> 2;
            case "tres", "3" -> 3;
            case "cuatro", "4" -> 4;
            case "cinco", "5" -> 5;
            case "seis", "6" -> 6;
            case "siete", "7" -> 7;
            case "ocho", "8" -> 8;
            case "nueve", "9" -> 9;
            case "diez", "10" -> 10;
            case "11" -> 11;
            case "12" -> 12;
            default -> 1;
        };
    }

    /**
     * Normaliza el texto para mejorar la búsqueda de productos.
     */
    public String normalizarParaBusqueda(String texto) {
        if (texto == null) return "";

        // Convertir a minúsculas
        String resultado = texto.toLowerCase();

        // Eliminar acentos
        resultado = Normalizer.normalize(resultado, Normalizer.Form.NFD)
            .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // Reemplazar caracteres especiales
        resultado = resultado
            .replace("ñ", "n")
            .replace("Ñ", "n");

        // Eliminar caracteres no alfanuméricos excepto espacios
        resultado = resultado.replaceAll("[^a-z0-9\\s]", " ");

        // Normalizar espacios
        resultado = resultado.replaceAll("\\s+", " ").trim();

        // Expandir abreviaturas comunes
        resultado = expandirAbreviaturas(resultado);

        return resultado;
    }

    /**
     * Expande abreviaturas comunes en útiles escolares.
     */
    private String expandirAbreviaturas(String texto) {
        return texto
            .replaceAll("\\bcuad\\b", "cuaderno")
            .replaceAll("\\bcuads\\b", "cuadernos")
            .replaceAll("\\blap\\b", "lapicero")
            .replaceAll("\\blaps\\b", "lapiceros")
            .replaceAll("\\bplum\\b", "plumon")
            .replaceAll("\\bplums\\b", "plumones")
            .replaceAll("\\bcolrs\\b", "colores")
            .replaceAll("\\bfoldr\\b", "folder")
            .replaceAll("\\bfldrs\\b", "folders")
            .replaceAll("\\bgoma\\b", "goma")
            .replaceAll("\\btemp\\b", "tempera")
            .replaceAll("\\btemps\\b", "temperas")
            .replaceAll("\\besc\\b", "escolar")
            .replaceAll("\\btij\\b", "tijera")
            .replaceAll("\\bpeg\\b", "pegamento");
    }

    /**
     * Extrae palabras clave principales de un texto para búsqueda.
     * Elimina artículos y preposiciones comunes.
     */
    public List<String> extraerPalabrasClave(String texto) {
        List<String> palabrasClave = new ArrayList<>();

        if (texto == null || texto.isBlank()) {
            return palabrasClave;
        }

        String normalizado = normalizarParaBusqueda(texto);
        String[] palabras = normalizado.split("\\s+");

        // Palabras a ignorar (stopwords en español)
        List<String> stopwords = List.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "de", "del", "para", "por", "con", "sin", "en", "a",
            "y", "o", "e", "u", "que", "como", "tipo", "marca"
        );

        for (String palabra : palabras) {
            if (palabra.length() >= 3 && !stopwords.contains(palabra)) {
                palabrasClave.add(palabra);
            }
        }

        return palabrasClave;
    }
}
