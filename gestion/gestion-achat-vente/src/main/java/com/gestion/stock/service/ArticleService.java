// ArticleService.java
package com.gestion.stock.service;

import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.CategorieArticle;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.Stock;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.entity.UniteMesure;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ArticleService {

    private final ArticleRepository articleRepository;
    private final CategorieArticleRepository categorieRepository;
    private final UniteMesureRepository uniteRepository;
    private final StockRepository stockRepository;
    private final LotRepository lotRepository;
    private final StockMovementRepository mouvementRepository;
    private final StockMovementRepository stockMovementRepository;

    public Page<Article> rechercherArticles(String recherche, String categorieId,
            Boolean actif, Boolean gestionParLot,
            Pageable pageable) {

        // Convertir categorieId de String à UUID si nécessaire
        UUID categorieUUID = null;
        if (categorieId != null && !categorieId.isEmpty()) {
            try {
                categorieUUID = UUID.fromString(categorieId);
            } catch (IllegalArgumentException e) {
                log.warn("Format UUID invalide pour categorieId: {}", categorieId);
                categorieUUID = null;
            }
        }

        // Préparer le paramètre de recherche (évite CONCAT côté SQL)
        String searchParam = null;
        if (recherche != null && !recherche.isBlank()) {
            searchParam = "%" + recherche.toLowerCase() + "%";
        }

        return articleRepository.rechercherAvecFiltres(
                searchParam, categorieUUID, actif, gestionParLot, pageable);
    }

    public Article getArticleById(UUID id) {
        return articleRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));
    }

    public Map<String, Object> getDetailsArticle(UUID articleId) {
        Article article = getArticleById(articleId);
        Map<String, Object> details = new HashMap<>();

        // Informations de base
        details.put("article", article);

        // Statistiques stock
        List<Object[]> statsStock = stockRepository.getStatistiquesStockArticleAsArray(articleId);
        if (!statsStock.isEmpty()) {
            Object[] stats = statsStock.get(0);
            details.put("stockTotal", stats[0]);
            details.put("stockDisponible", stats[1]);
            details.put("valeurStock", stats[2]);
        }

        // Statistiques mouvements (30 derniers jours)
        Long entree30j = mouvementRepository.countEntrees30Jours(articleId);
        Long sortie30j = mouvementRepository.countSorties30Jours(articleId);
        details.put("entrees30j", entree30j);
        details.put("sorties30j", sortie30j);

        // Rotation stock
        BigDecimal rotation = calculerRotationArticle(articleId);
        details.put("rotation", rotation);

        // Dernier mouvement
        LocalDateTime dernierMouvement = mouvementRepository.getDernierMouvementDate(articleId);
        details.put("dernierMouvement", dernierMouvement);

        return details;
    }

    @Transactional
    public Article creerArticle(Map<String, String> params, UUID utilisateurId) {
        log.info("Création article par utilisateur: {}", utilisateurId);

        // Validation des données
        String codeArticle = params.get("codeArticle");
        if (articleRepository.existsByCodeArticle(codeArticle)) {
            throw new RuntimeException("Code article déjà existant");
        }

        Article article = new Article();
        return enregistrerArticle(article, params, utilisateurId, true);
    }

    @Transactional
    public Article modifierArticle(UUID articleId, Map<String, String> params, UUID utilisateurId) {
        log.info("Modification article {} par utilisateur: {}", articleId, utilisateurId);

        Article article = getArticleById(articleId);
        return enregistrerArticle(article, params, utilisateurId, false);
    }

    private Article enregistrerArticle(Article article, Map<String, String> params,
            UUID utilisateurId, boolean isCreation) {

        // Informations de base
        article.setCodeArticle(params.get("codeArticle"));
        article.setCodeBarre(params.get("codeBarre"));
        article.setLibelle(params.get("libelle"));
        article.setDescription(params.get("description"));

        // Catégorie
        String categorieId = params.get("categorieId");
        if (categorieId != null && !categorieId.isEmpty()) {
            article.setCategorie(categorieRepository.findById(UUID.fromString(categorieId))
                    .orElseThrow(() -> new RuntimeException("Catégorie non trouvée")));
        }

        // Unité de mesure
        String uniteId = params.get("uniteMesureId");
        if (uniteId != null && !uniteId.isEmpty()) {
            article.setUniteMesure(uniteRepository.findById(UUID.fromString(uniteId))
                    .orElseThrow(() -> new RuntimeException("Unité de mesure non trouvée")));
        }

        // Gestion des lots
        article.setGestionParLot("true".equals(params.get("gestionParLot")));
        article.setGestionParSerie("true".equals(params.get("gestionParSerie")));

        // Durée de vie
        String dureeVie = params.get("dureeVieJours");
        if (dureeVie != null && !dureeVie.isEmpty()) {
            article.setDureeVieJours(Integer.parseInt(dureeVie));
        }

        // Caractéristiques physiques
        String poids = params.get("poidsKg");
        if (poids != null && !poids.isEmpty()) {
            article.setPoidsKg(new BigDecimal(poids));
        }

        String volume = params.get("volumeM3");
        if (volume != null && !volume.isEmpty()) {
            article.setVolumeM3(new BigDecimal(volume));
        }

        // Seuils de stock
        String stockMin = params.get("stockMinimum");
        if (stockMin != null && !stockMin.isEmpty()) {
            article.setStockMinimum(Integer.parseInt(stockMin));
        }

        String stockSec = params.get("stockSecurite");
        if (stockSec != null && !stockSec.isEmpty()) {
            article.setStockSecurite(Integer.parseInt(stockSec));
        }

        String stockMax = params.get("stockMaximum");
        if (stockMax != null && !stockMax.isEmpty()) {
            article.setStockMaximum(Integer.parseInt(stockMax));
        }

        // Valorisation
        article.setMethodeValorisation(params.get("methodeValorisation"));

        String coutStandard = params.get("coutStandard");
        if (coutStandard != null && !coutStandard.isEmpty()) {
            article.setCoutStandard(new BigDecimal(coutStandard));
        }

        String prixVente = params.get("prixVenteHt");
        if (prixVente != null && !prixVente.isEmpty()) {
            article.setPrixVenteHt(new BigDecimal(prixVente));
        }

        String tva = params.get("tvaPourcentage");
        if (tva != null && !tva.isEmpty()) {
            article.setTvaPourcentage(new BigDecimal(tva));
        }

        // Statut
        article.setActif("true".equals(params.get("actif")));

        // Audit
        if (isCreation) {
            article.setCreatedBy(utilisateurId);
            article.setCreatedAt(LocalDateTime.now());
        }
        article.setUpdatedBy(utilisateurId);
        article.setUpdatedAt(LocalDateTime.now());

        Article articleSauvegarde = articleRepository.save(article);
        log.info("Article {}: {}", isCreation ? "créé" : "modifié", articleSauvegarde.getCodeArticle());

        return articleSauvegarde;
    }

    @Transactional
    public void toggleActifArticle(UUID articleId) {
        Article article = getArticleById(articleId);
        article.setActif(!article.isActif());
        articleRepository.save(article);

        log.info("Article {} {}: {}", article.getCodeArticle(),
                article.isActif() ? "activé" : "désactivé", articleId);
    }

    public List<Lot> getLotsArticle(UUID articleId) {
        return lotRepository.findLotsActifsByArticle(articleId);
    }

    public List<StockMovement> getHistoriqueArticle(UUID articleId, int jours) {
        return mouvementRepository.getHistoriqueArticle(articleId, jours);
    }

    public List<CategorieArticle> getCategoriesActives() {
        return categorieRepository.findActiveCategories();
    }

    public List<UniteMesure> getUnitesMesure() {
        return uniteRepository.findAllUnites();
    }

    public List<Map<String, Object>> getFournisseurs() {
        // À implémenter avec le repository fournisseurs
        return new ArrayList<>();
    }

    public Map<String, Object> getStatistiquesArticles() {
        Map<String, Object> stats = new HashMap<>();

        long totalArticles = articleRepository.count();
        long articlesActifs = articleRepository.countByActifTrue();
        long articlesAvecLot = articleRepository.countByGestionParLotTrue();
        long articlesAvecSerie = articleRepository.countByGestionParSerieTrue();

        stats.put("totalArticles", totalArticles);
        stats.put("articlesActifs", articlesActifs);
        stats.put("articlesAvecLot", articlesAvecLot);
        stats.put("articlesAvecSerie", articlesAvecSerie);
        stats.put("tauxActivation", totalArticles > 0 ? (double) articlesActifs / totalArticles * 100 : 0);

        return stats;
    }

    private BigDecimal calculerRotationArticle(UUID articleId) {
        // Calcul simplifié de la rotation
        Long sortiesAnnee = mouvementRepository.countSortiesAnnee(articleId);
        BigDecimal stockMoyen = stockRepository.getStockMoyenArticle(articleId);

        if (stockMoyen != null && stockMoyen.compareTo(BigDecimal.ZERO) > 0) {
            return BigDecimal.valueOf(sortiesAnnee)
                    .divide(stockMoyen, 2, java.math.RoundingMode.HALF_UP);
        }

        return BigDecimal.ZERO;
    }

    public String exporterArticles(String format) {
        // À implémenter avec Apache POI ou autre librairie
        log.info("Export articles format: {}", format);
        return "export_" + LocalDateTime.now().toString() +
                ("excel".equalsIgnoreCase(format) ? ".xlsx" : ".csv");
    }

    public List<Article> getArticlesActifs() {
        return articleRepository.findByActifTrue();
    }

    public Map<String, Object> getStockInfoForArticle(UUID articleId) {
        Map<String, Object> result = new HashMap<>();

        try {
            // 1. Récupérer le stock total
            List<Stock> stocks = stockRepository.findByArticleId(articleId);

            Integer quantiteTheorique = 0;
            Integer quantiteReservee = 0;

            if (stocks != null && !stocks.isEmpty()) {
                quantiteTheorique = stocks.stream()
                        .mapToInt(Stock::getQuantiteTheorique)
                        .sum();

                quantiteReservee = stocks.stream()
                        .mapToInt(Stock::getQuantiteReservee)
                        .sum();
            }

            Integer quantiteDisponible = quantiteTheorique - quantiteReservee;

            // 2. Récupérer la date du dernier mouvement
            LocalDateTime dateDernierMouvement = null;

            // Utiliser la nouvelle méthode
            Optional<StockMovement> dernierMouvement = stockMovementRepository
                    .findTopByArticleIdOrderByDateMouvementDesc(articleId);

            if (dernierMouvement.isPresent()) {
                dateDernierMouvement = dernierMouvement.get().getDateMouvement();
            }

            result.put("quantiteTheorique", quantiteTheorique);
            result.put("quantiteDisponible", quantiteDisponible);
            result.put("dateDernierMouvement", dateDernierMouvement);
            result.put("hasStock", quantiteTheorique > 0);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des infos stock pour l'article {}", articleId, e);
            result.put("quantiteTheorique", 0);
            result.put("quantiteDisponible", 0);
            result.put("dateDernierMouvement", null);
            result.put("hasStock", false);
        }

        return result;
    }

    /**
     * Récupère les informations de stock pour plusieurs articles (optimisé)
     */
    public Map<UUID, Map<String, Object>> getStockInfoForArticles(List<UUID> articleIds) {
        Map<UUID, Map<String, Object>> result = new HashMap<>();

        // Récupérer tous les stocks pour ces articles
        List<Stock> allStocks = stockRepository.findByArticleIdIn(articleIds);

        // Grouper par article
        Map<UUID, List<Stock>> stocksByArticle = allStocks.stream()
                .collect(Collectors.groupingBy(stock -> stock.getArticle().getId()));

        // Pour chaque article, calculer les totaux
        for (UUID articleId : articleIds) {
            Map<String, Object> info = new HashMap<>();
            List<Stock> stocks = stocksByArticle.get(articleId);

            if (stocks != null && !stocks.isEmpty()) {
                Integer quantiteTheorique = stocks.stream()
                        .mapToInt(Stock::getQuantiteTheorique)
                        .sum();

                Integer quantiteReservee = stocks.stream()
                        .mapToInt(Stock::getQuantiteReservee)
                        .sum();

                info.put("quantiteTheorique", quantiteTheorique);
                info.put("quantiteDisponible", quantiteTheorique - quantiteReservee);
            } else {
                info.put("quantiteTheorique", 0);
                info.put("quantiteDisponible", 0);
            }

            result.put(articleId, info);
        }

        return result;
    }

    // Ajoutez cette méthode dans ArticleService.java
    public List<Article> searchArticles(String searchTerm, int limit) {
        // Utiliser la méthode du repository si elle existe
        if (searchTerm != null && !searchTerm.trim().isEmpty()) {
            String search = "%" + searchTerm.toLowerCase() + "%";
            List<Article> results = articleRepository.searchActifs(search);

            // Limiter les résultats
            if (results.size() > limit) {
                return results.subList(0, limit);
            }
            return results;
        }

        // Si pas de terme de recherche, retourner les articles actifs limités
        return articleRepository.findByActifTrue().stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
    /**
     * Récupère l'historique des mouvements d'un article
     */
    // public List<StockMovement> getHistoriqueArticle(UUID articleId, int
    // limitDays) {
    // LocalDateTime since = LocalDateTime.now().minusDays(limitDays);
    // return stockMovementRepository.findRecentMovementsByArticleId(articleId,
    // since);
    // }
}