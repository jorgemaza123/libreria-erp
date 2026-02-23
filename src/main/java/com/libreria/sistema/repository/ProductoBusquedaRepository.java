package com.libreria.sistema.repository;

import com.libreria.sistema.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Repositorio especializado para búsquedas avanzadas de productos.
 * Implementa el "Omnibuscador" con:
 * - Búsqueda multi-campo (nombre, marca, categoría, códigos, descripción, TAGS/sinónimos)
 * - Case-insensitive usando ILIKE de PostgreSQL
 * - Ordenamiento por relevancia (stock > 0 primero, coincidencias exactas)
 * - Soporte para búsqueda tokenizada (palabras en cualquier orden)
 */
public interface ProductoBusquedaRepository extends JpaRepository<Producto, Long> {

    // =====================================================
    //  OMNIBUSCADOR V1: Búsqueda simple multi-campo
    //  Busca el término completo en todos los campos + TAGS
    // =====================================================

    /**
     * Búsqueda multi-campo con ILIKE (PostgreSQL case-insensitive).
     * Busca en: nombre, marca, categoría, código de barras, código interno, descripción, TAGS.
     * Ordenado por: stock > 0 primero, luego por nombre.
     *
     * @param termino Término de búsqueda (se aplica %termino%)
     * @return Lista de productos ordenados por relevancia
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (
            p.nombre ILIKE '%' || :termino || '%'
            OR p.marca ILIKE '%' || :termino || '%'
            OR p.categoria ILIKE '%' || :termino || '%'
            OR p.codigo_barra ILIKE '%' || :termino || '%'
            OR p.codigo_interno ILIKE '%' || :termino || '%'
            OR p.descripcion ILIKE '%' || :termino || '%'
            OR p.modelo ILIKE '%' || :termino || '%'
            OR p.tags ILIKE '%' || :termino || '%'
        )
        ORDER BY
            CASE WHEN p.stock_actual > 0 THEN 0 ELSE 1 END,
            CASE WHEN p.codigo_barra = :termino THEN 0
                 WHEN p.codigo_interno = :termino THEN 1
                 WHEN p.nombre ILIKE :termino THEN 2
                 WHEN p.nombre ILIKE :termino || '%' THEN 3
                 WHEN p.tags ILIKE '%' || :termino || '%' THEN 4
                 ELSE 5 END,
            p.nombre ASC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> omnibuscarSimple(@Param("termino") String termino, @Param("limite") int limite);

    // =====================================================
    //  OMNIBUSCADOR V2: Búsqueda tokenizada (heurística)
    //  Busca TODAS las palabras en CUALQUIER orden + TAGS
    // =====================================================

    /**
     * Búsqueda tokenizada: cada palabra del término debe existir en algún campo o en TAGS.
     * Ejemplo: "cuaderno loro" encuentra "LORO CUADERNO ARIMANY" (cualquier orden).
     * Ejemplo con tags: "pegamento" encuentra "CINTA 3M" si tiene tag "pegamento, diurex"
     *
     * @param token1 Primera palabra (obligatoria)
     * @param token2 Segunda palabra (puede ser vacío '')
     * @param token3 Tercera palabra (puede ser vacío '')
     * @param token4 Cuarta palabra (puede ser vacío '')
     * @param limite Máximo de resultados
     * @return Lista de productos que contienen TODAS las palabras
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (
            -- Token 1 (obligatorio) - busca en todos los campos incluyendo tags
            p.nombre ILIKE '%' || :token1 || '%'
            OR p.marca ILIKE '%' || :token1 || '%'
            OR p.categoria ILIKE '%' || :token1 || '%'
            OR p.codigo_barra ILIKE '%' || :token1 || '%'
            OR p.codigo_interno ILIKE '%' || :token1 || '%'
            OR p.descripcion ILIKE '%' || :token1 || '%'
            OR p.tags ILIKE '%' || :token1 || '%'
        )
        AND (
            :token2 = '' OR (
                p.nombre ILIKE '%' || :token2 || '%'
                OR p.marca ILIKE '%' || :token2 || '%'
                OR p.categoria ILIKE '%' || :token2 || '%'
                OR p.descripcion ILIKE '%' || :token2 || '%'
                OR p.tags ILIKE '%' || :token2 || '%'
            )
        )
        AND (
            :token3 = '' OR (
                p.nombre ILIKE '%' || :token3 || '%'
                OR p.marca ILIKE '%' || :token3 || '%'
                OR p.categoria ILIKE '%' || :token3 || '%'
                OR p.descripcion ILIKE '%' || :token3 || '%'
                OR p.tags ILIKE '%' || :token3 || '%'
            )
        )
        AND (
            :token4 = '' OR (
                p.nombre ILIKE '%' || :token4 || '%'
                OR p.marca ILIKE '%' || :token4 || '%'
                OR p.categoria ILIKE '%' || :token4 || '%'
                OR p.descripcion ILIKE '%' || :token4 || '%'
                OR p.tags ILIKE '%' || :token4 || '%'
            )
        )
        ORDER BY
            CASE WHEN p.stock_actual > 0 THEN 0 ELSE 1 END,
            CASE
                -- Coincidencia exacta de código
                WHEN p.codigo_barra = :token1 THEN 0
                WHEN p.codigo_interno = :token1 THEN 1
                -- Nombre contiene todas las palabras
                WHEN p.nombre ILIKE '%' || :token1 || '%'
                     AND (:token2 = '' OR p.nombre ILIKE '%' || :token2 || '%')
                     AND (:token3 = '' OR p.nombre ILIKE '%' || :token3 || '%')
                     AND (:token4 = '' OR p.nombre ILIKE '%' || :token4 || '%') THEN 2
                -- Coincidencia por tags (sinónimos)
                WHEN p.tags ILIKE '%' || :token1 || '%' THEN 3
                ELSE 4
            END,
            p.nombre ASC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> omnibuscarTokenizado(
            @Param("token1") String token1,
            @Param("token2") String token2,
            @Param("token3") String token3,
            @Param("token4") String token4,
            @Param("limite") int limite
    );

    // =====================================================
    //  OMNIBUSCADOR V3: Búsqueda flexible OR con scoring
    //  Usado EXCLUSIVAMENTE por listas escolares
    //  Al menos 1 token debe coincidir, puntúa por relevancia
    // =====================================================

    @Query(value = """
        SELECT p.* FROM productos p
        WHERE p.activo = true
        AND (
            p.nombre ILIKE '%' || :t1 || '%'
            OR p.marca ILIKE '%' || :t1 || '%'
            OR p.categoria ILIKE '%' || :t1 || '%'
            OR p.tags ILIKE '%' || :t1 || '%'
            OR p.descripcion ILIKE '%' || :t1 || '%'
            OR (:t2 != '' AND (p.nombre ILIKE '%' || :t2 || '%' OR p.marca ILIKE '%' || :t2 || '%' OR p.tags ILIKE '%' || :t2 || '%'))
            OR (:t3 != '' AND (p.nombre ILIKE '%' || :t3 || '%' OR p.marca ILIKE '%' || :t3 || '%' OR p.tags ILIKE '%' || :t3 || '%'))
            OR (:t4 != '' AND (p.nombre ILIKE '%' || :t4 || '%' OR p.marca ILIKE '%' || :t4 || '%' OR p.tags ILIKE '%' || :t4 || '%'))
            OR (:t5 != '' AND (p.nombre ILIKE '%' || :t5 || '%' OR p.marca ILIKE '%' || :t5 || '%' OR p.tags ILIKE '%' || :t5 || '%'))
            OR (:t6 != '' AND (p.nombre ILIKE '%' || :t6 || '%' OR p.marca ILIKE '%' || :t6 || '%' OR p.tags ILIKE '%' || :t6 || '%'))
        )
        ORDER BY
            (
                CASE WHEN p.nombre ILIKE '%' || :t1 || '%' THEN 50 ELSE 0 END +
                CASE WHEN :t2 != '' AND p.nombre ILIKE '%' || :t2 || '%' THEN 50 ELSE 0 END +
                CASE WHEN :t3 != '' AND p.nombre ILIKE '%' || :t3 || '%' THEN 50 ELSE 0 END +
                CASE WHEN :t4 != '' AND p.nombre ILIKE '%' || :t4 || '%' THEN 50 ELSE 0 END +
                CASE WHEN :t5 != '' AND p.nombre ILIKE '%' || :t5 || '%' THEN 50 ELSE 0 END +
                CASE WHEN :t6 != '' AND p.nombre ILIKE '%' || :t6 || '%' THEN 50 ELSE 0 END +
                CASE WHEN p.tags IS NOT NULL AND p.tags ILIKE '%' || :t1 || '%' THEN 40 ELSE 0 END +
                CASE WHEN :t2 != '' AND p.tags IS NOT NULL AND p.tags ILIKE '%' || :t2 || '%' THEN 40 ELSE 0 END +
                CASE WHEN :t3 != '' AND p.tags IS NOT NULL AND p.tags ILIKE '%' || :t3 || '%' THEN 40 ELSE 0 END +
                CASE WHEN :t4 != '' AND p.tags IS NOT NULL AND p.tags ILIKE '%' || :t4 || '%' THEN 40 ELSE 0 END +
                CASE WHEN :t5 != '' AND p.tags IS NOT NULL AND p.tags ILIKE '%' || :t5 || '%' THEN 40 ELSE 0 END +
                CASE WHEN :t6 != '' AND p.tags IS NOT NULL AND p.tags ILIKE '%' || :t6 || '%' THEN 40 ELSE 0 END +
                CASE WHEN p.marca ILIKE '%' || :t1 || '%' THEN 30 ELSE 0 END +
                CASE WHEN :t2 != '' AND p.marca ILIKE '%' || :t2 || '%' THEN 30 ELSE 0 END +
                CASE WHEN :t3 != '' AND p.marca ILIKE '%' || :t3 || '%' THEN 30 ELSE 0 END +
                CASE WHEN :t4 != '' AND p.marca ILIKE '%' || :t4 || '%' THEN 30 ELSE 0 END +
                CASE WHEN :t5 != '' AND p.marca ILIKE '%' || :t5 || '%' THEN 30 ELSE 0 END +
                CASE WHEN :t6 != '' AND p.marca ILIKE '%' || :t6 || '%' THEN 30 ELSE 0 END +
                CASE WHEN p.categoria ILIKE '%' || :t1 || '%' THEN 20 ELSE 0 END +
                CASE WHEN :t2 != '' AND p.categoria ILIKE '%' || :t2 || '%' THEN 20 ELSE 0 END +
                CASE WHEN :t3 != '' AND p.categoria ILIKE '%' || :t3 || '%' THEN 20 ELSE 0 END +
                CASE WHEN :t4 != '' AND p.categoria ILIKE '%' || :t4 || '%' THEN 20 ELSE 0 END +
                CASE WHEN :t5 != '' AND p.categoria ILIKE '%' || :t5 || '%' THEN 20 ELSE 0 END +
                CASE WHEN :t6 != '' AND p.categoria ILIKE '%' || :t6 || '%' THEN 20 ELSE 0 END +
                CASE WHEN p.descripcion ILIKE '%' || :t1 || '%' THEN 10 ELSE 0 END +
                CASE WHEN :t2 != '' AND p.descripcion ILIKE '%' || :t2 || '%' THEN 10 ELSE 0 END +
                CASE WHEN :t3 != '' AND p.descripcion ILIKE '%' || :t3 || '%' THEN 10 ELSE 0 END +
                CASE WHEN :t4 != '' AND p.descripcion ILIKE '%' || :t4 || '%' THEN 10 ELSE 0 END +
                CASE WHEN :t5 != '' AND p.descripcion ILIKE '%' || :t5 || '%' THEN 10 ELSE 0 END +
                CASE WHEN :t6 != '' AND p.descripcion ILIKE '%' || :t6 || '%' THEN 10 ELSE 0 END +
                CASE WHEN p.stock_actual > 0 THEN 25 ELSE 0 END
            ) DESC,
            p.nombre ASC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> omnibuscarFlexible(
            @Param("t1") String t1,
            @Param("t2") String t2,
            @Param("t3") String t3,
            @Param("t4") String t4,
            @Param("t5") String t5,
            @Param("t6") String t6,
            @Param("limite") int limite
    );

    // =====================================================
    //  AUTOCOMPLETE: Sugerencias rápidas (3+ caracteres)
    // =====================================================

    /**
     * Sugerencias rápidas para autocomplete.
     * Optimizado para velocidad: busca en nombre, códigos, marca y TAGS.
     * Retorna máximo 10 resultados ordenados por stock y relevancia.
     *
     * @param termino Texto parcial (mínimo 1 carácter)
     * @return Top 10 sugerencias
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (
            p.codigo_barra ILIKE :termino || '%'
            OR p.codigo_interno ILIKE :termino || '%'
            OR p.nombre ILIKE :termino || '%'
            OR p.nombre ILIKE '% ' || :termino || '%'
            OR p.marca ILIKE :termino || '%'
            OR p.tags ILIKE '%' || :termino || '%'
        )
        ORDER BY
            CASE WHEN p.stock_actual > 0 THEN 0 ELSE 1 END,
            CASE
                WHEN p.codigo_barra = :termino THEN 0
                WHEN p.codigo_barra ILIKE :termino || '%' THEN 1
                WHEN p.codigo_interno = :termino THEN 2
                WHEN p.codigo_interno ILIKE :termino || '%' THEN 3
                WHEN p.nombre ILIKE :termino || '%' THEN 4
                WHEN p.tags ILIKE '%' || :termino || '%' THEN 5
                ELSE 6
            END,
            p.nombre ASC
        LIMIT 10
        """, nativeQuery = true)
    List<Producto> autocomplete(@Param("termino") String termino);

    // =====================================================
    //  BÚSQUEDA POR CÓDIGO EXACTO (para escáner)
    // =====================================================

    /**
     * Búsqueda exacta por código de barras o código interno.
     * Ideal para lectores de código de barras.
     *
     * @param codigo Código exacto escaneado
     * @return Producto si existe, o lista vacía
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (p.codigo_barra = :codigo OR p.codigo_interno = :codigo)
        LIMIT 1
        """, nativeQuery = true)
    List<Producto> buscarPorCodigoExacto(@Param("codigo") String codigo);

    // =====================================================
    //  BÚSQUEDA PAGINADA PARA LISTADOS
    // =====================================================

    /**
     * Búsqueda paginada multi-campo para listados con filtros.
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (
            :termino IS NULL OR :termino = ''
            OR p.nombre ILIKE '%' || :termino || '%'
            OR p.marca ILIKE '%' || :termino || '%'
            OR p.categoria ILIKE '%' || :termino || '%'
            OR p.codigo_barra ILIKE '%' || :termino || '%'
            OR p.codigo_interno ILIKE '%' || :termino || '%'
            OR p.tags ILIKE '%' || :termino || '%'
        )
        ORDER BY p.nombre ASC
        """,
            countQuery = """
        SELECT COUNT(*) FROM productos p
        WHERE p.activo = true
        AND (
            :termino IS NULL OR :termino = ''
            OR p.nombre ILIKE '%' || :termino || '%'
            OR p.marca ILIKE '%' || :termino || '%'
            OR p.categoria ILIKE '%' || :termino || '%'
            OR p.codigo_barra ILIKE '%' || :termino || '%'
            OR p.codigo_interno ILIKE '%' || :termino || '%'
            OR p.tags ILIKE '%' || :termino || '%'
        )
        """,
            nativeQuery = true)
    Page<Producto> buscarPaginado(@Param("termino") String termino, Pageable pageable);

    // =====================================================
    //  PRODUCTOS RELACIONADOS (por categoría o tags similares)
    // =====================================================

    /**
     * Busca productos relacionados basándose en categoría y tags.
     * Excluye el producto actual.
     *
     * @param productoId ID del producto actual (a excluir)
     * @param categoria Categoría del producto
     * @param tags Tags del producto (puede ser null)
     * @param limite Máximo de resultados
     * @return Lista de productos relacionados
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND p.id != :productoId
        AND p.stock_actual > 0
        AND (
            p.categoria = :categoria
            OR (:tags IS NOT NULL AND p.tags IS NOT NULL AND (
                p.tags ILIKE '%' || SPLIT_PART(:tags, ',', 1) || '%'
                OR p.tags ILIKE '%' || SPLIT_PART(:tags, ',', 2) || '%'
            ))
        )
        ORDER BY
            CASE WHEN p.categoria = :categoria THEN 0 ELSE 1 END,
            p.stock_actual DESC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> buscarRelacionados(
            @Param("productoId") Long productoId,
            @Param("categoria") String categoria,
            @Param("tags") String tags,
            @Param("limite") int limite
    );

    // =====================================================
    //  FULL-TEXT SEARCH (FTS) - NUEVO, no modifica existentes
    //  Usa columna search_vector + diccionario español
    //  Maneja plurales, stemming y acentos automáticamente
    // =====================================================

    /**
     * Búsqueda full-text usando tsvector con pesos por campo:
     * A=nombre, B=marca/tags, C=categoria/modelo, D=descripcion.
     * El diccionario 'spanish' normaliza plurales y acentos.
     * Requiere columna search_vector inicializada (BusquedaIndexInitializer).
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND p.search_vector @@ plainto_tsquery('spanish', :termino)
        ORDER BY
            CASE WHEN p.stock_actual > 0 THEN 0 ELSE 1 END,
            ts_rank(p.search_vector, plainto_tsquery('spanish', :termino)) DESC,
            p.nombre ASC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> buscarFullText(@Param("termino") String termino, @Param("limite") int limite);

    // =====================================================
    //  FUZZY SEARCH (pg_trgm similarity) - NUEVO
    //  Para typos y errores ortográficos no cubiertos por FTS
    //  Requiere extensión pg_trgm habilitada
    // =====================================================

    /**
     * Búsqueda difusa TOKEN-AWARE usando trigramas (pg_trgm similarity).
     *
     * Por qué tokens y no el término completo:
     *   similarity("lapizs 2b", "lapiz 2b HB") es bajo porque el término completo
     *   tiene pocos trigramas en común con el nombre largo.
     *   similarity("lapizs", "lapiz 2b HB") > threshold porque "lapizs" comparte
     *   muchos trigramas con "lapiz" aunque el nombre sea más largo.
     *
     * Hasta 3 tokens: t1 obligatorio, t2 y t3 opcionales (pasar '' si no aplica).
     *
     * IMPORTANTE: No usar CTEs ni casts de parámetros — Hibernate los convierte
     * incorrectamente. Usar comparación directa con :threshold como double.
     */
    @Query(value = """
        SELECT * FROM productos p
        WHERE p.activo = true
        AND (
            similarity(p.nombre, :t1) > :threshold
            OR similarity(coalesce(p.marca, ''), :t1) > :threshold
            OR similarity(coalesce(p.categoria, ''), :t1) > :threshold
            OR similarity(coalesce(p.tags, ''), :t1) > :threshold
            OR (:t2 != '' AND similarity(p.nombre, :t2) > :threshold)
            OR (:t2 != '' AND similarity(coalesce(p.marca, ''), :t2) > :threshold)
            OR (:t2 != '' AND similarity(coalesce(p.categoria, ''), :t2) > :threshold)
            OR (:t2 != '' AND similarity(coalesce(p.tags, ''), :t2) > :threshold)
            OR (:t3 != '' AND similarity(p.nombre, :t3) > :threshold)
            OR (:t3 != '' AND similarity(coalesce(p.marca, ''), :t3) > :threshold)
            OR (:t3 != '' AND similarity(coalesce(p.categoria, ''), :t3) > :threshold)
            OR (:t3 != '' AND similarity(coalesce(p.tags, ''), :t3) > :threshold)
        )
        ORDER BY
            CASE WHEN p.stock_actual > 0 THEN 0 ELSE 1 END,
            GREATEST(
                similarity(p.nombre, :t1),
                similarity(coalesce(p.marca, ''), :t1),
                similarity(coalesce(p.categoria, ''), :t1),
                similarity(coalesce(p.tags, ''), :t1)
            ) DESC,
            p.nombre ASC
        LIMIT :limite
        """, nativeQuery = true)
    List<Producto> buscarFuzzyTokenizado(
            @Param("t1") String t1,
            @Param("t2") String t2,
            @Param("t3") String t3,
            @Param("threshold") double threshold,
            @Param("limite") int limite
    );

    // =====================================================
    //  ESTADÍSTICAS DE BÚSQUEDA
    // =====================================================

    /**
     * Cuenta resultados para una búsqueda (útil para mostrar "X resultados encontrados").
     */
    @Query(value = """
        SELECT COUNT(*) FROM productos p
        WHERE p.activo = true
        AND (
            p.nombre ILIKE '%' || :termino || '%'
            OR p.marca ILIKE '%' || :termino || '%'
            OR p.categoria ILIKE '%' || :termino || '%'
            OR p.codigo_barra ILIKE '%' || :termino || '%'
            OR p.codigo_interno ILIKE '%' || :termino || '%'
            OR p.tags ILIKE '%' || :termino || '%'
        )
        """, nativeQuery = true)
    long contarResultados(@Param("termino") String termino);
}
