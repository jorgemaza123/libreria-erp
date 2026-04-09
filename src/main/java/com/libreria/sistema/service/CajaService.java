package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.CategoriaMovimiento;
import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.SesionCaja;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.MovimientoCajaRepository;
import com.libreria.sistema.repository.SesionCajaRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.libreria.sistema.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CajaService {

    // Usamos el nombre correcto del repositorio
    private final MovimientoCajaRepository movimientoRepo;
    private final SesionCajaRepository sesionRepo;
    private final UsuarioRepository usuarioRepo;
    private final VentaRepository ventaRepo;

    public CajaService(MovimientoCajaRepository movimientoRepo, SesionCajaRepository sesionRepo,
                       UsuarioRepository usuarioRepo, VentaRepository ventaRepo) {
        this.movimientoRepo = movimientoRepo;
        this.sesionRepo = sesionRepo;
        this.usuarioRepo = usuarioRepo;
        this.ventaRepo = ventaRepo;
    }

    private Usuario getUsuarioActual() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return usuarioRepo.findByUsername(username).orElse(null);
    }

    public Optional<SesionCaja> obtenerSesionActiva() {
        Usuario u = getUsuarioActual();
        if (u == null) return Optional.empty();
        return sesionRepo.findByUsuarioAndEstado(u, "ABIERTA");
    }

    @Transactional
    @Auditable(modulo = "CAJA", accion = "CREAR", descripcion = "Apertura de caja")
    public void abrirCaja(BigDecimal montoInicial) {
        if (obtenerSesionActiva().isPresent()) {
            throw new RuntimeException("Ya tienes una caja abierta.");
        }
        SesionCaja sesion = new SesionCaja();
        sesion.setUsuario(getUsuarioActual());
        sesion.setMontoInicial(montoInicial);
        sesion.setFechaInicio(LocalDateTime.now());
        sesion.setEstado("ABIERTA");
        sesionRepo.save(sesion);
        
        registrarMovimiento("INGRESO", "APERTURA DE CAJA", montoInicial, CategoriaMovimiento.OTRO_INGRESO);
    }

    /**
     * Registra movimiento CON categoría estructurada.
     */
    @Transactional
    public void registrarMovimiento(String tipo, String concepto, BigDecimal monto, String categoria) {
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("El tipo de movimiento es obligatorio.");
        }
        if (concepto == null || concepto.isBlank()) {
            throw new IllegalArgumentException("El concepto del movimiento es obligatorio.");
        }
        if (monto == null || monto.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("El monto debe ser mayor a cero.");
        }

        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new RuntimeException("CAJA CERRADA: Debe abrir caja antes de operar."));

        MovimientoCaja mov = new MovimientoCaja();
        mov.setTipo(tipo.trim().toUpperCase());
        mov.setConcepto(concepto.trim().toUpperCase());
        mov.setMonto(monto);
        mov.setFecha(LocalDateTime.now());
        mov.setUsuario(getUsuarioActual());
        mov.setSesion(sesion);
        mov.setCategoriaMovimiento(categoria != null && !categoria.isBlank() ? categoria.trim().toUpperCase() : null);

        movimientoRepo.save(mov);
    }

    /**
     * Registra movimiento sin categoría (retrocompatibilidad).
     * Asigna OTRO_INGRESO u OTRO_EGRESO según tipo.
     */
    @Transactional
    public void registrarMovimiento(String tipo, String concepto, BigDecimal monto) {
        String categoria = "INGRESO".equalsIgnoreCase(tipo) ? CategoriaMovimiento.OTRO_INGRESO : CategoriaMovimiento.OTRO_EGRESO;
        registrarMovimiento(tipo, concepto, monto, categoria);
    }

    @Transactional(readOnly = true)
    public List<MovimientoCaja> listarMovimientosSesion() {
        return obtenerSesionActiva()
                .map(movimientoRepo::findBySesionOrderByFechaDesc)
                .orElse(List.of());
    }

    // CORREGIDO: Usa las consultas seguras del repositorio
    public Map<String, BigDecimal> obtenerBalanceSesion() {
        SesionCaja sesion = obtenerSesionActiva().orElse(null);
        if (sesion == null) return Map.of();

        BigDecimal ingresos = movimientoRepo.sumarPorSesionYTipo(sesion, "INGRESO");
        BigDecimal egresos = movimientoRepo.sumarPorSesionYTipo(sesion, "EGRESO");
        
        if (ingresos == null) ingresos = BigDecimal.ZERO;
        if (egresos == null) egresos = BigDecimal.ZERO;

        // FIX ERROR-1: el movimiento "APERTURA DE CAJA" ya está incluido en `ingresos`,
        // por lo que sesion.montoInicial NO debe sumarse o el saldo queda duplicado.
        BigDecimal saldo = ingresos.subtract(egresos);

        return Map.of(
            "inicial", sesion.getMontoInicial(),
            "ingresos", ingresos,
            "egresos", egresos,
            "saldo", saldo
        );
    }

    @Transactional
    @Auditable(modulo = "CAJA", accion = "MODIFICAR", descripcion = "Cierre de caja")
    public void cerrarCaja(BigDecimal montoRealEnFisico) {
        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new RuntimeException("No hay caja abierta para cerrar."));

        Map<String, BigDecimal> balance = obtenerBalanceSesion();
        BigDecimal saldoSistema = balance.get("saldo");

        sesion.setFechaFin(LocalDateTime.now());
        sesion.setMontoFinalCalculado(saldoSistema);
        sesion.setMontoFinalReal(montoRealEnFisico);
        sesion.setDiferencia(montoRealEnFisico.subtract(saldoSistema));
        sesion.setEstado("CERRADA");

        sesionRepo.save(sesion);
    }
    
    public Map<String, BigDecimal> obtenerBalanceHoy() {
       return obtenerBalanceSesion();
    }

    /**
     * Obtiene resumen de movimientos agrupados por categoría para la sesión activa.
     * Retorna mapa: categoría → monto total.
     */
    public Map<String, BigDecimal> obtenerResumenPorCategoria() {
        SesionCaja sesion = obtenerSesionActiva().orElse(null);
        if (sesion == null) return Map.of();

        List<Object[]> resultados = movimientoRepo.sumarPorSesionYCategoria(sesion);
        Map<String, BigDecimal> resumen = new HashMap<>();
        for (Object[] row : resultados) {
            String categoria = row[0] != null ? (String) row[0] : "SIN_CATEGORIA";
            BigDecimal total = row[1] != null ? (BigDecimal) row[1] : BigDecimal.ZERO;
            resumen.put(categoria, total);
        }
        return resumen;
    }

    // =====================================================
    //  ALERTA DE CRÉDITOS PENDIENTES (MISIÓN 3 - AUDITORÍA)
    // =====================================================

    /**
     * Cuenta las ventas al crédito del día que tienen saldo pendiente.
     *
     * CASO DE USO: Al cerrar caja, alertar al cajero si hay créditos
     * del día sin cobrar completamente.
     *
     * @return Map con "cantidad" (int) y "montoTotal" (BigDecimal) de créditos pendientes
     */
    public Map<String, Object> contarCreditosPendientesHoy() {
        Map<String, Object> resultado = new HashMap<>();

        try {
            LocalDate hoy = LocalDate.now();

            // FIX ERROR-11: en lugar de findAll() + filter en Java, usamos queries directas en BD.
            long cantidad = ventaRepo.countCreditosPendientesPorFecha(hoy);
            BigDecimal montoTotal = ventaRepo.sumSaldoPendienteCreditosPorFecha(hoy);
            if (montoTotal == null) montoTotal = BigDecimal.ZERO;

            resultado.put("cantidad", cantidad);
            resultado.put("montoTotal", montoTotal);
            resultado.put("success", true);

        } catch (Exception e) {
            resultado.put("cantidad", 0);
            resultado.put("montoTotal", BigDecimal.ZERO);
            resultado.put("success", false);
            resultado.put("error", e.getMessage());
        }

        return resultado;
    }
}
