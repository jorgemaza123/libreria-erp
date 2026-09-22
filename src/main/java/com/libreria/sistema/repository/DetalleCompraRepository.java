package com.libreria.sistema.repository;
import com.libreria.sistema.model.DetalleCompra;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface DetalleCompraRepository extends JpaRepository<DetalleCompra, Long> {

    @Query("SELECT d FROM DetalleCompra d JOIN FETCH d.compra c LEFT JOIN FETCH c.proveedor " +
           "WHERE d.producto.id = :productoId AND c.estado != 'ANULADA' " +
           "ORDER BY c.fecha DESC, d.id DESC")
    List<DetalleCompra> ultimasComprasProducto(@Param("productoId") Long productoId, Pageable pageable);

    @Query("SELECT d FROM DetalleCompra d JOIN FETCH d.compra c LEFT JOIN FETCH c.proveedor " +
           "WHERE d.producto.id IN :productoIds AND c.estado != 'ANULADA' " +
           "ORDER BY d.producto.id, c.fecha DESC, d.id DESC")
    List<DetalleCompra> ultimasComprasProductos(@Param("productoIds") Collection<Long> productoIds);

    @Query("SELECT d.producto.id, p.razonSocial, c.fecha " +
           "FROM DetalleCompra d JOIN d.compra c LEFT JOIN c.proveedor p " +
           "WHERE d.producto.id IN :productoIds AND c.estado != 'ANULADA' " +
           "ORDER BY d.producto.id, c.fecha DESC, d.id DESC")
    List<Object[]> ultimosProveedoresPorProducto(@Param("productoIds") Collection<Long> productoIds);
}
