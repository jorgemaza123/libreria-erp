package com.libreria.sistema.repository;
import com.libreria.sistema.model.SolicitudProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface SolicitudProductoRepository extends JpaRepository<SolicitudProducto, Long> {
    Optional<SolicitudProducto> findByNombreProductoAndEstado(String nombre, String estado);
// NUEVO: Para listar en la tabla ordenado por los más pedidos
    List<SolicitudProducto> findByEstadoOrderByContadorDesc(String estado);
}