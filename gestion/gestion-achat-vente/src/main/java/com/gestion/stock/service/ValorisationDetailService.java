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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ValorisationDetailService {

    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final LotRepository lotRepository;
    private final StockMovementRepository mouvementRepository;
    private final HistoriqueCoutRepository historiqueRepository;

    /**
     * Obtenir les données de valorisation avec filtres
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getValorisationDetailFiltre(
            UUID depotId,
            String articleSearch,
            String methode,
            LocalDate dateDebut,
            LocalDate dateFin) {

        List<Map<String, Object>> resultats = new ArrayList<>();

        // Récupérer tous les articles actifs
        List<Article> articles;
        if (articleSearch != null && !articleSearch.isEmpty()) {
            articles = articleRepository.findByCodeArticleContainingOrLibelleContaining(
                    articleSearch);
        } else {
            articles = articleRepository.findByActifTrue();
        }

        // Filtrer par méthode si spécifiée
        if (methode != null && !methode.isEmpty()) {
            articles = articles.stream()
                    .filter(a -> methode.equals(a.getMethodeValorisation()))
                    .collect(Collectors.toList());
        }

        // Pour chaque article, récupérer les stocks par dépôt
        for (Article article : articles) {
            List<Stock> stocks;
            if (depotId != null) {
                stocks = stockRepository.findByArticleIdAndDepotId(article.getId(), depotId)
                        .map(List::of)
                        .orElse(Collections.emptyList());
            } else {
                stocks = stockRepository.findByArticleId(article.getId());
            }

            for (Stock stock : stocks) {
                Map<String, Object> ligne = new HashMap<>();
                
                // Informations de base
                ligne.put("articleId", article.getId());
                ligne.put("codeArticle", article.getCodeArticle());
                ligne.put("libelle", article.getLibelle());
                ligne.put("methode", article.getMethodeValorisation());
                ligne.put("depotId", stock.getDepot().getId());
                ligne.put("depotNom", stock.getDepot().getNom());
                
                // Prix unitaire (coût moyen actuel)
                BigDecimal coutUnitaire = stock.getCoutUnitaireMoyen() != null ? 
                        stock.getCoutUnitaireMoyen() : BigDecimal.ZERO;
                ligne.put("prixUnitaire", coutUnitaire);
                
                // Quantités avec filtres de date
                Map<String, Object> mouvements = getMouvementsPeriode(
                        article.getId(), 
                        stock.getDepot().getId(),
                        dateDebut,
                        dateFin);
                
                ligne.put("quantiteEntree", mouvements.get("entrees"));
                ligne.put("quantiteSortie", mouvements.get("sorties"));
                ligne.put("quantiteRestante", stock.getQuantiteTheorique());
                
                // Valorisation totale
                BigDecimal valorisation = calculerValorisation(article, stock);
                ligne.put("valorisation", valorisation);
                
                // Date du dernier mouvement
                ligne.put("dateDernierMouvement", stock.getDateDernierMouvement());
                
                // Date de première entrée dans la période
                ligne.put("dateEntree", mouvements.get("premiereEntree"));
                
                // Mouvements détaillés pour le modal
                ligne.put("mouvements", getMouvementsDetail(article.getId(), stock.getDepot().getId(), dateDebut, dateFin));
                
                resultats.add(ligne);
            }
        }

        // Trier par code article
        resultats.sort(Comparator.comparing(m -> (String) m.get("codeArticle")));
        
        return resultats;
    }

    /**
     * Calculer la valorisation selon la méthode de l'article
     */
    private BigDecimal calculerValorisation(Article article, Stock stock) {
        switch (article.getMethodeValorisation()) {
            case "FIFO":
                return calculerValorisationFIFO(article.getId(), stock.getDepot().getId());
            case "FEFO":
                return calculerValorisationFEFO(article.getId(), stock.getDepot().getId());
            case "CUMP":
            default:
                return stock.getValeurStockCump() != null ? 
                        stock.getValeurStockCump() : BigDecimal.ZERO;
        }
    }

    /**
     * Calcul valorisation FIFO
     */
    private BigDecimal calculerValorisationFIFO(UUID articleId, UUID depotId) {
        List<Lot> lots = lotRepository.findByArticleIdAndDepotOrderByDateReceptionAsc(articleId, depotId);
        
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (!stockOpt.isPresent()) {
            return BigDecimal.ZERO;
        }

        Stock stock = stockOpt.get();
        Integer quantiteRestante = stock.getQuantiteTheorique();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        for (Lot lot : lots) {
            if (quantiteRestante <= 0) break;
            
            Integer quantiteLot = Math.min(lot.getQuantiteActuelle(), quantiteRestante);
            valeurTotale = valeurTotale.add(
                    lot.getCoutUnitaire().multiply(BigDecimal.valueOf(quantiteLot))
            );
            quantiteRestante -= quantiteLot;
        }

        return valeurTotale;
    }

    /**
     * Calcul valorisation FEFO
     */
    private BigDecimal calculerValorisationFEFO(UUID articleId, UUID depotId) {
        List<Lot> lots = lotRepository.findByArticleIdAndDepotOrderByDatePeremptionAsc(articleId, depotId);
        
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        if (!stockOpt.isPresent()) {
            return BigDecimal.ZERO;
        }

        Stock stock = stockOpt.get();
        Integer quantiteRestante = stock.getQuantiteTheorique();
        BigDecimal valeurTotale = BigDecimal.ZERO;

        for (Lot lot : lots) {
            if (quantiteRestante <= 0) break;
            
            Integer quantiteLot = Math.min(lot.getQuantiteActuelle(), quantiteRestante);
            valeurTotale = valeurTotale.add(
                    lot.getCoutUnitaire().multiply(BigDecimal.valueOf(quantiteLot))
            );
            quantiteRestante -= quantiteLot;
        }

        return valeurTotale;
    }

    /**
     * Obtenir les mouvements sur une période
     */
    private Map<String, Object> getMouvementsPeriode(UUID articleId, UUID depotId, 
                                                      LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> result = new HashMap<>();
        
        LocalDateTime debut = dateDebut != null ? dateDebut.atStartOfDay() : 
                LocalDateTime.now().minusYears(1);
        LocalDateTime fin = dateFin != null ? dateFin.atTime(23, 59, 59) : 
                LocalDateTime.now();
        
        List<StockMovement> mouvements = mouvementRepository
                .findByArticleIdAndDepotIdAndDateMouvementBetween(
                        articleId, depotId, debut, fin);
        
        long entrees = 0;
        long sorties = 0;
        LocalDateTime premiereEntree = null;
        
        for (StockMovement mvt : mouvements) {
            if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE) {
                entrees += mvt.getQuantite();
                if (premiereEntree == null || 
                    mvt.getDateMouvement().isBefore(premiereEntree)) {
                    premiereEntree = mvt.getDateMouvement();
                }
            } else {
                sorties += mvt.getQuantite();
            }
        }
        
        result.put("entrees", entrees);
        result.put("sorties", sorties);
        result.put("premiereEntree", premiereEntree);
        
        return result;
    }

    /**
     * Obtenir les mouvements détaillés pour un article
     */
    private List<Map<String, Object>> getMouvementsDetail(UUID articleId, UUID depotId,
                                                           LocalDate dateDebut, LocalDate dateFin) {
        List<Map<String, Object>> mouvementsDetail = new ArrayList<>();
        
        LocalDateTime debut = dateDebut != null ? dateDebut.atStartOfDay() : 
                LocalDateTime.now().minusYears(1);
        LocalDateTime fin = dateFin != null ? dateFin.atTime(23, 59, 59) : 
                LocalDateTime.now();
        
        List<StockMovement> mouvements = mouvementRepository
                .findByArticleIdAndDepotIdAndDateMouvementBetweenOrderByDateMouvementDesc(
                        articleId, depotId, debut, fin);
        
        for (StockMovement mvt : mouvements) {
            Map<String, Object> detail = new HashMap<>();
            detail.put("type", mvt.getType().getSens().toString());
            detail.put("quantite", mvt.getQuantite());
            detail.put("date", mvt.getDateMouvement());
            detail.put("coutUnitaire", mvt.getCoutUnitaire());
            detail.put("lot", mvt.getLot() != null ? mvt.getLot().getNumeroLot() : null);
            detail.put("reference", mvt.getReference());
            mouvementsDetail.add(detail);
        }
        
        return mouvementsDetail;
    }

    /**
     * Obtenir les suggestions d'articles pour l'autocomplete
     */
    public List<Map<String, Object>> getSuggestionsArticles(String query) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        List<Article> articles = articleRepository
                .findTop10ByCodeArticleContainingOrLibelleContainingAndActifTrue(
                        query);
        
        for (Article article : articles) {
            Map<String, Object> suggestion = new HashMap<>();
            suggestion.put("id", article.getId());
            suggestion.put("code", article.getCodeArticle());
            suggestion.put("libelle", article.getLibelle());
            suggestion.put("methode", article.getMethodeValorisation());
            suggestions.add(suggestion);
        }
        
        return suggestions;
    }

    /**
     * Obtenir les détails complets d'un article
     */
    public Map<String, Object> getDetailsArticle(UUID articleId, UUID depotId) {
        Map<String, Object> details = new HashMap<>();
        
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        
        details.put("article", article);
        
        // Récupérer les stocks
        List<Stock> stocks;
        if (depotId != null) {
            stocks = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                    .map(List::of)
                    .orElse(Collections.emptyList());
        } else {
            stocks = stockRepository.findByArticleId(articleId);
        }
        
        List<Map<String, Object>> stocksData = new ArrayList<>();
        for (Stock stock : stocks) {
            Map<String, Object> stockData = new HashMap<>();
            stockData.put("depot", stock.getDepot().getNom());
            stockData.put("quantiteTheorique", stock.getQuantiteTheorique());
            stockData.put("quantitePhysique", stock.getQuantitePhysique());
            stockData.put("quantiteDisponible", stock.getQuantiteDisponible());
            stockData.put("valeurStock", stock.getValeurStockCump());
            stockData.put("coutUnitaireMoyen", stock.getCoutUnitaireMoyen());
            stockData.put("dateDernierMouvement", stock.getDateDernierMouvement());
            stocksData.add(stockData);
        }
        details.put("stocks", stocksData);
        
        // Récupérer l'historique des coûts
        List<HistoriqueCout> historique = historiqueRepository
                .findTop12ByArticleIdOrderByDateEffetDesc(articleId);
        details.put("historique", historique);
        
        return details;
    }

    /**
     * Obtenir les statistiques globales pour les cartes
     */
    public Map<String, Object> getStatistiquesGlobales() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Stock> tousStocks = stockRepository.findAll();
        
        BigDecimal valeurTotale = BigDecimal.ZERO;
        int quantiteTotale = 0;
        Map<String, Integer> compteurMethodes = new HashMap<>();
        BigDecimal sommeCoutUnitaire = BigDecimal.ZERO;
        int countCout = 0;
        
        for (Stock stock : tousStocks) {
            if (stock.getValeurStockCump() != null) {
                valeurTotale = valeurTotale.add(stock.getValeurStockCump());
            }
            quantiteTotale += stock.getQuantiteTheorique();
            
            String methode = stock.getArticle().getMethodeValorisation();
            compteurMethodes.put(methode, compteurMethodes.getOrDefault(methode, 0) + 1);
            
            if (stock.getCoutUnitaireMoyen() != null) {
                sommeCoutUnitaire = sommeCoutUnitaire.add(stock.getCoutUnitaireMoyen());
                countCout++;
            }
        }
        
        stats.put("valeurTotale", valeurTotale);
        stats.put("quantiteTotale", quantiteTotale);
        stats.put("nombreArticles", tousStocks.size());
        
        BigDecimal coutMoyen = countCout > 0 ? 
                sommeCoutUnitaire.divide(BigDecimal.valueOf(countCout), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO;
        stats.put("coutMoyen", coutMoyen);
        
        // Méthode dominante
        String methodeDominante = compteurMethodes.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("CUMP");
        stats.put("methodeDominante", methodeDominante);
        
        // Calcul rotation stock (simplifié)
        BigDecimal rotation = quantiteTotale > 0 ? 
                valeurTotale.divide(BigDecimal.valueOf(quantiteTotale), 2, RoundingMode.HALF_UP) : 
                BigDecimal.ZERO;
        stats.put("rotationStock", rotation);
        
        // Variation par rapport au mois dernier
        LocalDate moisDernier = LocalDate.now().minusMonths(1);
        BigDecimal valeurMoisDernier = getValeurStockAuDate(moisDernier);
        if (valeurMoisDernier.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal variation = valeurTotale.subtract(valeurMoisDernier)
                    .divide(valeurMoisDernier, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            stats.put("variation", variation);
        } else {
            stats.put("variation", BigDecimal.ZERO);
        }
        
        return stats;
    }

    /**
     * Obtenir la valeur du stock à une date donnée (via historique)
     */
    private BigDecimal getValeurStockAuDate(LocalDate date) {
        List<HistoriqueCout> historiques = historiqueRepository.findByDateEffet(date);
        return historiques.stream()
                .map(HistoriqueCout::getValeurStock)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Générer un CSV à partir des données
     */
    public String genererCSV(List<Map<String, Object>> donnees) {
        StringBuilder csv = new StringBuilder();
        
        // En-tête
        csv.append("Code Article;Libellé;Méthode;Prix Unitaire;Qté Entrée;Date Entrée;Qté Sortie;Qté Restante;Valorisation;Dernier Mouvement\n");
        
        // Données
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        
        for (Map<String, Object> ligne : donnees) {
            csv.append(ligne.get("codeArticle")).append(";")
               .append(ligne.get("libelle")).append(";")
               .append(ligne.get("methode")).append(";")
               .append(ligne.get("prixUnitaire")).append(";")
               .append(ligne.get("quantiteEntree")).append(";")
               .append(ligne.get("dateEntree") != null ? 
                       ((LocalDateTime) ligne.get("dateEntree")).format(dateFormatter) : "").append(";")
               .append(ligne.get("quantiteSortie")).append(";")
               .append(ligne.get("quantiteRestante")).append(";")
               .append(ligne.get("valorisation")).append(";")
               .append(ligne.get("dateDernierMouvement") != null ? 
                       ((LocalDateTime) ligne.get("dateDernierMouvement")).format(dateFormatter) : "")
               .append("\n");
        }
        
        return csv.toString();
    }
}