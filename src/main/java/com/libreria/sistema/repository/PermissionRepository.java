package com.libreria.sistema.repository;

import com.libreria.sistema.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    Optional<Permission> findByCodigo(String codigo);

    List<Permission> findByModulo(String modulo);

    List<Permission> findByModuloOrderByAccionAsc(String modulo);

    boolean existsByCodigo(String codigo);
}
