package com.libreria.sistema.repository;

import com.libreria.sistema.model.ListaEscolar;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ListaEscolarRepository extends JpaRepository<ListaEscolar, Long> {

    // --- BÚSQUEDA POR CORRELATIVO ---
    Optional<ListaEscolar> findBySerieAndNumero(String serie, Integer numero);

    @Query("SELECT COALESCE(MAX(l.numero), 0) FROM ListaEscolar l WHERE l.serie = :serie")
    Integer findMaxNumeroBySerie(@Param("serie") String serie);

    // --- LISTADOS CON PAGINACIÓN ---
    Page<ListaEscolar> findByEstadoOrderByFechaCreacionDesc(String estado, Pageable pageable);

    @Query(value = "SELECT * FROM listas_escolares l WHERE " +
           "(:estado IS NULL OR l.estado = CAST(:estado AS VARCHAR)) AND " +
           "(:busqueda IS NULL OR :busqueda = '' OR " +
           "  TRANSLATE(LOWER(COALESCE(l.nombre_alumno, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.colegio, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.contacto_nombre, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  LOWER(COALESCE(l.contacto_telefono, '')) LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.grado, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  LOWER(CONCAT(l.serie, '-', LPAD(CAST(l.numero AS VARCHAR), 6, '0'))) LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%')" +
           ") AND " +
           "(:anio IS NULL OR l.anio_escolar = :anio) AND " +
           "l.estado != 'ELIMINADA' " +
           "ORDER BY l.fecha_creacion DESC",
           countQuery = "SELECT COUNT(*) FROM listas_escolares l WHERE " +
           "(:estado IS NULL OR l.estado = CAST(:estado AS VARCHAR)) AND " +
           "(:busqueda IS NULL OR :busqueda = '' OR " +
           "  TRANSLATE(LOWER(COALESCE(l.nombre_alumno, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.colegio, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.contacto_nombre, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  LOWER(COALESCE(l.contacto_telefono, '')) LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  TRANSLATE(LOWER(COALESCE(l.grado, '')), 'áéíóúàèìòùâêîôûäëïöüñ', 'aeiouaeiouaeiouaeioun') LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%') OR " +
           "  LOWER(CONCAT(l.serie, '-', LPAD(CAST(l.numero AS VARCHAR), 6, '0'))) LIKE CONCAT('%', CAST(:busqueda AS VARCHAR), '%')" +
           ") AND " +
           "(:anio IS NULL OR l.anio_escolar = :anio) AND " +
           "l.estado != 'ELIMINADA'",
           nativeQuery = true)
    Page<ListaEscolar> buscarConFiltros(
        @Param("estado") String estado,
        @Param("busqueda") String busqueda,
        @Param("anio") Integer anio,
        Pageable pageable
    );

    // --- BÚSQUEDA POR CLIENTE ---
    List<ListaEscolar> findByClienteIdOrderByFechaCreacionDesc(Long clienteId);

    @Query("SELECT l FROM ListaEscolar l WHERE l.contactoTelefono = :telefono " +
           "ORDER BY l.fechaCreacion DESC")
    List<ListaEscolar> findByTelefono(@Param("telefono") String telefono);

    // --- BÚSQUEDA POR ALUMNO ---
    @Query("SELECT l FROM ListaEscolar l WHERE " +
           "LOWER(l.nombreAlumno) LIKE LOWER(CONCAT('%', :nombre, '%')) " +
           "ORDER BY l.fechaCreacion DESC")
    List<ListaEscolar> buscarPorNombreAlumno(@Param("nombre") String nombre);

    // --- LISTAS VENCIDAS ---
    @Query("SELECT l FROM ListaEscolar l WHERE " +
           "l.estado NOT IN ('COMPLETADA', 'CANCELADA', 'VENCIDA', 'ELIMINADA') AND " +
           "l.fechaVencimiento < :fecha")
    List<ListaEscolar> findListasVencidas(@Param("fecha") LocalDateTime fecha);

    // --- LISTAS PENDIENTES CON VENTAS PARCIALES ---
    @Query("SELECT l FROM ListaEscolar l WHERE l.estado = 'EN_PROCESO' " +
           "ORDER BY l.fechaUltimaVenta DESC")
    List<ListaEscolar> findListasEnProceso();

    // --- ESTADÍSTICAS ---
    @Query("SELECT COUNT(l) FROM ListaEscolar l WHERE l.estado = :estado")
    Long countByEstado(@Param("estado") String estado);

    @Query("SELECT COUNT(l) FROM ListaEscolar l WHERE l.anioEscolar = :anio AND l.estado != 'ELIMINADA'")
    Long countByAnioEscolar(@Param("anio") Integer anio);

    @Query("SELECT l.colegio, COUNT(l) FROM ListaEscolar l " +
           "WHERE l.anioEscolar = :anio AND l.colegio IS NOT NULL " +
           "GROUP BY l.colegio ORDER BY COUNT(l) DESC")
    List<Object[]> countByColegioAndAnio(@Param("anio") Integer anio);

    @Query("SELECT l.grado, COUNT(l) FROM ListaEscolar l " +
           "WHERE l.anioEscolar = :anio " +
           "GROUP BY l.grado ORDER BY l.grado ASC")
    List<Object[]> countByGradoAndAnio(@Param("anio") Integer anio);

    // --- CON DETALLES ---
    @Query("SELECT l FROM ListaEscolar l LEFT JOIN FETCH l.detalles WHERE l.id = :id")
    Optional<ListaEscolar> findByIdConDetalles(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM ListaEscolar l WHERE l.id = :id")
    Optional<ListaEscolar> findByIdWithLock(@Param("id") Long id);
}
