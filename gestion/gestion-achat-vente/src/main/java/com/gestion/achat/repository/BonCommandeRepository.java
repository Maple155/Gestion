package com.gestion.achat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.BonCommande;

@Repository
public interface BonCommandeRepository extends JpaRepository<BonCommande, UUID> {
    Optional<BonCommande> findByReferenceBc(String referenceBc);
    
    @Query("SELECT b FROM BonCommande b WHERE b.statutFinance = 'EN_ATTENTE_VALIDATION'")
    List<BonCommande> findPendingValidations();
}