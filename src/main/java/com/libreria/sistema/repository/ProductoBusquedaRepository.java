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
