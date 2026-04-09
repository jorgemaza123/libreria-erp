package com.libreria.sistema.repository;

import com.libreria.sistema.model.AdicionalPersonalizado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AdicionalPersonalizadoRepository extends JpaRepository<AdicionalPersonalizado, Long> {

    @EntityGraph(attributePaths = {"categoriaAdicional", "insumoPersonalizado"})
    List<AdicionalPersonalizado> findAllByOrderByActivoDescNombreAsc();

    Optional<AdicionalPersonalizado> findByCodigo(String codigo);

    @EntityGraph(attributePaths = {"categoriaAdicional", "insumoPersonalizado"})
    @Query("SELECT a FROM AdicionalPersonalizado a WHERE a.activo = true AND (" +
            "LOWER(a.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(a.codigo) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(a.descripcion, '')) LIKE LOWER(CONCAT('%', :termino, '%')))")
    List<AdicionalPersonalizado> buscarActivos(@Param("termino") String termino);

    @EntityGraph(attributePaths = {"categoriaAdicional", "insumoPersonalizado"})
    @Query("SELECT a FROM AdicionalPersonalizado a WHERE a.id = :id")
    Optional<AdicionalPersonalizado> findDetalleById(@Param("id") Long id);

    List<AdicionalPersonalizado> findByActivoTrueOrderByNombreAsc();
}
