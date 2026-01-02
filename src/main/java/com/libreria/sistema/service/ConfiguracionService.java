package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;

@Service
public class ConfiguracionService {

    private final ConfiguracionRepository repository;

    public ConfiguracionService(ConfiguracionRepository repository) {
        this.repository = repository;
    }

    public Configuracion obtenerConfiguracion() {
        return repository.findById(1L).orElseGet(() -> {
            // Si no existe (primer uso), creamos una por defecto
            Configuracion config = new Configuracion();
            config.setNombreEmpresa("MI EMPRESA S.A.C.");
            config.setRuc("20000000001");
            config.setDireccion("Dirección por configurar");
            config.setMoneda("S/");
            return repository.save(config);
        });
    }

    public void guardarConfiguracion(Configuracion config) {
        config.setId(1L); // Forzamos que siempre sea el ID 1 (Singleton en BD)
        repository.save(config);
    }
}