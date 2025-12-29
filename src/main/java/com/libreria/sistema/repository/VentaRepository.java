package com.libreria.sistema.repository;

import com.libreria.sistema.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}