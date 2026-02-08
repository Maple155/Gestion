package com.gestion.achat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.BonReception;

@Repository
public interface BonReceptionRepository extends JpaRepository<BonReception, UUID> {
    List<BonReception> findAllByOrderByDateReceptionDesc();

    List<BonReception> findTop10ByOrderByDateReceptionDesc();
}