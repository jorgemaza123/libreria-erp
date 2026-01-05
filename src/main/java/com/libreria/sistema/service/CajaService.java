package com.libreria.sistema.service;

import com.libreria.sistema.aspect.Auditable;
import com.libreria.sistema.model.MovimientoCaja;
import com.libreria.sistema.model.SesionCaja;
import com.libreria.sistema.model.Usuario;
import com.libreria.sistema.repository.MovimientoCajaRepository;
import com.libreria.sistema.repository.SesionCajaRepository;
import com.libreria.sistema.repository.UsuarioRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class CajaService {

    // Usamos el nombre correcto del repositorio
    private final MovimientoCajaRepository movimientoRepo;
    private final SesionCajaRepository sesionRepo;
    private final UsuarioRepository usuarioRepo;

    public CajaService(MovimientoCajaRepository movimientoRepo, SesionCajaRepository sesionRepo, UsuarioRepository usuarioRepo) {
        this.movimientoRepo = movimientoRepo;
        this.sesionRepo = sesionRepo;
        this.usuarioRepo = usuarioRepo;
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
        
        registrarMovimiento("INGRESO", "APERTURA DE CAJA", montoInicial);
    }

    @Transactional
    public void registrarMovimiento(String tipo, String concepto, BigDecimal monto) {
        SesionCaja sesion = obtenerSesionActiva()
                .orElseThrow(() -> new RuntimeException("CAJA CERRADA: Debe abrir caja antes de operar."));

        MovimientoCaja mov = new MovimientoCaja();
        mov.setTipo(tipo);
        mov.setConcepto(concepto.toUpperCase());
        mov.setMonto(monto);
        mov.setFecha(LocalDateTime.now());
        mov.setUsuario(getUsuarioActual());
        mov.setSesion(sesion);

        movimientoRepo.save(mov);
    }

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
        
        // Protección extra por si acaso
        if (ingresos == null) ingresos = BigDecimal.ZERO;
        if (egresos == null) egresos = BigDecimal.ZERO;

        BigDecimal saldo = sesion.getMontoInicial().add(ingresos).subtract(egresos);

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
}