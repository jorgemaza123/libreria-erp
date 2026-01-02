package com.libreria.sistema.service;

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
        return usuarioRepo.findByUsername(username).orElseThrow();
    }

    public Optional<SesionCaja> obtenerSesionActiva() {
        return sesionRepo.findByUsuarioAndEstado(getUsuarioActual(), "ABIERTA");
    }

    @Transactional
    public void abrirCaja(BigDecimal montoInicial) {
        if (obtenerSesionActiva().isPresent()) {
            throw new RuntimeException("Ya tienes una caja abierta.");
        }
        SesionCaja sesion = new SesionCaja();
        sesion.setUsuario(getUsuarioActual());
        sesion.setMontoInicial(montoInicial);
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
        mov.setSesion(sesion); // VINCULAMOS A LA SESIÓN

        movimientoRepo.save(mov);
    }

    public List<MovimientoCaja> listarMovimientosSesion() {
        return obtenerSesionActiva()
                .map(movimientoRepo::findBySesionOrderByFechaDesc)
                .orElse(List.of());
    }

    public Map<String, BigDecimal> obtenerBalanceSesion() {
        SesionCaja sesion = obtenerSesionActiva().orElse(null);
        if (sesion == null) return Map.of();

        List<MovimientoCaja> movs = movimientoRepo.findBySesionOrderByFechaDesc(sesion);
        
        BigDecimal ingresos = movs.stream().filter(m -> "INGRESO".equals(m.getTipo())).map(MovimientoCaja::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal egresos = movs.stream().filter(m -> "EGRESO".equals(m.getTipo())).map(MovimientoCaja::getMonto).reduce(BigDecimal.ZERO, BigDecimal::add);
        
        // Saldo Actual = Monto Inicial + Ingresos - Egresos
        BigDecimal saldo = sesion.getMontoInicial().add(ingresos).subtract(egresos);

        return Map.of(
            "inicial", sesion.getMontoInicial(),
            "ingresos", ingresos,
            "egresos", egresos,
            "saldo", saldo
        );
    }

    @Transactional
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
    
    // Obtener saldo de HOY solo para el dashboard (informativo)
    public Map<String, BigDecimal> obtenerBalanceHoy() {
       return obtenerBalanceSesion(); // Reutilizamos la lógica de sesión
    }
}