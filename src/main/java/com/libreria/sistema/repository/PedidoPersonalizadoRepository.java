package com.libreria.sistema.repository;

import com.libreria.sistema.model.PedidoPersonalizado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PedidoPersonalizadoRepository extends JpaRepository<PedidoPersonalizado, Long> {

    Optional<PedidoPersonalizado> findByCodigoPedido(String codigoPedido);

    @EntityGraph(attributePaths = {"items", "items.componentes", "items.plantilla"})
    @Query("SELECT p FROM PedidoPersonalizado p WHERE p.id = :id")
    Optional<PedidoPersonalizado> findDetalleById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"items"})
    List<PedidoPersonalizado> findAllByOrderByFechaCreacionDesc();

    @EntityGraph(attributePaths = {"items"})
    @Query("SELECT p FROM PedidoPersonalizado p WHERE (:estado IS NULL OR p.estado = :estado) AND (" +
            ":termino IS NULL OR LOWER(COALESCE(p.codigoPedido, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(p.clienteNombre, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(p.clienteNumeroDocumento, '')) LIKE LOWER(CONCAT('%', :termino, '%')) OR " +
            "LOWER(COALESCE(p.nombreDestinatario, '')) LIKE LOWER(CONCAT('%', :termino, '%')))")
    List<PedidoPersonalizado> buscar(@Param("termino") String termino, @Param("estado") String estado);

    @Query("SELECT COUNT(p) FROM PedidoPersonalizado p WHERE p.estado IN ('BORRADOR','COTIZADO','CONFIRMADO','EN_PRODUCCION','LISTO')")
    long countPendientes();
}
