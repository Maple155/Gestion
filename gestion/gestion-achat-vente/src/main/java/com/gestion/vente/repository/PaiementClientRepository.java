package com.gestion.vente.repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gestion.vente.entity.PaiementClient;

public interface PaiementClientRepository extends JpaRepository<PaiementClient, UUID> {
    List<PaiementClient> findAllByOrderByDatePaiementDesc();

    @Query("select coalesce(sum(p.montant), 0) from PaiementClient p where p.facture.id = :factureId and p.statut = com.gestion.vente.enums.StatutPaiement.ENREGISTRE")
    BigDecimal sumMontantByFacture(@Param("factureId") UUID factureId);
}
