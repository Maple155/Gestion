package com.gestion.vente.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.BacklogStockVente;

public interface BacklogStockVenteRepository extends JpaRepository<BacklogStockVente, UUID> {
}
