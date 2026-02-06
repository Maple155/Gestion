package com.gestion.manager.repository;


import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.manager.entity.Manager;


public interface ManagerRepository extends JpaRepository<Manager, UUID> {

    Optional<Manager> findByUsername(String username);
}
