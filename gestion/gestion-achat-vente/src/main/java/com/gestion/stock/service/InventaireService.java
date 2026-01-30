package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventaireService {
    
    private final InventaireRepository inventaireRepository;
    private final LigneInventaireRepository ligneInventaireRepository;
    private final AjustementInventaireRepository ajustementInventaireRepository;
    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final StockMovementRepository mouvementRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SequenceGeneratorService sequenceService;
    private final StockService stockService;
    private final UtilisateurRepository utilisateurRepository;
    
    /**
     * Créer une nouvelle campagne d'inventaire
     */
    @Transactional
    public Inventaire creerInventaire(String type, UUID depotId, UUID zoneId, 
                                     UUID categorieId, LocalDate dateDebut, 
                                     LocalDate dateFin, UUID responsableId, 
                                     String observations) {
        
        log.info("Création inventaire type: {}, dépôt: {}", type, depotId);
        
        // Générer référence
        String reference = "INV-" + LocalDate.now().getYear() + 
                          String.format("-%04d", sequenceService.getNextInventaireSequence());
        
        Depot depot = depotId != null ? depotRepository.findById(depotId).orElse(null) : null;
        
        Inventaire inventaire = Inventaire.builder()
            .reference(reference)
            .type(Inventaire.TypeInventaire.valueOf(type))
            .depot(depot)
            .dateDebut(dateDebut)
            .dateFin(dateFin)
            .statut(Inventaire.StatutInventaire.PLANIFIE)
            .responsable(getUtilisateurById(responsableId)) // À adapter
            .observations(observations)
            .build();
        
        Inventaire inventaireSauvegarde = inventaireRepository.save(inventaire);
        
        // Initialiser les lignes d'inventaire
        initialiserLignesInventaire(inventaireSauvegarde.getId(), depotId, zoneId, categorieId);
        
        log.info("Inventaire créé: {} avec {} lignes", reference, 
                 ligneInventaireRepository.findByInventaireId(inventaireSauvegarde.getId()).size());
        
        return inventaireSauvegarde;
    }
    
    /**
     * Initialiser les lignes d'inventaire avec le stock théorique
     */
    @Transactional
    public void initialiserLignesInventaire(UUID inventaireId, UUID depotId, 
                                           UUID zoneId, UUID categorieId) {
        
        log.info("Initialisation lignes inventaire: {}", inventaireId);
        
        // Récupérer les stocks selon les critères
        List<Stock> stocks = getStocksPourInventaire(depotId, zoneId, categorieId);
        
        for (Stock stock : stocks) {
            // Vérifier si l'article est actif
            if (!stock.getArticle().isActif()) {
                continue;
            }
            
            LigneInventaire ligne = LigneInventaire.builder()
                .inventaire(inventaireRepository.findById(inventaireId).orElseThrow())
                .article(stock.getArticle())
                .depot(stock.getDepot())
                .quantiteTheorique(stock.getQuantiteTheorique())
                .coutUnitaire(stock.getCoutUnitaireMoyen())
                .statut(LigneInventaire.StatutLigneInventaire.A_COMPTER)
                .build();
            
            ligneInventaireRepository.save(ligne);
        }
        
        // Mettre à jour le nombre d'articles
        long nombreLignes = ligneInventaireRepository.findByInventaireId(inventaireId).size();
        Inventaire inventaire = inventaireRepository.findById(inventaireId).orElseThrow();
        inventaire.setNombreArticlesComptes((int) nombreLignes);
        inventaireRepository.save(inventaire);
        
        log.info("{} lignes initialisées pour inventaire {}", nombreLignes, inventaireId);
    }
    
    /**
     * Enregistrer un comptage
     */
    @Transactional
    public LigneInventaire enregistrerComptage(UUID ligneId, Integer quantiteComptee, 
                                              UUID compteurId, boolean isRecomptage) {
        
        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
            .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));
        
        if (isRecomptage) {
            ligne.setQuantiteComptee2(quantiteComptee);
            ligne.setCompteur2Id(compteurId);
            ligne.setDateComptage2(LocalDateTime.now());
        } else {
            ligne.setQuantiteComptee1(quantiteComptee);
            ligne.setCompteur1Id(compteurId);
            ligne.setDateComptage1(LocalDateTime.now());
        }
        
        // Déterminer la quantité finale
        determinerQuantiteFinale(ligne);
        
        // Vérifier si besoin de recomptage
        if (doitEtreRecompte(ligne)) {
            ligne.setStatut(LigneInventaire.StatutLigneInventaire.ECART_A_RECOMPTER);
        } else {
            ligne.setStatut(LigneInventaire.StatutLigneInventaire.COMPTE);
        }
        
        return ligneInventaireRepository.save(ligne);
    }
    
    /**
     * Valider une ligne d'inventaire
     */
    @Transactional
    public LigneInventaire validerLigne(UUID ligneId, String quantiteFinale, 
                                       String causeEcart, UUID valideurId) {
        
        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
            .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));
        
        if (quantiteFinale != null) {
            ligne.setQuantiteCompteeFinale(Integer.parseInt(quantiteFinale));
        }
        
        ligne.setCauseEcart(causeEcart);
        ligne.setStatut(LigneInventaire.StatutLigneInventaire.VALIDE);
        
        // Si écart, créer ajustement
        if (ligne.getEcart() != 0) {
            creerAjustement(ligne, valideurId);
        }
        
        return ligneInventaireRepository.save(ligne);
    }
    
    /**
     * Clôturer un inventaire
     */
    @Transactional
    public Inventaire cloturerInventaire(UUID inventaireId, UUID utilisateurId) {
        
        Inventaire inventaire = inventaireRepository.findById(inventaireId)
            .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));
        
        // Vérifier que toutes les lignes sont validées
        long lignesNonValidees = ligneInventaireRepository.findByInventaireId(inventaireId)
            .stream()
            .filter(l -> l.getStatut() != LigneInventaire.StatutLigneInventaire.VALIDE 
                      && l.getStatut() != LigneInventaire.StatutLigneInventaire.EXCLU)
            .count();
        
        if (lignesNonValidees > 0) {
            throw new RuntimeException(lignesNonValidees + " lignes non validées");
        }
        
        // Calculer les statistiques finales
        calculerStatistiquesInventaire(inventaireId);
        
        // Mettre à jour le statut
        inventaire.setStatut(Inventaire.StatutInventaire.CLOTURE);
        inventaire.setDateCloture(LocalDateTime.now());
        
        // Mettre à jour les stocks physiques
        mettreAJourStocksPhysiques(inventaireId);
        
        log.info("Inventaire clôturé: {}", inventaire.getReference());
        
        return inventaireRepository.save(inventaire);
    }
    
    /**
     * Créer un ajustement d'inventaire
     */
    @Transactional
    public AjustementInventaire creerAjustement(LigneInventaire ligne, UUID valideurId) {
        
        log.info("Création ajustement pour ligne: {}", ligne.getId());
        
        // Vérifier si double validation requise
        boolean doubleValidation = requiertDoubleValidation(ligne);
        
        AjustementInventaire ajustement = AjustementInventaire.builder()
            .ligneInventaire(ligne)
            .quantiteAjustee(ligne.getEcart())
            .valeurAjustement(ligne.getEcartValeur())
            .valideurId(valideurId)
            .motif("Ajustement suite inventaire - " + ligne.getCauseEcart())
            .justification("Inventaire " + ligne.getInventaire().getReference())
            .requiertDoubleValidation(doubleValidation)
            .build();
        
        AjustementInventaire ajustementSauvegarde = ajustementInventaireRepository.save(ajustement);
        
        // Si pas besoin de double validation, créer mouvement de stock immédiat
        if (!doubleValidation) {
            creerMouvementAjustement(ajustementSauvegarde, valideurId);
        }
        
        return ajustementSauvegarde;
    }
    
    /**
     * Valider un ajustement (seconde validation si nécessaire)
     */
    @Transactional
    public AjustementInventaire validerAjustement(UUID ajustementId, UUID secondValideurId, 
                                                 String justification) {
        
        AjustementInventaire ajustement = ajustementInventaireRepository.findById(ajustementId)
            .orElseThrow(() -> new RuntimeException("Ajustement non trouvé"));
        
        if (!ajustement.getRequiertDoubleValidation()) {
            throw new RuntimeException("Cet ajustement ne requiert pas de double validation");
        }
        
        ajustement.setSecondValideurId(secondValideurId);
        ajustement.setDateSecondValidation(LocalDateTime.now());
        ajustement.setJustification(justification);
        
        // Créer mouvement de stock
        creerMouvementAjustement(ajustement, secondValideurId);
        
        return ajustementInventaireRepository.save(ajustement);
    }
    
    /**
     * Créer un mouvement de stock pour ajustement
     */
    @Transactional
    public StockMovement creerMouvementAjustement(AjustementInventaire ajustement, 
        UUID utilisateurId) {
        
        LigneInventaire ligne = ajustement.getLigneInventaire();
        String typeMouvement = ajustement.getQuantiteAjustee() > 0 ? 
            "AJUSTEMENT_POSITIF" : "AJUSTEMENT_NEGATIF";
        
        MovementType type = movementTypeRepository.findByCode(typeMouvement)
            .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));
        
        String reference = "MVT-AJUST-" + LocalDate.now().getYear() + 
                          String.format("-%06d", sequenceService.getNextMovementSequence());
        
        StockMovement mouvement = StockMovement.builder()
            .reference(reference)
            .type(type)
            .article(ligne.getArticle())
            .depot(ligne.getDepot())
            .quantite(Math.abs(ajustement.getQuantiteAjustee()))
            .coutUnitaire(ligne.getCoutUnitaire())
            .inventaire(ligne.getInventaire())
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(utilisateurId)
            .motif("Ajustement inventaire: " + ajustement.getMotif())
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        // Lier l'ajustement au mouvement
        ajustement.setMouvementStock(mouvement);
        ajustementInventaireRepository.save(ajustement);
        
        // Mettre à jour le stock
        if (ajustement.getQuantiteAjustee() > 0) {
            stockRepository.incrementerQuantiteTheorique(
                ligne.getArticle().getId(),
                ligne.getDepot().getId(),
                ajustement.getQuantiteAjustee());
        } else {
            stockRepository.decrementerQuantiteTheorique(
                ligne.getArticle().getId(),
                ligne.getDepot().getId(),
                Math.abs(ajustement.getQuantiteAjustee()));
        }
        
        return mouvementRepository.save(mouvement);
    }
    
    /**
     * Méthodes utilitaires
     */
    private List<Stock> getStocksPourInventaire(UUID depotId, UUID zoneId, UUID categorieId) {
        if (depotId != null) {
            return stockRepository.findByDepotId(depotId);
        }
        // Logique plus complexe pour filtres combinés
        return stockRepository.findAll();
    }
    
    private void determinerQuantiteFinale(LigneInventaire ligne) {
        if (ligne.getQuantiteComptee2() != null) {
            ligne.setQuantiteCompteeFinale(ligne.getQuantiteComptee2());
        } else if (ligne.getQuantiteComptee1() != null) {
            ligne.setQuantiteCompteeFinale(ligne.getQuantiteComptee1());
        }
    }
    
    private boolean doitEtreRecompte(LigneInventaire ligne) {
        if (ligne.getQuantiteCompteeFinale() == null || ligne.getQuantiteTheorique() == null) {
            return false;
        }
        
        int ecart = Math.abs(ligne.getQuantiteCompteeFinale() - ligne.getQuantiteTheorique());
        double pourcentageEcart = (double) ecart / ligne.getQuantiteTheorique() * 100;
        
        // Règles de recomptage
        return ecart > 10 || pourcentageEcart > 5 || 
               (ligne.getCoutUnitaire() != null && 
                ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ecart))
                    .compareTo(new BigDecimal("1000")) > 0);
    }
    
    private boolean requiertDoubleValidation(LigneInventaire ligne) {
        if (ligne.getEcartValeur() == null) {
            return false;
        }
        // Double validation si écart > 5000€
        return ligne.getEcartValeur().abs().compareTo(new BigDecimal("5000")) > 0;
    }
    
    private void calculerStatistiquesInventaire(UUID inventaireId) {
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);
        
        BigDecimal valeurEcartTotal = BigDecimal.ZERO;
        BigDecimal valeurStockTheorique = BigDecimal.ZERO;
        BigDecimal quantiteTheoriqueTotal = BigDecimal.ZERO;
        BigDecimal quantiteEcartTotal = BigDecimal.ZERO;
        
        for (LigneInventaire ligne : lignes) {
            if (ligne.getEcartValeur() != null) {
                valeurEcartTotal = valeurEcartTotal.add(ligne.getEcartValeur().abs());
            }
            if (ligne.getCoutUnitaire() != null && ligne.getQuantiteTheorique() != null) {
                valeurStockTheorique = valeurStockTheorique.add(
                    ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ligne.getQuantiteTheorique())));
            }
            if (ligne.getQuantiteTheorique() != null) {
                quantiteTheoriqueTotal = quantiteTheoriqueTotal.add(
                    BigDecimal.valueOf(ligne.getQuantiteTheorique()));
            }
            if (ligne.getEcart() != null) {
                quantiteEcartTotal = quantiteEcartTotal.add(
                    BigDecimal.valueOf(Math.abs(ligne.getEcart())));
            }
        }
        
        // Calculer taux de précision
        BigDecimal tauxPrecision = BigDecimal.ZERO;
        if (quantiteTheoriqueTotal.compareTo(BigDecimal.ZERO) > 0) {
            tauxPrecision = BigDecimal.ONE.subtract(
                quantiteEcartTotal.divide(quantiteTheoriqueTotal, 4, RoundingMode.HALF_UP))
                .multiply(BigDecimal.valueOf(100));
        }
        
        Inventaire inventaire = inventaireRepository.findById(inventaireId).orElseThrow();
        inventaire.setValeurEcartTotal(valeurEcartTotal);
        inventaire.setTauxPrecision(tauxPrecision);
        inventaireRepository.save(inventaire);
        
        log.info("Statistiques inventaire {}: Écart={}, Précision={}%", 
                 inventaire.getReference(), valeurEcartTotal, tauxPrecision);
    }
    
    private void mettreAJourStocksPhysiques(UUID inventaireId) {
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);
        
        for (LigneInventaire ligne : lignes) {
            if (ligne.getQuantiteCompteeFinale() != null) {
                Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(
                    ligne.getArticle().getId(), ligne.getDepot().getId());
                
                if (stockOpt.isPresent()) {
                    Stock stock = stockOpt.get();
                    stock.setQuantitePhysique(ligne.getQuantiteCompteeFinale());
                    stock.setDateDernierInventaire(LocalDateTime.now());
                    stockRepository.save(stock);
                }
            }
        }
    }
    
    public UUID getInventaireIdByLigneId(UUID ligneId) {
        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
            .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));
        return ligne.getInventaire().getId();
    }
    
    private Utilisateur getUtilisateurById(UUID userId) {
        return utilisateurRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));
    }
    
    public List<Inventaire> getInventairesEnCours() {
        return inventaireRepository.findByStatut(Inventaire.StatutInventaire.EN_COURS);
    }
    
    /**
     * Get statistiques pour dashboard
     */
    public Map<String, Object> getStatistiquesInventaires() {
        Map<String, Object> stats = new HashMap<>();
        
        long totalInventaires = inventaireRepository.count();
        long inventairesPlanifies = inventaireRepository.countByStatut(Inventaire.StatutInventaire.PLANIFIE);
        long inventairesEnCours = inventaireRepository.countByStatut(Inventaire.StatutInventaire.EN_COURS);
        long inventairesClotures = inventaireRepository.countByStatut(Inventaire.StatutInventaire.CLOTURE);
        
        // Calculate precision moyenne
        BigDecimal precisionMoyenne = inventaireRepository.calculateAveragePrecision();
        
        stats.put("totalInventaires", totalInventaires);
        stats.put("inventairesPlanifies", inventairesPlanifies);
        stats.put("inventairesEnCours", inventairesEnCours);
        stats.put("inventairesClotures", inventairesClotures);
        stats.put("precisionMoyenne", precisionMoyenne);
        
        return stats;
    }
}