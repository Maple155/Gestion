package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.LivraisonClient;

public interface LivraisonClientRepository extends JpaRepository<LivraisonClient, UUID> {
    List<LivraisonClient> findAllByOrderByCreatedAtDesc();
}
