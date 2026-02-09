package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleTomaInventario;
import com.libreria.sistema.model.TomaInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DetalleTomaInventarioRepository extends JpaRepository<DetalleTomaInventario, Long> {

    /**
     * Lista todos los detalles de una toma de inventario con producto cargado.
     */
    @EntityGraph(attributePaths = {"producto"})
    List<DetalleTomaInventario> findByTomaInventarioOrderByProductoNombreAsc(TomaInventario tomaInventario);

    /**
     * Lista detalles de una toma por ID de toma.
     */
    @EntityGraph(attributePaths = {"producto"})
    @Query("SELECT d FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId ORDER BY d.producto.nombre ASC")
    List<DetalleTomaInventario> findByTomaInventarioId(@Param("tomaId") Long tomaId);

    /**
     * Lista paginada de detalles para una toma.
     */
    @EntityGraph(attributePaths = {"producto"})
    Page<DetalleTomaInventario> findByTomaInventarioId(Long tomaId, Pageable pageable);

    /**
     * Busca detalles con diferencia (para resumen de ajustes).
     */
    @Query("SELECT d FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.diferencia != 0 ORDER BY d.diferencia ASC")
    List<DetalleTomaInventario> findConDiferencia(@Param("tomaId") Long tomaId);

    /**
     * Busca detalles pendientes de conteo.
     */
    @Query("SELECT d FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.contado = false ORDER BY d.producto.nombre ASC")
    List<DetalleTomaInventario> findPendientesConteo(@Param("tomaId") Long tomaId);

    /**
     * Cuenta productos ya contados en una toma.
     */
    @Query("SELECT COUNT(d) FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.contado = true")
    long countContados(@Param("tomaId") Long tomaId);

    /**
     * Cuenta productos con diferencia.
     */
    @Query("SELECT COUNT(d) FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.diferencia != 0")
    long countConDiferencia(@Param("tomaId") Long tomaId);

    /**
     * Suma total de faltantes (diferencias negativas).
     */
    @Query("SELECT COALESCE(SUM(ABS(d.diferencia)), 0) FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.diferencia < 0")
    int sumFaltantes(@Param("tomaId") Long tomaId);

    /**
     * Suma total de sobrantes (diferencias positivas).
     */
    @Query("SELECT COALESCE(SUM(d.diferencia), 0) FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId AND d.diferencia > 0")
    int sumSobrantes(@Param("tomaId") Long tomaId);

    /**
     * Busca un detalle por toma y producto.
     */
    Optional<DetalleTomaInventario> findByTomaInventarioIdAndProductoId(Long tomaId, Long productoId);

    /**
     * Verifica si un producto ya está en una toma.
     */
    boolean existsByTomaInventarioIdAndProductoId(Long tomaId, Long productoId);

    /**
     * Elimina todos los detalles de una toma (para cancelación).
     */
    @Modifying
    @Query("DELETE FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId")
    void deleteByTomaInventarioId(@Param("tomaId") Long tomaId);

    /**
     * Busca detalles filtrados por nombre de producto (para búsqueda en UI).
     */
    @Query("SELECT d FROM DetalleTomaInventario d WHERE d.tomaInventario.id = :tomaId " +
           "AND (LOWER(d.producto.nombre) LIKE LOWER(CONCAT('%', :filtro, '%')) " +
           "OR LOWER(d.producto.codigoBarra) LIKE LOWER(CONCAT('%', :filtro, '%'))) " +
           "ORDER BY d.producto.nombre ASC")
    List<DetalleTomaInventario> findByTomaIdAndFiltro(@Param("tomaId") Long tomaId, @Param("filtro") String filtro);
}
