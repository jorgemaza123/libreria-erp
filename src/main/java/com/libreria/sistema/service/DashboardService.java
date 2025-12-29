package com.libreria.sistema.service;

import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.ReporteDTO;
import com.libreria.sistema.repository.ProductoRepository;
import com.libreria.sistema.repository.VentaRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DashboardService {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;

    public DashboardService(ProductoRepository productoRepository, VentaRepository ventaRepository) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
    }

    public Map<String, Object> obtenerDatosDashboard() {
        // Datos para Gráficos
        List<ReporteDTO> topProductos = productoRepository.obtenerTopProductos();
        List<ReporteDTO> ventasSemana = ventaRepository.obtenerVentasUltimaSemana();

        // Datos para Tablas de Alerta
        List<Producto> stockCritico = productoRepository.obtenerStockCritico();
        List<Producto> sinMovimiento = productoRepository.obtenerProductosSinMovimiento();

        return Map.of(
            "topProductos", topProductos,
            "ventasSemana", ventasSemana,
            "stockCritico", stockCritico,
            "sinMovimiento", sinMovimiento
        );
    }
}