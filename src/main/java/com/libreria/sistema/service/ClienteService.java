package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.Cotizacion;
import com.libreria.sistema.model.OrdenServicio;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.ClienteRepository;
import com.libreria.sistema.repository.CotizacionRepository;
import com.libreria.sistema.repository.OrdenServicioRepository;
import com.libreria.sistema.repository.VentaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio de gestión de clientes con:
 * - CRUD completo
 * - Gestión de créditos y límites
 * - Estadísticas de compras
 * - Actualización automática de métricas
 */
@Service
@Slf4j
public class ClienteService {

    private final ClienteRepository clienteRepository;
    private final VentaRepository ventaRepository;
    private final CotizacionRepository cotizacionRepository;
    private final OrdenServicioRepository ordenServicioRepository;

    public ClienteService(ClienteRepository clienteRepository, VentaRepository ventaRepository,
                          CotizacionRepository cotizacionRepository, OrdenServicioRepository ordenServicioRepository) {
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
        this.cotizacionRepository = cotizacionRepository;
        this.ordenServicioRepository = ordenServicioRepository;
    }

    // =====================================================
    //  CRUD BÁSICO
    // =====================================================

    public List<Cliente> listarTodos() {
        return clienteRepository.findByActivoTrue();
    }

    public Page<Cliente> listarPaginado(Pageable pageable) {
        return clienteRepository.findByActivoTrueOrderByNombreRazonSocialAsc(pageable);
    }

    public Optional<Cliente> obtenerPorId(Long id) {
        return clienteRepository.findById(id);
    }

    public Optional<Cliente> obtenerPorDocumento(String numeroDocumento) {
        return clienteRepository.findByNumeroDocumento(numeroDocumento);
    }

    @Transactional
    @Auditable(modulo = "CLIENTES", accion = "CREAR", descripcion = "Guardar cliente")
    public Cliente guardar(Cliente cliente) throws Exception {
        // Validar documento único
        Optional<Cliente> existente = clienteRepository.findByNumeroDocumento(cliente.getNumeroDocumento());
        if (existente.isPresent() && !existente.get().getId().equals(cliente.getId())) {
            throw new Exception("Ya existe un cliente con el documento: " + cliente.getNumeroDocumento());
        }

        // Normalizar nombre
        if (cliente.getNombreRazonSocial() != null) {
            cliente.setNombreRazonSocial(cliente.getNombreRazonSocial().toUpperCase().trim());
        }

        // Validar tipo de documento según longitud
        if (cliente.getTipoDocumento() == null || cliente.getTipoDocumento().isEmpty()) {
            cliente.setTipoDocumento(cliente.getNumeroDocumento().length() == 11 ? "6" : "1");
        }

        // Valores por defecto para crédito
        if (cliente.getLimiteCredito() == null) {
            cliente.setLimiteCredito(BigDecimal.ZERO);
        }
        if (cliente.getSaldoDeudor() == null) {
            cliente.setSaldoDeudor(BigDecimal.ZERO);
        }

        return clienteRepository.save(cliente);
    }

    @Transactional
    @Auditable(modulo = "CLIENTES", accion = "ELIMINAR", descripcion = "Desactivar cliente")
    public void eliminar(Long id) {
        clienteRepository.findById(id).ifPresent(cliente -> {
            cliente.setActivo(false);
            clienteRepository.save(cliente);
        });
    }

    @Transactional
    public void activar(Long id) {
        clienteRepository.findById(id).ifPresent(cliente -> {
            cliente.setActivo(true);
            clienteRepository.save(cliente);
        });
    }

    // =====================================================
    //  BÚSQUEDAS
    // =====================================================

    public List<Cliente> buscar(String termino) {
        if (termino == null || termino.trim().isEmpty()) {
            return listarTodos();
        }
        return clienteRepository.buscarInteligente(termino.trim());
    }

    public Page<Cliente> buscarPaginado(String termino, Pageable pageable) {
        if (termino == null || termino.trim().isEmpty()) {
            return listarPaginado(pageable);
        }
        return clienteRepository.buscarInteligentePaginated(termino.trim(), pageable);
    }

    public List<Cliente> buscarParaAutocompletado(String termino) {
        return clienteRepository.buscarParaAutocompletado(termino);
    }

    // =====================================================
    //  GESTIÓN DE CRÉDITO
    // =====================================================

    public List<Cliente> obtenerClientesConCredito() {
        return clienteRepository.findClientesConCredito();
    }

    public List<Cliente> obtenerClientesConDeuda() {
        return clienteRepository.findClientesConDeuda();
    }

    public List<Cliente> obtenerClientesMorosos() {
        return clienteRepository.findClientesMorosos();
    }

    @Transactional
    @Auditable(modulo = "CLIENTES", accion = "MODIFICAR", descripcion = "Habilitar crédito")
    public void habilitarCredito(Long clienteId, BigDecimal limiteCredito, Integer diasCredito) {
        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            cliente.setTieneCredito(true);
            cliente.setLimiteCredito(limiteCredito);
            cliente.setDiasCredito(diasCredito != null ? diasCredito : 30);
            clienteRepository.save(cliente);
            log.info("Crédito habilitado para cliente {}: límite S/ {}", clienteId, limiteCredito);
        });
    }

    @Transactional
    @Auditable(modulo = "CLIENTES", accion = "MODIFICAR", descripcion = "Deshabilitar crédito")
    public void deshabilitarCredito(Long clienteId) {
        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            cliente.setTieneCredito(false);
            clienteRepository.save(cliente);
            log.info("Crédito deshabilitado para cliente {}", clienteId);
        });
    }

    /**
     * Actualiza el saldo deudor del cliente (llamado desde VentaService y CobranzaController)
     */
    @Transactional
    public void actualizarSaldoDeudor(Long clienteId, BigDecimal nuevoSaldo) {
        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            cliente.setSaldoDeudor(nuevoSaldo);

            // Actualizar categoría si es moroso
            if (cliente.getTieneCredito() && cliente.getLimiteCredito() != null &&
                nuevoSaldo.compareTo(cliente.getLimiteCredito()) >= 0) {
                cliente.setCategoria("MOROSO");
            }

            clienteRepository.save(cliente);
        });
    }

    /**
     * Verifica si el cliente puede recibir crédito por un monto específico
     */
    public boolean puedeRecibirCredito(Long clienteId, BigDecimal monto) {
        return clienteRepository.findById(clienteId)
                .map(cliente -> cliente.puedeRecibirCredito(monto))
                .orElse(false);
    }

    // =====================================================
    //  ESTADÍSTICAS DE CLIENTE
    // =====================================================

    /**
     * Actualiza las estadísticas del cliente después de una venta
     */
    @Transactional
    public void registrarCompra(Long clienteId, BigDecimal montoCompra) {
        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            cliente.setFechaUltimaCompra(LocalDate.now());
            cliente.setCantidadCompras((cliente.getCantidadCompras() != null ? cliente.getCantidadCompras() : 0) + 1);

            BigDecimal totalActual = cliente.getTotalComprasHistorico() != null ?
                    cliente.getTotalComprasHistorico() : BigDecimal.ZERO;
            cliente.setTotalComprasHistorico(totalActual.add(montoCompra));

            // Actualizar categoría basada en frecuencia
            actualizarCategoriaAutomatica(cliente);

            clienteRepository.save(cliente);
        });
    }

    /**
     * Actualiza la categoría del cliente automáticamente basado en su historial
     */
    private void actualizarCategoriaAutomatica(Cliente cliente) {
        if ("MOROSO".equals(cliente.getCategoria())) {
            return; // No cambiar si está moroso
        }

        Integer compras = cliente.getCantidadCompras() != null ? cliente.getCantidadCompras() : 0;
        BigDecimal total = cliente.getTotalComprasHistorico() != null ?
                cliente.getTotalComprasHistorico() : BigDecimal.ZERO;

        if (total.compareTo(new BigDecimal("5000")) >= 0 || compras >= 50) {
            cliente.setCategoria("VIP");
        } else if (total.compareTo(new BigDecimal("1000")) >= 0 || compras >= 10) {
            cliente.setCategoria("FRECUENTE");
        } else if (compras >= 1) {
            cliente.setCategoria("REGULAR");
        } else {
            cliente.setCategoria("NUEVO");
        }
    }

    /**
     * Obtiene el historial de compras de un cliente
     */
    public List<Venta> obtenerHistorialCompras(Long clienteId) {
        return ventaRepository.findByClienteEntityIdOrderByFechaEmisionDesc(clienteId);
    }

    /**
     * Obtiene estadísticas resumidas del cliente
     */
    public Map<String, Object> obtenerEstadisticas(Long clienteId) {
        Map<String, Object> stats = new HashMap<>();

        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            stats.put("totalCompras", cliente.getTotalComprasHistorico());
            stats.put("cantidadCompras", cliente.getCantidadCompras());
            stats.put("fechaUltimaCompra", cliente.getFechaUltimaCompra());
            stats.put("categoria", cliente.getCategoria());

            if (cliente.isTieneCredito()) {
                stats.put("limiteCredito", cliente.getLimiteCredito());
                stats.put("saldoDeudor", cliente.getSaldoDeudor());
                stats.put("creditoDisponible", cliente.getCreditoDisponible());
            }
        });

        return stats;
    }

    // =====================================================
    //  ESTADÍSTICAS GENERALES
    // =====================================================

    /**
     * Dashboard de clientes
     */
    public Map<String, Object> obtenerDashboard() {
        Map<String, Object> dashboard = new HashMap<>();

        dashboard.put("totalClientes", clienteRepository.countByActivoTrue());
        dashboard.put("clientesConCredito", clienteRepository.countClientesConCredito());
        dashboard.put("clientesConDeuda", clienteRepository.countClientesConDeuda());
        dashboard.put("deudaTotal", clienteRepository.sumarDeudaTotal());
        dashboard.put("topClientes", clienteRepository.findTopClientes(PageRequest.of(0, 5)));
        dashboard.put("clientesMorosos", clienteRepository.findClientesMorosos());

        return dashboard;
    }

    /**
     * Lista de categorías de clientes
     */
    public List<String> obtenerCategorias() {
        return List.of("NUEVO", "REGULAR", "FRECUENTE", "VIP", "MOROSO");
    }

    // =====================================================
    //  ESTADÍSTICAS AVANZADAS PARA VISTA 360°
    // =====================================================

    /**
     * Estadísticas completas para la vista detalle del cliente
     */
    public Map<String, Object> obtenerEstadisticasCompletas(Long clienteId) {
        Map<String, Object> stats = new HashMap<>();

        // Defaults seguros para evitar NullPointerException en Thymeleaf
        stats.put("totalCompras", BigDecimal.ZERO);
        stats.put("cantidadCompras", 0);
        stats.put("fechaUltimaCompra", null);
        stats.put("categoria", "NUEVO");
        stats.put("ticketPromedio", BigDecimal.ZERO);
        stats.put("frecuenciaMensual", 0.0);
        stats.put("diasSinComprar", -1L);
        stats.put("tieneCredito", false);
        stats.put("score", 0);
        stats.put("scoreLabel", "Bajo");

        clienteRepository.findById(clienteId).ifPresent(cliente -> {
            BigDecimal totalCompras = cliente.getTotalComprasHistorico() != null ?
                    cliente.getTotalComprasHistorico() : BigDecimal.ZERO;
            Integer cantidadCompras = cliente.getCantidadCompras() != null ?
                    cliente.getCantidadCompras() : 0;

            stats.put("totalCompras", totalCompras);
            stats.put("cantidadCompras", cantidadCompras);
            stats.put("fechaUltimaCompra", cliente.getFechaUltimaCompra());
            stats.put("categoria", cliente.getCategoria());

            // Ticket promedio
            if (cantidadCompras > 0) {
                stats.put("ticketPromedio", totalCompras.divide(
                        new BigDecimal(cantidadCompras), 2, RoundingMode.HALF_UP));
            } else {
                stats.put("ticketPromedio", BigDecimal.ZERO);
            }

            // Frecuencia: compras por mes desde registro
            if (cantidadCompras > 0 && cliente.getFechaRegistro() != null) {
                long mesesDesdeRegistro = ChronoUnit.MONTHS.between(
                        cliente.getFechaRegistro().toLocalDate(), LocalDate.now());
                if (mesesDesdeRegistro < 1) mesesDesdeRegistro = 1;
                double frecuenciaMensual = (double) cantidadCompras / mesesDesdeRegistro;
                stats.put("frecuenciaMensual", Math.round(frecuenciaMensual * 100.0) / 100.0);
            } else {
                stats.put("frecuenciaMensual", 0.0);
            }

            // Días desde última compra
            if (cliente.getFechaUltimaCompra() != null) {
                long diasSinComprar = ChronoUnit.DAYS.between(
                        cliente.getFechaUltimaCompra(), LocalDate.now());
                stats.put("diasSinComprar", diasSinComprar);
            } else {
                stats.put("diasSinComprar", -1);
            }

            // Crédito
            if (cliente.isTieneCredito()) {
                stats.put("tieneCredito", true);
                stats.put("limiteCredito", cliente.getLimiteCredito());
                stats.put("saldoDeudor", cliente.getSaldoDeudor());
                stats.put("creditoDisponible", cliente.getCreditoDisponible());
                stats.put("diasCredito", cliente.getDiasCredito());

                // Porcentaje de uso de crédito
                if (cliente.getLimiteCredito() != null && cliente.getLimiteCredito().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal deuda = cliente.getSaldoDeudor() != null ? cliente.getSaldoDeudor() : BigDecimal.ZERO;
                    BigDecimal porcentajeUso = deuda.multiply(new BigDecimal("100"))
                            .divide(cliente.getLimiteCredito(), 1, RoundingMode.HALF_UP);
                    stats.put("porcentajeUsoCredito", porcentajeUso);
                } else {
                    stats.put("porcentajeUsoCredito", BigDecimal.ZERO);
                }
            } else {
                stats.put("tieneCredito", false);
            }

            // Scoring del cliente (0-100)
            int score = calcularScore(cliente);
            stats.put("score", score);
            stats.put("scoreLabel", score >= 80 ? "Excelente" : score >= 60 ? "Bueno" : score >= 40 ? "Regular" : "Bajo");
        });

        return stats;
    }

    /**
     * Calcula un score del 0-100 para el cliente basado en múltiples factores
     */
    private int calcularScore(Cliente cliente) {
        int score = 0;
        Integer compras = cliente.getCantidadCompras() != null ? cliente.getCantidadCompras() : 0;
        BigDecimal total = cliente.getTotalComprasHistorico() != null ?
                cliente.getTotalComprasHistorico() : BigDecimal.ZERO;

        // Puntos por cantidad de compras (max 30)
        score += Math.min(compras * 3, 30);

        // Puntos por monto total (max 30)
        if (total.compareTo(new BigDecimal("5000")) >= 0) score += 30;
        else if (total.compareTo(new BigDecimal("2000")) >= 0) score += 20;
        else if (total.compareTo(new BigDecimal("500")) >= 0) score += 10;
        else if (total.compareTo(new BigDecimal("100")) >= 0) score += 5;

        // Puntos por recencia - última compra (max 20)
        if (cliente.getFechaUltimaCompra() != null) {
            long dias = ChronoUnit.DAYS.between(cliente.getFechaUltimaCompra(), LocalDate.now());
            if (dias <= 7) score += 20;
            else if (dias <= 30) score += 15;
            else if (dias <= 90) score += 10;
            else if (dias <= 180) score += 5;
        }

        // Puntos por pago puntual - sin deuda (max 20)
        BigDecimal deuda = cliente.getSaldoDeudor() != null ? cliente.getSaldoDeudor() : BigDecimal.ZERO;
        if (deuda.compareTo(BigDecimal.ZERO) == 0) {
            score += 20;
        } else if (cliente.getLimiteCredito() != null && cliente.getLimiteCredito().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal porcentaje = deuda.multiply(new BigDecimal("100"))
                    .divide(cliente.getLimiteCredito(), 0, RoundingMode.HALF_UP);
            if (porcentaje.intValue() < 30) score += 15;
            else if (porcentaje.intValue() < 60) score += 10;
            else if (porcentaje.intValue() < 90) score += 5;
        }

        return Math.min(score, 100);
    }

    /**
     * Obtiene cotizaciones del cliente.
     * Datos secundarios: si falla, la vista muestra tab vacío en vez de error 500.
     */
    public List<Cotizacion> obtenerCotizaciones(Long clienteId) {
        try {
            List<Cotizacion> cotizaciones = cotizacionRepository.findByClienteEntityIdOrderByFechaCreacionDesc(clienteId);
            if (cotizaciones.isEmpty()) {
                return clienteRepository.findById(clienteId)
                        .map(c -> cotizacionRepository.findByClienteDocumento(c.getNumeroDocumento()))
                        .orElse(List.of());
            }
            return cotizaciones;
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Error BD obteniendo cotizaciones del cliente {}: {}", clienteId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Obtiene órdenes de servicio del cliente.
     * Datos secundarios: si falla, la vista muestra tab vacío en vez de error 500.
     */
    public List<OrdenServicio> obtenerOrdenesServicio(Long clienteId) {
        try {
            return clienteRepository.findById(clienteId)
                    .map(c -> ordenServicioRepository.findByClienteDocumentoOrderByFechaRecepcionDesc(c.getNumeroDocumento()))
                    .orElse(List.of());
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Error BD obteniendo ordenes de servicio del cliente {}: {}", clienteId, e.getMessage(), e);
            return List.of();
        }
    }

    /**
     * Obtiene ventas a crédito pendientes del cliente.
     * Dato financiero crítico: logueamos como ERROR con stacktrace completo.
     */
    public List<Venta> obtenerDeudasPendientes(Long clienteId) {
        return ventaRepository.findDeudasPorClienteId(clienteId);
    }
}
