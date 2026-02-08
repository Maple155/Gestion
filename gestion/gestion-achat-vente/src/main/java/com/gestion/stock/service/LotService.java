package com.gestion.stock.service;

import com.gestion.achat.entity.BonReception;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.achat.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LotService {
    
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final BonReceptionRepository bonReceptionRepository;
    private final EmplacementRepository emplacementRepository;
    private final DepotRepository depotRepository;
    private final StockMovementRepository mouvementRepository;

    /**
     * Vérifier et bloquer les lots périmés (batch quotidien)
     */
    @Scheduled(cron = "0 0 2 * * ?") // Tous les jours à 2h du matin
    @Transactional
    public void verifierLotsPerimes() {
        log.info("Début vérification lots périmés");
        
        LocalDate aujourdhui = LocalDate.now();
        
        // 1. Bloquer les lots périmés
        List<Lot> lotsPerimes = lotRepository.findByDatePeremptionLessThanEqual(aujourdhui);
        
        int compteurPerimes = 0;
        for (Lot lot : lotsPerimes) {
            if (lot.getStatut() == Lot.LotStatus.DISPONIBLE) {
                lot.setStatut(Lot.LotStatus.PERIME);
                lotRepository.save(lot);
                compteurPerimes++;
                
                log.warn("Lot périmé bloqué: {} - Article: {}", 
                        lot.getNumeroLot(), lot.getArticle().getCodeArticle());
            }
        }
        
        // 2. Alerter pour les lots proches péremption (7 jours)
        LocalDate limiteAlerte = aujourdhui.plusDays(7);
        List<Lot> lotsProchePeremption = lotRepository.findLotsProchePeremption(limiteAlerte);
        
        for (Lot lot : lotsProchePeremption) {
            long joursRestants = aujourdhui.until(lot.getDatePeremption()).getDays();
            log.info("ALERTE: Lot proche péremption: {} - Date: {} - Jours restants: {}", 
                    lot.getNumeroLot(), lot.getDatePeremption(), joursRestants);
        }
        
        log.info("Vérification lots terminée: {} lots périmés, {} lots en alerte", 
                compteurPerimes, lotsProchePeremption.size());
    }
    
    /**
     * Trouver le meilleur lot selon FEFO (pour produits périssables)
     */
    public Lot trouverLotFEFO(UUID articleId, Integer quantiteRequise) {
        List<Lot> lots = lotRepository.findLotsFEFO(articleId, quantiteRequise);
        
        if (!lots.isEmpty()) {
            return lots.get(0); // Premier lot avec péremption la plus proche
        }
        
        // Si aucun lot n'a la quantité suffisante, chercher le plus approprié
        List<Lot> tousLots = lotRepository.findByArticleIdAndStatutOrderByDatePeremptionAsc(
            articleId, Lot.LotStatus.DISPONIBLE);
        
        for (Lot lot : tousLots) {
            if (lot.getQuantiteActuelle() > 0) {
                return lot; // On retourne le premier lot avec du stock
            }
        }
        
        return null;
    }
    
    /**
     * Trouver le meilleur lot selon FIFO
     */
    public Lot trouverLotFIFO(UUID articleId, Integer quantiteRequise) {
        List<Lot> lots = lotRepository.findByArticleIdAndStatutOrderByDateReceptionAsc(
            articleId, Lot.LotStatus.DISPONIBLE);
        
        for (Lot lot : lots) {
            if (lot.getQuantiteActuelle() >= quantiteRequise) {
                return lot;
            }
        }
        
        // Si aucun lot n'a la quantité suffisante, retourner le premier disponible
        if (!lots.isEmpty()) {
            return lots.get(0);
        }
        
        return null;
    }
    
    /**
     * Mettre à jour la quantité d'un lot après mouvement
     */
    @Transactional
    public void mettreAJourQuantiteLot(UUID lotId, Integer quantite, boolean isSortie) {
        Lot lot = lotRepository.findById(lotId)
            .orElseThrow(() -> new RuntimeException("Lot non trouvé"));
        
        if (isSortie) {
            if (lot.getQuantiteActuelle() < quantite) {
                throw new RuntimeException("Quantité insuffisante dans le lot");
            }
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantite);
        } else {
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() + quantite);
        }
        
        // Vérifier si le lot est épuisé
        if (lot.getQuantiteActuelle() == 0) {
            lot.setStatut(Lot.LotStatus.EPUISE);
        }
        
        lotRepository.save(lot);
        
        log.info("Lot {} mis à jour: quantité = {}", lot.getNumeroLot(), lot.getQuantiteActuelle());
    }
    
    /**
     * Fusionner deux lots
     */
    @Transactional
    public Lot fusionnerLots(UUID lotSourceId, UUID lotDestinationId, UUID utilisateurId) {
        Lot lotSource = lotRepository.findById(lotSourceId)
            .orElseThrow(() -> new RuntimeException("Lot source non trouvé"));
        Lot lotDestination = lotRepository.findById(lotDestinationId)
            .orElseThrow(() -> new RuntimeException("Lot destination non trouvé"));
        
        // Vérifier que les lots concernent le même article
        if (!lotSource.getArticle().getId().equals(lotDestination.getArticle().getId())) {
            throw new RuntimeException("Les lots ne concernent pas le même article");
        }
        
        // Transférer la quantité
        int quantiteTransfert = lotSource.getQuantiteActuelle();
        lotDestination.setQuantiteActuelle(lotDestination.getQuantiteActuelle() + quantiteTransfert);
        
        // Marquer le lot source comme épuisé
        lotSource.setQuantiteActuelle(0);
        lotSource.setStatut(Lot.LotStatus.EPUISE);
        
        lotRepository.save(lotSource);
        lotRepository.save(lotDestination);
        
        log.info("Lots fusionnés: {} ({} unités) → {}", 
                lotSource.getNumeroLot(), quantiteTransfert, lotDestination.getNumeroLot());
        
        return lotDestination;
    }
    
    /**
     * Obtenir les statistiques des lots
     */
    public Map<String, Object> getStatistiquesLots(String depotId) {
        Map<String, Object> stats = new HashMap<>();
        
        // Compter les lots par statut
        long totalLots = lotRepository.count();
        long lotsDisponibles = lotRepository.countByStatut(Lot.LotStatus.DISPONIBLE);
        long lotsPerimes = lotRepository.countByStatut(Lot.LotStatus.PERIME);
        long lotsEpulses = lotRepository.countByStatut(Lot.LotStatus.EPUISE);
        
        // Valeur des lots proches péremption
        LocalDate limite = LocalDate.now().plusDays(30);
        List<Lot> lotsRisque = lotRepository.findLotsProchePeremption(limite);
        BigDecimal valeurRisque = BigDecimal.ZERO;
        
        for (Lot lot : lotsRisque) {
            if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
                valeurRisque = valeurRisque.add(
                    lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()))
                );
            }
        }
        
        stats.put("totalLots", totalLots);
        stats.put("lotsDisponibles", lotsDisponibles);
        stats.put("lotsPerimes", lotsPerimes);
        stats.put("lotsEpulses", lotsEpulses);
        stats.put("lotsProchePeremption", lotsRisque.size());
        stats.put("valeurRisque", valeurRisque);
        stats.put("dateCalcul", LocalDateTime.now());
        
        return stats;
    }
    
    /**
     * Scanner un lot (pour inventaire mobile)
     */
    @Transactional
    public Lot scannerLot(String numeroLot, String codeBarreArticle, String utilisateurId) {
        Article article = articleRepository.findByCodeBarre(codeBarreArticle)
            .orElseThrow(() -> new RuntimeException("Article non trouvé avec code barre: " + codeBarreArticle));
        
        Lot lot = lotRepository.findByNumeroLotAndArticleId(numeroLot, article.getId())
            .orElseThrow(() -> new RuntimeException("Lot non trouvé: " + numeroLot + " pour article: " + article.getCodeArticle()));
        
        // Enregistrer le scan
        log.info("Lot scanné: {} par utilisateur {}", numeroLot, utilisateurId);
        
        return lot;
    }

    public Page<Lot> rechercherLots(String numeroLot, UUID articleId, String statut, 
        UUID depotId, Boolean prochePeremption, Pageable pageable) {
        return lotRepository.rechercherAvecFiltres(
            numeroLot, articleId, statut, prochePeremption, depotId, pageable);
    }
    
    public Lot getLotById(UUID id) {
        return lotRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Lot non trouvé"));
    }
    
    public Map<String, Object> getDetailsLot(UUID lotId) {
        Lot lot = getLotById(lotId);
        Map<String, Object> details = new HashMap<>();
        
        details.put("lot", lot);
        
        // Calculer jours restants avant péremption
        if (lot.getDatePeremption() != null) {
            long joursRestants = LocalDate.now().until(lot.getDatePeremption()).getDays();
            details.put("joursRestants", joursRestants);
            details.put("estPerime", joursRestants < 0);
            details.put("estProchePeremption", joursRestants >= 0 && joursRestants <= 7);
        }
        
        // Valeur du lot
        if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
            details.put("valeurLot", 
                lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
        }
        
        // Consommation (quantité initiale - actuelle)
        if (lot.getQuantiteInitiale() != null && lot.getQuantiteActuelle() != null) {
            int consommee = lot.getQuantiteInitiale() - lot.getQuantiteActuelle();
            double tauxConsommation = (double) consommee / lot.getQuantiteInitiale() * 100;
            details.put("quantiteConsommee", consommee);
            details.put("tauxConsommation", tauxConsommation);
        }
        
        return details;
    }
    
    @Transactional
    public Lot creerLot(Map<String, String> params, String utilisateurId) {
        log.info("Création lot par utilisateur: {}", utilisateurId);
        
        // Validation
        String numeroLot = params.get("numeroLot");
        if (lotRepository.existsByNumeroLot(numeroLot)) {
            throw new RuntimeException("Numéro de lot déjà existant");
        }
        
        Lot lot = new Lot();
        
        // Informations de base
        lot.setNumeroLot(numeroLot);
        
        // Article
        String articleId = params.get("articleId");
        Article article = articleRepository.findById(UUID.fromString(articleId))
            .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        lot.setArticle(article);
        
        // Bon de réception (optionnel)
        String bonReceptionId = params.get("bonReceptionId");
        if (bonReceptionId != null && !bonReceptionId.isEmpty()) {
            BonReception br = bonReceptionRepository.findById(UUID.fromString(bonReceptionId))
                .orElseThrow(() -> new RuntimeException("Bon réception non trouvé"));
            lot.setBonReception(br);
        }
        
        // Quantités
        Integer quantiteInitiale = Integer.parseInt(params.get("quantiteInitiale"));
        lot.setQuantiteInitiale(quantiteInitiale);
        lot.setQuantiteActuelle(quantiteInitiale);
        
        // Dates
        lot.setDateReception(LocalDate.parse(params.get("dateReception")));
        
        String dateFabrication = params.get("dateFabrication");
        if (dateFabrication != null && !dateFabrication.isEmpty()) {
            lot.setDateFabrication(LocalDate.parse(dateFabrication));
        }
        
        String datePeremption = params.get("datePeremption");
        if (datePeremption != null && !datePeremption.isEmpty()) {
            lot.setDatePeremption(LocalDate.parse(datePeremption));
        }
        
        String dluo = params.get("dluo");
        if (dluo != null && !dluo.isEmpty()) {
            lot.setDluo(LocalDate.parse(dluo));
        }
        
        // Coût
        BigDecimal coutUnitaire = new BigDecimal(params.get("coutUnitaire"));
        lot.setCoutUnitaire(coutUnitaire);
        
        // Localisation
        String emplacementId = params.get("emplacementId");
        if (emplacementId != null && !emplacementId.isEmpty()) {
            Emplacement emplacement = emplacementRepository.findById(UUID.fromString(emplacementId))
                .orElseThrow(() -> new RuntimeException("Emplacement non trouvé"));
            lot.setEmplacement(emplacement);
        }
        
        // Statut
        lot.setStatut(Lot.LotStatus.DISPONIBLE);
        
        // Certificat (optionnel)
        lot.setCertificatConformite(params.get("certificatConformite"));
        
        // Audit
        lot.setCreatedAt(LocalDateTime.now());
        
        Lot lotSauvegarde = lotRepository.save(lot);
        log.info("Lot créé: {}", lotSauvegarde.getNumeroLot());
        
        return lotSauvegarde;
    }
    
    @Transactional
    public void changerStatutLot(UUID lotId, String nouveauStatut, String motif, UUID utilisateurId) {
        Lot lot = getLotById(lotId);
        Lot.LotStatus statut = Lot.LotStatus.valueOf(nouveauStatut);
        
        // Logique métier selon le statut
        if (statut == Lot.LotStatus.PERIME && lot.getQuantiteActuelle() > 0) {
            log.warn("Blocage lot périmé: {} (quantité: {})", 
                    lot.getNumeroLot(), lot.getQuantiteActuelle());
        }
        
        lot.setStatut(statut);
        
        // Enregistrer le motif si fourni
        if (motif != null && !motif.isEmpty()) {
            log.info("Changement statut lot {}: {} -> {} (motif: {})", 
                    lot.getNumeroLot(), lot.getStatut(), statut, motif);
        }
        
        lotRepository.save(lot);
    }
    
    public List<Lot> getLotsProchePeremption(Integer jours, String depotId) {
        LocalDate limite = LocalDate.now().plusDays(jours);
        return lotRepository.findLotsProchePeremptionAvecDetails(limite);
    }
    
    public Map<String, Object> getLotsProchesPeremption() {
        LocalDate dateLimite = LocalDate.now().plusDays(30);
        List<Lot> lotsProches = lotRepository.findLotsProchePeremption(dateLimite);
        
        BigDecimal valeurTotale = BigDecimal.ZERO;
        BigDecimal valeurRisque = BigDecimal.ZERO;
        
        for (Lot lot : lotsProches) {
            // Calculate the value of this lot
            BigDecimal valeur = lot.getCoutUnitaire()
                .multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()));
            
            valeurTotale = valeurTotale.add(valeur);
            
            // Calculate days remaining
            long joursRestants = ChronoUnit.DAYS.between(
                LocalDate.now(), 
                lot.getDatePeremption()
            );
            
            // If already expired (negative days)
            if (joursRestants < 0) {
                valeurRisque = valeurRisque.add(valeur);
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("lotsProches", lotsProches);
        result.put("valeurTotale", valeurTotale);
        result.put("valeurRisque", valeurRisque);
        result.put("nombreLots", lotsProches.size());
        
        return result;
    }
    
    @Transactional
    public Lot fusionnerLots(UUID lotSourceId, UUID lotDestinationId, String motif, UUID utilisateurId) {
        Lot lotSource = getLotById(lotSourceId);
        Lot lotDestination = getLotById(lotDestinationId);
        
        // Vérifications
        if (!lotSource.getArticle().getId().equals(lotDestination.getArticle().getId())) {
            throw new RuntimeException("Les lots ne concernent pas le même article");
        }
        
        if (lotSource.getStatut() != Lot.LotStatus.DISPONIBLE) {
            throw new RuntimeException("Le lot source n'est pas disponible");
        }
        
        if (lotDestination.getStatut() != Lot.LotStatus.DISPONIBLE) {
            throw new RuntimeException("Le lot destination n'est pas disponible");
        }
        
        int quantiteTransfert = lotSource.getQuantiteActuelle();
        
        // Calculer nouveau coût moyen pondéré
        BigDecimal coutSource = lotSource.getCoutUnitaire();
        BigDecimal coutDest = lotDestination.getCoutUnitaire();
        int qteSource = lotSource.getQuantiteActuelle();
        int qteDest = lotDestination.getQuantiteActuelle();
        
        BigDecimal nouveauCout = (coutSource.multiply(BigDecimal.valueOf(qteSource))
                .add(coutDest.multiply(BigDecimal.valueOf(qteDest))))
                .divide(BigDecimal.valueOf(qteSource + qteDest), 4, RoundingMode.HALF_UP);
        
        // Mettre à jour le lot destination
        lotDestination.setQuantiteActuelle(qteDest + qteSource);
        lotDestination.setCoutUnitaire(nouveauCout);
        
        // Marquer le lot source comme fusionné
        lotSource.setQuantiteActuelle(0);
        lotSource.setStatut(Lot.LotStatus.FUSIONNE);
        
        // Enregistrer l'historique de fusion
        enregistrerHistoriqueFusion(lotSource, lotDestination, quantiteTransfert, motif, utilisateurId);
        
        lotRepository.save(lotSource);
        lotRepository.save(lotDestination);
        
        log.info("Lots fusionnés: {} ({} unités) → {}", 
                lotSource.getNumeroLot(), quantiteTransfert, lotDestination.getNumeroLot());
        
        return lotDestination;
    }
    
    private void enregistrerHistoriqueFusion(Lot source, Lot destination, int quantite, 
                                           String motif, UUID utilisateurId) {
        // À implémenter avec une table d'historique
        log.info("Fusion enregistrée: {} -> {} ({} unités) par {}", 
                source.getNumeroLot(), destination.getNumeroLot(), quantite, utilisateurId);
    }
    
    public List<Article> getArticlesGestionLot() {
        return articleRepository.findArticlesAvecGestionLot();
    }
    
    public List<Depot> getDepotsActifs() {
        return depotRepository.findDepotsActifs();
    }
    
    public List<Emplacement> getEmplacementsDisponibles() {
        return emplacementRepository.findEmplacementsDisponibles();
    }
    
    public List<BonReception> getBonsReceptionRecents() {
        return bonReceptionRepository.findTop10ByOrderByDateReceptionDesc();
    }
    
    public List<StockMovement> getMouvementsLot(UUID lotId) {
        return mouvementRepository.findMouvementsByLotId(lotId);
    }
    
    public String exporterLots(String format, String statut) {
        // À implémenter avec Apache POI
        log.info("Export lots format: {}, statut: {}", format, statut);
        return "export_lots_" + LocalDateTime.now().toString() + 
               ("excel".equalsIgnoreCase(format) ? ".xlsx" : ".csv");
    }

    public Map<String, Object> getStatistiquesAlertesPeremption(Integer jours) {
        LocalDate dateLimite = LocalDate.now().plusDays(jours != null ? jours : 30);
        
        List<Lot> lotsProches = lotRepository.findLotsProchePeremption(dateLimite);
        
        Map<String, Object> stats = new HashMap<>();
        
        // Nombre total de lots proches péremption
        int totalLots = lotsProches.size();
        
        // Catégoriser par niveau d'urgence
        int dejaPerimes = 0;
        int dangerCritique = 0; // moins de 7 jours
        int dangerModere = 0; // 7 à 15 jours
        int vigilance = 0; // 15 à 30 jours
        int auDela = 0; // au-delà de la période
        
        BigDecimal valeurTotale = BigDecimal.ZERO;
        BigDecimal valeurDejaPerime = BigDecimal.ZERO;
        BigDecimal valeurDangerCritique = BigDecimal.ZERO;
        BigDecimal valeurDangerModere = BigDecimal.ZERO;
        BigDecimal valeurVigilance = BigDecimal.ZERO;
        
        LocalDate aujourdhui = LocalDate.now();
        
        for (Lot lot : lotsProches) {
            if (lot.getDatePeremption() == null) continue;
            
            long joursRestants = ChronoUnit.DAYS.between(aujourdhui, lot.getDatePeremption());
            
            // Calculer la valeur du lot
            BigDecimal valeur = BigDecimal.ZERO;
            if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
                valeur = lot.getCoutUnitaire()
                    .multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()));
                valeurTotale = valeurTotale.add(valeur);
            }
            
            // Catégoriser
            if (joursRestants < 0) {
                dejaPerimes++;
                valeurDejaPerime = valeurDejaPerime.add(valeur);
            } else if (joursRestants <= 7) {
                dangerCritique++;
                valeurDangerCritique = valeurDangerCritique.add(valeur);
            } else if (joursRestants <= 15) {
                dangerModere++;
                valeurDangerModere = valeurDangerModere.add(valeur);
            } else if (joursRestants <= 30) {
                vigilance++;
                valeurVigilance = valeurVigilance.add(valeur);
            } else {
                auDela++;
            }
        }
        
        // Calculer les pourcentages
        double pourcentageDejaPerimes = totalLots > 0 ? (dejaPerimes * 100.0) / totalLots : 0;
        double pourcentageDangerCritique = totalLots > 0 ? (dangerCritique * 100.0) / totalLots : 0;
        double pourcentageDangerModere = totalLots > 0 ? (dangerModere * 100.0) / totalLots : 0;
        double pourcentageVigilance = totalLots > 0 ? (vigilance * 100.0) / totalLots : 0;
        
        stats.put("totalLots", totalLots);
        stats.put("dejaPerimes", dejaPerimes);
        stats.put("dangerCritique", dangerCritique);
        stats.put("dangerModere", dangerModere);
        stats.put("vigilance", vigilance);
        stats.put("auDela", auDela);
        
        stats.put("valeurTotale", valeurTotale);
        stats.put("valeurDejaPerime", valeurDejaPerime);
        stats.put("valeurDangerCritique", valeurDangerCritique);
        stats.put("valeurDangerModere", valeurDangerModere);
        stats.put("valeurVigilance", valeurVigilance);
        
        stats.put("pourcentageDejaPerimes", pourcentageDejaPerimes);
        stats.put("pourcentageDangerCritique", pourcentageDangerCritique);
        stats.put("pourcentageDangerModere", pourcentageDangerModere);
        stats.put("pourcentageVigilance", pourcentageVigilance);
        
        stats.put("dateLimite", dateLimite);
        stats.put("joursAnalyse", jours != null ? jours : 30);
        stats.put("dateCalcul", LocalDateTime.now());
        
        // Ajouter des libellés pour l'affichage
        stats.put("libelleDejaPerimes", "Déjà périmés");
        stats.put("libelleDangerCritique", "Péremption ≤ 7 jours");
        stats.put("libelleDangerModere", "Péremption 8-15 jours");
        stats.put("libelleVigilance", "Péremption 16-30 jours");
        
        log.info("Statistiques alertes péremption: {} lots au total, valeur totale: {}", 
                totalLots, valeurTotale);
        
        return stats;
    }
    
    /**
     * Version simplifiée sans paramètre (pour les appels sans argument)
     */
    public Map<String, Object> getStatistiquesAlertesPeremption() {
        return getStatistiquesAlertesPeremption(30); // Par défaut 30 jours
    }

    public List<Map<String, Object>> getLotsDisponiblesArticle(UUID articleId, UUID depotId) {
    List<Lot> lots = lotRepository.findLotsDisponiblesByArticleAndDepot(articleId, depotId);
    List<Map<String, Object>> result = new ArrayList<>();
    
    for (Lot lot : lots) {
        Map<String, Object> lotInfo = new HashMap<>();
        lotInfo.put("id", lot.getId());
        lotInfo.put("numeroLot", lot.getNumeroLot());
        lotInfo.put("quantiteActuelle", lot.getQuantiteActuelle());
        lotInfo.put("datePeremption", lot.getDatePeremption());
        lotInfo.put("coutUnitaire", lot.getCoutUnitaire());
        
        // Calculer jours restants
        if (lot.getDatePeremption() != null) {
            long joursRestants = LocalDate.now().until(lot.getDatePeremption()).getDays();
            lotInfo.put("joursRestants", joursRestants);
        }
        
        result.add(lotInfo);
    }
    
    return result;
}
}