package com.libreria.sistema.repository;

import com.libreria.sistema.model.InsumoPersonalizado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface InsumoPersonalizadoRepository extends JpaRepository<InsumoPersonalizado, Long> {

    @EntityGraph(attributePaths = {"producto"})
    @Query("SELECT i FROM InsumoPersonalizado i WHERE i.activo = true ORDER BY i.nombre ASC")
    List<InsumoPersonalizado> findActivos();

    @EntityGraph(attributePaths = {"producto"})
    @Query("SELECT i FROM InsumoPersonalizado i ORDER BY i.activo DESC, i.nombre ASC")
    List<InsumoPersonalizado> findTodosOrdenados();

    Optional<InsumoPersonalizado> findByCodigo(String codigo);

    Optional<InsumoPersonalizado> findBySlugBusqueda(String slugBusqueda);

    @EntityGraph(attributePaths = {"producto"})
    @Query("SELECT i FROM InsumoPersonalizado i WHERE i.id = :id")
    Optional<InsumoPersonalizado> findDetalleById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"producto"})
    @Query("SELECT i FROM InsumoPersonalizado i WHERE i.activo = true AND (" +
            "LOWER(i.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(i.codigo) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(i.slugBusqueda, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(i.tags, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(i.categoria, '')) LIKE LOWER(CONCAT('%', :termino, '%')))")
    List<InsumoPersonalizado> buscarActivos(@Param("termino") String termino);

    long countByActivoTrue();
}
