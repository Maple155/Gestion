package com.gestion.achat.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.BonReception;

@Repository
public interface BonReceptionRepository extends JpaRepository<BonReception, UUID> {
    Optional<BonReception> findByBonCommandeId(UUID bcId);

    List<BonReception> findAllByOrderByDateReceptionDesc();
}
