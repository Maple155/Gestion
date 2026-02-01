package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.CommandeClient;

public interface CommandeClientRepository extends JpaRepository<CommandeClient, UUID> {
    List<CommandeClient> findAllByOrderByDateCommandeDesc();
}
