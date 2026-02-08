package com.gestion.vente.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.LigneCommandeClient;

public interface LigneCommandeClientRepository extends JpaRepository<LigneCommandeClient, UUID> {
}
