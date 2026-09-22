package com.libreria.sistema.repository;

import com.libreria.sistema.model.Role;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsername(String username);

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT DISTINCT u FROM Usuario u")
    List<Usuario> findAllWithRoles();

    @EntityGraph(attributePaths = {"roles"})
    @Query("SELECT u FROM Usuario u WHERE u.id = :id")
    Optional<Usuario> findByIdWithRoles(@Param("id") Long id);

    /**
     * Búsqueda de usuario insensible a mayúsculas/minúsculas.
     * Útil para login desde dispositivos móviles donde el teclado auto-capitaliza.
     */
    Optional<Usuario> findByUsernameIgnoreCase(String username);

    long countByRole(Role role);

    @Query("SELECT u.role.id, COUNT(u) " +
           "FROM Usuario u " +
           "WHERE u.role.id IN :roleIds " +
           "GROUP BY u.role.id")
    List<Object[]> countUsuariosByRoleIds(@Param("roleIds") Collection<Long> roleIds);
}
