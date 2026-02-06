package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReportingService {

    private final StockRepository stockRepository;
    private final StockMovementRepository mouvementRepository;
    private final InventaireRepository inventaireRepository;
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;

    public Map<String, Object> getDashboardKPIs() {
        Map<String, Object> kpis = new HashMap<>();

        // 1. Valeur stock total
        BigDecimal valeurStockTotal = stockRepository.findAll().stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. Nombre d'articles actifs
        long nombreArticlesActifs = articleRepository.countByActifTrue();

        // 3. Articles en rupture
        long articlesRupture = 0;
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            Article article = stock.getArticle();
            if (article != null && article.getStockMinimum() != null) {
                if (stock.getQuantiteDisponible() < article.getStockMinimum()) {
                    articlesRupture++;
                }
            }
        }

        // 4. Taux de précision stock (simplifié)
        Double tauxPrecisionMoyen = 95.5; // À remplacer par calcul réel

        // 5. Rotation de stock moyenne
        BigDecimal rotationMoyenne = calculerRotationMoyenne();

        // 6. Valeur des lots à péremption proche
        LocalDate limite = LocalDate.now().plusDays(30);
        List<Lot> lotsRisque = lotRepository.findLotsProchePeremption(limite);
        BigDecimal valeurRisque = lotsRisque.stream()
                .map(lot -> {
                    if (lot.getCoutUnitaire() != null && lot.getQuantiteActuelle() != null) {
                        return lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle()));
                    }
                    return BigDecimal.ZERO;
                })
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 7. Mouvements du jour
        long mouvementsAujourdhui = mouvementRepository.countMouvementsAujourdhui();

        // 8. Évolution par rapport au mois précédent (simplifié)
        BigDecimal evolution = BigDecimal.valueOf(2.5); // +2.5%

        // 9. Date du dernier inventaire
        LocalDate dateDernierInventaire = null;
        try {
            List<Inventaire> inventaires = inventaireRepository.findTop1ByOrderByDateFinDesc();
            if (!inventaires.isEmpty() && inventaires.get(0).getDateFin() != null) {
                dateDernierInventaire = inventaires.get(0).getDateFin();
            }
        } catch (Exception e) {
            log.warn("Impossible de récupérer la date du dernier inventaire", e);
        }

        kpis.put("evolutionValeur", evolution);
        kpis.put("valeurStockTotal", valeurStockTotal);
        kpis.put("nombreArticlesActifs", nombreArticlesActifs);
        kpis.put("articlesRupture", articlesRupture);
        kpis.put("tauxPrecision", tauxPrecisionMoyen != null ? String.format("%.1f%%", tauxPrecisionMoyen) : "N/A");
        kpis.put("rotationMoyenne", rotationMoyenne);
        kpis.put("rotationStock", rotationMoyenne);
        kpis.put("valeurRisquePeremption", valeurRisque);
        kpis.put("mouvementsAujourdhui", mouvementsAujourdhui);
        kpis.put("lotsProchePeremption", lotsRisque.size());
        kpis.put("dateDernierInventaire", dateDernierInventaire);
        kpis.put("dateCalcul", LocalDateTime.now());

        return kpis;
    }

    public List<Map<String, Object>> getEvolutionValeurStock(int mois) {
        List<Map<String, Object>> evolution = new ArrayList<>();

        // Pour démonstration, créer des données réalistes
        LocalDate aujourdhui = LocalDate.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        BigDecimal valeurBase = BigDecimal.valueOf(20000); // Valeur de base

        for (int i = mois - 1; i >= 0; i--) {
            LocalDate date = aujourdhui.minusMonths(i);
            YearMonth yearMonth = YearMonth.from(date);

            // Simulation d'évolution réaliste (± 5-15%)
            double variation = 0.9 + (Math.random() * 0.2); // Entre 0.9 et 1.1
            BigDecimal valeurMois = valeurBase.multiply(BigDecimal.valueOf(variation));

            Map<String, Object> point = new HashMap<>();
            point.put("periode", yearMonth.format(formatter));
            point.put("valeur", valeurMois);
            point.put("annee", yearMonth.getYear());
            point.put("mois", yearMonth.getMonthValue());

            evolution.add(point);
        }

        return evolution;
    }

    /**
     * Top 10 articles par valeur de stock
     */
    public List<Map<String, Object>> getTopArticlesParValeur(int limit) {
        List<Map<String, Object>> topArticles = new ArrayList<>();

        List<Stock> stocks = stockRepository.findAll();
        stocks.sort((s1, s2) -> s2.getValeurStockCump().compareTo(s1.getValeurStockCump()));

        int count = 0;
        for (Stock stock : stocks) {
            if (count >= limit)
                break;

            Map<String, Object> articleData = new HashMap<>();
            articleData.put("codeArticle", stock.getArticle().getCodeArticle());
            articleData.put("libelle", stock.getArticle().getLibelle());
            articleData.put("valeurStock", stock.getValeurStockCump());
            articleData.put("quantite", stock.getQuantiteTheorique());
            articleData.put("coutMoyen", stock.getCoutUnitaireMoyen());
            articleData.put("depot", stock.getDepot().getNom());

            topArticles.add(articleData);
            count++;
        }

        return topArticles;
    }

    /**
     * Analyse ABC des articles
     */
    public List<Map<String, Object>> analyserABC(UUID depotId) {
        List<Map<String, Object>> analyse = new ArrayList<>();

        List<Stock> stocks = depotId != null ? stockRepository.findByDepotId(depotId) : stockRepository.findAll();

        // Trier par valeur décroissante
        stocks.sort((s1, s2) -> s2.getValeurStockCump().compareTo(s1.getValeurStockCump()));

        // Calculer la valeur totale
        BigDecimal valeurTotale = stocks.stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valeurCumulee = BigDecimal.ZERO;

        for (Stock stock : stocks) {
            BigDecimal pourcentageValeur = stock.getValeurStockCump()
                    .divide(valeurTotale, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            valeurCumulee = valeurCumulee.add(stock.getValeurStockCump());
            BigDecimal pourcentageCumule = valeurCumulee
                    .divide(valeurTotale, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            // Classification ABC
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
            item.put("rotation", calculerRotationArticle(stock.getArticle().getId()));

            analyse.add(item);
        }

        return analyse;
    }

    /**
     * Rotation de stock par article
     */
    public List<Map<String, Object>> getRotationStock(int periodeMois) {
        List<Map<String, Object>> rotationData = new ArrayList<>();

        List<Article> articles = articleRepository.findByActifTrue();

        for (Article article : articles) {
            List<Stock> stocks = stockRepository.findByArticleId(article.getId());

            for (Stock stock : stocks) {
                Map<String, Object> rotation = calculerRotationArticleDepot(
                        article.getId(), stock.getDepot().getId(), periodeMois);

                if (rotation != null) {
                    rotation.put("articleCode", article.getCodeArticle());
                    rotation.put("articleLibelle", article.getLibelle());
                    rotation.put("depot", stock.getDepot().getNom());
                    rotation.put("stockMoyen", stock.getQuantiteTheorique());

                    rotationData.add(rotation);
                }
            }
        }

        // Trier par rotation décroissante
        rotationData.sort((r1, r2) -> {
            BigDecimal rot1 = (BigDecimal) r1.get("rotation");
            BigDecimal rot2 = (BigDecimal) r2.get("rotation");
            return rot2.compareTo(rot1);
        });

        return rotationData;
    }

    /**
     * Statistiques des mouvements
     */
    public Map<String, Object> getStatistiquesMouvements(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();

        List<StockMovement> mouvements = mouvementRepository
                .findByDateMouvementBetween(dateDebut, dateFin);

        long totalMouvements = mouvements.size();
        long entrees = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                .count();
        long sorties = totalMouvements - entrees;

        BigDecimal valeurEntrees = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                .map(StockMovement::getValeurMouvement)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valeurSorties = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
                .map(StockMovement::getValeurMouvement)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Top 5 types de mouvement
        Map<String, Long> mouvementsParType = new HashMap<>();
        for (StockMovement mvt : mouvements) {
            String type = mvt.getType().getLibelle();
            mouvementsParType.put(type, mouvementsParType.getOrDefault(type, 0L) + 1);
        }

        stats.put("totalMouvements", totalMouvements);
        stats.put("entrees", entrees);
        stats.put("sorties", sorties);
        stats.put("valeurEntrees", valeurEntrees);
        stats.put("valeurSorties", valeurSorties);
        stats.put("mouvementsParType", mouvementsParType);
        stats.put("periode", dateDebut + " - " + dateFin);

        return stats;
    }

    /**
     * Alertes et notifications
     */
    public Map<String, Object> getAlertes() {
        Map<String, Object> alertes = new HashMap<>();

        // 1. Ruptures de stock
        List<Stock> ruptures = stockRepository.findArticlesEnRupture();
        List<Map<String, Object>> alertesRupture = new ArrayList<>();
        for (Stock stock : ruptures) {
            Map<String, Object> alerte = new HashMap<>();
            alerte.put("article", stock.getArticle().getCodeArticle());
            alerte.put("libelle", stock.getArticle().getLibelle());
            alerte.put("depot", stock.getDepot().getNom());
            alerte.put("stockMinimum", stock.getArticle().getStockMinimum());
            alerte.put("quantite", stock.getQuantiteTheorique());
            alertesRupture.add(alerte);
        }

        // 2. Lots proches péremption
        LocalDate limite = LocalDate.now().plusDays(7);
        List<Lot> lotsProchePeremption = lotRepository.findLotsProchePeremption(limite);
        List<Map<String, Object>> alertesPeremption = new ArrayList<>();
        for (Lot lot : lotsProchePeremption) {
            Map<String, Object> alerte = new HashMap<>();
            alerte.put("lot", lot.getNumeroLot());
            alerte.put("article", lot.getArticle().getCodeArticle());
            alerte.put("datePeremption", lot.getDatePeremption());
            alerte.put("joursRestants", LocalDate.now().until(lot.getDatePeremption()).getDays());
            alerte.put("quantite", lot.getQuantiteActuelle());
            alerte.put("valeur", lot.getCoutUnitaire().multiply(
                    BigDecimal.valueOf(lot.getQuantiteActuelle())));
            alertesPeremption.add(alerte);
        }

        // 3. Articles en surstock
        List<Map<String, Object>> alertesSurstock = getArticlesSurstock();

        // Calculer la valeur totale du surstock
        BigDecimal valeurSurstock = alertesSurstock.stream()
                .map(s -> (BigDecimal) s.get("valeurExcedent"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Calculer la valeur totale des lots à risque FEFO
        BigDecimal valeurRisqueFefo = alertesPeremption.stream()
                .map(a -> (BigDecimal) a.get("valeur"))
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Inventaires en cours
        List<Inventaire> inventairesEnCours = inventaireRepository.findInventairesActifs();

        // Total des alertes
        int nombreAlertes = alertesRupture.size() + alertesPeremption.size() + alertesSurstock.size();

        alertes.put("ruptures", alertesRupture);
        alertes.put("rupturesCount", alertesRupture.size());
        alertes.put("rupturesStock", alertesRupture.size()); // Alias pour template
        alertes.put("peremption", alertesPeremption);
        alertes.put("peremptionCount", alertesPeremption.size());
        alertes.put("lotsProchePeremption", alertesPeremption.size()); // Alias pour template
        alertes.put("valeurRisqueFefo", valeurRisqueFefo);
        alertes.put("surstock", alertesSurstock);
        alertes.put("surstockCount", alertesSurstock.size());
        alertes.put("surstocks", alertesSurstock.size()); // Alias pour template
        alertes.put("valeurSurstock", valeurSurstock);
        alertes.put("nombreAlertes", nombreAlertes);
        alertes.put("inventairesEnCours", inventairesEnCours.size());
        alertes.put("dateVerification", LocalDateTime.now());

        return alertes;
    }

    /**
     * Rapport mensuel de stock
     */
    public Map<String, Object> getRapportMensuel(int annee, int mois) {
        Map<String, Object> rapport = new HashMap<>();

        LocalDate debutMois = LocalDate.of(annee, mois, 1);
        LocalDate finMois = debutMois.withDayOfMonth(debutMois.lengthOfMonth());

        // Statistiques de base
        rapport.put("periode", String.format("%02d/%d", mois, annee));
        rapport.put("valeurStockDebut", calculerValeurStockParMois(annee, mois));
        rapport.put("valeurStockFin", calculerValeurStockParMois(
                finMois.getYear(), finMois.getMonthValue()));

        // Mouvements du mois
        Map<String, Object> statsMouvements = getStatistiquesMouvements(debutMois, finMois);
        rapport.put("mouvements", statsMouvements);

        // Inventaires du mois
        List<Inventaire> inventaires = inventaireRepository.findByDateDebutBetween(debutMois, finMois);
        rapport.put("inventairesRealises", inventaires.size());

        // Ajustements du mois
        BigDecimal valeurAjustements = BigDecimal.ZERO; // À calculer avec ajustementRepository
        rapport.put("valeurAjustements", valeurAjustements);

        // Top 10 mouvements
        List<StockMovement> mouvementsMois = mouvementRepository
                .findByDateMouvementBetween(debutMois, finMois);
        mouvementsMois.sort((m1, m2) -> m2.getValeurMouvement().compareTo(m1.getValeurMouvement()));

        List<Map<String, Object>> topMouvements = new ArrayList<>();
        int limit = Math.min(10, mouvementsMois.size());
        for (int i = 0; i < limit; i++) {
            StockMovement mvt = mouvementsMois.get(i);
            Map<String, Object> mouvementData = new HashMap<>();
            mouvementData.put("reference", mvt.getReference());
            mouvementData.put("date", mvt.getDateMouvement());
            mouvementData.put("type", mvt.getType().getLibelle());
            mouvementData.put("article", mvt.getArticle().getCodeArticle());
            mouvementData.put("quantite", mvt.getQuantite());
            mouvementData.put("valeur", mvt.getValeurMouvement());
            topMouvements.add(mouvementData);
        }

        rapport.put("topMouvements", topMouvements);
        rapport.put("dateGeneration", LocalDateTime.now());

        return rapport;
    }

    /**
     * Méthodes utilitaires privées
     */
    private BigDecimal calculerRotationMoyenne() {
        List<Article> articles = articleRepository.findByActifTrue();
        BigDecimal rotationTotale = BigDecimal.ZERO;
        int count = 0;

        for (Article article : articles) {
            BigDecimal rotation = calculerRotationArticle(article.getId());
            if (rotation != null && rotation.compareTo(BigDecimal.ZERO) > 0) {
                rotationTotale = rotationTotale.add(rotation);
                count++;
            }
        }

        return count > 0 ? rotationTotale.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private BigDecimal calculerRotationArticle(UUID articleId) {
        List<Stock> stocks = stockRepository.findByArticleId(articleId);
        BigDecimal rotationMoyenne = BigDecimal.ZERO;
        int count = 0;

        for (Stock stock : stocks) {
            Map<String, Object> rotation = calculerRotationArticleDepot(
                    articleId, stock.getDepot().getId(), 12);
            if (rotation != null && rotation.get("rotation") != null) {
                rotationMoyenne = rotationMoyenne.add((BigDecimal) rotation.get("rotation"));
                count++;
            }
        }

        return count > 0 ? rotationMoyenne.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
    }

    private Map<String, Object> calculerRotationArticleDepot(UUID articleId, UUID depotId, int periodeMois) {
        LocalDate dateDebut = LocalDate.now().minusMonths(periodeMois);

        Long sorties = mouvementRepository.sumSortiesByArticleAndDepot(articleId, depotId);
        if (sorties == null)
            sorties = 0L;

        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (stockOpt.isPresent()) {
            Stock stock = stockOpt.get();
            Integer stockMoyen = stock.getQuantiteTheorique();

            if (stockMoyen > 0) {
                BigDecimal rotation = BigDecimal.valueOf(sorties)
                        .divide(BigDecimal.valueOf(stockMoyen), 2, RoundingMode.HALF_UP);

                Map<String, Object> result = new HashMap<>();
                result.put("sorties", sorties);
                result.put("stockMoyen", stockMoyen);
                result.put("rotation", rotation);
                result.put("periodeMois", periodeMois);

                return result;
            }
        }

        return null;
    }

    private BigDecimal calculerValeurStockParMois(int annee, int mois) {
        // Simplifié : utiliser la valeur actuelle
        // Dans une implémentation réelle, utiliser l'historique des coûts
        return stockRepository.findAll().stream()
                .map(Stock::getValeurStockCump)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Map<String, Object>> getArticlesSurstock() {
        List<Map<String, Object>> surstocks = new ArrayList<>();

        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            Article article = stock.getArticle();
            if (article.getStockMaximum() != null &&
                    stock.getQuantiteTheorique() > article.getStockMaximum()) {

                Map<String, Object> surstock = new HashMap<>();
                surstock.put("article", article.getCodeArticle());
                surstock.put("libelle", article.getLibelle());
                surstock.put("stockActuel", stock.getQuantiteTheorique());
                surstock.put("stockMaximum", article.getStockMaximum());
                surstock.put("excedent", stock.getQuantiteTheorique() - article.getStockMaximum());
                surstock.put("valeurExcedent", stock.getCoutUnitaireMoyen().multiply(
                        BigDecimal.valueOf(stock.getQuantiteTheorique() - article.getStockMaximum())));
                surstock.put("depot", stock.getDepot().getNom());

                surstocks.add(surstock);
            }
        }

        // Trier par valeur d'excédent
        surstocks.sort((s1, s2) -> {
            BigDecimal v1 = (BigDecimal) s1.get("valeurExcedent");
            BigDecimal v2 = (BigDecimal) s2.get("valeurExcedent");
            return v2.compareTo(v1);
        });

        return surstocks;
    }
}