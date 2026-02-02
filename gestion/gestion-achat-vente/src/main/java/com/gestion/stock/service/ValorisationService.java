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

    /**
     * Calcul du CUMP (Coût Unitaire Moyen Pondéré) complet
     */
    @Transactional
    public BigDecimal calculerCUMP(UUID articleId, UUID depotId) {
        log.info("Calcul CUMP pour article: {}, dépôt: {}", articleId, depotId);

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
     */
    private BigDecimal calculerValorisationFIFO(UUID articleId, UUID depotId) {
        log.info("Calcul valorisation FIFO pour article: {}", articleId);

        // Récupérer les lots disponibles triés par date réception (FIFO)
        List<Lot> lots = lotRepository.findByArticleIdAndStatutOrderByDateReceptionAsc(
                articleId, Lot.LotStatus.DISPONIBLE);

        // Récupérer le stock actuel
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (!stockOpt.isPresent()) {
            return BigDecimal.ZERO;
        }

        Stock stock = stockOpt.get();
        Integer quantiteRestante = stock.getQuantiteTheorique();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        // Parcourir les lots du plus ancien au plus récent
        for (Lot lot : lots) {
            if (quantiteRestante <= 0)
                break;

            Integer quantiteLot = Math.min(lot.getQuantiteActuelle(), quantiteRestante);
            BigDecimal valeurLot = lot.getCoutUnitaire()
                    .multiply(BigDecimal.valueOf(quantiteLot));

            valeurTotale = valeurTotale.add(valeurLot);
            quantiteRestante -= quantiteLot;
        }

        // Si il reste de la quantité non valorisée (cas où pas assez de lots)
        if (quantiteRestante > 0) {
            // Utiliser le coût standard de l'article
            Article article = stock.getArticle();
            BigDecimal coutStandard = article.getCoutStandard() != null ? article.getCoutStandard() : BigDecimal.ZERO;

            valeurTotale = valeurTotale.add(
                    coutStandard.multiply(BigDecimal.valueOf(quantiteRestante)));
        }

        return valeurTotale;
    }

    /**
     * Valorisation FEFO (First Expired, First Out) pour produits périssables
     */
    private BigDecimal calculerValorisationFEFO(UUID articleId, UUID depotId) {
        log.info("Calcul valorisation FEFO pour article: {}", articleId);

        // Récupérer les lots triés par date de péremption (FEFO)
        List<Lot> lots = lotRepository.findByArticleIdAndStatutOrderByDatePeremptionAsc(
                articleId, Lot.LotStatus.DISPONIBLE);

        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (!stockOpt.isPresent()) {
            return BigDecimal.ZERO;
        }

        Stock stock = stockOpt.get();
        Integer quantiteRestante = stock.getQuantiteTheorique();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        // Parcourir les lots par date de péremption croissante
        for (Lot lot : lots) {
            if (quantiteRestante <= 0)
                break;

            Integer quantiteLot = Math.min(lot.getQuantiteActuelle(), quantiteRestante);
            BigDecimal valeurLot = lot.getCoutUnitaire()
                    .multiply(BigDecimal.valueOf(quantiteLot));

            valeurTotale = valeurTotale.add(valeurLot);
            quantiteRestante -= quantiteLot;
        }

        // Complément avec coût standard si nécessaire
        if (quantiteRestante > 0) {
            Article article = stock.getArticle();
            BigDecimal coutStandard = article.getCoutStandard() != null ? article.getCoutStandard() : BigDecimal.ZERO;

            valeurTotale = valeurTotale.add(
                    coutStandard.multiply(BigDecimal.valueOf(quantiteRestante)));
        }

        return valeurTotale;
    }

    /**
     * Valorisation CUMP standard
     */
    private BigDecimal calculerValorisationCUMP(UUID articleId, UUID depotId) {
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

    /**
     * Sauvegarder dans l'historique des coûts
     */
    private void sauvegarderHistoriqueCout(UUID articleId, UUID depotId, LocalDate dateEffet,
            BigDecimal coutUnitaire, Integer quantite,
            BigDecimal valeurStock, String methode, UUID utilisateurId) {
        // À implémenter avec HistoriqueCoutRepository
        log.info("Historique sauvegardé: Article {}, Date: {}, Valeur: {}",
                articleId, dateEffet, valeurStock);
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

    // ValorisationService.java - Méthodes supplémentaires pour le dashboard
    public Map<String, Object> getSyntheseValorisation() {
        Map<String, Object> synthese = new HashMap<>();

        // Récupérer tous les stocks
        List<Stock> stocks = stockRepository.findAll();

        BigDecimal valeurFifoTotal = BigDecimal.ZERO;
        BigDecimal valeurFefoTotal = BigDecimal.ZERO;
        BigDecimal valeurCumpTotal = BigDecimal.ZERO;

        int articlesFifo = 0;
        int articlesFefo = 0;
        int articlesCump = 0;

        for (Stock stock : stocks) {
            String methode = stock.getArticle().getMethodeValorisation();
            BigDecimal valeur = stock.getValeurStockCump() != null ? stock.getValeurStockCump() : BigDecimal.ZERO;

            switch (methode) {
                case "FIFO":
                    valeurFifoTotal = valeurFifoTotal.add(valeur);
                    articlesFifo++;
                    break;
                case "FEFO":
                    valeurFefoTotal = valeurFefoTotal.add(valeur);
                    articlesFefo++;
                    break;
                case "CUMP":
                    valeurCumpTotal = valeurCumpTotal.add(valeur);
                    articlesCump++;
                    break;
            }
        }

        // Total
        BigDecimal valeurTotale = valeurFifoTotal.add(valeurFefoTotal).add(valeurCumpTotal);

        synthese.put("valeurFifo", valeurFifoTotal);
        synthese.put("valeurFefo", valeurFefoTotal);
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

        BigDecimal coutSortiesFifo = BigDecimal.ZERO;
        BigDecimal coutSortiesFefo = BigDecimal.ZERO;

        List<Stock> fifoStocks = stocks.stream()
                .filter(s -> s.getArticle() != null && "FIFO".equals(s.getArticle().getMethodeValorisation()))
                .collect(Collectors.toList());

        List<Stock> fefoStocks = stocks.stream()
                .filter(s -> s.getArticle() != null && "FEFO".equals(s.getArticle().getMethodeValorisation()))
                .collect(Collectors.toList());

        if (!fifoStocks.isEmpty()) {
            BigDecimal sum = fifoStocks.stream()
                    .map(s -> s.getCoutUnitaireMoyen() != null ? s.getCoutUnitaireMoyen() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            coutSortiesFifo = sum.divide(BigDecimal.valueOf(fifoStocks.size()), 4, RoundingMode.HALF_UP);
        }

        if (!fefoStocks.isEmpty()) {
            BigDecimal sum = fefoStocks.stream()
                    .map(s -> s.getCoutUnitaireMoyen() != null ? s.getCoutUnitaireMoyen() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            coutSortiesFefo = sum.divide(BigDecimal.valueOf(fefoStocks.size()), 4, RoundingMode.HALF_UP);
        }

        BigDecimal differenceCout = coutSortiesFefo.subtract(coutSortiesFifo);

        // Put into synthese map (so template can access them)
        synthese.put("coutSortiesFifo", coutSortiesFifo);
        synthese.put("coutSortiesFefo", coutSortiesFefo);
        synthese.put("differenceCout", differenceCout);

        return synthese;
    }

    public Map<String, Object> getDetailValorisationParMethode(String methode) {
        Map<String, Object> detail = new HashMap<>();

        // Récupérer les stocks par méthode
        List<Stock> stocks = stockRepository.findByArticleMethodeValorisation(methode);

        BigDecimal valeurTotale = stocks.stream()
                .map(s -> s.getValeurStockCump() != null ? s.getValeurStockCump() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long nombreLots = stocks.stream()
                .flatMap(s -> lotRepository.findByArticleId(s.getArticle().getId()).stream())
                .filter(l -> l.getStatut() == Lot.LotStatus.DISPONIBLE)
                .count();

        // Calculer coût moyen
        BigDecimal coutMoyen = BigDecimal.ZERO;
        if (!stocks.isEmpty()) {
            BigDecimal sommeCout = stocks.stream()
                    .map(s -> s.getCoutUnitaireMoyen() != null ? s.getCoutUnitaireMoyen() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            coutMoyen = sommeCout.divide(BigDecimal.valueOf(stocks.size()), 2, RoundingMode.HALF_UP);
        }

        detail.put("valeurTotale", valeurTotale);
        detail.put("nombreArticles", stocks.size());
        detail.put("nombreLots", nombreLots);
        detail.put("coutMoyen", coutMoyen);

        // Ajouter des métriques spécifiques
        if ("FEFO".equals(methode)) {
            LocalDate limite = LocalDate.now().plusDays(30);
            List<Lot> lotsProchePeremption = lotRepository.findLotsProchePeremption(limite);
            detail.put("lotsProchePeremption", lotsProchePeremption.size());

            BigDecimal valeurRisque = BigDecimal.ZERO;
            for (Lot lot : lotsProchePeremption) {
                if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
                    valeurRisque = valeurRisque.add(
                            lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
                }
            }
            detail.put("valeurRisque", valeurRisque);
        }

        if ("CUMP".equals(methode)) {
            // Calcul d'un écart moyen fictif : ici on prend l'écart moyen des stocks si disponible
            BigDecimal ecartMoyen = BigDecimal.ZERO;
            if (!stocks.isEmpty()) {
                // Exemple : écart moyen en pourcentage basé sur variance (vous pouvez remplacer par votre logique)
                // Ici on met un calcul simple : si coutMoyen > 0, ecartMoyen = (coutMoyen - coutStandard)/(coutMoyen) * 100
                BigDecimal coutStandardMoyen = stocks.stream()
                        .map(s -> s.getArticle().getCoutStandard() != null ? s.getArticle().getCoutStandard() : BigDecimal.ZERO)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                if (coutMoyen.compareTo(BigDecimal.ZERO) > 0) {
                    try {
                        BigDecimal moyenneCoutStandard = coutStandardMoyen.divide(BigDecimal.valueOf(stocks.size()), 4, RoundingMode.HALF_UP);
                        ecartMoyen = moyenneCoutStandard.compareTo(BigDecimal.ZERO) != 0
                                    ? moyenneCoutStandard.subtract(coutMoyen).abs()
                                        .divide(coutMoyen, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                    : BigDecimal.ZERO;
                    } catch (Exception ex) {
                        ecartMoyen = BigDecimal.ZERO;
                    }
                }
            }
            detail.put("ecartMoyen", ecartMoyen.setScale(2, RoundingMode.HALF_UP));
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
        BigDecimal coutMoyen = quantiteTotale > 0 ?
                valorisationTotale.divide(BigDecimal.valueOf(quantiteTotale), 4, RoundingMode.HALF_UP) :
                BigDecimal.ZERO;
        
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
            Map<String, Object> detail = new HashMap<>();
            
            // Récupérer tous les stocks pour cet article
            List<Stock> stocks = stockRepository.findByArticleId(article.getId());
            
            if (!stocks.isEmpty()) {
                // Calculer les valorisations
                BigDecimal valorisationFifo = BigDecimal.ZERO;
                BigDecimal valorisationFefo = BigDecimal.ZERO;
                BigDecimal valorisationCump = BigDecimal.ZERO;
                Integer quantiteTotale = 0;
                
                for (Stock stock : stocks) {
                    // Pour chaque dépôt, calculer la valorisation
                    switch (article.getMethodeValorisation()) {
                        case "FIFO":
                            valorisationFifo = valorisationFifo.add(stock.getValeurStockCump());
                            break;
                        case "FEFO":
                            valorisationFefo = valorisationFefo.add(stock.getValeurStockCump());
                            break;
                        case "CUMP":
                            valorisationCump = valorisationCump.add(stock.getValeurStockCump());
                            break;
                    }
                    quantiteTotale += stock.getQuantiteTheorique();
                }
                
                // S'assurer que toutes les méthodes ont une valeur (même si 0)
                detail.put("codeArticle", article.getCodeArticle());
                detail.put("libelle", article.getLibelle());
                detail.put("methode", article.getMethodeValorisation());
                detail.put("quantite", quantiteTotale);
                detail.put("valorisationFifo", valorisationFifo);
                detail.put("valorisationFefo", valorisationFefo);
                detail.put("valorisationCump", valorisationCump);
                
                details.add(detail);
            }
        }
        
        // Limiter aux 50 premiers pour éviter des données trop lourdes
        return details.stream().limit(50).collect(Collectors.toList());
    }
}