package com.gestion.stock.service;

import com.gestion.achat.entity.BonReception;
import com.gestion.stock.dto.LotDTO;
import com.gestion.stock.dto.LotSearchCriteria;
import com.gestion.stock.dto.SerieDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.stock.repository.specification.LotSpecifications;
import com.gestion.stock.repository.specification.SerieSpecifications;
import com.gestion.achat.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
    private final StockService stockService;
    private final SerieRepository serieRepository;
    private final EmplacementService emplacementService;

    public Lot findById(UUID lotId) {
        List<Lot> lots = lotRepository.findAll();

        for (Lot lot : lots) {
            if (lot.getId().equals(lotId)) {
                return lot;
            }    
        }

        return null;
    }

    public List<Lot> findByArticleId(UUID articleId) {
        return lotRepository.findByArticleId(articleId);
    }

    public List<StockMovement> findByLotId(UUID lotId) {
        return mouvementRepository.findByLotId(lotId);
    }

    public Serie findSerieById(UUID serieId) {
        // Changez getById (qui retourne un proxy) par findById (qui fait une requête réelle)
        List<Serie> serieList = serieRepository.findAll();
        
        for (Serie serie : serieList) {
            if (serie.getId().equals(serieId)) {
                return serie;
            }    
        }

        return null;
    }

    public Serie createSerie(SerieDTO serieDto) throws Exception {
        Serie serie = new Serie();

        if (serieDto == null) {
            throw new Exception("Serie non trouvé");
        }

        serie.builder()
                .id(serieDto.getId())
                .numeroSerie(serieDto.getNumeroSerie())
                .article(serieDto.getArticle())
                .lot(serieDto.getLot())
                .statut(serieDto.getStatut())
                .bonReception(serieDto.getBonReception())
                .dateReception(serieDto.getDateReception())
                .commandeClientId(serieDto.getCommandeClientId())
                .dateSortie(serieDto.getDateSortie())
                .emplacement(serieDto.getEmplacement())
                .createdAt(serieDto.getCreatedAt());

        return serie;
    }

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
                        lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
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
                .orElseThrow(() -> new RuntimeException(
                        "Lot non trouvé: " + numeroLot + " pour article: " + article.getCodeArticle()));

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
        List<Lot> lots = lotRepository.findAll();

        for (Lot lot : lots) {
            if (lot.getId().equals(id)) {
                return lot;
            }
        }

        return null;
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
                    lot.getDatePeremption());

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
            if (lot.getDatePeremption() == null)
                continue;

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

    // Créer un lot
    public Lot createLot(LotDTO lotDTO) {
        // Vérifier si le numéro de lot existe déjà
        if (lotRepository.findByNumeroLotContainingIgnoreCase(lotDTO.getNumeroLot()).stream()
                .anyMatch(lot -> lot.getNumeroLot().equalsIgnoreCase(lotDTO.getNumeroLot()))) {
            throw new IllegalArgumentException("Le numéro de lot existe déjà");
        }

        Lot lot = new Lot();
        mapDTOToLot(lotDTO, lot);

        // Définir les dates par défaut
        if (lot.getDateReception() == null) {
            lot.setDateReception(LocalDate.now());
        }

        // Calculer la date de péremption si durée de vie définie
        if (lotDTO.getArticleId() != null) {
            Article article = articleRepository.findById(lotDTO.getArticleId())
                    .orElseThrow(() -> new IllegalArgumentException("Article non trouvé"));

            if (article.getDureeVieJours() != null && lot.getDateFabrication() != null) {
                lot.setDatePeremption(lot.getDateFabrication().plusDays(article.getDureeVieJours()));
            }
        }

        Lot savedLot = lotRepository.save(lot);

        // Mettre à jour le stock si le lot est disponible
        if (savedLot.getStatut() == Lot.LotStatus.DISPONIBLE) {
            updateStockFromLot(savedLot);
        }

        return savedLot;
    }

    // Mettre à jour un lot
    public Lot updateLot(UUID id, LotDTO lotDTO) {
        Lot lot = lotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Lot non trouvé"));

        // Sauvegarder l'ancien statut et quantité
        Lot.LotStatus ancienStatut = lot.getStatut();
        Integer ancienneQuantite = lot.getQuantiteActuelle();

        mapDTOToLot(lotDTO, lot);

        Lot updatedLot = lotRepository.save(lot);

        // Mettre à jour le stock si changement de statut ou quantité
        if (ancienStatut != updatedLot.getStatut() ||
                !ancienneQuantite.equals(updatedLot.getQuantiteActuelle())) {
            updateStockFromLot(updatedLot);
        }

        return updatedLot;
    }

    public Page<Lot> searchLots(LotSearchCriteria criteria, Pageable pageable) {
        Specification<Lot> spec = Specification.where(null);

        // Numéro de lot
        if (criteria.getNumeroLot() != null && !criteria.getNumeroLot().trim().isEmpty()) {
            spec = spec.and(LotSpecifications.hasNumeroLot(criteria.getNumeroLot()));
        }

        // Article
        if (criteria.getArticleId() != null) {
            spec = spec.and(LotSpecifications.hasArticleId(criteria.getArticleId()));
        }

        // Statut
        if (criteria.getStatut() != null) {
            spec = spec.and(LotSpecifications.hasStatut(criteria.getStatut()));
        }

        // Dépôt
        if (criteria.getDepotId() != null) {
            spec = spec.and(LotSpecifications.hasDepotId(criteria.getDepotId()));
        }

        // Dates de péremption
        spec = spec.and(LotSpecifications.datePeremptionBetween(
                criteria.getDatePeremptionFrom(),
                criteria.getDatePeremptionTo()));

        // Recherche avec proche péremption
        if (Boolean.TRUE.equals(criteria.getProchePeremption())) {
            LocalDate dateLimite = LocalDate.now().plusDays(30);
            spec = spec.and((root, query, cb) -> cb.and(
                    cb.lessThanOrEqualTo(root.get("datePeremption"), dateLimite),
                    cb.greaterThan(root.get("quantiteActuelle"), 0)));
        }

        return lotRepository.findAll(spec, pageable);
    }

    // Obtenir les lots proches de la péremption
    public List<Lot> getLotsProchePeremption(int joursAlerte) {
        LocalDate dateLimit = LocalDate.now().plusDays(joursAlerte);
        return lotRepository.findLotsProchePeremption(dateLimit);
    }

    // Allouer un lot (FIFO/FEFO)
    public Lot allouerLot(UUID articleId, UUID depotId, Integer quantite, String methode) {
        List<Lot> lotsDisponibles = lotRepository.findLotsForAllocation(articleId, methode);

        for (Lot lot : lotsDisponibles) {
            // Vérifier si le lot est dans le bon dépôt
            if (depotId != null && lot.getEmplacement() != null) {
                UUID lotDepotId = lot.getEmplacement().getZone().getDepot().getId();
                if (!lotDepotId.equals(depotId)) {
                    continue;
                }
            }

            if (lot.getQuantiteActuelle() >= quantite) {
                // Réserver la quantité
                lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantite);

                // Si épuisé, changer le statut
                if (lot.getQuantiteActuelle() == 0) {
                    lot.setStatut(Lot.LotStatus.EPUISE);
                }

                lotRepository.save(lot);
                return lot;
            }
        }

        throw new IllegalArgumentException("Stock insuffisant pour l'allocation");
    }

    // Changer le statut d'un lot
    public Lot changerStatutLot(UUID lotId, Lot.LotStatus nouveauStatut, String motif) {
        Lot lot = lotRepository.findById(lotId)
                .orElseThrow(() -> new IllegalArgumentException("Lot non trouvé"));

        Lot.LotStatus ancienStatut = lot.getStatut();
        lot.setStatut(nouveauStatut);

        Lot updatedLot = lotRepository.save(lot);

        // Mettre à jour le stock si changement de statut
        if (ancienStatut != nouveauStatut) {
            updateStockFromLot(updatedLot);
        }

        // Journaliser le changement
        journaliserChangementStatut(lotId, ancienStatut, nouveauStatut, motif);

        return updatedLot;
    }

    // Fusionner deux lots
    public Lot fusionnerLots(UUID lotSourceId, UUID lotDestinationId) {
        Lot lotSource = lotRepository.findById(lotSourceId)
                .orElseThrow(() -> new IllegalArgumentException("Lot source non trouvé"));
        Lot lotDestination = lotRepository.findById(lotDestinationId)
                .orElseThrow(() -> new IllegalArgumentException("Lot destination non trouvé"));

        // Vérifier que les lots sont du même article
        if (!lotSource.getArticle().getId().equals(lotDestination.getArticle().getId())) {
            throw new IllegalArgumentException("Les lots doivent être du même article");
        }

        // Vérifier que les lots sont dans le même emplacement
        if (!lotSource.getEmplacement().getId().equals(lotDestination.getEmplacement().getId())) {
            throw new IllegalArgumentException("Les lots doivent être dans le même emplacement");
        }

        // Calculer le nouveau coût unitaire (moyenne pondérée)
        BigDecimal nouveauCoutUnitaire = calculerCoutMoyenPondere(
                lotSource.getQuantiteActuelle(), lotSource.getCoutUnitaire(),
                lotDestination.getQuantiteActuelle(), lotDestination.getCoutUnitaire());

        // Fusionner les quantités
        lotDestination.setQuantiteActuelle(
                lotDestination.getQuantiteActuelle() + lotSource.getQuantiteActuelle());
        lotDestination.setQuantiteInitiale(
                lotDestination.getQuantiteInitiale() + lotSource.getQuantiteInitiale());
        lotDestination.setCoutUnitaire(nouveauCoutUnitaire);

        // Marquer le lot source comme fusionné
        lotSource.setStatut(Lot.LotStatus.FUSIONNE);
        lotSource.setQuantiteActuelle(0);

        lotRepository.save(lotSource);
        Lot lotFusionne = lotRepository.save(lotDestination);

        return lotFusionne;
    }

    // Mettre à jour le stock à partir d'un lot
    private void updateStockFromLot(Lot lot) {
        if (lot.getEmplacement() != null && lot.getArticle() != null) {
            UUID depotId = lot.getEmplacement().getZone().getDepot().getId();
            stockService.mettreAJourStockDepuisLot(
                    lot.getArticle().getId(),
                    depotId,
                    lot.getQuantiteActuelle(),
                    lot.getCoutUnitaire(),
                    lot.getStatut());
        }
    }

    // Mapper DTO vers entité
    private void mapDTOToLot(LotDTO dto, Lot lot) {
        lot.setNumeroLot(dto.getNumeroLot());

        if (dto.getArticleId() != null) {
            Article article = articleRepository.findById(dto.getArticleId())
                    .orElseThrow(() -> new IllegalArgumentException("Article non trouvé"));
            lot.setArticle(article);
        }

        if (dto.getEmplacementId() != null) {
            Emplacement emplacement = emplacementRepository.findById(dto.getEmplacementId())
                    .orElseThrow(() -> new IllegalArgumentException("Emplacement non trouvé"));
            lot.setEmplacement(emplacement);
        }

        if (dto.getBonReceptionId() != null) {
            BonReception bonReception = bonReceptionRepository.findById(dto.getBonReceptionId())
                    .orElseThrow(() -> new IllegalArgumentException("Bon de réception non trouvé"));
            lot.setBonReception(bonReception);
        }

        lot.setQuantiteInitiale(dto.getQuantiteInitiale());
        lot.setQuantiteActuelle(dto.getQuantiteActuelle());
        lot.setDateFabrication(dto.getDateFabrication());
        lot.setDateReception(dto.getDateReception());
        lot.setDatePeremption(dto.getDatePeremption());
        lot.setDluo(dto.getDluo());
        lot.setCoutUnitaire(dto.getCoutUnitaire());
        lot.setCertificatConformite(dto.getCertificatConformite());

        if (dto.getStatut() != null) {
            lot.setStatut(Lot.LotStatus.valueOf(dto.getStatut()));
        }
    }

    private BigDecimal calculerCoutMoyenPondere(Integer qte1, BigDecimal cout1, Integer qte2, BigDecimal cout2) {
        BigDecimal valeurTotale = cout1.multiply(BigDecimal.valueOf(qte1))
                .add(cout2.multiply(BigDecimal.valueOf(qte2)));
        return valeurTotale.divide(BigDecimal.valueOf(qte1 + qte2), 4, RoundingMode.HALF_UP);
    }

    private void journaliserChangementStatut(UUID lotId, Lot.LotStatus ancienStatut,
            Lot.LotStatus nouveauStatut, String motif) {
        // Implémenter la journalisation ici
    }

    // Ajoutez ces méthodes à la fin de votre LotService.java existant

    /**
     * Obtenir les statistiques des lots
     */
    public Map<String, Object> getLotStatistics() {
        Map<String, Object> stats = new HashMap<>();

        long totalLots = lotRepository.count();
        long lotsDisponibles = lotRepository.countByStatut(Lot.LotStatus.DISPONIBLE);
        long lotsPerimes = lotRepository.countByStatut(Lot.LotStatus.PERIME);
        long lotsBloques = lotRepository.countByStatut(Lot.LotStatus.BLOQUE);
        long lotsEnQuarantaine = lotRepository.countByStatut(Lot.LotStatus.QUARANTAINE);
        long lotsEpulses = lotRepository.countByStatut(Lot.LotStatus.EPUISE);

        // Lots proches péremption (30 jours)
        List<Lot> lotsProches = getLotsProchePeremption(30);
        BigDecimal valeurRisque = BigDecimal.ZERO;

        for (Lot lot : lotsProches) {
            if (lot.getCoutUnitaire() != null) {
                valeurRisque = valeurRisque.add(
                        lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
            }
        }

        stats.put("totalLots", totalLots);
        stats.put("lotsDisponibles", lotsDisponibles);
        stats.put("lotsPerimes", lotsPerimes);
        stats.put("lotsBloques", lotsBloques);
        stats.put("lotsEnQuarantaine", lotsEnQuarantaine);
        stats.put("lotsEpulses", lotsEpulses);
        stats.put("lotsProchesPeremption", lotsProches.size());
        stats.put("valeurRisque", valeurRisque);
        stats.put("dateCalcul", LocalDateTime.now());

        return stats;
    }

    public Page<Serie> searchSeries(String numeroSerie, UUID articleId, UUID lotId,
            String statut, Pageable pageable) {
        // Utilise maintenant les spécifications
        return serieRepository.findAll(
                SerieSpecifications.fromCriteria(numeroSerie, articleId, lotId, statut),
                pageable);
    }

    /**
     * Trouver les lots compatibles pour fusion
     */
    public List<Lot> findLotsCompatiblesPourFusion(UUID lotId) {
        Lot lotSource = lotRepository.findById(lotId).get();

        // Chercher les lots du même article, même dépôt, statut DISPONIBLE
        return lotRepository.findLotsCompatiblesPourFusion(
                lotSource.getArticle().getId(),
                lotSource.getEmplacement() != null ? lotSource.getEmplacement().getId() : null,
                lotId);
    }

    /**
     * Transférer un lot vers un nouvel emplacement
     */
    @Transactional
    public Lot transfererLot(UUID lotId, UUID nouvelEmplacementId, String motif) {
        return transfererLot(lotId, nouvelEmplacementId, null, motif);
    }

    /**
     * Transférer un lot (avec quantité optionnelle)
     */
    @Transactional
    public Lot transfererLot(UUID lotId, UUID nouvelEmplacementId, Integer quantite, String motif) {
        Lot lot = lotRepository.findById(lotId).get();
        Emplacement nouvelEmplacement = emplacementRepository.findById(nouvelEmplacementId)
                .orElseThrow(() -> new IllegalArgumentException("Emplacement non trouvé"));

        if (quantite == null) {
            quantite = lot.getQuantiteActuelle();
        }

        if (lot.getQuantiteActuelle() < quantite) {
            throw new IllegalArgumentException("Quantité insuffisante dans le lot");
        }

        // Vérifier la capacité du nouvel emplacement
        if (!emplacementService.verifierCapacite(nouvelEmplacement, lot.getArticle(), quantite)) {
            throw new IllegalArgumentException("Capacité insuffisante dans l'emplacement");
        }

        // Créer un nouveau lot si nécessaire (si transfert partiel)
        if (quantite < lot.getQuantiteActuelle()) {
            // Créer un nouveau lot pour la quantité transférée
            Lot nouveauLot = new Lot();
            nouveauLot.setNumeroLot(genererNumeroLot());
            nouveauLot.setArticle(lot.getArticle());
            nouveauLot.setBonReception(lot.getBonReception());
            nouveauLot.setQuantiteInitiale(quantite);
            nouveauLot.setQuantiteActuelle(quantite);
            nouveauLot.setDateFabrication(lot.getDateFabrication());
            nouveauLot.setDateReception(lot.getDateReception());
            nouveauLot.setDatePeremption(lot.getDatePeremption());
            nouveauLot.setDluo(lot.getDluo());
            nouveauLot.setCoutUnitaire(lot.getCoutUnitaire());
            nouveauLot.setStatut(Lot.LotStatus.DISPONIBLE);
            nouveauLot.setEmplacement(nouvelEmplacement);
            nouveauLot.setCertificatConformite(lot.getCertificatConformite());

            lotRepository.save(nouveauLot);

            // Mettre à jour le lot source
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantite);
            lotRepository.save(lot);

            return nouveauLot;
        } else {
            // Transfert complet
            lot.setEmplacement(nouvelEmplacement);
            return lotRepository.save(lot);
        }
    }

    /**
     * Supprimer un lot
     */
    @Transactional
    public void deleteLot(UUID id, String motif) {
        Lot lot = lotRepository.findById(id).get();

        // Vérifier les contraintes
        if (lot.getQuantiteActuelle() > 0) {
            throw new IllegalArgumentException("Impossible de supprimer un lot avec du stock");
        }

        if (serieRepository.existsByLotId(id)) {
            throw new IllegalArgumentException("Impossible de supprimer: le lot a des séries associées");
        }

        // Marquer comme supprimé (soft delete) ou supprimer réellement
        lotRepository.delete(lot);

        log.info("Lot {} supprimé. Motif: {}", lot.getNumeroLot(), motif);
    }

    /**
     * Exporter des lots
     */
    public String exportLots(String format, String numeroLot, UUID articleId,
            UUID depotId, String statut, LocalDate datePeremptionFrom,
            LocalDate datePeremptionTo) {
        // Rechercher les lots selon les critères
        LotSearchCriteria criteria = new LotSearchCriteria();
        criteria.setNumeroLot(numeroLot);
        criteria.setArticleId(articleId);
        criteria.setDepotId(depotId);
        if (statut != null) {
            criteria.setStatut(Lot.LotStatus.valueOf(statut));
        }
        criteria.setDatePeremptionFrom(datePeremptionFrom);
        criteria.setDatePeremptionTo(datePeremptionTo);

        List<Lot> lots = lotRepository.findAllByCriteria(criteria);

        // Générer le nom du fichier
        String fileName = "export_lots_" + LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("excel".equalsIgnoreCase(format)) {
            fileName += ".xlsx";
            return exporterVersExcel(lots, fileName);
        } else {
            fileName += ".csv";
            return exporterVersCSV(lots, fileName);
        }
    }

    /**
     * Obtenir l'historique d'une série
     */
    public List<Object> getHistoriqueSerie(UUID serieId) {
        // Cette méthode dépend de votre modèle HistoriqueSerie
        // Pour l'instant, retourner une liste vide ou lever une exception
        // return historiqueSerieRepository.findBySerieIdOrderByDateDesc(serieId);
        throw new UnsupportedOperationException("HistoriqueSerie non implémenté");
    }

    /**
     * Page de lots proches péremption
     */
    public Page<Lot> getLotsProchePeremption(int joursAlerte, Pageable pageable) {
        LocalDate dateLimite = LocalDate.now().plusDays(joursAlerte);
        return lotRepository.findLotsProchePeremptionPage(dateLimite, pageable);
    }

    // Méthodes utilitaires privées
    private String genererNumeroLot() {
        return "LOT-" + LocalDateTime.now().getYear() + "-" +
                String.format("%04d", lotRepository.count() + 1);
    }

    private String exporterVersExcel(List<Lot> lots, String fileName) {
        // Implémentation simplifiée
        // Utiliser Apache POI pour générer le fichier Excel
        log.info("Export Excel: {} lots vers {}", lots.size(), fileName);
        return "/exports/" + fileName;
    }

    private String exporterVersCSV(List<Lot> lots, String fileName) {
        // Implémentation simplifiée
        log.info("Export CSV: {} lots vers {}", lots.size(), fileName);
        return "/exports/" + fileName;
    }
}