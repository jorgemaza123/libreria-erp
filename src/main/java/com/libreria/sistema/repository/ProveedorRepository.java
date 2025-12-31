package com.libreria.sistema.repository;
import com.libreria.sistema.model.Proveedor;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    List<Proveedor> findByActivoTrue(); // Para listar solo los activos en combos

    // Para buscar por RUC y evitar duplicados (opcional pero recomendado)
    boolean existsByRuc(String ruc);
}