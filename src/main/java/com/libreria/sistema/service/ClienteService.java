package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.Cliente;
import com.libreria.sistema.model.Venta;
import com.libreria.sistema.repository.ClienteRepository;
import com.libreria.sistema.repository.VentaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
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

    public ClienteService(ClienteRepository clienteRepository, VentaRepository ventaRepository) {
        this.clienteRepository = clienteRepository;
        this.ventaRepository = ventaRepository;
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
}
