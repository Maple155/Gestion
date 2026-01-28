package com.gestion.achat.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.Fournisseur;

@Repository
public interface FournisseurRepository extends JpaRepository<Fournisseur, UUID> {
    List<Fournisseur> findByActifTrue();
}