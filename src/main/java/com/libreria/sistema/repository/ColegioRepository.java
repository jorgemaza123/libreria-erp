package com.libreria.sistema.repository;

import com.libreria.sistema.model.Colegio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ColegioRepository extends JpaRepository<Colegio, Long> {

    Optional<Colegio> findByNombre(String nombre);

    List<Colegio> findByActivoTrueOrderByNombreAsc();

    @Query("SELECT c FROM Colegio c WHERE c.activo = true AND " +
           "LOWER(c.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "ORDER BY c.nombre ASC")
    List<Colegio> buscarPorNombre(@Param("termino") String termino);

    @Query("SELECT c FROM Colegio c WHERE c.activo = true AND c.distrito = :distrito " +
           "ORDER BY c.nombre ASC")
    List<Colegio> findByDistrito(@Param("distrito") String distrito);

    boolean existsByNombre(String nombre);
}
