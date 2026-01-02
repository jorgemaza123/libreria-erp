package com.libreria.sistema.repository;

import com.libreria.sistema.model.SesionCaja;
import com.libreria.sistema.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface SesionCajaRepository extends JpaRepository<SesionCaja, Long> {
    // Buscar si hay una caja abierta para un usuario (o global si quitas el usuario)
    Optional<SesionCaja> findByUsuarioAndEstado(Usuario usuario, String estado);
}