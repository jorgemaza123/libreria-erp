package com.libreria.sistema.repository;

import com.libreria.sistema.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    
    Optional<Producto> findByCodigoBarra(String codigoBarra);
    Optional<Producto> findByCodigoInterno(String codigoInterno);
    List<Producto> findByActivoTrue();

    // Búsqueda inteligente: Busca en nombre O en categoría O en código
    @Query("SELECT p FROM Producto p WHERE p.activo = true AND " +
           "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
           "p.codigoInterno LIKE %:termino%)")
    List<Producto> buscarInteligente(String termino);

    // 1. Top Productos Más Vendidos
    @Query("SELECT new com.libreria.sistema.model.dto.ReporteDTO(p.nombre, SUM(d.cantidad)) " +
           "FROM DetalleVenta d JOIN d.producto p " +
           "GROUP BY p.nombre " +
           "ORDER BY SUM(d.cantidad) DESC LIMIT 5")
    List<com.libreria.sistema.model.dto.ReporteDTO> obtenerTopProductos();

    // 2. Productos Sin Movimiento (Estancados en los últimos 30 días)
    // Selecciona productos que NO están en la lista de ventas recientes
    @Query(value = "SELECT * FROM productos p WHERE p.id NOT IN " +
                   "(SELECT DISTINCT d.producto_id FROM detalle_ventas d JOIN ventas v ON d.venta_id = v.id " +
                   "WHERE v.fecha_emision >= date('now', '-30 days')) " +
                   "AND p.stock_actual > 0", nativeQuery = true)
    List<Producto> obtenerProductosSinMovimiento();

    // 3. Stock Crítico
    @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo AND p.activo = true")
    List<Producto> obtenerStockCritico();
}