package com.libreria.sistema.repository;
import com.libreria.sistema.model.Kardex;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KardexRepository extends JpaRepository<Kardex, Long> {
    // Para ver el historial de UN producto específico
    List<Kardex> findByProductoIdOrderByFechaDesc(Long productoId);
}