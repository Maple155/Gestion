package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.FactureVente;

public interface FactureVenteRepository extends JpaRepository<FactureVente, UUID> {
    List<FactureVente> findAllByOrderByDateFactureDesc();
    boolean existsByCommandeId(UUID commandeId);
}
