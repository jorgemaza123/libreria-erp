package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
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
        return productoRepository.findAll();
    }
    
    public List<Producto> listarActivos() {
        return productoRepository.findByActivoTrue();
    }

    public Optional<Producto> obtenerPorId(Long id) {
        return productoRepository.findById(id);
    }

    @Transactional
    @Auditable(modulo = "PRODUCTOS", accion = "MODIFICAR", descripcion = "Guardar producto")
    public void guardar(Producto producto) throws Exception {
        if (producto.getCodigoInterno() == null || producto.getCodigoInterno().trim().isEmpty()) {
            long cantidad = productoRepository.count();
            producto.setCodigoInterno("PROD-" + String.format("%04d", cantidad + 1));
        }
        Optional<Producto> existenteBarra = productoRepository.findByCodigoBarra(producto.getCodigoBarra());
        if (existenteBarra.isPresent() && !existenteBarra.get().getId().equals(producto.getId())) {
            throw new Exception("El código de barras ya existe en el sistema.");
        }

        if (producto.getCodigoInterno() != null && !producto.getCodigoInterno().isEmpty()) {
            Optional<Producto> existenteInterno = productoRepository.findByCodigoInterno(producto.getCodigoInterno());
            if (existenteInterno.isPresent() && !existenteInterno.get().getId().equals(producto.getId())) {
                throw new Exception("El código interno ya existe.");
            }
        }
        

        productoRepository.save(producto);
    }

    @Transactional
    @Auditable(modulo = "PRODUCTOS", accion = "ELIMINAR", descripcion = "Eliminar producto")
    public void eliminar(Long id) {
        // Baja lógica: No borramos el registro, solo lo desactivamos
        productoRepository.findById(id).ifPresent(p -> {
            p.setActivo(false);
            productoRepository.save(p);
        });
    }
}