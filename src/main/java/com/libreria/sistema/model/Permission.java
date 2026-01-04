package com.libreria.sistema.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "permissions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String modulo; // VENTAS, COMPRAS, INVENTARIO, CAJA, REPORTES, CONFIGURACION, USUARIOS, AUDITORIA

    @Column(nullable = false, length = 50)
    private String accion; // VER, CREAR, EDITAR, ELIMINAR, EXPORTAR

    @Column(nullable = false, unique = true, length = 100)
    private String codigo; // Ej: "VENTAS_CREAR", "INVENTARIO_EDITAR"

    @Column(length = 255)
    private String descripcion;

    @ManyToMany(mappedBy = "permissions", fetch = FetchType.LAZY)
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private Set<Role> roles = new HashSet<>();

    public Permission(String modulo, String accion, String codigo, String descripcion) {
        this.modulo = modulo;
        this.accion = accion;
        this.codigo = codigo;
        this.descripcion = descripcion;
    }
}
