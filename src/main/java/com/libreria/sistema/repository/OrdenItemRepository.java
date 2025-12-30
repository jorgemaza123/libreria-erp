package com.libreria.sistema.repository;
import com.libreria.sistema.model.OrdenItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrdenItemRepository extends JpaRepository<OrdenItem, Long> {
}