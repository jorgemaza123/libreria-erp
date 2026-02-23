package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoBusquedaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Servicio de búsqueda avanzada usando PostgreSQL FTS + pg_trgm.
 *
 * NO reemplaza ProductoBusquedaService — es completamente independiente.
 * Se activa solo cuando busqueda.avanzada.enabled=true en application.properties.
 *
 * Estrategias en cascada (cada una como fallback de la anterior):
 *  1. Full-Text Search con diccionario español (plurales, stemming, acentos nativos)
 *  2. Fuzzy/similarity con pg_trgm (typos, errores ortográficos)
 *  3. Fallback al ILIKE existente si FTS y fuzzy no están disponibles
 *
 * Retorna los mismos tipos (List<Producto>, List<Map>) que ProductoBusquedaService
 * para ser completamente intercambiable como delegado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class ProductoBusquedaAvanzadaService {

    private final ProductoBusquedaRepository busquedaRepository;

    /**
     * Delegado al servicio existente para:
     * - normalizarTexto() (accent stripping, lowercase)
     * - formatearParaSelect2() (formato JSON estándar)
     * - buscar() como último fallback
     * - buscarFlexibleEscolar() como fallback
     */
    private final ProductoBusquedaService busquedaService;

    private static final int LIMITE_BUSQUEDA = 50;
    private static final double FUZZY_THRESHOLD = 0.15;

    // =====================================================
    //  BÚSQUEDA PRINCIPAL
    // =====================================================

    /**
     * Búsqueda avanzada con FTS + fallbacks automáticos.
     * Interfaz idéntica a ProductoBusquedaService.buscar().
     */
    public List<Producto> buscar(String termino) {
        return buscar(termino, LIMITE_BUSQUEDA);
    }

    public List<Producto> buscar(String termino, int limite) {
        if (termino == null || termino.trim().length() < 1) {
            return Collections.emptyList();
        }

        // Para FTS usamos el texto limpio sin strip de acentos:
        // el diccionario 'spanish' maneja acentos y plurales de forma nativa
        String terminoFts = termino.trim().toLowerCase();

        // Estrategia 1: Full-Text Search en español
        try {
            List<Producto> ftsResults = busquedaRepository.buscarFullText(terminoFts, limite);
            if (!ftsResults.isEmpty()) {
                log.debug("FTS: {} resultados para '{}'", ftsResults.size(), termino);
                return ftsResults;
            }
        } catch (Exception e) {
            log.warn("FTS no disponible ({}), intentando fuzzy", e.getMessage());
        }

        // Estrategia 2: Fuzzy/similarity para typos y errores ortográficos (token-aware)
        String terminoNorm = busquedaService.normalizarTexto(termino);
        try {
            String[] ft = terminoNorm.split("\\s+");
            String ft1 = ft.length > 0 ? ft[0] : "";
            String ft2 = ft.length > 1 ? ft[1] : "";
            String ft3 = ft.length > 2 ? ft[2] : "";
            List<Producto> fuzzyResults = busquedaRepository.buscarFuzzyTokenizado(ft1, ft2, ft3, FUZZY_THRESHOLD, limite);
            if (!fuzzyResults.isEmpty()) {
                log.debug("Fuzzy: {} resultados para '{}'", fuzzyResults.size(), termino);
                return fuzzyResults;
            }
        } catch (Exception e) {
            log.warn("Fuzzy no disponible ({}), usando ILIKE", e.getMessage());
        }

        // Estrategia 3: Fallback al servicio ILIKE existente
        log.debug("Fallback ILIKE para '{}'", termino);
        return busquedaService.buscar(termino, limite);
    }

    // =====================================================
    //  BÚSQUEDA FLEXIBLE ESCOLAR (multi-pass con FTS primero)
    // =====================================================

    /**
     * Versión avanzada de buscarFlexibleEscolar().
     * Agrega FTS como primer pass antes de los ILIKE passes existentes.
     * NO modifica ProductoBusquedaService.buscarFlexibleEscolar().
     */
    public List<Producto> buscarFlexibleEscolar(String termino, int limite) {
        if (termino == null || termino.trim().length() < 1) {
            return Collections.emptyList();
        }

        Map<Long, Producto> resultadosUnicos = new LinkedHashMap<>();

        // PASS 1: FTS (alta precisión semántica, maneja plurales automáticamente)
        String terminoFts = termino.trim().toLowerCase();
        try {
            List<Producto> ftsPaso = busquedaRepository.buscarFullText(terminoFts, limite);
            for (Producto p : ftsPaso) {
                resultadosUnicos.putIfAbsent(p.getId(), p);
            }
            log.debug("FTS Pass 1: {} resultados para '{}'", ftsPaso.size(), termino);
        } catch (Exception e) {
            log.warn("FTS falló en buscarFlexibleEscolar: {}", e.getMessage());
        }

        // PASS 2: ILIKE flexible multi-token (complementa con coincidencias exactas de tags)
        if (resultadosUnicos.size() < limite) {
            List<Producto> ilikePaso = busquedaService.buscarFlexibleEscolar(termino, limite);
            for (Producto p : ilikePaso) {
                resultadosUnicos.putIfAbsent(p.getId(), p);
            }
        }

        List<Producto> resultado = new ArrayList<>(resultadosUnicos.values());
        if (resultado.size() > limite) {
            resultado = resultado.subList(0, limite);
        }

        log.info("BusquedaAvanzada flexible '{}': {} resultados", termino, resultado.size());
        return resultado;
    }

    // =====================================================
    //  INTEGRACIÓN CON SELECT2 (mismo formato que el original)
    // =====================================================

    /**
     * Busca y formatea para Select2.
     * Mismo formato que ProductoBusquedaService.buscarParaSelect2().
     */
    public List<Map<String, Object>> buscarParaSelect2(String termino) {
        return buscar(termino, 10).stream()
                .map(busquedaService::formatearParaSelect2)
                .collect(Collectors.toList());
    }
}
