package com.gestion.achat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.DemandeAchat;
import com.gestion.achat.enums.StatutDemande;

@Repository
public interface DemandeAchatRepository extends JpaRepository<DemandeAchat, UUID> {
    List<DemandeAchat> findByStatut(StatutDemande statut);
}