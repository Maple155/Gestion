package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.AvoirClient;

public interface AvoirClientRepository extends JpaRepository<AvoirClient, UUID> {
    List<AvoirClient> findAllByOrderByDateAvoirDesc();
}
