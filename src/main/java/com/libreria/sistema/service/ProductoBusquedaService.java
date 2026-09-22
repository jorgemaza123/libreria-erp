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
    private static final int MIN_CHARS_FUZZY = 4;
    private static final double FUZZY_THRESHOLD = 0.35; // 0=todo, 1=exacto; POS necesita evitar sugerencias ambiguas

    public List<Producto> buscarPorCodigoExacto(String codigo) {
        if (codigo == null || codigo.trim().isBlank()) {
            return Collections.emptyList();
        }
        return busquedaRepository.buscarPorCodigoExacto(codigo.trim());
    }

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
     *  3. ILIKE tokenizado            → coincidencia literal y predecible para POS
     *  4. FTS español                 → plurales, stemming, acentos nativos si no hubo literal
     *  5. Fuzzy pg_trgm               → typos/errores ortográficos solo como último recurso
     *
     * Fuzzy se ejecuta SOLO si no hay resultados literales ni FTS.
     * similarity() no se ejecuta para términos cortos para evitar falsos positivos.
     */
    public List<Producto> buscar(String termino, int limite) {
        if (termino == null || termino.trim().length() < MIN_CARACTERES_BUSQUEDA) {
            return Collections.emptyList();
        }

        int limiteFinal = normalizarLimite(limite);
        int limiteCandidatos = limiteCandidatos(limiteFinal);

        List<Producto> porCodigoDirecto = buscarPorCodigoExacto(termino);
        if (!porCodigoDirecto.isEmpty()) {
            log.debug("Encontrado por código exacto: {}", porCodigoDirecto.get(0).getNombre());
            return porCodigoDirecto;
        }

        String terminoLimpio = normalizarTexto(termino);
        log.debug("Omnibuscador: '{}' -> normalizado: '{}'", termino, terminoLimpio);

        // 1. Código exacto normalizado (escáner/SKU) — máxima prioridad
        if (pareceCodigoBarras(terminoLimpio)) {
            List<Producto> porCodigo = busquedaRepository.buscarPorCodigoExacto(terminoLimpio);
            if (!porCodigo.isEmpty()) {
                log.debug("Encontrado por código exacto: {}", porCodigo.get(0).getNombre());
                return porCodigo;
            }
        }

        // 2. Numérico puro → ILIKE legacy (NO pasar por FTS ni fuzzy, protege barcodes cortos)
        if (terminoLimpio.matches("^\\d+$")) {
            return ordenarPorRelevancia(terminoLimpio, buscarLegacy(terminoLimpio, limiteCandidatos), limiteFinal, false);
        }

        // 3. Búsqueda literal primero. En caja importa más la precisión que "adivinar".
        List<Producto> legacyResults = buscarLegacy(terminoLimpio, limiteCandidatos);
        List<Producto> resultadosConfiables = ordenarPorRelevancia(terminoLimpio, legacyResults, limiteFinal, false);
        if (!resultadosConfiables.isEmpty()) {
            log.debug("ILIKE literal rankeado: {} resultados para '{}'", resultadosConfiables.size(), termino);
            return resultadosConfiables;
        }

        // 4. Guardias independientes para FTS y Fuzzy:
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

        // 5. FTS en español: plurales y stemming automáticos.
        //    Solo corre si el término está completo (aptoParaFts).
        //    El helper aísla la transacción (REQUIRES_NEW) para no envenenar el fallback.
        if (aptoParaFts) {
            List<Producto> ftsResults = busquedaAvanzadaHelper.buscarFullText(
                    termino.trim().toLowerCase(), limiteCandidatos);
            resultadosConfiables = ordenarPorRelevancia(terminoLimpio, ftsResults, limiteFinal, false);
            if (!resultadosConfiables.isEmpty()) {
                log.debug("FTS rankeado: {} resultados para '{}'", resultadosConfiables.size(), termino);
                return resultadosConfiables;
            }
            log.debug("FTS sin resultados para '{}', intentando fuzzy", termino);
        }

        // 6. Fuzzy token-aware (pg_trgm): corrige typos solo como último recurso.
        //    Corre si term >= 4 chars, independientemente de si FTS era apto o no.
        //    "lapisz 2"   → ft1="lapisz" (typo), ft2="2" → encuentra "lapiz 2b".
        //    "cuadeno"    → ft1="cuadeno" → encuentra "cuaderno 100 hojas".
        //    "fabre cas"  → ft1="fabre", ft2="cas" → encuentra "faber castell".
        if (aptoParaFuzzy) {
            String[] ft = terminoLimpio.split("\\s+");
            String ft1 = ft.length > 0 ? ft[0] : "";
            String ft2 = ft.length > 1 ? ft[1] : "";
            String ft3 = ft.length > 2 ? ft[2] : "";

            List<Producto> fuzzyResults = busquedaAvanzadaHelper.buscarFuzzyTokenizado(
                    ft1, ft2, ft3, FUZZY_THRESHOLD, limiteCandidatos);
            resultadosConfiables = ordenarPorRelevancia(terminoLimpio, fuzzyResults, limiteFinal, true);
            if (!resultadosConfiables.isEmpty()) {
                log.debug("Fuzzy tokenizado rankeado: {} resultados para '{}'", resultadosConfiables.size(), termino);
                return resultadosConfiables;
            }
            log.debug("Fuzzy tokenizado sin resultados para '{}'", termino);
        }

        return Collections.emptyList();
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

    private List<Producto> ordenarPorRelevancia(String terminoLimpio, List<Producto> candidatos, int limite, boolean permiteFuzzy) {
        if (candidatos == null || candidatos.isEmpty()) {
            return Collections.emptyList();
        }

        String[] tokens = tokenizar(terminoLimpio);
        Set<Long> ids = new HashSet<>();
        List<ResultadoRelevancia> rankeados = new ArrayList<>();

        for (Producto producto : candidatos) {
            if (producto == null) {
                continue;
            }
            if (producto.getId() != null && !ids.add(producto.getId())) {
                continue;
            }
            int score = calcularRelevancia(producto, terminoLimpio, tokens, permiteFuzzy);
            if (score > 0) {
                rankeados.add(new ResultadoRelevancia(producto, score));
            }
        }

        return rankeados.stream()
                .sorted(Comparator
                        .comparingInt(ResultadoRelevancia::score).reversed()
                        .thenComparing(r -> tieneStock(r.producto()) ? 0 : 1)
                        .thenComparing(r -> normalizarTexto(r.producto().getNombre())))
                .limit(limite)
                .map(ResultadoRelevancia::producto)
                .collect(Collectors.toList());
    }

    private int calcularRelevancia(Producto p, String terminoLimpio, String[] tokens, boolean permiteFuzzy) {
        String nombre = normalizarTexto(p.getNombre());
        String codigoBarra = normalizarTexto(p.getCodigoBarra());
        String codigoInterno = normalizarTexto(p.getCodigoInterno());
        String marca = normalizarTexto(p.getMarca());
        String categoria = normalizarTexto(p.getCategoria());
        String descripcion = normalizarTexto(p.getDescripcion());
        String tags = normalizarTexto(p.getTags());
        String modelo = normalizarTexto(p.getModelo());
        String color = normalizarTexto(p.getColor());
        String generacion = normalizarTexto(p.getGeneracion());
        String searchable = unirCampos(nombre, codigoBarra, codigoInterno, marca, categoria, descripcion, tags, modelo, color, generacion);

        if (terminoLimpio.isBlank()) {
            return 0;
        }

        if (igual(codigoBarra, terminoLimpio) || igual(codigoInterno, terminoLimpio)) {
            return conBonos(p, 10000);
        }
        if (empieza(codigoBarra, terminoLimpio) || empieza(codigoInterno, terminoLimpio)) {
            return conBonos(p, 9200);
        }

        boolean terminoCorto = terminoLimpio.length() <= 2;
        if (terminoCorto) {
            if (empieza(nombre, terminoLimpio)) return conBonos(p, 7600);
            if (algunaPalabraEmpiezaCon(nombre, terminoLimpio)) return conBonos(p, 7100);
            if (empieza(marca, terminoLimpio)) return conBonos(p, 4300);
            if (empieza(modelo, terminoLimpio) || empieza(color, terminoLimpio)) return conBonos(p, 4000);
            return 0;
        }

        int score = 0;
        if (igual(nombre, terminoLimpio)) {
            score = 8600;
        } else if (empieza(nombre, terminoLimpio)) {
            score = 7800;
        } else if (algunaPalabraEmpiezaCon(nombre, terminoLimpio)) {
            score = 7300;
        } else if (contiene(nombre, terminoLimpio)) {
            score = 6900;
        } else if (todosLosTokensEn(nombre, tokens)) {
            score = 6400;
        } else if (tokens.length > 0 && todosLosTokensEn(searchable, tokens)) {
            score = 5300;
        } else if (igual(marca, terminoLimpio) || igual(modelo, terminoLimpio) || igual(color, terminoLimpio)) {
            score = 5100;
        } else if (empieza(marca, terminoLimpio) || empieza(modelo, terminoLimpio) || empieza(color, terminoLimpio)) {
            score = 4700;
        } else if (contiene(tags, terminoLimpio)) {
            score = 3900;
        } else if (igual(categoria, terminoLimpio) || empieza(categoria, terminoLimpio)) {
            score = 3600;
        } else if (contiene(searchable, terminoLimpio)) {
            score = 3000;
        } else if (permiteFuzzy) {
            double similitud = mejorSimilitud(terminoLimpio, tokens, nombre, marca, categoria, tags, modelo, color, generacion);
            if (similitud >= 0.78) {
                score = 2600 + (int) Math.round(similitud * 1000);
            }
        }

        if (score == 0) {
            return 0;
        }

        int tokensCoincidentes = contarTokensCoincidentes(searchable, tokens);
        boolean matchFuerte = contiene(nombre, terminoLimpio)
                || empieza(codigoBarra, terminoLimpio)
                || empieza(codigoInterno, terminoLimpio)
                || todosLosTokensEn(nombre, tokens);
        if (!permiteFuzzy && tokens.length > 1 && tokensCoincidentes < tokens.length && !matchFuerte) {
            return 0;
        }

        return conBonos(p, score);
    }

    private int conBonos(Producto producto, int score) {
        int bono = 0;
        if (tieneStock(producto)) {
            bono += 120;
        }
        if (Boolean.TRUE.equals(producto.getTemporadaActiva())) {
            bono += 30;
        }
        if (Boolean.TRUE.equals(producto.getPosRapido())) {
            bono += 20;
        }
        return score + bono;
    }

    private boolean tieneStock(Producto producto) {
        return producto.getStockActual() != null && producto.getStockActual() > 0;
    }

    private boolean igual(String campo, String termino) {
        return campo != null && campo.equals(termino);
    }

    private boolean contiene(String campo, String termino) {
        return campo != null && !campo.isBlank() && campo.contains(termino);
    }

    private boolean empieza(String campo, String termino) {
        return campo != null && !campo.isBlank() && campo.startsWith(termino);
    }

    private boolean todosLosTokensEn(String campo, String[] tokens) {
        if (campo == null || campo.isBlank() || tokens == null || tokens.length == 0) {
            return false;
        }
        for (String token : tokens) {
            if (!campo.contains(token)) {
                return false;
            }
        }
        return true;
    }

    private int contarTokensCoincidentes(String campo, String[] tokens) {
        if (campo == null || campo.isBlank() || tokens == null || tokens.length == 0) {
            return 0;
        }
        int total = 0;
        for (String token : tokens) {
            if (campo.contains(token)) {
                total++;
            }
        }
        return total;
    }

    private boolean algunaPalabraEmpiezaCon(String campo, String termino) {
        if (campo == null || campo.isBlank() || termino == null || termino.isBlank()) {
            return false;
        }
        return Arrays.stream(campo.split("\\s+")).anyMatch(p -> p.startsWith(termino));
    }

    private String unirCampos(String... campos) {
        return Arrays.stream(campos)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .collect(Collectors.joining(" "));
    }

    private double mejorSimilitud(String terminoLimpio, String[] tokens, String... campos) {
        double mejor = 0.0;
        String[] tokensBusqueda = tokens != null && tokens.length > 0 ? tokens : new String[]{terminoLimpio};
        for (String campo : campos) {
            if (campo == null || campo.isBlank()) {
                continue;
            }
            for (String tokenBusqueda : tokensBusqueda) {
                for (String tokenCampo : campo.split("\\s+")) {
                    mejor = Math.max(mejor, similitud(tokenBusqueda, tokenCampo));
                }
            }
        }
        return mejor;
    }

    private double similitud(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return 0.0;
        }
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() < 4 || b.length() < 4) {
            return 0.0;
        }
        int max = Math.max(a.length(), b.length());
        return 1.0 - ((double) distanciaLevenshtein(a, b) / max);
    }

    private int distanciaLevenshtein(String a, String b) {
        int[] anterior = new int[b.length() + 1];
        int[] actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) {
            anterior[j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                actual[j] = Math.min(Math.min(
                        actual[j - 1] + 1,
                        anterior[j] + 1),
                        anterior[j - 1] + costo);
            }
            int[] temp = anterior;
            anterior = actual;
            actual = temp;
        }
        return anterior[b.length()];
    }

    private int normalizarLimite(int limite) {
        if (limite <= 0) {
            return LIMITE_AUTOCOMPLETE;
        }
        return Math.min(limite, LIMITE_BUSQUEDA);
    }

    private int limiteCandidatos(int limite) {
        return Math.min(Math.max(limite * 4, 40), 100);
    }

    private record ResultadoRelevancia(Producto producto, int score) {
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
        } else if (p.getCodigoInterno() != null && !p.getCodigoInterno().isEmpty()) {
            text.append(p.getCodigoInterno()).append(" - ");
        }
        text.append(p.getNombre());
        if (p.getMarca() != null && !p.getMarca().isEmpty()) {
            text.append(" (").append(p.getMarca()).append(")");
        }
        text.append(" [Stock: ").append(p.getStockActual()).append("]");

        map.put("text", text.toString());
        map.put("nombre", p.getNombre());
        map.put("marca", p.getMarca());
        map.put("modelo", p.getModelo());
        map.put("color", p.getColor());
        map.put("generacion", p.getGeneracion());
        map.put("categoria", p.getCategoria());
        map.put("codigoBarra", p.getCodigoBarra());
        map.put("codigoInterno", p.getCodigoInterno());
        map.put("precio", p.getPrecioVenta());
        map.put("stock", p.getStockActual());
        map.put("imagen", p.getImagen());
        map.put("tieneStock", p.getStockActual() != null && p.getStockActual() > 0);
        map.put("temporadaActiva", Boolean.TRUE.equals(p.getTemporadaActiva()));
        map.put("posRapido", Boolean.TRUE.equals(p.getPosRapido()));

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
