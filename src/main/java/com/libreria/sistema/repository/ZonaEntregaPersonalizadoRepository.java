package com.libreria.sistema.repository;

import com.libreria.sistema.model.ZonaEntregaPersonalizado;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ZonaEntregaPersonalizadoRepository extends JpaRepository<ZonaEntregaPersonalizado, Long> {
    List<ZonaEntregaPersonalizado> findAllByOrderByActivoDescDepartamentoAscProvinciaAscDistritoAsc();
    List<ZonaEntregaPersonalizado> findByActivoTrueOrderByDepartamentoAscProvinciaAscDistritoAsc();
    Optional<ZonaEntregaPersonalizado> findByDepartamentoIgnoreCaseAndProvinciaIgnoreCaseAndDistritoIgnoreCase(String departamento, String provincia, String distrito);

    @Query("SELECT z FROM ZonaEntregaPersonalizado z " +
            "WHERE z.activo = true " +
            "AND LOWER(z.departamento) = LOWER(:departamento) " +
            "AND LOWER(z.provincia) = LOWER(:provincia) " +
            "AND LOWER(z.distrito) = LOWER(:distrito)")
    Optional<ZonaEntregaPersonalizado> findZonaExacta(@Param("departamento") String departamento,
                                                      @Param("provincia") String provincia,
                                                      @Param("distrito") String distrito);
}
