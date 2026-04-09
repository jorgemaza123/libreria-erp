package com.libreria.sistema.repository;

import com.libreria.sistema.model.Producto;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

       // =====================================================
       // MÉTODOS CON LOCK PESIMISTA PARA STOCK (CONCURRENCIA)
       // =====================================================

       /**
        * Obtiene un producto con LOCK PESIMISTA para operaciones de stock.
        * CRÍTICO: Usar este método cuando se va a descontar stock para evitar
        * que dos ventas simultáneas vendan el mismo último ítem.
        *
        * El lock se mantiene hasta el fin de la transacción.
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM Producto p WHERE p.id = :id")
       Optional<Producto> findByIdWithLock(@Param("id") Long id);

       /**
        * Obtiene producto por código de barras con LOCK PESIMISTA
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM Producto p WHERE p.codigoBarra = :codigoBarra")
       Optional<Producto> findByCodigoBarraWithLock(@Param("codigoBarra") String codigoBarra);

       /**
        * Obtiene producto por código interno con LOCK PESIMISTA
        */
       @Lock(LockModeType.PESSIMISTIC_WRITE)
       @Query("SELECT p FROM Producto p WHERE p.codigoInterno = :codigoInterno")
       Optional<Producto> findByCodigoInternoWithLock(@Param("codigoInterno") String codigoInterno);

       // =====================================================
       // MÉTODOS CON PAGINACIÓN
       // =====================================================

       /**
        * Listado paginado de todos los productos activos
        */
       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.nombre ASC")
       Page<Producto> findByActivoTruePaginated(Pageable pageable);

       /**
        * Listado paginado de todos los productos (activos e inactivos)
        */
       @Query("SELECT p FROM Producto p ORDER BY p.activo DESC, p.nombre ASC")
       Page<Producto> findAllPaginated(Pageable pageable);

       /**
        * Búsqueda paginada por categoría
        */
       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND LOWER(p.categoria) = LOWER(:categoria) ORDER BY p.nombre ASC")
       Page<Producto> findByCategoriaPaginated(@Param("categoria") String categoria, Pageable pageable);

       /**
        * Búsqueda inteligente paginada
        */
       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' AND " +
                     "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
                     "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
                     "p.codigoInterno LIKE %:termino% OR " +
                     "p.codigoBarra LIKE %:termino%) ORDER BY p.nombre ASC")
       Page<Producto> buscarInteligentePaginated(@Param("termino") String termino, Pageable pageable);

       // =====================================================
       // MÉTODOS EXISTENTES (MANTENIDOS)
       // =====================================================

       Optional<Producto> findByCodigoBarra(String codigoBarra);

       Optional<Producto> findByCodigoInterno(String codigoInterno);

       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO'")
       List<Producto> findByActivoTrue();

       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.nombre ASC")
       List<Producto> findByActivoTrueOrderByNombreAsc();

        @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = true " +
                     "ORDER BY COALESCE(p.laminaTitulo, p.nombre) ASC")
       List<Producto> findLaminasActivasOrdenadas();

       @Query("SELECT p FROM Producto p WHERE COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                    "AND COALESCE(p.esLamina, false) = true " +
                    "ORDER BY p.activo DESC, COALESCE(p.laminaTitulo, p.nombre) ASC")
       List<Producto> findTodasLasLaminasOrdenadas();

       Optional<Producto> findFirstByEsLaminaTrueAndLaminaNumeroIgnoreCase(String laminaNumero);

       @Query("SELECT p FROM Producto p " +
                     "WHERE COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = true " +
                     "AND LOWER(COALESCE(p.laminaNumero, '')) = LOWER(COALESCE(:laminaNumero, '')) " +
                     "AND LOWER(COALESCE(p.laminaTitulo, '')) = LOWER(COALESCE(:laminaTitulo, '')) " +
                     "AND LOWER(COALESCE(p.laminaContenedor, '')) = LOWER(COALESCE(:laminaContenedor, '')) " +
                     "ORDER BY p.id ASC")
       List<Producto> findLaminasCoincidenciaExacta(@Param("laminaNumero") String laminaNumero,
                     @Param("laminaTitulo") String laminaTitulo,
                     @Param("laminaContenedor") String laminaContenedor);

       @Query("SELECT p FROM Producto p WHERE COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.nombre ASC")
       List<Producto> findCatalogoGeneralOrdenado();

       @Query("SELECT p FROM Producto p WHERE COALESCE(p.origenCatalogo, 'GENERAL') = 'PERSONALIZADO' ORDER BY p.nombre ASC")
       List<Producto> findCatalogoPersonalizadoOrdenado();

       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') = 'PERSONALIZADO' ORDER BY p.nombre ASC")
       List<Producto> findCatalogoPersonalizadoActivoOrdenado();

       /**
        * Búsqueda inteligente: Busca en nombre O en categoría O en código
        */
       @Query("SELECT p FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' AND " +
                     "(LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
                     "LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
                     "p.codigoInterno LIKE %:termino%)")
       List<Producto> buscarInteligente(@Param("termino") String termino);

       /**
        * Top 5 Productos Más Vendidos (sin paginación - para Dashboard)
        */
       @Query(value = "SELECT new com.libreria.sistema.model.dto.ReporteDTO(p.nombre, SUM(d.cantidad)) " +
                     "FROM DetalleVenta d JOIN d.producto p " +
                     "GROUP BY p.nombre " +
                     "ORDER BY SUM(d.cantidad) DESC " +
                     "LIMIT 5")
       List<com.libreria.sistema.model.dto.ReporteDTO> obtenerTopProductos();

       /**
        * Top Productos Más Vendidos con paginación (para reportes)
        */
       @Query("SELECT new com.libreria.sistema.model.dto.ReporteDTO(p.nombre, SUM(d.cantidad)) " +
                     "FROM DetalleVenta d JOIN d.producto p " +
                     "GROUP BY p.nombre " +
                     "ORDER BY SUM(d.cantidad) DESC")
       List<com.libreria.sistema.model.dto.ReporteDTO> obtenerTopProductosPaginated(Pageable pageable);

       /**
        * Productos Sin Movimiento (Estancados en los últimos 30 días)
        */
       @Query(value = "SELECT * FROM productos p WHERE p.id NOT IN " +
                     "(SELECT DISTINCT d.producto_id FROM detalle_ventas d JOIN ventas v ON d.venta_id = v.id " +
                     "WHERE v.fecha_emision >= CURRENT_DATE - INTERVAL '30 days') " +
                     "AND p.stock_actual > 0 AND p.activo = true " +
                     "AND COALESCE(p.origen_catalogo, 'GENERAL') <> 'PERSONALIZADO'", nativeQuery = true)
       List<Producto> obtenerProductosSinMovimiento();

       /**
        * Stock Crítico (productos activos con stock bajo)
        */
       @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo AND p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.stockActual ASC")
       List<Producto> obtenerStockCritico();

       /**
        * Stock Crítico con paginación
        */
       @Query("SELECT p FROM Producto p WHERE p.stockActual <= p.stockMinimo AND p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.stockActual ASC")
       Page<Producto> obtenerStockCriticoPaginated(Pageable pageable);

       /**
        * Contar productos activos (excluye láminas)
        */
       @Query("SELECT COUNT(p) FROM Producto p WHERE p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = false")
       long countActivos();

       /**
        * Contar productos con stock crítico (excluye láminas)
        */
       @Query("SELECT COUNT(p) FROM Producto p WHERE p.stockActual <= p.stockMinimo AND p.activo = true " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = false")
       long countStockCritico();

       /**
        * Obtener categorías únicas de productos activos
        */
       @Query("SELECT DISTINCT p.categoria FROM Producto p WHERE p.activo = true AND p.categoria IS NOT NULL " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' ORDER BY p.categoria")
       List<String> findDistinctCategorias();

       /**
        * Obtener el último código interno (SKU) con formato SKU-XXXXX
        */
       @Query("SELECT p.codigoInterno FROM Producto p WHERE p.codigoInterno LIKE 'SKU-%' ORDER BY p.codigoInterno DESC LIMIT 1")
       Optional<String> findUltimoSku();

       // =====================================================
       // STOCK MODULE QUERIES
       // =====================================================

       @Query("SELECT COUNT(p) FROM Producto p WHERE p.activo = true AND p.stockActual = 0 " +
                     "AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = false")
       long countSinStock();

       @Query("SELECT COALESCE(SUM(p.stockActual * p.precioCompra), 0) FROM Producto p WHERE p.activo = true " +
                     "AND p.precioCompra IS NOT NULL AND COALESCE(p.origenCatalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.esLamina, false) = false")
       java.math.BigDecimal calcularValorInventario();

       @Query(value = "SELECT * FROM productos p WHERE p.activo = true " +
                     "AND COALESCE(p.origen_catalogo, 'GENERAL') <> 'PERSONALIZADO' " +
                     "AND COALESCE(p.es_lamina, false) = false " +
                     "AND (:termino IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                     "    OR LOWER(p.codigo_interno) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                     "    OR LOWER(p.codigo_barra) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                     "    OR LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                     "AND (:categoria IS NULL OR LOWER(p.categoria) = LOWER(:categoria)) " +
                     "AND (:estado IS NULL " +
                     "    OR (:estado = 'SIN_STOCK' AND p.stock_actual = 0) " +
                     "    OR (:estado = 'CRITICO' AND p.stock_actual > 0 AND p.stock_actual <= p.stock_minimo) " +
                     "    OR (:estado = 'BAJO' AND p.stock_actual > p.stock_minimo AND p.stock_actual <= p.stock_minimo * 1.5) "
                     +
                     "    OR (:estado = 'OK' AND p.stock_actual > p.stock_minimo * 1.5)) ", countQuery = "SELECT COUNT(*) FROM productos p WHERE p.activo = true "
                                   +
                                   "AND COALESCE(p.origen_catalogo, 'GENERAL') <> 'PERSONALIZADO' "
                                   +
                                   "AND COALESCE(p.es_lamina, false) = false "
                                   +
                                   "AND (:termino IS NULL OR LOWER(p.nombre) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                                   "    OR LOWER(p.codigo_interno) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                                   "    OR LOWER(p.codigo_barra) LIKE LOWER(CONCAT('%', :termino, '%')) " +
                                   "    OR LOWER(p.categoria) LIKE LOWER(CONCAT('%', :termino, '%'))) " +
                                   "AND (:categoria IS NULL OR LOWER(p.categoria) = LOWER(:categoria)) " +
                                   "AND (:estado IS NULL " +
                                   "    OR (:estado = 'SIN_STOCK' AND p.stock_actual = 0) " +
                                   "    OR (:estado = 'CRITICO' AND p.stock_actual > 0 AND p.stock_actual <= p.stock_minimo) "
                                   +
                                   "    OR (:estado = 'BAJO' AND p.stock_actual > p.stock_minimo AND p.stock_actual <= p.stock_minimo * 1.5) "
                                   +
                                   "    OR (:estado = 'OK' AND p.stock_actual > p.stock_minimo * 1.5)) ", nativeQuery = true)
       Page<Producto> buscarStockFiltrado(@Param("termino") String termino,
                     @Param("categoria") String categoria,
                     @Param("estado") String estado,
                     Pageable pageable);
}
