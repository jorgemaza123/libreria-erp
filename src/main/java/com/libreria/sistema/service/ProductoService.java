package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
public class ProductoService {

    private final ProductoRepository productoRepository;

    public ProductoService(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    public List<Producto> listarTodos() {
        return productoRepository.findAll(); // Mostrar todos, incluso inactivos en el inventario
    }
    
    public List<Producto> listarActivos() {
        return productoRepository.findByActivoTrue();
    }

    public Optional<Producto> obtenerPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Transactional
    public void guardar(Producto producto) throws Exception {
        // 1. Si no tiene Código Interno, generar uno automático (PROD-0001)
        if (producto.getCodigoInterno() == null || producto.getCodigoInterno().trim().isEmpty()) {
            long cantidad = productoRepository.count();
            producto.setCodigoInterno("PROD-" + String.format("%04d", cantidad + 1));
        }
        // Validar Código de Barras duplicado
        Optional<Producto> existenteBarra = productoRepository.findByCodigoBarra(producto.getCodigoBarra());
        if (existenteBarra.isPresent() && !existenteBarra.get().getId().equals(producto.getId())) {
            throw new Exception("El código de barras ya existe en el sistema.");
        }

        // Validar Código Interno duplicado (si se ingresó)
        if (producto.getCodigoInterno() != null && !producto.getCodigoInterno().isEmpty()) {
            Optional<Producto> existenteInterno = productoRepository.findByCodigoInterno(producto.getCodigoInterno());
            if (existenteInterno.isPresent() && !existenteInterno.get().getId().equals(producto.getId())) {
                throw new Exception("El código interno ya existe.");
            }
        }
        

        productoRepository.save(producto);
    }

    @Transactional
    public void eliminar(Long id) {
        // Baja lógica: No borramos el registro, solo lo desactivamos
        productoRepository.findById(id).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }
}