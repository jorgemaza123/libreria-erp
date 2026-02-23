package com.libreria.sistema.repository;

import com.libreria.sistema.model.DetalleListaEscolar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

@Repository
public interface DetalleListaEscolarRepository extends JpaRepository<DetalleListaEscolar, Long> {

    // --- BÚSQUEDA POR LISTA ---
    List<DetalleListaEscolar> findByListaEscolarIdOrderByOrdenAsc(Long listaEscolarId);

    // --- BÚSQUEDA POR ESTADO ---
    List<DetalleListaEscolar> findByListaEscolarIdAndEstado(Long listaEscolarId, String estado);

    @Query("SELECT d FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.estado IN ('COTIZADO', 'PENDIENTE', 'REEMPLAZADO') " +
           "ORDER BY d.orden ASC")
    List<DetalleListaEscolar> findVendibles(@Param("listaId") Long listaEscolarId);

    @Query("SELECT d FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.estado = 'VENDIDO' ORDER BY d.fechaVenta ASC")
    List<DetalleListaEscolar> findVendidos(@Param("listaId") Long listaEscolarId);

    @Query("SELECT d FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.estado = 'PENDIENTE' ORDER BY d.orden ASC")
    List<DetalleListaEscolar> findPendientes(@Param("listaId") Long listaEscolarId);

    // --- CONTADORES ---
    @Query("SELECT COUNT(d) FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId")
    Integer countByListaId(@Param("listaId") Long listaEscolarId);

    @Query("SELECT COUNT(d) FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.estado = :estado")
    Integer countByListaIdAndEstado(@Param("listaId") Long listaEscolarId, @Param("estado") String estado);

    // --- TOTALES ---
    @Query("SELECT COALESCE(SUM(d.precioEconomico * d.cantidadSolicitada), 0) " +
           "FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.precioEconomico IS NOT NULL")
    BigDecimal sumTotalEconomico(@Param("listaId") Long listaEscolarId);

    @Query("SELECT COALESCE(SUM(d.precioMedio * d.cantidadSolicitada), 0) " +
           "FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.precioMedio IS NOT NULL")
    BigDecimal sumTotalMedio(@Param("listaId") Long listaEscolarId);

    @Query("SELECT COALESCE(SUM(d.precioPremium * d.cantidadSolicitada), 0) " +
           "FROM DetalleListaEscolar d WHERE d.listaEscolar.id = :listaId " +
           "AND d.precioPremium IS NOT NULL")
    BigDecimal sumTotalPremium(@Param("listaId") Long listaEscolarId);

    // --- ACTUALIZACIÓN MASIVA ---
    @Modifying
    @Query("UPDATE DetalleListaEscolar d SET d.estado = :nuevoEstado " +
           "WHERE d.listaEscolar.id = :listaId AND d.estado = :estadoActual")
    int actualizarEstadoMasivo(
        @Param("listaId") Long listaEscolarId,
        @Param("estadoActual") String estadoActual,
        @Param("nuevoEstado") String nuevoEstado
    );

    @Modifying
    @Query("UPDATE DetalleListaEscolar d SET d.nivelSeleccionado = :nivel " +
           "WHERE d.listaEscolar.id = :listaId AND d.estado = 'COTIZADO'")
    int seleccionarNivelMasivo(@Param("listaId") Long listaEscolarId, @Param("nivel") String nivel);

    // --- BÚSQUEDA POR PRODUCTO ---
    @Query("SELECT d FROM DetalleListaEscolar d WHERE d.producto.id = :productoId " +
           "AND d.estado IN ('COTIZADO', 'PENDIENTE')")
    List<DetalleListaEscolar> findByProductoIdPendientes(@Param("productoId") Long productoId);

    // --- BÚSQUEDA POR VENTA ---
    List<DetalleListaEscolar> findByVentaId(Long ventaId);

    // --- PRODUCTOS FALTANTES (sin producto asignado) ---

    /**
     * Encuentra items sin producto asignado en nivel ECONOMICO.
     */
    @Query("SELECT d FROM DetalleListaEscolar d " +
           "JOIN FETCH d.listaEscolar l " +
           "WHERE d.productoEconomico IS NULL " +
           "AND d.cotizadoProveedorEconomico = false " +
           "AND d.esRegalo = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "ORDER BY d.textoOriginal ASC")
    List<DetalleListaEscolar> findFaltantesEconomico();

    /**
     * Encuentra items sin producto asignado en nivel MEDIO.
     */
    @Query("SELECT d FROM DetalleListaEscolar d " +
           "JOIN FETCH d.listaEscolar l " +
           "WHERE d.productoMedio IS NULL " +
           "AND d.cotizadoProveedorMedio = false " +
           "AND d.esRegalo = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "ORDER BY d.textoOriginal ASC")
    List<DetalleListaEscolar> findFaltantesMedio();

    /**
     * Encuentra items sin producto asignado en nivel PREMIUM.
     */
    @Query("SELECT d FROM DetalleListaEscolar d " +
           "JOIN FETCH d.listaEscolar l " +
           "WHERE d.productoPremium IS NULL " +
           "AND d.cotizadoProveedorPremium = false " +
           "AND d.esRegalo = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "ORDER BY d.textoOriginal ASC")
    List<DetalleListaEscolar> findFaltantesPremium();

    /**
     * Encuentra todos los items con cotización de proveedor (pendientes de compra).
     */
    @Query("SELECT d FROM DetalleListaEscolar d " +
           "JOIN FETCH d.listaEscolar l " +
           "WHERE (d.cotizadoProveedorEconomico = true " +
           "   OR d.cotizadoProveedorMedio = true " +
           "   OR d.cotizadoProveedorPremium = true) " +
           "AND d.productoComprado = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "ORDER BY d.fechaCotizacionProveedor DESC")
    List<DetalleListaEscolar> findCotizadosProveedor();

    /**
     * Cuenta items sin producto por texto (para agrupar).
     */
    @Query("SELECT LOWER(TRIM(d.textoOriginal)) as texto, COUNT(d) as veces, SUM(d.cantidadSolicitada) as cantidad " +
           "FROM DetalleListaEscolar d " +
           "JOIN d.listaEscolar l " +
           "WHERE d.textoOriginal IS NOT NULL " +
           "AND d.productoEconomico IS NULL AND d.productoMedio IS NULL AND d.productoPremium IS NULL " +
           "AND d.cotizadoProveedorEconomico = false " +
           "AND d.cotizadoProveedorMedio = false " +
           "AND d.cotizadoProveedorPremium = false " +
           "AND d.esRegalo = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "GROUP BY LOWER(TRIM(d.textoOriginal)) " +
           "ORDER BY COUNT(d) DESC")
    List<Object[]> countFaltantesAgrupados();

    /**
     * Busca items faltantes por texto original (para detalle de un producto faltante específico).
     * Reemplaza findAll() + filtrado en memoria.
     */
    @Query("SELECT d FROM DetalleListaEscolar d " +
           "JOIN FETCH d.listaEscolar l " +
           "WHERE d.textoOriginal IS NOT NULL " +
           "AND d.esRegalo = false " +
           "AND d.estado NOT IN ('VENDIDO', 'CANCELADO') " +
           "AND l.estado NOT IN ('COMPLETADA', 'CANCELADA') " +
           "AND LOWER(TRIM(d.textoOriginal)) = :textoNormalizado " +
           "AND (d.productoEconomico IS NULL AND d.cotizadoProveedorEconomico = false " +
           "  OR d.productoMedio IS NULL AND d.cotizadoProveedorMedio = false " +
           "  OR d.productoPremium IS NULL AND d.cotizadoProveedorPremium = false) " +
           "ORDER BY l.fechaCreacion DESC")
    List<DetalleListaEscolar> findFaltantesPorTexto(@Param("textoNormalizado") String textoNormalizado);
}
