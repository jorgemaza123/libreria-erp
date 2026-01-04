package com.libreria.sistema.repository;

import com.libreria.sistema.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {
    // Obtener el último número de serie para correlativos
    @Query("SELECT MAX(v.numero) FROM Venta v WHERE v.serie = :serie AND v.tipoComprobante = :tipo")
    Integer obtenerUltimoNumero(String serie, String tipo);

    // Ventas de los últimos 7 días (para gráfico lineal)
    @Query("SELECT new com.libreria.sistema.model.dto.ReporteDTO(CAST(v.fechaEmision AS string), SUM(v.total)) " +
           "FROM Venta v WHERE v.estado = 'EMITIDO' " +
           "GROUP BY v.fechaEmision ORDER BY v.fechaEmision ASC LIMIT 7")
    List<com.libreria.sistema.model.dto.ReporteDTO> obtenerVentasUltimaSemana();

    @Query("SELECT v FROM Venta v WHERE v.clienteNumeroDocumento = :dni AND v.saldoPendiente > 0 AND v.estado != 'ANULADO'")
    List<Venta> findDeudasPorDni(@Param("dni") String dni);

    /**
     * Buscar ventas para devolución por serie-número, cliente o documento
     */
    @Query("SELECT v FROM Venta v LEFT JOIN FETCH v.clienteEntity c " +
           "WHERE (CONCAT(v.serie, '-', v.numero) LIKE %:termino% " +
           "OR LOWER(v.clienteDenominacion) LIKE LOWER(CONCAT('%', :termino, '%')) " +
           "OR v.clienteNumeroDocumento LIKE %:termino%) " +
           "AND v.estado != 'ANULADO' " +
           "ORDER BY v.fechaEmision DESC")
    List<Venta> buscarParaDevolucion(@Param("termino") String termino);

    /**
     * Obtener venta con sus detalles (JOIN FETCH para evitar LazyInitializationException)
     */
    @Query("SELECT v FROM Venta v " +
           "LEFT JOIN FETCH v.items d " +
           "LEFT JOIN FETCH d.producto p " +
           "LEFT JOIN FETCH v.clienteEntity c " +
           "WHERE v.id = :id")
    Optional<Venta> findByIdWithDetalles(@Param("id") Long id);
}