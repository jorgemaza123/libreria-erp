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

    // Umbral: números mayores a este valor son casi siempre especificación del producto,
    // no unidades a comprar (ej: "100 hojas", "24 colores", "50 páginas").
    private static final int UMBRAL_MAX_CANTIDAD = 5;

    // Palabras que indican que el número es una especificación del producto, no cantidad de compra.
    // Ejemplo: "24 colores" → colores es spec word → cantidadSolicitada=1, búsqueda="24 colores"
    private static final java.util.Set<String> SPEC_WORDS = java.util.Set.of(
        "hojas", "paginas", "colores", "cm", "mm", "ml", "gr", "grs",
        "gramos", "metros", "litros", "piezas", "plumones", "marcadores",
        "lineas", "cuadros", "unidades"
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

        // true → el número al inicio era especificación del producto (ej: "100 hojas"),
        //        cantidadSolicitada fue forzada a 1 y el número quedó en textoNormalizado.
        private boolean esEspecificacion = false;

        // true → caso ambiguo: número ≤ UMBRAL pero primera palabra es spec word
        //        (ej: "3 colores"). El sistema eligió cantidad=1, pero puede ser incorrecto.
        private boolean alertaCantidad = false;

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
     * Determina si el número al inicio de línea es una especificación del producto
     * (no una cantidad de compra).
     *
     * Regla 1: número > UMBRAL_MAX_CANTIDAD (5) → casi siempre es spec
     *          ("100 hojas", "24 colores", "50 páginas")
     * Regla 2: primera palabra de la descripción es una spec word
     *          ("3 colores", "2 hojas", "1 cm")
     *
     * Cuando devuelve true: cantidadSolicitada=1, el número queda en el texto de búsqueda.
     * Principio: equivocarse con cantidad=1 es corregible; cantidad=100 genera daño financiero.
     */
    private boolean esCantidadEspecificacion(int numero, String descripcion) {
        if (numero > UMBRAL_MAX_CANTIDAD) {
            return true;
        }
        if (descripcion != null && !descripcion.isBlank()) {
            String primeraPalabra = descripcion.trim().toLowerCase().split("\\s+")[0];
            primeraPalabra = Normalizer.normalize(primeraPalabra, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .replaceAll("[^a-z]", "");
            if (SPEC_WORDS.contains(primeraPalabra)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extrae un item con su cantidad de una línea.
     * Aplica lógica conservadora: ante ambigüedad, cantidadSolicitada = 1.
     */
    private ItemParseado extraerItem(String lineaOriginal, String lineaLimpia, int numeroLinea) {
        int cantidad = 1;
        String descripcion = lineaLimpia;
        boolean esEspecificacion = false;
        boolean alertaCantidad = false;

        // Intentar extraer cantidad numérica al inicio
        Matcher matcherNumero = PATRON_CANTIDAD_INICIO.matcher(lineaLimpia);
        if (matcherNumero.matches()) {
            try {
                int numeroParsed = Integer.parseInt(matcherNumero.group(1));
                String descripcionExtraida = matcherNumero.group(2).trim();

                if (esCantidadEspecificacion(numeroParsed, descripcionExtraida)) {
                    // El número es especificación del producto.
                    // cantidad=1, incluir el número en el texto para búsqueda más precisa.
                    cantidad = 1;
                    descripcion = lineaLimpia; // línea completa con el número
                    esEspecificacion = true;
                    // Marcar alerta solo cuando el número es pequeño (ambigüedad real)
                    alertaCantidad = (numeroParsed <= UMBRAL_MAX_CANTIDAD);
                } else {
                    // Número inequívocamente es cantidad de compra
                    cantidad = numeroParsed;
                    descripcion = descripcionExtraida;
                }
            } catch (NumberFormatException e) {
                // Mantener cantidad = 1 y descripcion = lineaLimpia
            }
        } else {
            // Intentar con palabras (uno, dos, etc.) — estos son siempre cantidad de compra
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

        ItemParseado item = new ItemParseado(lineaOriginal.trim(), descripcionNormalizada, cantidad, numeroLinea);
        item.setEsEspecificacion(esEspecificacion);
        item.setAlertaCantidad(alertaCantidad);
        return item;
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

        // Expandir sinonimos escolares para matching flexible
        resultado = expandirSinonimosEscolares(resultado);

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
     * Expande sinonimos y nombres genericos comunes en utiles escolares.
     * Normaliza marcas usadas como generico y variaciones regionales.
     * Solo se usa para mejorar el matching de listas escolares.
     */
    private String expandirSinonimosEscolares(String texto) {
        return texto
            // Marcas usadas como generico
            .replaceAll("\\bdiurex\\b", "cinta adhesiva")
            .replaceAll("\\bdurex\\b", "cinta adhesiva")
            .replaceAll("\\bscotch\\b", "cinta adhesiva")
            .replaceAll("\\bliquid paper\\b", "corrector")
            .replaceAll("\\bliquid\\b", "corrector")
            .replaceAll("\\btipex\\b", "corrector")
            .replaceAll("\\btip-ex\\b", "corrector")
            .replaceAll("\\buhupor\\b", "pegamento barra")
            .replaceAll("\\buhu\\b", "pegamento")
            .replaceAll("\\bfaber castell\\b", "faber")
            .replaceAll("\\bfaber-castell\\b", "faber")
            .replaceAll("\\bartesco\\b", "artesco")
            .replaceAll("\\bstabilo\\b", "stabilo")
            .replaceAll("\\bpelikan\\b", "pelikan")
            // Variaciones regionales / sinonimos
            .replaceAll("\\btajador\\b", "sacapuntas")
            .replaceAll("\\btajalapiz\\b", "sacapuntas")
            .replaceAll("\\btaja lapiz\\b", "sacapuntas")
            .replaceAll("\\bboligrafo\\b", "lapicero")
            .replaceAll("\\besfero\\b", "lapicero")
            .replaceAll("\\bbirome\\b", "lapicero")
            .replaceAll("\\blibreta\\b", "cuaderno")
            .replaceAll("\\bblock\\b", "cuaderno")
            .replaceAll("\\bbloc\\b", "cuaderno")
            .replaceAll("\\bmarcador\\b", "plumon")
            .replaceAll("\\bmarcadores\\b", "plumones")
            .replaceAll("\\bsubrayador\\b", "resaltador")
            .replaceAll("\\bhighlighter\\b", "resaltador")
            .replaceAll("\\bcrayola\\b", "crayon")
            .replaceAll("\\bcrayolas\\b", "crayones")
            .replaceAll("\\bpasteles\\b", "crayones")
            .replaceAll("\\bacuarela\\b", "tempera")
            .replaceAll("\\bacuarelas\\b", "temperas")
            .replaceAll("\\bgoma eva\\b", "foami")
            .replaceAll("\\bmicroporoso\\b", "foami")
            .replaceAll("\\bfomi\\b", "foami")
            .replaceAll("\\bsilicon\\b", "silicona")
            .replaceAll("\\bmasking\\b", "cinta masking")
            .replaceAll("\\bmaskingtape\\b", "cinta masking")
            .replaceAll("\\bmasking tape\\b", "cinta masking")
            .replaceAll("\\bpapelote\\b", "papelografo")
            .replaceAll("\\bpapel sabana\\b", "papelografo")
            // Utiles comunes - plurales y variantes
            .replaceAll("\\blapices\\b", "lapiz")
            .replaceAll("\\blapiceros\\b", "lapicero")
            .replaceAll("\\bcuadernos\\b", "cuaderno")
            .replaceAll("\\bfolders\\b", "folder")
            .replaceAll("\\bsobres\\b", "sobre")
            .replaceAll("\\btijeras\\b", "tijera")
            .replaceAll("\\bborradores\\b", "borrador")
            .replaceAll("\\breglas\\b", "regla")
            .replaceAll("\\bcolores\\b", "color")
            .replaceAll("\\btemperas\\b", "tempera")
            .replaceAll("\\bplumones\\b", "plumon")
            .replaceAll("\\bcartulinas\\b", "cartulina")
            // Especificaciones comunes (normalizar)
            .replaceAll("\\bcuadriculado\\b", "cuadriculado")
            .replaceAll("\\brayado\\b", "rayado")
            .replaceAll("\\bdoble raya\\b", "doble raya")
            .replaceAll("\\btriple raya\\b", "triple raya")
            .replaceAll("\\bgrapado\\b", "grapado")
            .replaceAll("\\banillado\\b", "anillado")
            .replaceAll("\\bespiral\\b", "espiral");
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
