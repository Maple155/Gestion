package com.gestion.achat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.FactureAchat;

@Repository
public interface FactureAchatRepository extends JpaRepository<FactureAchat, UUID> {
    List<FactureAchat> findByEstPayeeFalse();

    boolean existsByBonCommandeId(UUID id);
}