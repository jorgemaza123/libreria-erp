package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * Entidad Usuario con soporte para:
 * - Bloqueo de cuenta por intentos fallidos de login
 * - Sistema dual de roles (legacy y granular)
 */
@Data
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password; // Encriptada con BCrypt

    private String nombreCompleto;

    private boolean activo = true;

    // =====================================================
    //  CAMPOS PARA BLOQUEO DE CUENTA (SEGURIDAD)
    // =====================================================

    /**
     * Contador de intentos fallidos de login.
     * Se resetea a 0 tras un login exitoso.
     */
    @Column(name = "intentos_fallidos")
    private Integer intentosFallidos = 0;

    /**
     * Fecha/hora en que la cuenta fue bloqueada.
     * NULL si la cuenta no está bloqueada.
     */
    @Column(name = "fecha_bloqueo")
    private LocalDateTime fechaBloqueo;

    /**
     * Indica si la cuenta está bloqueada por intentos fallidos.
     * Diferente de 'activo' que es un bloqueo administrativo.
     */
    @Column(name = "cuenta_bloqueada")
    private Boolean cuentaBloqueada = false;

    /**
     * Último intento de login (exitoso o fallido)
     */
    @Column(name = "ultimo_intento_login")
    private LocalDateTime ultimoIntentoLogin;

    /**
     * Último login exitoso
     */
    @Column(name = "ultimo_login_exitoso")
    private LocalDateTime ultimoLoginExitoso;

    // =====================================================
    //  SISTEMA DE ROLES
    // =====================================================

    /**
     * Sistema legacy de roles (ManyToMany)
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "usuarios_roles",
        joinColumns = @JoinColumn(name = "usuario_id"),
        inverseJoinColumns = @JoinColumn(name = "rol_id")
    )
    private Set<Rol> roles = new HashSet<>();

    /**
     * Nuevo sistema de roles granulares (ManyToOne)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    private Role role;

    // =====================================================
    //  MÉTODOS DE UTILIDAD PARA BLOQUEO
    // =====================================================

    /**
     * Incrementa el contador de intentos fallidos
     */
    public void incrementarIntentosFallidos() {
        this.intentosFallidos++;
        this.ultimoIntentoLogin = LocalDateTime.now();
    }

    /**
     * Resetea el contador de intentos fallidos (llamar tras login exitoso)
     */
    public void resetearIntentosFallidos() {
        this.intentosFallidos = 0;
        this.cuentaBloqueada = false;
        this.fechaBloqueo = null;
        this.ultimoLoginExitoso = LocalDateTime.now();
        this.ultimoIntentoLogin = LocalDateTime.now();
    }

    /**
     * Bloquea la cuenta
     */
    public void bloquearCuenta() {
        this.cuentaBloqueada = true;
        this.fechaBloqueo = LocalDateTime.now();
    }

    /**
     * Desbloquea la cuenta (puede ser llamado manualmente o por timeout)
     */
    public void desbloquearCuenta() {
        this.cuentaBloqueada = false;
        this.fechaBloqueo = null;
        this.intentosFallidos = 0;
    }

    /**
     * Verifica si el bloqueo ha expirado basado en los minutos configurados
     * @param minutosBloqueo Duración del bloqueo en minutos
     * @return true si el bloqueo ha expirado
     */
    public boolean haExpiradoBloqueo(int minutosBloqueo) {
        if (!cuentaBloqueada || fechaBloqueo == null) {
            return true;
        }
        return LocalDateTime.now().isAfter(fechaBloqueo.plusMinutes(minutosBloqueo));
    }

    /**
     * Verifica si el usuario puede intentar login
     * (no está bloqueado administrativamente ni por intentos)
     */
    public boolean puedeIntentarLogin() {
        return activo && !cuentaBloqueada;
    }
}
