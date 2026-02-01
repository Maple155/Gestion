package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.DevisVente;

public interface DevisVenteRepository extends JpaRepository<DevisVente, UUID> {
    List<DevisVente> findAllByOrderByDateDevisDesc();
}
