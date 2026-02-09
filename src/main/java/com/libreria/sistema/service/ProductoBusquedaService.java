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
 * Características:
 * - Búsqueda multi-campo (nombre, marca, categoría, códigos, descripción)
 * - Búsqueda tokenizada (palabras en cualquier orden)
 * - Normalización de acentos y caracteres especiales
 * - Ordenamiento por relevancia (stock > 0 primero)
 * - Detección automática de códigos de barras
 * - Cache para búsquedas frecuentes
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductoBusquedaService {

    private final ProductoBusquedaRepository busquedaRepository;

    // Límites configurables
    private static final int LIMITE_AUTOCOMPLETE = 10;
    private static final int LIMITE_BUSQUEDA = 50;
    private static final int MIN_CARACTERES_BUSQUEDA = 1;
    private static final int MAX_TOKENS = 4;

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
     */
    public List<Producto> buscar(String termino, int limite) {
        // Validación básica
        if (termino == null || termino.trim().length() < MIN_CARACTERES_BUSQUEDA) {
            return Collections.emptyList();
        }

        String terminoLimpio = normalizarTexto(termino);
        log.debug("Omnibuscador: '{}' -> normalizado: '{}'", termino, terminoLimpio);

        // 1. Primero intentar búsqueda por código exacto (escáner)
        if (pareceCodigoBarras(terminoLimpio)) {
            List<Producto> porCodigo = busquedaRepository.buscarPorCodigoExacto(terminoLimpio);
            if (!porCodigo.isEmpty()) {
                log.debug("Encontrado por código exacto: {}", porCodigo.get(0).getNombre());
                return porCodigo;
            }
        }

        // 2. Tokenizar el término para búsqueda heurística
        String[] tokens = tokenizar(terminoLimpio);

        if (tokens.length > 1) {
            // Búsqueda tokenizada (múltiples palabras)
            log.debug("Búsqueda tokenizada con {} tokens: {}", tokens.length, Arrays.toString(tokens));
            return busquedaRepository.omnibuscarTokenizado(
                    safeToken(tokens, 0),
                    safeToken(tokens, 1),
                    safeToken(tokens, 2),
                    safeToken(tokens, 3),
                    limite
            );
        } else {
            // Búsqueda simple (una palabra)
            return busquedaRepository.omnibuscarSimple(terminoLimpio, limite);
        }
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

        // Ubicación si está disponible
        if (p.getUbicacionEstante() != null || p.getUbicacionFila() != null) {
            String ubicacion = String.format("%s-%s",
                    p.getUbicacionEstante() != null ? p.getUbicacionEstante() : "",
                    p.getUbicacionFila() != null ? p.getUbicacionFila() : "");
            map.put("ubicacion", ubicacion);
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
