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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValorisationService {

    private final StockRepository stockRepository;
    private final StockMovementRepository mouvementRepository;
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final HistoriqueCoutRepository historiqueRepository;
    private final ClotureMensuelleRepository clotureRepository;
    private final DepotRepository depotRepository; 

    /**
     * Calcul du CUMP (Coût Unitaire Moyen Pondéré) depuis l'historique complet des mouvements.
     * 
     * ⚠️ ATTENTION: Cette méthode recalcule le CUMP depuis TOUTES les entrées historiques.
     * Pour la valorisation courante, utilisez stock.getCoutUnitaireMoyen() ou stock.getValeurStockCump()
     * qui sont mis à jour en temps réel lors de chaque mouvement.
     * 
     * Cas d'utilisation de cette méthode:
     * - Audit et vérification des données
     * - Recalcul complet après correction de données
     * - Clôture mensuelle / historique
     * 
     * @return Le CUMP unitaire (coût par unité)
     */
    @Transactional
    public BigDecimal calculerCUMP(UUID articleId, UUID depotId) {
        log.info("Calcul CUMP depuis historique pour article: {}, dépôt: {}", articleId, depotId);

        // Récupérer toutes les entrées pour l'article/dépôt
        List<StockMovement> entrees = mouvementRepository.findByArticleIdAndDepotIdOrderByDateMouvementDesc(
                articleId, depotId);

        BigDecimal valeurTotaleEntrees = BigDecimal.ZERO;
        BigDecimal quantiteTotaleEntrees = BigDecimal.ZERO;

        for (StockMovement mvt : entrees) {
            if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE
                    && mvt.getStatut() == StockMovement.MovementStatus.VALIDE) {

                BigDecimal valeurMouvement = mvt.getCoutUnitaire()
                        .multiply(BigDecimal.valueOf(mvt.getQuantite()));

                valeurTotaleEntrees = valeurTotaleEntrees.add(valeurMouvement);
                quantiteTotaleEntrees = quantiteTotaleEntrees.add(
                        BigDecimal.valueOf(mvt.getQuantite()));
            }
        }

        if (quantiteTotaleEntrees.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }

        BigDecimal cump = valeurTotaleEntrees.divide(quantiteTotaleEntrees, 4, RoundingMode.HALF_UP);
        log.info("CUMP calculé: {} = {} / {}", cump, valeurTotaleEntrees, quantiteTotaleEntrees);

        return cump;
    }

    /**
     * Mettre à jour la valorisation d'un stock
     */
    @Transactional
    public void mettreAJourValorisationStock(UUID articleId, UUID depotId) {
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);

        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            Article article = stock.getArticle();

            BigDecimal nouvelleValorisation = BigDecimal.ZERO;

            switch (article.getMethodeValorisation()) {
                case "CUMP":
                    nouvelleValorisation = calculerValorisationCUMP(articleId, depotId);
                    break;

                case "FIFO":
                    nouvelleValorisation = calculerValorisationFIFO(articleId, depotId);
                    break;

                case "FEFO":
                    nouvelleValorisation = calculerValorisationFEFO(articleId, depotId);
                    break;

                default:
                    nouvelleValorisation = calculerValorisationCUMP(articleId, depotId);
            }

            stock.setValeurStockCump(nouvelleValorisation);
            stockRepository.save(stock);

            log.info("Valorisation mise à jour: Article {}, Dépôt {}, Valeur: {}",
                    article.getCodeArticle(), stock.getDepot().getCode(), nouvelleValorisation);
        }
    }

    /**
     * Valorisation FIFO (First In, First Out)
     * ⚠️ IMPORTANT: La valorisation FIFO se base sur la quantiteActuelle des lots,
     * pas sur la quantité initiale. Seuls les lots avec quantité > 0 sont considérés.
     */
    public BigDecimal calculerValorisationFIFO(UUID articleId, UUID depotId) {
        log.info("Calcul valorisation FIFO pour article: {}, depot: {}", articleId, depotId);

        // ✅ Essayer d'abord avec filtrage par dépôt
        List<Lot> lots = lotRepository.findLotsForFIFO(articleId, depotId);

        // ✅ FALLBACK: Si aucun lot trouvé, essayer sans filtrage par dépôt
        // (cas où les lots n'ont pas d'emplacement assigné)
        if (lots.isEmpty()) {
            log.warn("Aucun lot trouvé avec dépôt, tentative sans filtrage dépôt - Article: {}", articleId);
            lots = lotRepository.findLotsForFIFOWithoutDepot(articleId);
        }

        if (lots.isEmpty()) {
            log.warn("Aucun lot disponible pour FIFO - Article: {}, Dépôt: {}", articleId, depotId);
            return BigDecimal.ZERO;
        }

        BigDecimal valeurTotale = BigDecimal.ZERO;

        // ✅ Calcul basé uniquement sur la quantiteActuelle des lots
        for (Lot lot : lots) {
            // Utiliser quantiteActuelle, pas quantiteInitiale
            BigDecimal valeurLot = lot.getCoutUnitaire()
                    .multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()));
            valeurTotale = valeurTotale.add(valeurLot);
            
            log.debug("Lot {} - Qté actuelle: {}, Coût: {}, Valeur: {}", 
                    lot.getNumeroLot(), lot.getQuantiteActuelle(), lot.getCoutUnitaire(), valeurLot);
        }

        log.info("Valorisation FIFO calculée: {} pour {} lots", valeurTotale, lots.size());
        return valeurTotale;
    }

    /**
     * Valorisation FEFO (First Expired, First Out) pour produits périssables
     * ⚠️ IMPORTANT: La valorisation FEFO se base sur la quantiteActuelle des lots,
     * pas sur la quantité initiale. Seuls les lots avec quantité > 0 sont considérés.
     */
    public BigDecimal calculerValorisationFEFO(UUID articleId, UUID depotId) {
        log.info("Calcul valorisation FEFO pour article: {}, depot: {}", articleId, depotId);

        // ✅ Essayer d'abord avec filtrage par dépôt
        List<Lot> lots = lotRepository.findLotsForFEFO(articleId, depotId);

        // ✅ FALLBACK: Si aucun lot trouvé, essayer sans filtrage par dépôt
        // (cas où les lots n'ont pas d'emplacement assigné)
        if (lots.isEmpty()) {
            log.warn("Aucun lot trouvé avec dépôt pour FEFO, tentative sans filtrage dépôt - Article: {}", articleId);
            lots = lotRepository.findLotsForFEFOWithoutDepot(articleId);
        }

        if (lots.isEmpty()) {
            log.warn("Aucun lot disponible pour FEFO - Article: {}, Dépôt: {}", articleId, depotId);
            return BigDecimal.ZERO;
        }

        BigDecimal valeurTotale = BigDecimal.ZERO;

        // ✅ Calcul basé uniquement sur la quantiteActuelle des lots
        for (Lot lot : lots) {
            // Utiliser quantiteActuelle, pas quantiteInitiale
            BigDecimal valeurLot = lot.getCoutUnitaire()
                    .multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()));
            valeurTotale = valeurTotale.add(valeurLot);
            
            log.debug("Lot {} - Qté actuelle: {}, Péremption: {}, Coût: {}, Valeur: {}", 
                    lot.getNumeroLot(), lot.getQuantiteActuelle(), lot.getDatePeremption(), 
                    lot.getCoutUnitaire(), valeurLot);
        }

        log.info("Valorisation FEFO calculée: {} pour {} lots", valeurTotale, lots.size());
        return valeurTotale;
    }

    /**
     * Valorisation CUMP standard
     * ✅ CORRIGÉ: Utilise directement stock.valeurStockCump qui est mis à jour en temps réel
     * lors de chaque mouvement, au lieu de recalculer depuis l'historique
     */
    private BigDecimal calculerValorisationCUMP(UUID articleId, UUID depotId) {
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);

        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            // ✅ Utiliser directement la valeur stockée (mise à jour à chaque mouvement)
            return stock.getValeurStockCump() != null ? stock.getValeurStockCump() : BigDecimal.ZERO;
        }

        return BigDecimal.ZERO;
    }

    /**
     * Recalculer le CUMP depuis l'historique complet des mouvements
     * ⚠️ À utiliser uniquement pour audit/vérification ou recalcul complet
     */
    public BigDecimal recalculerCUMPDepuisHistorique(UUID articleId, UUID depotId) {
        BigDecimal cump = calculerCUMP(articleId, depotId);
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);

        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            return cump.multiply(BigDecimal.valueOf(stock.getQuantiteTheorique()));
        }

        return BigDecimal.ZERO;
    }

    /**
     * Clôture mensuelle - Gel des coûts
     */
    @Transactional
    public void cloturerMois(Integer annee, Integer mois, UUID utilisateurId) {
        log.info("Clôture mensuelle pour {}/{} par {}", mois, annee, utilisateurId);

        LocalDate dateDebutMois = LocalDate.of(annee, mois, 1);
        LocalDate dateFinMois = dateDebutMois.withDayOfMonth(dateDebutMois.lengthOfMonth());

        // Récupérer tous les stocks actifs
        List<Stock> stocks = stockRepository.findAll();

        for (Stock stock : stocks) {
            // Calculer la valorisation à la fin du mois
            BigDecimal valorisationFinMois = calculerValorisationCUMP(
                    stock.getArticle().getId(), stock.getDepot().getId());

            // Sauvegarder dans l'historique
            sauvegarderHistoriqueCout(
                    stock.getArticle().getId(),
                    stock.getDepot().getId(),
                    dateFinMois,
                    stock.getCoutUnitaireMoyen(),
                    stock.getQuantiteTheorique(),
                    valorisationFinMois,
                    stock.getArticle().getMethodeValorisation(),
                    utilisateurId);

            log.debug("Clôture stock: {} - Valeur: {}",
                    stock.getArticle().getCodeArticle(), valorisationFinMois);
        }

        log.info("Clôture mensuelle terminée: {} stocks clôturés", stocks.size());
    }

    @Transactional
    private void sauvegarderHistoriqueCout(UUID articleId, UUID depotId, LocalDate dateEffet,
            BigDecimal coutUnitaire, Integer quantite,
            BigDecimal valeurStock, String methode, UUID utilisateurId) {

        try {
            Article article = articleRepository.findById(articleId)
                    .orElseThrow(() -> new RuntimeException("Article non trouvé"));

            Depot depot = depotRepository.findById(depotId)
                    .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));

            // Vérifier si un historique existe déjà pour cette date
            Optional<HistoriqueCout> existing = historiqueRepository
                    .findDernierByArticleAndDepot(articleId, depotId);

            if (existing.isPresent() && existing.get().getDateEffet().equals(dateEffet)) {
                log.debug("Historique déjà existant pour article {} à la date {}",
                        article.getCodeArticle(), dateEffet);
                return;
            }

            HistoriqueCout historique = HistoriqueCout.builder()
                    .article(article)
                    .depot(depot)
                    .dateEffet(dateEffet)
                    .coutUnitaireMoyen(coutUnitaire)
                    .quantiteStock(quantite)
                    .valeurStock(valeurStock)
                    .methodeValorisation(methode)
                    .createdBy(utilisateurId)
                    .build();

            historiqueRepository.save(historique);

            log.debug("Historique sauvegardé: Article {}, Date: {}, Valeur: {}",
                    article.getCodeArticle(), dateEffet, valeurStock);

        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'historique: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur historique: " + e.getMessage(), e);
        }
    }

    /**
     * Nouvelle méthode : Clôturer le mois avec historique
     */
    @Transactional
    public Map<String, Object> cloturerMoisComplet(Integer annee, Integer mois, UUID utilisateurId, ClotureService cls) {
        log.info("Clôture mensuelle complète pour {}/{} par {}", mois, annee, utilisateurId);

        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Initialiser ou récupérer la clôture
            ClotureMensuelle cloture = cls.initialiserCloture(annee, mois, utilisateurId);

            // 2. Exécuter la clôture
            cloture = cls.executerCloture(cloture.getId(), utilisateurId);

            result.put("success", true);
            result.put("cloture", cloture);
            result.put("message", "Clôture exécutée avec succès");

        } catch (Exception e) {
            log.error("Erreur lors de la clôture: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "Erreur: " + e.getMessage());
        }

        return result;
    }

    /**
     * Obtenir l'évolution des coûts pour un article
     */
    public List<Map<String, Object>> getEvolutionCoutArticle(UUID articleId, UUID depotId, int nbMois, ClotureService cls) {
        List<HistoriqueCout> historiques = cls.getHistoriqueArticle(articleId, depotId, nbMois);

        return historiques.stream()
                .map(h -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("date", h.getDateEffet());
                    map.put("coutUnitaire", h.getCoutUnitaireMoyen());
                    map.put("quantite", h.getQuantiteStock());
                    map.put("valeur", h.getValeurStock());
                    map.put("methode", h.getMethodeValorisation());
                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculer la rotation de stock (turnover)
     */
    public Map<String, Object> calculerRotationStock(UUID articleId, UUID depotId, Integer periodeMois) {
        Map<String, Object> result = new HashMap<>();

        // Calculer les sorties sur la période
        LocalDate dateDebut = LocalDate.now().minusMonths(periodeMois);

        Long totalSorties = mouvementRepository.sumSortiesByArticleAndDepot(articleId, depotId);
        if (totalSorties == null)
            totalSorties = 0L;

        // Récupérer le stock moyen sur la période
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            Integer stockMoyen = stock.getQuantiteTheorique(); // Simplifié

            BigDecimal rotation = stockMoyen > 0
                    ? BigDecimal.valueOf(totalSorties).divide(BigDecimal.valueOf(stockMoyen), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

            result.put("totalSorties", totalSorties);
            result.put("stockMoyen", stockMoyen);
            result.put("rotation", rotation);
            result.put("periodeMois", periodeMois);
        }

        return result;
    }

    /**
     * Analyse ABC (Pareto) des articles
     */
    public List<Map<String, Object>> analyserABC(UUID depotId) {
        List<Map<String, Object>> analyse = new ArrayList<>();

        // Récupérer tous les stocks du dépôt
        List<Stock> stocks = stockRepository.findByDepotId(depotId);

        // Calculer la valeur totale
        BigDecimal valeurTotale = stocks.stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Trier par valeur décroissante
        stocks.sort((s1, s2) -> s2.getValeurStockCump().compareTo(s1.getValeurStockCump()));

        BigDecimal valeurCumulee = BigDecimal.ZERO;

        for (Stock stock : stocks) {
            BigDecimal pourcentageValeur = stock.getValeurStockCump()
                    .divide(valeurTotale, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            valeurCumulee = valeurCumulee.add(stock.getValeurStockCump());
            BigDecimal pourcentageCumule = valeurCumulee
                    .divide(valeurTotale, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Déterminer classe ABC
            String classeABC = "C";
            if (pourcentageCumule.compareTo(new BigDecimal("80")) <= 0) {
                classeABC = "A";
            } else if (pourcentageCumule.compareTo(new BigDecimal("95")) <= 0) {
                classeABC = "B";
            }

            Map<String, Object> item = new HashMap<>();
            item.put("articleCode", stock.getArticle().getCodeArticle());
            item.put("articleLibelle", stock.getArticle().getLibelle());
            item.put("valeurStock", stock.getValeurStockCump());
            item.put("pourcentageValeur", pourcentageValeur);
            item.put("pourcentageCumule", pourcentageCumule);
            item.put("classeABC", classeABC);
            item.put("quantite", stock.getQuantiteTheorique());

            analyse.add(item);
        }

        return analyse;
    }

    public Map<String, Object> getSyntheseValorisation() {
        Map<String, Object> synthese = new HashMap<>();

        // Calculer les valorisations réelles
        BigDecimal valeurFifoTotal = calculerValorisationFIFOTotale();
        BigDecimal valeurFefoTotal = calculerValorisationFEFOTotale();

        // Récupérer tous les stocks pour CUMP et calculs
        List<Stock> stocks = stockRepository.findAll();
        BigDecimal valeurCumpTotal = BigDecimal.ZERO;

        // Compter les articles par méthode
        int articlesFifo = 0;
        int articlesFefo = 0;
        int articlesCump = 0;

        for (Stock stock : stocks) {
            Article article = stock.getArticle();
            if (article != null) {
                String methode = article.getMethodeValorisation();

                switch (methode) {
                    case "FIFO":
                        articlesFifo++;
                        break;
                    case "FEFO":
                        articlesFefo++;
                        break;
                    case "CUMP":
                    default:
                        articlesCump++;
                        // Pour CUMP, utiliser la valeur stock existante
                        if (stock.getValeurStockCump() != null) {
                            valeurCumpTotal = valeurCumpTotal.add(stock.getValeurStockCump());
                        }
                        break;
                }
            }
        }

        // Total
        BigDecimal valeurTotale = valeurFifoTotal.add(valeurFefoTotal).add(valeurCumpTotal);

        synthese.put("valeurFifo", valeurFifoTotal);
        synthese.put("coutSortiesFifo", getCoutMoyenSortiesFIFO());
        synthese.put("valeurFefo", valeurFefoTotal);
        synthese.put("coutSortiesFefo", getCoutMoyenSortiesFEFO());
        synthese.put("valeurCump", valeurCumpTotal);
        synthese.put("valeurTotale", valeurTotale);
        synthese.put("articlesFifo", articlesFifo);
        synthese.put("articlesFefo", articlesFefo);
        synthese.put("articlesCump", articlesCump);

        // Pourcentages
        if (valeurTotale.compareTo(BigDecimal.ZERO) > 0) {
            synthese.put("pourcentageFifo",
                    valeurFifoTotal.divide(valeurTotale, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
            synthese.put("pourcentageFefo",
                    valeurFefoTotal.divide(valeurTotale, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
            synthese.put("pourcentageCump",
                    valeurCumpTotal.divide(valeurTotale, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100)));
        } else {
            synthese.put("pourcentageFifo", BigDecimal.ZERO);
            synthese.put("pourcentageFefo", BigDecimal.ZERO);
            synthese.put("pourcentageCump", BigDecimal.ZERO);
        }

        return synthese;
    }

    public BigDecimal getCoutMoyenSortiesFIFO() {
        log.info("Calcul coût moyen sorties FIFO");
        
        LocalDate dateDebut = LocalDate.now().minusMonths(3);
        LocalDateTime dateDebutTime = dateDebut.atStartOfDay();
        LocalDateTime dateFinTime = LocalDate.now().atTime(23, 59, 59);
    
        // Récupérer tous les articles FIFO
        List<Article> articlesFifo = articleRepository.findByMethodeValorisation("FIFO");
        
        if (articlesFifo.isEmpty()) {
            log.warn("Aucun article FIFO trouvé");
            return BigDecimal.ZERO;
        }
        
        List<UUID> articleIds = articlesFifo.stream()
            .map(Article::getId)
            .collect(Collectors.toList());
        
        // Récupérer tous les mouvements de sortie pour ces articles
        List<StockMovement> allMovements = mouvementRepository.findAll();
        
        List<StockMovement> sortiesFifo = allMovements.stream()
            .filter(m -> articleIds.contains(m.getArticle().getId()))
            .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
            .filter(m -> m.getStatut() == StockMovement.MovementStatus.VALIDE)
            .filter(m -> m.getDateMouvement().isAfter(dateDebutTime) && 
                         m.getDateMouvement().isBefore(dateFinTime))
            .collect(Collectors.toList());
        
        if (sortiesFifo.isEmpty()) {
            log.warn("Aucune sortie FIFO trouvée pour les 3 derniers mois");
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValeur = BigDecimal.ZERO;
        BigDecimal totalQuantite = BigDecimal.ZERO;
        
        for (StockMovement mvt : sortiesFifo) {
            if (mvt.getQuantite() > 0 && mvt.getCoutUnitaire() != null) {
                totalValeur = totalValeur.add(
                    mvt.getCoutUnitaire().multiply(BigDecimal.valueOf(mvt.getQuantite()))
                );
                totalQuantite = totalQuantite.add(BigDecimal.valueOf(mvt.getQuantite()));
            }
        }
        
        if (totalQuantite.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalValeur.divide(totalQuantite, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculer le coût moyen des sorties FEFO
     */
    public BigDecimal getCoutMoyenSortiesFEFO() {
        log.info("Calcul coût moyen sorties FEFO");
        
        LocalDate dateDebut = LocalDate.now().minusMonths(3);
        LocalDateTime dateDebutTime = dateDebut.atStartOfDay();
        LocalDateTime dateFinTime = LocalDate.now().atTime(23, 59, 59);
    
        // Récupérer tous les articles FEFO
        List<Article> articlesFefo = articleRepository.findByMethodeValorisation("FEFO");
        
        if (articlesFefo.isEmpty()) {
            log.warn("Aucun article FEFO trouvé");
            return BigDecimal.ZERO;
        }
        
        List<UUID> articleIds = articlesFefo.stream()
            .map(Article::getId)
            .collect(Collectors.toList());
        
        // Récupérer tous les mouvements de sortie pour ces articles
        List<StockMovement> allMovements = mouvementRepository.findAll();
        
        List<StockMovement> sortiesFefo = allMovements.stream()
            .filter(m -> articleIds.contains(m.getArticle().getId()))
            .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
            .filter(m -> m.getStatut() == StockMovement.MovementStatus.VALIDE)
            .filter(m -> m.getDateMouvement().isAfter(dateDebutTime) && 
                         m.getDateMouvement().isBefore(dateFinTime))
            .collect(Collectors.toList());
        
        if (sortiesFefo.isEmpty()) {
            log.warn("Aucune sortie FEFO trouvée pour les 3 derniers mois");
            return BigDecimal.ZERO;
        }
        
        BigDecimal totalValeur = BigDecimal.ZERO;
        BigDecimal totalQuantite = BigDecimal.ZERO;
        
        for (StockMovement mvt : sortiesFefo) {
            if (mvt.getQuantite() > 0 && mvt.getCoutUnitaire() != null) {
                totalValeur = totalValeur.add(
                    mvt.getCoutUnitaire().multiply(BigDecimal.valueOf(mvt.getQuantite()))
                );
                totalQuantite = totalQuantite.add(BigDecimal.valueOf(mvt.getQuantite()));
            }
        }
        
        if (totalQuantite.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        
        return totalValeur.divide(totalQuantite, 4, RoundingMode.HALF_UP);
    }
    
    /**
     * Calculer la différence de coût entre FIFO et FEFO
     */
    public BigDecimal getDifferenceCout() {
        BigDecimal coutFifo = getCoutMoyenSortiesFIFO();
        BigDecimal coutFefo = getCoutMoyenSortiesFEFO();
        
        return coutFefo.subtract(coutFifo);
    }

    public Map<String, Object> getDetailValorisationParMethode(String methode) {
        Map<String, Object> detail = new HashMap<>();

        // Récupérer les articles par méthode
        List<Article> articles = articleRepository.findByMethodeValorisation(methode);

        BigDecimal valeurTotale = BigDecimal.ZERO;
        long nombreLots = 0;
        BigDecimal coutMoyenTotal = BigDecimal.ZERO;
        int countCout = 0;

        for (Article article : articles) {
            // Récupérer les stocks pour cet article
            List<Stock> stocks = stockRepository.findByArticleId(article.getId());

            for (Stock stock : stocks) {
                BigDecimal valorisation = BigDecimal.ZERO;

                switch (methode) {
                    case "FIFO":
                        valorisation = calculerValorisationFIFO(article.getId(), stock.getDepot().getId());
                        break;
                    case "FEFO":
                        valorisation = calculerValorisationFEFO(article.getId(), stock.getDepot().getId());
                        break;
                    case "CUMP":
                        valorisation = stock.getValeurStockCump() != null ? stock.getValeurStockCump()
                                : BigDecimal.ZERO;
                        break;
                }

                valeurTotale = valeurTotale.add(valorisation);

                // Compter les lots disponibles
                nombreLots += lotRepository.findByArticleIdAndStatut(
                        article.getId(),
                        Lot.LotStatus.DISPONIBLE).size();

                // Calculer coût moyen
                if (stock.getCoutUnitaireMoyen() != null) {
                    coutMoyenTotal = coutMoyenTotal.add(stock.getCoutUnitaireMoyen());
                    countCout++;
                }
            }
        }

        // Calculer cout moyen
        BigDecimal coutMoyen = countCout > 0
                ? coutMoyenTotal.divide(BigDecimal.valueOf(countCout), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        detail.put("valeurTotale", valeurTotale);
        detail.put("nombreArticles", articles.size());
        detail.put("nombreLots", nombreLots);
        detail.put("coutMoyen", coutMoyen);

        // Métriques spécifiques FEFO
        if ("FEFO".equals(methode)) {
            LocalDate limite = LocalDate.now().plusDays(30);
            List<Lot> lotsProchePeremption = lotRepository.findLotsProchePeremption(limite);

            // Filtrer par méthode FEFO
            long lotsFefoProchePeremption = lotsProchePeremption.stream()
                    .filter(lot -> {
                        Article lotArticle = lot.getArticle();
                        return lotArticle != null && "FEFO".equals(lotArticle.getMethodeValorisation());
                    })
                    .count();

            detail.put("lotsProchePeremption", lotsFefoProchePeremption);

            BigDecimal valeurRisque = BigDecimal.ZERO;
            for (Lot lot : lotsProchePeremption) {
                Article lotArticle = lot.getArticle();
                if (lotArticle != null && "FEFO".equals(lotArticle.getMethodeValorisation())) {
                    if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
                        valeurRisque = valeurRisque.add(
                                lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
                    }
                }
            }
            detail.put("valeurRisque", valeurRisque);
        }

        if ("CUMP".equals(methode)) {
            // Calcul d'écart moyen
            BigDecimal ecartMoyen = BigDecimal.ZERO;
            if (!articles.isEmpty()) {
                BigDecimal sommeEcart = BigDecimal.ZERO;
                int countEcart = 0;

                for (Article article : articles) {
                    if (article.getCoutStandard() != null) {
                        // Trouver le coût moyen des stocks pour cet article
                        List<Stock> articleStocks = stockRepository.findByArticleId(article.getId());
                        BigDecimal coutMoyenArticle = BigDecimal.ZERO;
                        int countStocks = 0;

                        for (Stock stock : articleStocks) {
                            if (stock.getCoutUnitaireMoyen() != null) {
                                coutMoyenArticle = coutMoyenArticle.add(stock.getCoutUnitaireMoyen());
                                countStocks++;
                            }
                        }

                        if (countStocks > 0) {
                            coutMoyenArticle = coutMoyenArticle.divide(
                                    BigDecimal.valueOf(countStocks), 4, RoundingMode.HALF_UP);

                            if (coutMoyenArticle.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal ecart = article.getCoutStandard()
                                        .subtract(coutMoyenArticle)
                                        .abs()
                                        .divide(coutMoyenArticle, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100));

                                sommeEcart = sommeEcart.add(ecart);
                                countEcart++;
                            }
                        }
                    }
                }

                ecartMoyen = countEcart > 0 ? sommeEcart.divide(BigDecimal.valueOf(countEcart), 2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
            }

            detail.put("ecartMoyen", ecartMoyen);
            detail.put("dateMaj", LocalDateTime.now());
        }

        return detail;
    }

    public List<Map<String, Object>> getTopArticlesParValeur(int limit) {
        List<Map<String, Object>> topArticles = new ArrayList<>();

        // Récupérer tous les stocks triés par valeur
        List<Stock> stocks = stockRepository.findAllByOrderByValeurStockCumpDesc();

        stocks.stream()
                .limit(limit)
                .forEach(stock -> {
                    Map<String, Object> articleData = new HashMap<>();
                    articleData.put("code", stock.getArticle().getCodeArticle());
                    articleData.put("libelle", stock.getArticle().getLibelle());
                    articleData.put("valeur", stock.getValeurStockCump());
                    articleData.put("quantite", stock.getQuantiteTheorique());
                    articleData.put("methode", stock.getArticle().getMethodeValorisation());
                    articleData.put("coutUnitaire", stock.getCoutUnitaireMoyen());
                    topArticles.add(articleData);
                });

        return topArticles;
    }

    /**
     * Get évolution de la valorisation
     */
    public Map<String, Object> getEvolutionValorisation(int mois) {
        Map<String, Object> evolution = new HashMap<>();

        LocalDate dateDebut = LocalDate.now().minusMonths(mois);

        // Pour chaque mois, calculer la valorisation totale
        Map<String, BigDecimal> valorisationParMois = new TreeMap<>();

        for (int i = 0; i <= mois; i++) {
            LocalDate dateMois = dateDebut.plusMonths(i);
            String cleMois = dateMois.getYear() + "-" + String.format("%02d", dateMois.getMonthValue());

            // Simuler le calcul (dans un système réel, utiliser l'historique)
            BigDecimal valorisationMois = calculerValorisationMois(dateMois);
            valorisationParMois.put(cleMois, valorisationMois);
        }

        evolution.put("mois", mois);
        evolution.put("valorisationParMois", valorisationParMois);
        evolution.put("dateDebut", dateDebut);
        evolution.put("dateFin", LocalDate.now());

        return evolution;
    }

    private BigDecimal calculerValorisationMois(LocalDate dateMois) {
        // Méthode simplifiée - dans un système réel, utiliser l'historique des coûts
        List<Stock> stocks = stockRepository.findAll();

        return stocks.stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Get détail valorisation d'un article
     */
    public Map<String, Object> getDetailValorisationArticle(UUID articleId) {
        Map<String, Object> detail = new HashMap<>();

        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

        // Récupérer tous les stocks de cet article
        List<Stock> stocks = stockRepository.findAll().stream()
                .filter(s -> s.getArticle().getId().equals(articleId))
                .collect(Collectors.toList());

        // Calculer la valorisation totale
        BigDecimal valorisationTotale = stocks.stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Quantité totale
        Integer quantiteTotale = stocks.stream()
                .mapToInt(Stock::getQuantiteTheorique)
                .sum();

        // Coût moyen pondéré
        BigDecimal coutMoyen = quantiteTotale > 0
                ? valorisationTotale.divide(BigDecimal.valueOf(quantiteTotale), 4, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        detail.put("article", article);
        detail.put("stocks", stocks);
        detail.put("valorisationTotale", valorisationTotale);
        detail.put("quantiteTotale", quantiteTotale);
        detail.put("coutMoyen", coutMoyen);
        detail.put("methodeValorisation", article.getMethodeValorisation());

        return detail;
    }

    public List<Map<String, Object>> getValorisationDetailForDashboard() {
        List<Map<String, Object>> details = new ArrayList<>();
    
        // Récupérer tous les articles
        List<Article> articles = articleRepository.findAll();
    
        for (Article article : articles) {
            // Récupérer tous les stocks pour cet article
            List<Stock> stocks = stockRepository.findByArticleId(article.getId());
    
            if (!stocks.isEmpty()) {
                Map<String, Object> detail = new HashMap<>();
                
                // Calculer les valorisations pour TOUTES les méthodes
                BigDecimal valorisationFifo = BigDecimal.ZERO;
                BigDecimal valorisationFefo = BigDecimal.ZERO;
                BigDecimal valorisationCump = BigDecimal.ZERO;
                Integer quantiteRestante = 0;
                BigDecimal coutUnitaireMoyen = BigDecimal.ZERO;
                LocalDateTime dernierMouvement = null;
    
                for (Stock stock : stocks) {
                    // Calculer TOUJOURS les 3 valorisations pour pouvoir comparer
                    BigDecimal fifoStock = calculerValorisationFIFO(article.getId(), stock.getDepot().getId());
                    BigDecimal fefoStock = calculerValorisationFEFO(article.getId(), stock.getDepot().getId());
                    BigDecimal cumpStock = calculerValorisationCUMP(article.getId(), stock.getDepot().getId());
                    
                    valorisationFifo = valorisationFifo.add(fifoStock);
                    valorisationFefo = valorisationFefo.add(fefoStock);
                    valorisationCump = valorisationCump.add(cumpStock);
                    
                    // ✅ Utiliser quantiteTheorique (stock actuel)
                    quantiteRestante += stock.getQuantiteTheorique() != null ? stock.getQuantiteTheorique() : 0;
                    
                    // Récupérer le coût unitaire moyen
                    if (stock.getCoutUnitaireMoyen() != null) {
                        coutUnitaireMoyen = stock.getCoutUnitaireMoyen();
                    }
                    
                    // Récupérer la date du dernier mouvement
                    if (stock.getDateDernierMouvement() != null) {
                        if (dernierMouvement == null || stock.getDateDernierMouvement().isAfter(dernierMouvement)) {
                            dernierMouvement = stock.getDateDernierMouvement();
                        }
                    }
                }
    
                // Calculer les quantités entrées et sorties depuis les mouvements
                Integer quantiteEntree = 0;
                Integer quantiteSortie = 0;
                LocalDate dateEntree = null;
                
                List<StockMovement> mouvements = mouvementRepository.findByArticleIdOrderByDateMouvementDesc(article.getId());
                for (StockMovement mvt : mouvements) {
                    if (mvt.getStatut() == StockMovement.MovementStatus.VALIDE) {
                        if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE) {
                            quantiteEntree += mvt.getQuantite() != null ? mvt.getQuantite() : 0;
                            // Récupérer la date de la première entrée
                            if (dateEntree == null) {
                                dateEntree = mvt.getDateComptable();
                            }
                        } else {
                            quantiteSortie += mvt.getQuantite() != null ? mvt.getQuantite() : 0;
                        }
                    }
                }
    
                // Sélectionner la valorisation selon la méthode de l'article
                String methode = article.getMethodeValorisation() != null ? article.getMethodeValorisation() : "CUMP";
                BigDecimal valorisation;
                switch (methode) {
                    case "FIFO":
                        valorisation = valorisationFifo;
                        break;
                    case "FEFO":
                        valorisation = valorisationFefo;
                        break;
                    default:
                        valorisation = valorisationCump;
                }
    
                // Construire l'objet de retour avec TOUS les champs attendus par le template
                detail.put("codeArticle", article.getCodeArticle());
                detail.put("libelle", article.getLibelle());
                detail.put("methode", methode);
                detail.put("prixUnitaire", coutUnitaireMoyen);
                detail.put("quantiteEntree", quantiteEntree);
                detail.put("dateEntree", dateEntree);
                detail.put("quantiteSortie", quantiteSortie);
                detail.put("quantiteRestante", quantiteRestante);
                detail.put("valorisation", valorisation);  // ✅ La valorisation selon la méthode
                detail.put("valorisationFifo", valorisationFifo);
                detail.put("valorisationFefo", valorisationFefo);
                detail.put("valorisationCump", valorisationCump);
                detail.put("dateDernierMouvement", dernierMouvement);
    
                details.add(detail);
            }
        }
    
        return details;
    }

    public BigDecimal calculerValorisationFIFOTotale() {
        log.info("Calcul valorisation FIFO totale");

        List<Stock> stocks = stockRepository.findAll();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        for (Stock stock : stocks) {
            Article article = stock.getArticle();
            if (article != null && "FIFO".equals(article.getMethodeValorisation())) {
                BigDecimal valorisation = calculerValorisationFIFO(
                        article.getId(),
                        stock.getDepot().getId());
                valeurTotale = valeurTotale.add(valorisation);
            }
        }

        return valeurTotale;
    }

    /**
     * CORRECTION : Calculer la valorisation FEFO totale
     */
    public BigDecimal calculerValorisationFEFOTotale() {
        log.info("Calcul valorisation FEFO totale");

        List<Stock> stocks = stockRepository.findAll();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        for (Stock stock : stocks) {
            Article article = stock.getArticle();
            if (article != null && "FEFO".equals(article.getMethodeValorisation())) {
                BigDecimal valorisation = calculerValorisationFEFO(
                        article.getId(),
                        stock.getDepot().getId());
                valeurTotale = valeurTotale.add(valorisation);
            }
        }

        return valeurTotale;
    }

    /**
     * Met à jour le CUMP après une entrée en stock
     * Formule: Nouveau CUMP = (Ancienne valeur + Nouvelle valeur) / (Ancienne qté + Nouvelle qté)
     */
    @Transactional
    public void mettreAJourCUMPApresEntree(UUID articleId, UUID depotId, Integer quantiteEntree, BigDecimal coutUnitaire) {
        if (quantiteEntree == null || quantiteEntree <= 0 || coutUnitaire == null) {
            log.warn("Paramètres invalides pour mise à jour CUMP entrée");
            return;
        }

        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        
        Stock stock;
        BigDecimal ancienneValeur;
        int ancienneQuantite;

        if (stockOpt.isPresent()) {
            stock = stockOpt.get();
            ancienneValeur = stock.getValeurStockCump() != null ? stock.getValeurStockCump() : BigDecimal.ZERO;
            ancienneQuantite = stock.getQuantiteTheorique() != null ? stock.getQuantiteTheorique() : 0;
        } else {
            // Créer un nouveau stock si inexistant
            Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article non trouvé: " + articleId));
            Depot depot = depotRepository.findById(depotId)
                .orElseThrow(() -> new RuntimeException("Dépôt non trouvé: " + depotId));
                
            stock = Stock.builder()
                .article(article)
                .depot(depot)
                .quantiteTheorique(0)
                .quantitePhysique(0)
                .quantiteReservee(0)
                .valeurStockCump(BigDecimal.ZERO)
                .build();
            ancienneValeur = BigDecimal.ZERO;
            ancienneQuantite = 0;
        }

        // Calcul CUMP : (Ancienne valeur + Nouvelle valeur) / (Ancienne qté + Nouvelle qté)
        BigDecimal nouvelleValeurEntree = coutUnitaire.multiply(BigDecimal.valueOf(quantiteEntree));
        BigDecimal valeurTotale = ancienneValeur.add(nouvelleValeurEntree);
        int nouvelleQuantite = ancienneQuantite + quantiteEntree;

        stock.setQuantiteTheorique(nouvelleQuantite);
        stock.setQuantitePhysique(nouvelleQuantite);
        stock.setValeurStockCump(valeurTotale);
        stock.setDateDernierMouvement(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());

        stockRepository.save(stock);

        BigDecimal nouveauCUMP = nouvelleQuantite > 0 
            ? valeurTotale.divide(BigDecimal.valueOf(nouvelleQuantite), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
            
        log.info("CUMP mis à jour après ENTRÉE - Article: {}, Qté: {} -> {}, Valeur: {} -> {}, CUMP: {}",
            articleId, ancienneQuantite, nouvelleQuantite, ancienneValeur, valeurTotale, nouveauCUMP);
    }

    /**
     * Met à jour le CUMP après une sortie de stock
     * Le CUMP reste constant, seule la valeur totale diminue proportionnellement
     */
    @Transactional
    public void mettreAJourCUMPApresSortie(UUID articleId, UUID depotId, Integer quantiteSortie) {
        if (quantiteSortie == null || quantiteSortie <= 0) {
            return;
        }

        Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .orElseThrow(() -> new RuntimeException("Stock non trouvé pour article: " + articleId + ", dépôt: " + depotId));

        int ancienneQuantite = stock.getQuantiteTheorique() != null ? stock.getQuantiteTheorique() : 0;
        BigDecimal ancienneValeur = stock.getValeurStockCump() != null ? stock.getValeurStockCump() : BigDecimal.ZERO;

        if (ancienneQuantite < quantiteSortie) {
            throw new RuntimeException("Stock insuffisant. Disponible: " + ancienneQuantite + ", Demandé: " + quantiteSortie);
        }

        // CUMP reste constant à la sortie, la valeur diminue proportionnellement
        BigDecimal cumpActuel = ancienneQuantite > 0 
            ? ancienneValeur.divide(BigDecimal.valueOf(ancienneQuantite), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
        
        BigDecimal valeurSortie = cumpActuel.multiply(BigDecimal.valueOf(quantiteSortie));
        int nouvelleQuantite = ancienneQuantite - quantiteSortie;
        BigDecimal nouvelleValeur = nouvelleQuantite > 0 ? ancienneValeur.subtract(valeurSortie) : BigDecimal.ZERO;

        stock.setQuantiteTheorique(nouvelleQuantite);
        stock.setQuantitePhysique(nouvelleQuantite);
        stock.setValeurStockCump(nouvelleValeur);
        stock.setDateDernierMouvement(LocalDateTime.now());
        stock.setUpdatedAt(LocalDateTime.now());

        stockRepository.save(stock);

        log.info("CUMP mis à jour après SORTIE - Article: {}, Qté: {} -> {}, Valeur: {} -> {}, CUMP constant: {}",
            articleId, ancienneQuantite, nouvelleQuantite, ancienneValeur, nouvelleValeur, cumpActuel);
    }

    /**
     * Recalcule complètement la valorisation d'un article à partir des lots
     * Utile pour corriger les incohérences
     */
    @Transactional
    public void recalculerValorisationComplete(UUID articleId, UUID depotId) {
        List<Lot> lots = lotRepository.findByArticleIdAndDepotIdAndStatut(articleId, depotId, Lot.LotStatus.DISPONIBLE);

        BigDecimal valeurTotale = BigDecimal.ZERO;
        int quantiteTotale = 0;

        for (Lot lot : lots) {
            int qte = lot.getQuantiteActuelle() != null ? lot.getQuantiteActuelle() : 0;
            BigDecimal prix = lot.getCoutUnitaire() != null ? lot.getCoutUnitaire() : BigDecimal.ZERO;
            
            valeurTotale = valeurTotale.add(prix.multiply(BigDecimal.valueOf(qte)));
            quantiteTotale += qte;
        }

        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            stock.setValeurStockCump(valeurTotale);
            stock.setQuantiteTheorique(quantiteTotale);
            stock.setQuantitePhysique(quantiteTotale);
            stock.setUpdatedAt(LocalDateTime.now());
            stockRepository.save(stock);
            
            log.info("Valorisation recalculée - Article: {}, Dépôt: {}, Qté: {}, Valeur: {}", 
                articleId, depotId, quantiteTotale, valeurTotale);
        }
    }

    /**
     * Obtient un résumé de valorisation pour un article
     */
    public Map<String, Object> getResumeValorisation(UUID articleId, UUID depotId) {
        Map<String, Object> resume = new HashMap<>();
        
        Article article = articleRepository.findById(articleId).orElse(null);
        if (article == null) {
            return resume;
        }

        resume.put("articleId", articleId);
        resume.put("articleCode", article.getCodeArticle());
        resume.put("articleNom", article.getLibelle());
        resume.put("methode", article.getMethodeValorisation());
        resume.put("valorisationCUMP", calculerValorisationCUMP(articleId, depotId));
        resume.put("valorisationFIFO", calculerValorisationFIFO(articleId, depotId));
        resume.put("valorisationFEFO", calculerValorisationFEFO(articleId, depotId));
        
        stockRepository.findByArticleIdAndDepotId(articleId, depotId).ifPresent(stock -> {
            resume.put("quantiteStock", stock.getQuantiteTheorique());
            resume.put("coutUnitaireMoyen", stock.getCoutUnitaireMoyen());
        });

        return resume;
    }

}