package com.gestion.stock.service;

import com.gestion.stock.entity.Depot;
import com.gestion.stock.entity.Site;
import com.gestion.stock.repository.DepotRepository;
import com.gestion.stock.repository.SiteRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DepotService {
    
    private final DepotRepository depotRepository;
    private final SiteRepository siteRepository;
    
    /**
     * Récupérer tous les dépôts actifs
     */
    public List<Depot> getDepotsActifs() {
        return depotRepository.findByActifTrue();
    }
    
    /**
     * Récupérer un dépôt par son ID
     */
    public Depot getDepotById(UUID id) {
        return depotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Dépôt non trouvé avec l'ID: " + id));
    }
    
    /**
     * Récupérer un dépôt par son code
     */
    public Depot getDepotByCode(String code) {
        return depotRepository.findByCode(code)
            .orElseThrow(() -> new RuntimeException("Dépôt non trouvé avec le code: " + code));
    }
    
    /**
     * Créer un nouveau dépôt
     */
    @Transactional
    public Depot creerDepot(Map<String, String> params) {
        log.info("Création d'un nouveau dépôt");
        
        // Validation du code
        String code = params.get("code");
        if (depotRepository.existsByCode(code)) {
            throw new RuntimeException("Un dépôt avec ce code existe déjà: " + code);
        }
        
        Depot depot = new Depot();
        depot.setCode(code);
        depot.setNom(params.get("nom"));
        depot.setType(params.get("type"));
        depot.setAdresse(params.get("adresse"));
        
        // Site
        String siteId = params.get("siteId");
        Site site = siteRepository.findById(UUID.fromString(siteId))
            .orElseThrow(() -> new RuntimeException("Site non trouvé"));
        depot.setSite(site);
        
        // Capacité
        String capaciteM3 = params.get("capaciteM3");
        if (capaciteM3 != null && !capaciteM3.isEmpty()) {
            depot.setCapaciteM3(new BigDecimal(capaciteM3));
        }
        
        // Actif par défaut
        depot.setActif(true);
        depot.setCreatedAt(LocalDateTime.now());
        
        return depotRepository.save(depot);
    }
    
    /**
     * Mettre à jour un dépôt
     */
    @Transactional
    public Depot modifierDepot(UUID id, Map<String, String> params) {
        Depot depot = getDepotById(id);
        
        // Mettre à jour les champs
        if (params.containsKey("nom")) {
            depot.setNom(params.get("nom"));
        }
        
        if (params.containsKey("type")) {
            depot.setType(params.get("type"));
        }
        
        if (params.containsKey("adresse")) {
            depot.setAdresse(params.get("adresse"));
        }
        
        if (params.containsKey("capaciteM3")) {
            String capaciteM3 = params.get("capaciteM3");
            depot.setCapaciteM3(capaciteM3 != null && !capaciteM3.isEmpty() ? 
                new BigDecimal(capaciteM3) : null);
        }
        
        if (params.containsKey("actif")) {
            depot.setActif(Boolean.parseBoolean(params.get("actif")));
        }
        
        if (params.containsKey("siteId")) {
            String siteId = params.get("siteId");
            Site site = siteRepository.findById(UUID.fromString(siteId))
                .orElseThrow(() -> new RuntimeException("Site non trouvé"));
            depot.setSite(site);
        }
        
        log.info("Dépôt modifié: {}", depot.getCode());
        return depotRepository.save(depot);
    }
    
    /**
     * Activer/désactiver un dépôt
     */
    @Transactional
    public void changerStatutDepot(UUID id, boolean actif) {
        Depot depot = getDepotById(id);
        depot.setActif(actif);
        depotRepository.save(depot);
        
        log.info("Statut dépôt {} changé à: {}", depot.getCode(), actif ? "ACTIF" : "INACTIF");
    }
    
    /**
     * Supprimer un dépôt (logiquement)
     */
    @Transactional
    public void supprimerDepot(UUID id) {
        Depot depot = getDepotById(id);
        depot.setActif(false);
        depotRepository.save(depot);
        
        log.info("Dépôt désactivé: {}", depot.getCode());
    }
    
    /**
     * Statistiques des dépôts
     */
    public Map<String, Object> getStatistiquesDepots() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Depot> depots = depotRepository.findAll();
        long total = depots.size();
        long actifs = depotRepository.countByActifTrue();
        
        stats.put("totalDepots", total);
        stats.put("depotsActifs", actifs);
        stats.put("depotsInactifs", total - actifs);
        
        // Compter par type
        Map<String, Long> parType = new HashMap<>();
        for (Depot depot : depots) {
            String type = depot.getType() != null ? depot.getType() : "NON_DEFINI";
            parType.put(type, parType.getOrDefault(type, 0L) + 1);
        }
        stats.put("parType", parType);
        
        log.info("Statistiques dépôts: {} dépôts dont {} actifs", total, actifs);
        return stats;
    }
    
    /**
     * Rechercher des dépôts avec filtres
     */
    public List<Depot> rechercherDepots(String code, String nom, String type, Boolean actif, String siteId) {
        // Implémentation de recherche basée sur les critères
        // Cette méthode nécessiterait un DepotRepository avec des méthodes de recherche spécifiques
        // ou un repository personnalisé avec @Query
        return depotRepository.findAll().stream()
            .filter(depot -> code == null || depot.getCode().contains(code))
            .filter(depot -> nom == null || depot.getNom().contains(nom))
            .filter(depot -> type == null || type.equals(depot.getType()))
            .filter(depot -> actif == null || depot.isActif() == actif)
            .filter(depot -> siteId == null || (depot.getSite() != null && depot.getSite().getId().equals(siteId)))
            .toList();
    }
}