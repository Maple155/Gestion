package com.gestion.vente.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.LigneLivraisonClient;

public interface LigneLivraisonClientRepository extends JpaRepository<LigneLivraisonClient, UUID> {
}
