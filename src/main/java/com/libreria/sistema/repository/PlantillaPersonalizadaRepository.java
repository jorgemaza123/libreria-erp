package com.libreria.sistema.repository;

import com.libreria.sistema.model.PlantillaPersonalizada;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PlantillaPersonalizadaRepository extends JpaRepository<PlantillaPersonalizada, Long> {

    Optional<PlantillaPersonalizada> findByCodigoModelo(String codigoModelo);

    Optional<PlantillaPersonalizada> findBySlug(String slug);

    @EntityGraph(attributePaths = {"componentes", "componentes.insumoPersonalizado", "componentes.adicionalPersonalizado", "rangos"})
    @Query("SELECT p FROM PlantillaPersonalizada p WHERE p.id = :id")
    Optional<PlantillaPersonalizada> findDetalleById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"componentes", "componentes.insumoPersonalizado", "componentes.adicionalPersonalizado", "rangos"})
    @Query("SELECT p FROM PlantillaPersonalizada p WHERE p.activo = true AND (" +
            "LOWER(p.nombreComercial) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(p.codigoModelo) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(p.slug, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(p.descripcionComercial, '')) LIKE LOWER(CONCAT('%', :termino, '%')))")
    List<PlantillaPersonalizada> buscarActivas(@Param("termino") String termino);

    @EntityGraph(attributePaths = {"rangos"})
    List<PlantillaPersonalizada> findAllByOrderByActivoDescNombreComercialAsc();

    @EntityGraph(attributePaths = {"rangos"})
    List<PlantillaPersonalizada> findByActivoTrueOrderByNombreComercialAsc();

    @Query("SELECT COUNT(p) FROM PlantillaPersonalizada p WHERE p.activo = true")
    long countActivas();
}
