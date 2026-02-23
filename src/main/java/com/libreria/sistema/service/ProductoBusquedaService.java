package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoBusquedaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de búsqueda avanzada de productos - "OMNIBUSCADOR".
 *
 * Estrategia de búsqueda (en cascada, automática):
 *  1. Código exacto  → para escáner de barras (bypass total de FTS/ILIKE)
 *  2. FTS español    → plurales, stemming, acentos nativos (search_vector)
 *  3. ILIKE tokenizado → fallback garantizado si FTS no retorna resultados
 *
 * El fallback a ILIKE es automático (try/catch). No requiere intervención manual.
 * Términos puramente numéricos van directo a ILIKE para proteger barcodes cortos.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductoBusquedaService {

    private final ProductoBusquedaRepository busquedaRepository;

    /**
     * Helper que aísla FTS y Fuzzy en transacciones REQUIRES_NEW.
     * Previene que un fallo en estas queries envenene la transacción principal
     * y bloquee el fallback ILIKE (SQLState 25P02 "transaction aborted").
     */
    private final BusquedaAvanzadaHelper busquedaAvanzadaHelper;

    // Límites
    private static final int LIMITE_AUTOCOMPLETE = 10;
    private static final int LIMITE_BUSQUEDA = 50;
    private static final int MIN_CARACTERES_BUSQUEDA = 1;
    private static final int MAX_TOKENS = 4;

    // Búsqueda difusa (fuzzy / pg_trgm similarity)
    // similarity() solo se ejecuta si el término tiene al menos MIN_CHARS_FUZZY caracteres
    // (términos cortos producen falsos positivos con umbral bajo)
    private static final int MIN_CHARS_FUZZY = 3;
    private static final double FUZZY_THRESHOLD = 0.15; // 0=todo, 1=exacto; 0.15 tolera typos razonables

    /**
     * Búsqueda principal del Omnibuscador.
     * Detecta automáticamente si es un código exacto o una búsqueda de texto.
     * Usa tokenización para búsquedas de múltiples palabras.
     *
     * @param termino Texto de búsqueda del usuario
     * @return Lista de productos ordenados por relevancia
     */
    public List<Producto> buscar(String termino) {
        return buscar(termino, LIMITE_BUSQUEDA);
    }

    /**
     * Búsqueda principal con límite personalizado.
     * Usado por POS, Ventas, Cotizaciones, Compras y Listas Escolares.
     *
     * Cascada automática (sin intervención manual):
     *  1. Código exacto (escáner)     → bypass total, máxima prioridad
     *  2. Numérico puro               → ILIKE legacy (protege barcodes cortos)
     *  3. FTS español                 → plurales, stemming, acentos nativos
     *  4. Fuzzy pg_trgm               → typos/errores ortográficos (solo si term >= 3 chars)
     *  5. ILIKE tokenizado            → fallback garantizado sin extensiones
     *
     * Fuzzy (paso 4) se ejecuta SOLO si FTS retorna 0 resultados (FASE C).
     * similarity() no se ejecuta para términos < MIN_CHARS_FUZZY caracteres (FASE C).
     */
    public List<Producto> buscar(String termino, int limite) {
        if (termino == null || termino.trim().length() < MIN_CARACTERES_BUSQUEDA) {
            return Collections.emptyList();
        }

        String terminoLimpio = normalizarTexto(termino);
        log.debug("Omnibuscador: '{}' -> normalizado: '{}'", termino, terminoLimpio);

        // 1. Código exacto (escáner de barras) — sin cambios, máxima prioridad
        if (pareceCodigoBarras(terminoLimpio)) {
            List<Producto> porCodigo = busquedaRepository.buscarPorCodigoExacto(terminoLimpio);
            if (!porCodigo.isEmpty()) {
                log.debug("Encontrado por código exacto: {}", porCodigo.get(0).getNombre());
                return porCodigo;
            }
        }

        // 2. Numérico puro → ILIKE legacy (NO pasar por FTS ni fuzzy, protege barcodes cortos)
        if (terminoLimpio.matches("^\\d+$")) {
            return buscarLegacy(terminoLimpio, limite);
        }

        // 3. Guardias independientes para FTS y Fuzzy:
        //
        //    aptoParaFts:   term >= 4 chars Y todos los tokens >= 2 chars.
        //                   FTS ignora tokens de 1 char → "cuadernos f" daría 0 resultados.
        //                   Solo activa cuando el término está suficientemente completo.
        //
        //    aptoParaFuzzy: term >= MIN_CHARS_FUZZY (3) sin importar tokens cortos.
        //                   "lapisz 2" → ft1="lapisz" se corrige aunque ft2="2" sea corto.
        //                   Desacoplado de FTS para que tokens numéricos cortos no bloqueen
        //                   la corrección de typos en el token principal.
        boolean aptoParaFts   = terminoLimpio.length() >= 4 && todoTokensCompletos(terminoLimpio);
        boolean aptoParaFuzzy = terminoLimpio.length() >= MIN_CHARS_FUZZY;

        // 4. FTS en español: plurales y stemming automáticos.
        //    Solo corre si el término está completo (aptoParaFts).
        //    El helper aísla la transacción (REQUIRES_NEW) para no envenenar el fallback.
        if (aptoParaFts) {
            List<Producto> ftsResults = busquedaAvanzadaHelper.buscarFullText(
                    termino.trim().toLowerCase(), limite);
            if (!ftsResults.isEmpty()) {
                log.debug("FTS: {} resultados para '{}'", ftsResults.size(), termino);
                return ftsResults;
            }
            log.debug("FTS sin resultados para '{}', intentando fuzzy", termino);
        }

        // 5. Fuzzy token-aware (pg_trgm): corrige typos en tokens individuales.
        //    Corre si term >= 3 chars, independientemente de si FTS era apto o no.
        //    "lapisz 2"   → ft1="lapisz" (typo), ft2="2" → encuentra "lapiz 2b".
        //    "cuadeno"    → ft1="cuadeno" → encuentra "cuaderno 100 hojas".
        //    "fabre cas"  → ft1="fabre", ft2="cas" → encuentra "faber castell".
        if (aptoParaFuzzy) {
            String[] ft = terminoLimpio.split("\\s+");
            String ft1 = ft.length > 0 ? ft[0] : "";
            String ft2 = ft.length > 1 ? ft[1] : "";
            String ft3 = ft.length > 2 ? ft[2] : "";

            List<Producto> fuzzyResults = busquedaAvanzadaHelper.buscarFuzzyTokenizado(
                    ft1, ft2, ft3, FUZZY_THRESHOLD, limite);
            if (!fuzzyResults.isEmpty()) {
                log.debug("Fuzzy tokenizado: {} resultados para '{}'", fuzzyResults.size(), termino);
                return fuzzyResults;
            }
            log.debug("Fuzzy tokenizado sin resultados para '{}'", termino);
        }

        // 6. ILIKE tokenizado — fallback garantizado y comportamiento por defecto
        //    mientras el usuario está escribiendo (términos parciales)
        return buscarLegacy(terminoLimpio, limite);
    }

    /**
     * Verifica que todos los tokens del término tengan al menos 2 caracteres.
     * Un token de 1 carácter indica que el usuario está en mitad de escribir una palabra
     * (ej: "cuadernos f" → ["cuadernos", "f"] → "f" incompleto → FTS no apto).
     * FTS ignora tokens cortos y devolvería resultados incorrectos o vacíos.
     */
    private boolean todoTokensCompletos(String terminoLimpio) {
        if (terminoLimpio == null || terminoLimpio.isBlank()) return false;
        String[] partes = terminoLimpio.trim().split("\\s+");
        for (String parte : partes) {
            if (parte.length() < 2) return false;
        }
        return true;
    }

    /**
     * Lógica ILIKE original (extraída para reutilización como fallback).
     * Misma lógica que tenía buscar() antes de la integración FTS.
     * Nunca se elimina — garantía de funcionamiento sin search_vector.
     */
    private List<Producto> buscarLegacy(String terminoLimpio, int limite) {
        String[] tokens = tokenizar(terminoLimpio);
        if (tokens.length > 1) {
            log.debug("ILIKE tokenizado con {} tokens: {}", tokens.length, Arrays.toString(tokens));
            return busquedaRepository.omnibuscarTokenizado(
                    safeToken(tokens, 0),
                    safeToken(tokens, 1),
                    safeToken(tokens, 2),
                    safeToken(tokens, 3),
                    limite
            );
        } else {
            return busquedaRepository.omnibuscarSimple(terminoLimpio, limite);
        }
    }

    // =====================================================
    //  BÚSQUEDA FLEXIBLE - EXCLUSIVA PARA LISTAS ESCOLARES
    //  Multi-pass: AND estricto → OR scoring → keyword fallback
    //  No modifica buscar() ni tokenizar() existentes
    // =====================================================

    private static final int MAX_TOKENS_FLEXIBLE = 6;
    private static final int LIMITE_FLEXIBLE = 50;

    private static final Set<String> STOPWORDS_ESCOLARES = Set.of(
            "el", "la", "los", "las", "un", "una", "unos", "unas",
            "de", "del", "para", "por", "con", "sin", "en", "al",
            "y", "o", "e", "u", "que", "como", "tipo", "marca",
            "hojas", "paginas", "unidades", "piezas", "paquete", "juego", "set",
            "mm", "cm", "pulgadas", "grs", "gramos", "ml",
            "nro", "num", "numero", "tamano", "medida"
    );

    /**
     * Búsqueda flexible multi-pass para matching de listas escolares.
     * NO reemplaza buscar() — es un método independiente con deduplación por ID.
     *
     * Pass 0: FTS español   (plurales/stemming — alta precisión semántica)
     * Pass 1: AND estricto  (ILIKE omnibuscarTokenizado — coincidencia multi-token)
     * Pass 2: OR scoring    (ILIKE omnibuscarFlexible — al menos 1 token)
     * Pass 3: Keyword solo  (ILIKE omnibuscarSimple — fallback mínimo)
     *
     * Cada pass solo se ejecuta si el anterior no llenó el límite de resultados.
     * Resultados deduplicados por ID preservando orden de prioridad.
     */
    public List<Producto> buscarFlexibleEscolar(String termino, int limite) {
        if (termino == null || termino.trim().length() < MIN_CARACTERES_BUSQUEDA) {
            return Collections.emptyList();
        }

        String terminoLimpio = normalizarTexto(termino);
        String[] tokens = tokenizarFlexible(terminoLimpio);
        int maxResultados = Math.min(limite, LIMITE_FLEXIBLE);

        log.debug("Busqueda flexible escolar: '{}' -> tokens: {}", termino, Arrays.toString(tokens));

        // Dedup por ID con LinkedHashMap para preservar orden de insercion
        Map<Long, Producto> resultadosUnicos = new LinkedHashMap<>();

        // PASS 0: FTS en español (plurales y stemming automáticos — alta precisión semántica)
        //         El helper aísla la transacción para que un fallo no bloquee los passes ILIKE.
        List<Producto> ftsPaso = busquedaAvanzadaHelper.buscarFullText(
                termino.trim().toLowerCase(), maxResultados);
        for (Producto p : ftsPaso) {
            resultadosUnicos.putIfAbsent(p.getId(), p);
        }
        log.debug("Pass 0 (FTS): {} resultados", ftsPaso.size());

        // PASS 1: AND estricto (alta confianza) - reusa query existente
        if (resultadosUnicos.size() < maxResultados && tokens.length > 1) {
            List<Producto> passAnd = busquedaRepository.omnibuscarTokenizado(
                    safeToken(tokens, 0), safeToken(tokens, 1),
                    safeToken(tokens, 2), safeToken(tokens, 3),
                    maxResultados
            );
            for (Producto p : passAnd) {
                resultadosUnicos.putIfAbsent(p.getId(), p);
            }
            log.debug("Pass 1 (AND): {} resultados, total {}", passAnd.size(), resultadosUnicos.size());
        }

        // PASS 2: OR con scoring (confianza media) - query existente
        if (resultadosUnicos.size() < maxResultados) {
            List<Producto> passOr = busquedaRepository.omnibuscarFlexible(
                    safeToken(tokens, 0), safeToken(tokens, 1),
                    safeToken(tokens, 2), safeToken(tokens, 3),
                    safeToken(tokens, 4), safeToken(tokens, 5),
                    maxResultados
            );
            for (Producto p : passOr) {
                resultadosUnicos.putIfAbsent(p.getId(), p);
            }
            log.debug("Pass 2 (OR scoring): {} nuevos, total {}", passOr.size(), resultadosUnicos.size());
        }

        // PASS 3: Keyword principal como fallback (confianza baja)
        if (resultadosUnicos.size() < 5 && tokens.length >= 1) {
            List<Producto> passKeyword = busquedaRepository.omnibuscarSimple(
                    tokens[0], maxResultados
            );
            for (Producto p : passKeyword) {
                resultadosUnicos.putIfAbsent(p.getId(), p);
            }
            log.debug("Pass 3 (keyword '{}'): total {}", tokens[0], resultadosUnicos.size());
        }

        List<Producto> resultado = new ArrayList<>(resultadosUnicos.values());
        if (resultado.size() > maxResultados) {
            resultado = resultado.subList(0, maxResultados);
        }

        log.info("Busqueda flexible '{}': {} resultados finales", termino, resultado.size());
        return resultado;
    }

    /**
     * Tokeniza texto para búsqueda flexible escolar.
     * Filtra stopwords y soporta hasta 6 tokens.
     * Método independiente - no modifica tokenizar() original.
     */
    private String[] tokenizarFlexible(String texto) {
        if (texto == null || texto.isBlank()) {
            return new String[0];
        }

        return Arrays.stream(texto.split("\\s+"))
                .filter(token -> token.length() >= 2 || token.matches("\\d+"))
                .filter(token -> !STOPWORDS_ESCOLARES.contains(token))
                .limit(MAX_TOKENS_FLEXIBLE)
                .toArray(String[]::new);
    }

    /**
     * Autocomplete para sugerencias en tiempo real.
     * Optimizado para velocidad con mínimo 3 caracteres.
     *
     * @param termino Texto parcial del usuario
     * @return Top 10 sugerencias
     */
    public List<Producto> autocomplete(String termino) {
        if (termino == null || termino.trim().length() < MIN_CARACTERES_BUSQUEDA) {
            return Collections.emptyList();
        }

        String terminoLimpio = normalizarTexto(termino);

        // Si parece código, buscar exacto primero
        if (pareceCodigoBarras(terminoLimpio)) {
            List<Producto> porCodigo = busquedaRepository.buscarPorCodigoExacto(terminoLimpio);
            if (!porCodigo.isEmpty()) {
                return porCodigo;
            }
        }

        return busquedaRepository.autocomplete(terminoLimpio);
    }

    /**
     * Búsqueda paginada para listados con DataTables.
     *
     * @param termino  Texto de búsqueda (puede ser null/vacío para listar todo)
     * @param pageable Configuración de paginación
     * @return Página de productos
     */
    public Page<Producto> buscarPaginado(String termino, Pageable pageable) {
        String terminoLimpio = (termino != null && !termino.isBlank())
                ? normalizarTexto(termino)
                : "";

        return busquedaRepository.buscarPaginado(terminoLimpio, pageable);
    }

    /**
     * Cuenta los resultados de una búsqueda.
     */
    public long contarResultados(String termino) {
        if (termino == null || termino.isBlank()) {
            return 0;
        }
        return busquedaRepository.contarResultados(normalizarTexto(termino));
    }

    // =====================================================
    //  MÉTODOS DE NORMALIZACIÓN Y TOKENIZACIÓN
    // =====================================================

    /**
     * Normaliza el texto de búsqueda:
     * - Convierte a minúsculas
     * - Elimina acentos (á -> a, ñ -> n)
     * - Elimina caracteres especiales
     * - Trim de espacios
     */
    public String normalizarTexto(String texto) {
        if (texto == null) return "";

        // 1. Trim y lowercase
        String resultado = texto.trim().toLowerCase();

        // 2. Normalizar acentos (NFD decompose + remove diacritics)
        resultado = Normalizer.normalize(resultado, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        // 3. Reemplazar caracteres especiales comunes
        resultado = resultado
                .replace("ñ", "n")
                .replace("Ñ", "n");

        // 4. Eliminar caracteres no alfanuméricos excepto espacios y guiones
        resultado = resultado.replaceAll("[^a-z0-9\\s\\-]", " ");

        // 5. Normalizar espacios múltiples
        resultado = resultado.replaceAll("\\s+", " ").trim();

        return resultado;
    }

    /**
     * Tokeniza el texto en palabras individuales.
     * Filtra palabras muy cortas (1-2 caracteres) excepto números.
     * Limita a MAX_TOKENS palabras.
     */
    private String[] tokenizar(String texto) {
        if (texto == null || texto.isBlank()) {
            return new String[0];
        }

        return Arrays.stream(texto.split("\\s+"))
                .filter(token -> token.length() >= 2 || token.matches("\\d+"))
                .limit(MAX_TOKENS)
                .toArray(String[]::new);
    }

    /**
     * Obtiene un token de forma segura, devolviendo cadena vacía si no existe.
     */
    private String safeToken(String[] tokens, int index) {
        return (tokens != null && index < tokens.length) ? tokens[index] : "";
    }

    /**
     * Detecta si el texto parece un código de barras.
     * - Solo números
     * - O formato SKU-XXXX
     * - O longitud típica de EAN-13, EAN-8, UPC
     */
    private boolean pareceCodigoBarras(String texto) {
        if (texto == null || texto.isEmpty()) return false;

        // Solo números (códigos EAN/UPC)
        if (texto.matches("^\\d+$")) {
            int len = texto.length();
            // EAN-13, EAN-8, UPC-A, UPC-E
            return len == 13 || len == 12 || len == 8 || len == 6 || len >= 5;
        }

        // Formato SKU interno
        if (texto.toUpperCase().startsWith("SKU-") || texto.toUpperCase().startsWith("PROD-")) {
            return true;
        }

        return false;
    }

    // =====================================================
    //  MÉTODOS AUXILIARES PARA EL FRONTEND
    // =====================================================

    /**
     * Formatea un producto para respuesta JSON del autocomplete.
     * Estructura optimizada para Select2.
     */
    public Map<String, Object> formatearParaSelect2(Producto p) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", p.getId());

        // Texto formateado con información relevante
        StringBuilder text = new StringBuilder();
        if (p.getCodigoBarra() != null && !p.getCodigoBarra().isEmpty()) {
            text.append(p.getCodigoBarra()).append(" - ");
        }
        text.append(p.getNombre());
        if (p.getMarca() != null && !p.getMarca().isEmpty()) {
            text.append(" (").append(p.getMarca()).append(")");
        }
        text.append(" [Stock: ").append(p.getStockActual()).append("]");

        map.put("text", text.toString());
        map.put("nombre", p.getNombre());
        map.put("marca", p.getMarca());
        map.put("categoria", p.getCategoria());
        map.put("codigoBarra", p.getCodigoBarra());
        map.put("precio", p.getPrecioVenta());
        map.put("stock", p.getStockActual());
        map.put("imagen", p.getImagen());
        map.put("tieneStock", p.getStockActual() != null && p.getStockActual() > 0);

        // Ubicación: campos separados + campo combinado para compatibilidad
        map.put("ubicacionEstante", p.getUbicacionEstante());
        map.put("ubicacionFila", p.getUbicacionFila());
        map.put("ubicacionColumna", p.getUbicacionColumna());
        if (p.getUbicacionEstante() != null || p.getUbicacionFila() != null || p.getUbicacionColumna() != null) {
            java.util.StringJoiner sj = new java.util.StringJoiner("-");
            if (p.getUbicacionEstante() != null && !p.getUbicacionEstante().isEmpty()) sj.add(p.getUbicacionEstante());
            if (p.getUbicacionFila() != null && !p.getUbicacionFila().isEmpty()) sj.add(p.getUbicacionFila());
            if (p.getUbicacionColumna() != null && !p.getUbicacionColumna().isEmpty()) sj.add(p.getUbicacionColumna());
            map.put("ubicacion", sj.toString());
        }

        return map;
    }

    /**
     * Busca y formatea para Select2 en un solo paso.
     */
    public List<Map<String, Object>> buscarParaSelect2(String termino) {
        return buscar(termino, LIMITE_AUTOCOMPLETE).stream()
                .map(this::formatearParaSelect2)
                .collect(Collectors.toList());
    }

    /**
     * Autocomplete y formatea para Select2.
     */
    public List<Map<String, Object>> autocompleteParaSelect2(String termino) {
        return autocomplete(termino).stream()
                .map(this::formatearParaSelect2)
                .collect(Collectors.toList());
    }

    /**
     * Busca productos relacionados basándose en categoría y tags.
     *
     * @param productoId ID del producto actual (se excluye de resultados)
     * @param categoria Categoría del producto
     * @param tags Tags/sinónimos del producto
     * @param limite Máximo de resultados
     * @return Lista de productos relacionados
     */
    public List<Producto> buscarRelacionados(Long productoId, String categoria, String tags, int limite) {
        if (productoId == null) {
            return Collections.emptyList();
        }

        // Si no hay categoría ni tags, no hay forma de buscar relacionados
        if ((categoria == null || categoria.isBlank()) && (tags == null || tags.isBlank())) {
            return Collections.emptyList();
        }

        try {
            return busquedaRepository.buscarRelacionados(
                    productoId,
                    categoria != null ? categoria : "",
                    tags,
                    limite
            );
        } catch (Exception e) {
            log.warn("Error al buscar productos relacionados: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
