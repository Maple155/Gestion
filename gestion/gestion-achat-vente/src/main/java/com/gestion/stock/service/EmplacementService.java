package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EmplacementService {

    private final EmplacementRepository emplacementRepository;
    private final ZoneStockageRepository zoneStockageRepository;
    private final DepotRepository depotRepository;

    /**
     * Trouver tous les emplacements actifs
     */
    public List<Emplacement> findAllActifs() {
        return emplacementRepository.findByActifTrue();
    }

    /**
     * Trouver les emplacements disponibles pour un article
     */
    public List<Emplacement> findEmplacementsDisponibles(Article article, Integer quantite) {
        // Logique simplifiée : retourner tous les emplacements actifs
        // En réalité, il faudrait vérifier la capacité disponible
        return emplacementRepository.findByActifTrue();
    }

    /**
     * Trouver un emplacement par son ID
     */
    public Emplacement findById(UUID id) {
        return emplacementRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Emplacement non trouvé: " + id));
    }

    /**
     * Vérifier la capacité d'un emplacement
     */
    public boolean verifierCapacite(Emplacement emplacement, Article article, Integer quantite) {
        // Vérifier le poids
        if (emplacement.getCapacitePoidsKg() != null && article.getPoidsKg() != null) {
            BigDecimal poidsTotal = article.getPoidsKg().multiply(BigDecimal.valueOf(quantite));
            if (poidsTotal.compareTo(emplacement.getCapacitePoidsKg()) > 0) {
                return false;
            }
        }

        // Vérifier le volume
        if (emplacement.getCapaciteVolumeM3() != null && article.getVolumeM3() != null) {
            BigDecimal volumeTotal = article.getVolumeM3().multiply(BigDecimal.valueOf(quantite));
            if (volumeTotal.compareTo(emplacement.getCapaciteVolumeM3()) > 0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Créer un nouvel emplacement
     */
    @Transactional
    public Emplacement createEmplacement(Emplacement emplacement) {
        // Vérifier l'unicité du code dans la zone
        if (emplacementRepository.existsByZoneIdAndCode(
                emplacement.getZone().getId(), emplacement.getCode())) {
            throw new IllegalArgumentException("Un emplacement avec ce code existe déjà dans cette zone");
        }

        return emplacementRepository.save(emplacement);
    }

    /**
     * Mettre à jour un emplacement
     */
    @Transactional
    public Emplacement updateEmplacement(UUID id, Emplacement emplacement) {
        Emplacement existing = findById(id);
        
        // Vérifier si le code est modifié et s'il est unique
        if (!existing.getCode().equals(emplacement.getCode())) {
            if (emplacementRepository.existsByZoneIdAndCode(
                    emplacement.getZone().getId(), emplacement.getCode())) {
                throw new IllegalArgumentException("Un emplacement avec ce code existe déjà dans cette zone");
            }
        }

        existing.setCode(emplacement.getCode());
        existing.setAllee(emplacement.getAllee());
        existing.setTravee(emplacement.getTravee());
        existing.setNiveau(emplacement.getNiveau());
        existing.setPosition(emplacement.getPosition());
        existing.setCapacitePoidsKg(emplacement.getCapacitePoidsKg());
        existing.setCapaciteVolumeM3(emplacement.getCapaciteVolumeM3());
        existing.setActif(emplacement.isActif());

        return emplacementRepository.save(existing);
    }

    /**
     * Désactiver un emplacement
     */
    @Transactional
    public void desactiverEmplacement(UUID id) {
        Emplacement emplacement = findById(id);
        // Vérifier si l'emplacement contient du stock
        // if (lotRepository.existsByEmplacementIdAndQuantiteActuelleGreaterThan(id, 0)) {
        //     throw new IllegalArgumentException("L'emplacement contient encore du stock");
        // }
        
        emplacement.setActif(false);
        emplacementRepository.save(emplacement);
    }

    /**
     * Trouver les emplacements par zone
     */
    public List<Emplacement> findByZoneId(UUID zoneId) {
        return emplacementRepository.findByZoneId(zoneId);
    }

    /**
     * Trouver les emplacements par dépôt
     */
    public List<Emplacement> findByDepotId(UUID depotId) {
        return emplacementRepository.findByZoneDepotId(depotId);
    }

    /**
     * Trouver les emplacements vides
     */
    public List<Emplacement> findEmplacementsVides() {
        // Implémentation simplifiée
        // En réalité, il faudrait vérifier qu'aucun lot n'est présent
        return emplacementRepository.findEmplacementsVides();
    }
}