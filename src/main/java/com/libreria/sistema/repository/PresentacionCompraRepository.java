package com.libreria.sistema.repository;

import com.libreria.sistema.model.PresentacionCompra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PresentacionCompraRepository extends JpaRepository<PresentacionCompra, Long> {

    @Query("SELECT p FROM PresentacionCompra p WHERE p.activa = true AND p.tipoCatalogo = :tipoCatalogo AND p.producto IS NULL AND p.insumoPersonalizado IS NULL ORDER BY p.predeterminada DESC, p.orden ASC, p.nombrePresentacion ASC")
    List<PresentacionCompra> findGlobalesActivas(@Param("tipoCatalogo") String tipoCatalogo);

    @Query("SELECT p FROM PresentacionCompra p WHERE p.activa = true AND p.producto.id = :productoId ORDER BY p.predeterminada DESC, p.orden ASC, p.nombrePresentacion ASC")
    List<PresentacionCompra> findByProductoId(@Param("productoId") Long productoId);

    @Query("SELECT p FROM PresentacionCompra p WHERE p.activa = true AND p.insumoPersonalizado.id = :insumoId ORDER BY p.predeterminada DESC, p.orden ASC, p.nombrePresentacion ASC")
    List<PresentacionCompra> findByInsumoPersonalizadoId(@Param("insumoId") Long insumoId);

    void deleteByProductoId(Long productoId);

    void deleteByInsumoPersonalizadoId(Long insumoId);
}
