package com.libreria.sistema.service;

import com.libreria.sistema.model.Configuracion;
import com.libreria.sistema.model.Producto;
import com.libreria.sistema.model.dto.CosteoPreviewRequestDTO;
import com.libreria.sistema.model.dto.CosteoPreviewResponseDTO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class CosteoCalculatorService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    private final ConfiguracionService configuracionService;
    private final CosteoSugerenciaService costeoSugerenciaService;

    public CosteoCalculatorService(ConfiguracionService configuracionService,
                                   CosteoSugerenciaService costeoSugerenciaService) {
        this.configuracionService = configuracionService;
        this.costeoSugerenciaService = costeoSugerenciaService;
    }

    public CosteoPreviewResponseDTO simular(CosteoPreviewRequestDTO request) {
        return simular(request, null);
    }

    public CosteoPreviewResponseDTO simular(CosteoPreviewRequestDTO request, Producto producto) {
        Configuracion config = configuracionService.obtenerConfiguracion();
        CosteoPreviewResponseDTO out = new CosteoPreviewResponseDTO();

        BigDecimal cantidad = nz(request.getCantidadComprada());
        BigDecimal total = nz(request.getTotalPagado());
        BigDecimal sugerido = costeoSugerenciaService.obtenerFactorSugeridoGlobal();
        BigDecimal manual = request.getFactorIndirectoManualPct();
        BigDecimal factorAplicado = manual != null ? nz(manual) : sugerido;
        BigDecimal gananciaObjetivo = resolverGananciaObjetivo(request, producto, config);
        BigDecimal gananciaMinima = resolverGananciaMinima(request, producto, config);
        BigDecimal precioVentaActual = nz(request.getPrecioVentaActual());

        out.setCantidadComprada(cantidad);
        out.setTotalPagado(total);
        out.setFactorIndirectoSugeridoPct(sugerido);
        out.setFactorIndirectoAplicadoPct(factorAplicado);
        out.setGananciaObjetivoPct(gananciaObjetivo);
        out.setGananciaMinimaPct(gananciaMinima);

        if (cantidad.compareTo(BigDecimal.ZERO) <= 0 || total.compareTo(BigDecimal.ZERO) <= 0) {
            out.setEstado("SIN_DATOS");
            out.setMensajeEstado("Ingresa cantidad comprada y total pagado.");
            return out;
        }

        BigDecimal costoBase = total.divide(cantidad, 4, RoundingMode.HALF_UP);
        BigDecimal costoReal = costoBase.multiply(BigDecimal.ONE.add(factorAplicado.divide(CIEN, 6, RoundingMode.HALF_UP)))
                .setScale(4, RoundingMode.HALF_UP);
        BigDecimal precioMinimo = aplicarGanancia(costoReal, gananciaMinima).setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioSugerido = aplicarGanancia(costoReal, gananciaObjetivo).setScale(2, RoundingMode.HALF_UP);
        BigDecimal precioSugeridoFinal = aplicarRedondeoRetail(precioSugerido, config);
        BigDecimal utilidad = precioVentaActual.compareTo(BigDecimal.ZERO) > 0
                ? precioVentaActual.subtract(costoReal).setScale(2, RoundingMode.HALF_UP)
                : precioSugeridoFinal.subtract(costoReal).setScale(2, RoundingMode.HALF_UP);

        out.setCostoBaseUnitario(costoBase);
        out.setCostoRealUnitario(costoReal);
        out.setPrecioMinimo(precioMinimo);
        out.setPrecioSugerido(precioSugerido);
        out.setPrecioSugeridoFinal(precioSugeridoFinal);
        out.setUtilidadPorUnidad(utilidad);

        if (precioVentaActual.compareTo(BigDecimal.ZERO) > 0 && costoReal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal margen = precioVentaActual.subtract(costoReal)
                    .divide(costoReal, 4, RoundingMode.HALF_UP)
                    .multiply(CIEN)
                    .setScale(2, RoundingMode.HALF_UP);
            out.setMargenActualPct(margen);
        }

        if (precioVentaActual.compareTo(BigDecimal.ZERO) > 0 && precioVentaActual.compareTo(costoReal) < 0) {
            out.setEstado("PERDIDA");
            out.setMensajeEstado("Atencion: ese precio te hace perder dinero.");
        } else if (precioVentaActual.compareTo(BigDecimal.ZERO) > 0 && precioVentaActual.compareTo(precioMinimo) < 0) {
            out.setEstado("BAJO");
            out.setMensajeEstado("Ese precio gana poco y queda debajo del minimo recomendado.");
        } else {
            out.setEstado("RENTABLE");
            out.setMensajeEstado("Precio saludable para vender con mejor control.");
        }

        return out;
    }

    public BigDecimal resolverGananciaObjetivo(Producto producto) {
        return resolverGananciaObjetivo(new CosteoPreviewRequestDTO(), producto, configuracionService.obtenerConfiguracion());
    }

    public BigDecimal resolverGananciaMinima(Producto producto) {
        return resolverGananciaMinima(new CosteoPreviewRequestDTO(), producto, configuracionService.obtenerConfiguracion());
    }

    private BigDecimal resolverGananciaObjetivo(CosteoPreviewRequestDTO request, Producto producto, Configuracion config) {
        if (request.getGananciaObjetivoPct() != null) return nz(request.getGananciaObjetivoPct());
        if (producto != null && Boolean.TRUE.equals(producto.getUsarReglaManualPrecio()) && producto.getGananciaObjetivoPct() != null) {
            return nz(producto.getGananciaObjetivoPct());
        }
        return nz(config.getGananciaObjetivoGlobalPct());
    }

    private BigDecimal resolverGananciaMinima(CosteoPreviewRequestDTO request, Producto producto, Configuracion config) {
        if (request.getGananciaMinimaPct() != null) return nz(request.getGananciaMinimaPct());
        if (producto != null && Boolean.TRUE.equals(producto.getUsarReglaManualPrecio()) && producto.getGananciaMinimaPct() != null) {
            return nz(producto.getGananciaMinimaPct());
        }
        return nz(config.getGananciaMinimaGlobalPct());
    }

    public BigDecimal aplicarGanancia(BigDecimal base, BigDecimal gananciaPct) {
        return base.multiply(BigDecimal.ONE.add(nz(gananciaPct).divide(CIEN, 6, RoundingMode.HALF_UP)));
    }

    public BigDecimal aplicarRedondeoRetail(BigDecimal precio, Configuracion config) {
        String modo = config.getRedondeoPrecioModo() != null ? config.getRedondeoPrecioModo().trim().toUpperCase() : "NINGUNO";
        BigDecimal paso = config.getRedondeoPrecioPaso() != null ? config.getRedondeoPrecioPaso() : BigDecimal.ZERO;
        if (!"MULTIPLO".equals(modo) || paso.compareTo(BigDecimal.ZERO) <= 0) {
            return precio.setScale(2, RoundingMode.HALF_UP);
        }
        BigDecimal factor = precio.divide(paso, 0, RoundingMode.UP);
        return factor.multiply(paso).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
