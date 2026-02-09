package com.libreria.sistema.repository;

import com.libreria.sistema.model.VentaListaEscolar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaListaEscolarRepository extends JpaRepository<VentaListaEscolar, Long> {

    // --- BÚSQUEDA POR LISTA ---
    List<VentaListaEscolar> findByListaEscolarIdOrderByFechaRegistroAsc(Long listaEscolarId);

    // --- BÚSQUEDA POR VENTA ---
    Optional<VentaListaEscolar> findByVentaId(Long ventaId);

    // --- VERIFICAR SI VENTA PERTENECE A LISTA ---
    boolean existsByListaEscolarIdAndVentaId(Long listaEscolarId, Long ventaId);

    // --- ESTADÍSTICAS POR LISTA ---
    @Query("SELECT COUNT(vl) FROM VentaListaEscolar vl WHERE vl.listaEscolar.id = :listaId")
    Integer countVentasByListaId(@Param("listaId") Long listaEscolarId);

    @Query("SELECT COALESCE(SUM(vl.montoVendido), 0) FROM VentaListaEscolar vl " +
           "WHERE vl.listaEscolar.id = :listaId")
    BigDecimal sumMontoVendidoByListaId(@Param("listaId") Long listaEscolarId);

    @Query("SELECT COALESCE(SUM(vl.itemsVendidos), 0) FROM VentaListaEscolar vl " +
           "WHERE vl.listaEscolar.id = :listaId")
    Integer sumItemsVendidosByListaId(@Param("listaId") Long listaEscolarId);

    // --- ESTADÍSTICAS GLOBALES ---
    @Query("SELECT COUNT(DISTINCT vl.listaEscolar.id) FROM VentaListaEscolar vl " +
           "WHERE vl.fechaRegistro BETWEEN :desde AND :hasta")
    Long countListasConVentasEnPeriodo(
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta
    );

    @Query("SELECT COALESCE(SUM(vl.montoVendido), 0) FROM VentaListaEscolar vl " +
           "WHERE vl.fechaRegistro BETWEEN :desde AND :hasta")
    BigDecimal sumMontoVendidoEnPeriodo(
        @Param("desde") LocalDateTime desde,
        @Param("hasta") LocalDateTime hasta
    );

    // --- ÚLTIMA VENTA DE UNA LISTA ---
    @Query("SELECT vl FROM VentaListaEscolar vl WHERE vl.listaEscolar.id = :listaId " +
           "ORDER BY vl.fechaRegistro DESC")
    List<VentaListaEscolar> findUltimaVentaByListaId(@Param("listaId") Long listaEscolarId);

    // --- VENTAS CON DATOS COMPLETOS ---
    @Query("SELECT vl FROM VentaListaEscolar vl " +
           "LEFT JOIN FETCH vl.venta " +
           "LEFT JOIN FETCH vl.listaEscolar " +
           "WHERE vl.listaEscolar.id = :listaId " +
           "ORDER BY vl.fechaRegistro ASC")
    List<VentaListaEscolar> findByListaIdConDatos(@Param("listaId") Long listaEscolarId);
}
