package com.libreria.sistema.repository;

import com.libreria.sistema.model.TomaInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TomaInventarioRepository extends JpaRepository<TomaInventario, Long> {

    /**
     * Busca tomas de inventario por estado.
     */
    List<TomaInventario> findByEstadoOrderByFechaInicioDesc(String estado);

    /**
     * Busca tomas de inventario abiertas (en proceso).
     */
    @Query("SELECT t FROM TomaInventario t WHERE t.estado = 'ABIERTO' ORDER BY t.fechaInicio DESC")
    List<TomaInventario> findTomasAbiertas();

    /**
     * Verifica si hay alguna toma abierta.
     */
    @Query("SELECT COUNT(t) > 0 FROM TomaInventario t WHERE t.estado = 'ABIERTO'")
    boolean existeTomaAbierta();

    /**
     * Busca toma por código único.
     */
    Optional<TomaInventario> findByCodigo(String codigo);

    /**
     * Lista paginada de tomas con usuario cargado.
     */
    @EntityGraph(attributePaths = {"usuario"})
    Page<TomaInventario> findAllByOrderByFechaInicioDesc(Pageable pageable);

    /**
     * Busca tomas por rango de fechas.
     */
    @Query("SELECT t FROM TomaInventario t WHERE t.fechaInicio BETWEEN :inicio AND :fin ORDER BY t.fechaInicio DESC")
    List<TomaInventario> findByFechaInicioBetween(@Param("inicio") LocalDateTime inicio,
                                                   @Param("fin") LocalDateTime fin);

    /**
     * Cuenta tomas por estado.
     */
    long countByEstado(String estado);

    /**
     * Obtiene el último código generado para crear el siguiente.
     */
    @Query("SELECT t.codigo FROM TomaInventario t WHERE t.codigo LIKE :prefijo% ORDER BY t.codigo DESC LIMIT 1")
    Optional<String> findUltimoCodigo(@Param("prefijo") String prefijo);

    /**
     * Carga toma con sus detalles (para procesamiento).
     */
    @EntityGraph(attributePaths = {"usuario", "detalles", "detalles.producto"})
    @Query("SELECT t FROM TomaInventario t WHERE t.id = :id")
    Optional<TomaInventario> findByIdConDetalles(@Param("id") Long id);
}
