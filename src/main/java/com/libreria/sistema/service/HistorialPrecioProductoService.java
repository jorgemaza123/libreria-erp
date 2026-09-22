package com.libreria.sistema.service;

import com.libreria.sistema.model.HistorialPrecioProducto;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.HistorialPrecioProductoRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class HistorialPrecioProductoService {

    private final HistorialPrecioProductoRepository repository;

    public HistorialPrecioProductoService(HistorialPrecioProductoRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void registrar(Producto producto,
                          BigDecimal precioAnterior,
                          BigDecimal precioNuevo,
                          BigDecimal costoAnterior,
                          BigDecimal costoNuevo,
                          BigDecimal precioMinimo,
                          BigDecimal precioSugerido,
                          String origen,
                          String motivo) {
        if (producto == null || producto.getId() == null) {
            return;
        }
        BigDecimal anterior = dinero(precioAnterior);
        BigDecimal nuevo = dinero(precioNuevo);
        BigDecimal costoAnt = costo(costoAnterior);
        BigDecimal costoNvo = costo(costoNuevo);
        boolean cambioPrecio = anterior.compareTo(nuevo) != 0;
        boolean cambioCosto = costoAnt.compareTo(costoNvo) != 0;
        if (!cambioPrecio && !cambioCosto) {
            return;
        }

        HistorialPrecioProducto historial = new HistorialPrecioProducto();
        historial.setProducto(producto);
        historial.setPrecioAnterior(anterior);
        historial.setPrecioNuevo(nuevo);
        historial.setCostoAnterior(costoAnt);
        historial.setCostoNuevo(costoNvo);
        historial.setPrecioMinimo(dinero(precioMinimo));
        historial.setPrecioSugerido(dinero(precioSugerido));
        historial.setOrigen(origen);
        historial.setMotivo(motivo);
        historial.setUsuario(usuarioActual());
        repository.save(historial);
    }

    @Transactional(readOnly = true)
    public List<HistorialPrecioProducto> ultimos(Long productoId) {
        return repository.findTop20ByProductoIdOrderByFechaDesc(productoId);
    }

    private BigDecimal dinero(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal costo(BigDecimal value) {
        return (value != null ? value : BigDecimal.ZERO).setScale(4, RoundingMode.HALF_UP);
    }

    private String usuarioActual() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null || auth.getName().isBlank()) {
            return "sistema";
        }
        return auth.getName();
    }
}
