package com.gestion.vente.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.Client;

public interface ClientRepository extends JpaRepository<Client, UUID> {
    Optional<Client> findByCode(String code);
}
