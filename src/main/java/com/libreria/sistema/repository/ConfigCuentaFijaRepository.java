package com.libreria.sistema.repository;

import com.libreria.sistema.model.ConfigCuentaFija;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConfigCuentaFijaRepository extends JpaRepository<ConfigCuentaFija, Long> {
    List<ConfigCuentaFija> findByActivaTrueOrderByOrdenAscNombreAsc();
    List<ConfigCuentaFija> findAllByOrderByActivaDescOrdenAscNombreAsc();
}
