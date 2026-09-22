package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProductoCategorizacionService {

    private final ProductoRepository productoRepository;

    public ProductoCategorizacionService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<Producto> listarProductos(boolean soloSinCategoria) {
        return soloSinCategoria ? productoRepository.findSinCategoriaOrdenados()
                : productoRepository.findProductosParaCategorizacion();
    }

    @Transactional
    public int aplicarCategoria(String categoria, List<Long> productoIds) {
        if (productoIds == null || productoIds.isEmpty()) return 0;
        String valor = categoria != null ? categoria.trim().toUpperCase() : null;
        int actualizados = 0;
        for (Producto producto : productoRepository.findAllById(productoIds)) {
            if (producto == null || producto.esLamina() || producto.esCatalogoPersonalizado()) continue;
            producto.setCategoria(valor);
            productoRepository.save(producto);
            actualizados++;
        }
        return actualizados;
    }
}
