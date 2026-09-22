package com.libreria.sistema.service;

import com.libreria.sistema.model.ConfigCuentaFija;
import com.libreria.sistema.model.dto.CuentaFijaDTO;
import com.libreria.sistema.repository.ConfigCuentaFijaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class CuentaFijaService {

    private final ConfigCuentaFijaRepository repository;

    public CuentaFijaService(ConfigCuentaFijaRepository repository) {
        this.repository = repository;
    }

    public List<ConfigCuentaFija> listarTodas() {
        return repository.findAllByOrderByActivaDescOrdenAscNombreAsc();
    }

    public List<ConfigCuentaFija> listarActivas() {
        return repository.findByActivaTrueOrderByOrdenAscNombreAsc();
    }

    public BigDecimal totalMensualActivo() {
        return listarActivas().stream()
                .map(ConfigCuentaFija::getMontoMensual)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public ConfigCuentaFija guardar(CuentaFijaDTO dto) {
        ConfigCuentaFija cuenta = dto.getId() != null
                ? repository.findById(dto.getId()).orElse(new ConfigCuentaFija())
                : new ConfigCuentaFija();
        cuenta.setNombre(dto.getNombre());
        cuenta.setMontoMensual(dto.getMontoMensual() != null ? dto.getMontoMensual() : BigDecimal.ZERO);
        cuenta.setCategoria(dto.getCategoria());
        cuenta.setTipoCosto(dto.getTipoCosto());
        cuenta.setReglaReparto(dto.getReglaReparto());
        cuenta.setCategoriaObjetivo(dto.getCategoriaObjetivo());
        cuenta.setBaseMensual(dto.getBaseMensual() != null ? dto.getBaseMensual() : BigDecimal.ZERO);
        cuenta.setPorcentajeUsoCosteo(dto.getPorcentajeUsoCosteo() != null ? dto.getPorcentajeUsoCosteo() : new BigDecimal("100.00"));
        cuenta.setIncluirEnCosteo(dto.getIncluirEnCosteo() == null ? true : dto.getIncluirEnCosteo());
        cuenta.setNotas(dto.getNotas());
        cuenta.setActiva(dto.getActiva() == null ? true : dto.getActiva());
        cuenta.setOrden(dto.getOrden() == null ? 0 : dto.getOrden());
        return repository.save(cuenta);
    }
}
