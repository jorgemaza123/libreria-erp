package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoBusquedaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Helper que aísla FTS y Fuzzy en transacciones independientes (REQUIRES_NEW).
 *
 * Problema que resuelve:
 *   Cuando una consulta nativa falla (ej: pg_trgm no disponible o error SQL),
 *   PostgreSQL marca la conexión como "transaction aborted" (SQLState 25P02).
 *   Aunque Java capture la excepción, la conexión JDBC subyacente sigue en estado
 *   abortado y cualquier query posterior en la misma transacción falla igual.
 *
 * Solución:
 *   Cada consulta avanzada corre en su propia transacción (REQUIRES_NEW).
 *   Si falla → solo esa mini-transacción se aborta, la transacción padre continúa
 *   limpia y el fallback ILIKE puede ejecutarse sin problemas.
 *
 * Nota: REQUIRES_NEW requiere inyección de esta clase en otro bean Spring
 * (no llamar desde la misma clase), para que el proxy AOP funcione correctamente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BusquedaAvanzadaHelper {

    private final ProductoBusquedaRepository busquedaRepository;

    /**
     * Full-Text Search en transacción propia.
     * Retorna lista vacía si search_vector no está disponible o falla.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Producto> buscarFullText(String termino, int limite) {
        try {
            return busquedaRepository.buscarFullText(termino, limite);
        } catch (Exception e) {
            log.warn("FTS no disponible para '{}': {}", termino, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Fuzzy token-aware (pg_trgm) en transacción propia.
     * Compara cada token individualmente contra nombre/marca/categoria/tags.
     * Retorna lista vacía si pg_trgm no está disponible o falla.
     *
     * @param t1 Token principal (obligatorio)
     * @param t2 Segundo token (pasar "" si no hay)
     * @param t3 Tercer token (pasar "" si no hay)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Producto> buscarFuzzyTokenizado(String t1, String t2, String t3, double threshold, int limite) {
        try {
            return busquedaRepository.buscarFuzzyTokenizado(t1, t2, t3, threshold, limite);
        } catch (Exception e) {
            log.warn("Fuzzy tokenizado no disponible para '{} {} {}': {}", t1, t2, t3, e.getMessage());
            return Collections.emptyList();
        }
    }
}
