package com.gestion.achat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.Proforma;

@Repository
public interface ProformaRepository extends JpaRepository<Proforma, UUID> {
    
    // Récupérer tous les proformas liés à une demande
    List<Proforma> findByDemandeAchatId(UUID demandeAchatId);

    // TROUVER LE MOINS CHER :
    // On cherche le proforma lié à la DA, trié par prix croissant, et on prend le premier.
    Optional<Proforma> findFirstByDemandeAchatIdOrderByPrixUnitaireHtAsc(UUID demandeAchatId);
}