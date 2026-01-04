package com.libreria.sistema.repository;

import com.libreria.sistema.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    Optional<Role> findByNombre(String nombre);

    List<Role> findByActivo(Boolean activo);

    List<Role> findByActivoOrderByNombreAsc(Boolean activo);

    boolean existsByNombre(String nombre);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.id = :id")
    Optional<Role> findByIdWithPermissions(Long id);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.nombre = :nombre")
    Optional<Role> findByNombreWithPermissions(String nombre);
}
