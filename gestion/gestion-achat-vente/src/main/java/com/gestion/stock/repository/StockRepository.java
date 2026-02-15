package com.gestion.stock.repository;

import com.gestion.stock.entity.Stock;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StockRepository extends JpaRepository<Stock, UUID> {

        Optional<Stock> findByArticleIdAndDepotId(UUID articleId, UUID depotId);

        List<Stock> findByArticleId(UUID articleId);

        List<Stock> findByDepotId(UUID depotId);

        @Query("SELECT s FROM Stock s WHERE s.quantiteTheorique - s.quantiteReservee = 0")
        List<Stock> findArticlesEnRupture();

        @Query("SELECT s FROM Stock s WHERE (s.quantiteTheorique - s.quantiteReservee) < s.article.stockMinimum")
        List<Stock> findArticlesSousStockMinimum();

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.quantiteTheorique = s.quantiteTheorique + :quantite, " +
                        "s.dateDernierMouvement = CURRENT_TIMESTAMP, s.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void incrementerQuantiteTheorique(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("quantite") Integer quantite);

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.quantiteTheorique = s.quantiteTheorique - :quantite, " +
                        "s.dateDernierMouvement = CURRENT_TIMESTAMP, s.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void decrementerQuantiteTheorique(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("quantite") Integer quantite);

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.quantiteReservee = s.quantiteReservee + :quantite, " +
                        "s.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void incrementerQuantiteReservee(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("quantite") Integer quantite);

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.quantiteReservee = s.quantiteReservee - :quantite, " +
                        "s.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void decrementerQuantiteReservee(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("quantite") Integer quantite);

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.valeurStockCump = :nouvelleValeur, " +
                        "s.updatedAt = CURRENT_TIMESTAMP " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void mettreAJourValeurStock(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("nouvelleValeur") BigDecimal nouvelleValeur);

        // NEW: Method to find stocks by article method
        @Query("SELECT s FROM Stock s WHERE s.article.methodeValorisation = :methode")
        List<Stock> findByArticleMethodeValorisation(@Param("methode") String methode);

        // NEW: Method to find all stocks ordered by value descending
        @Query("SELECT s FROM Stock s WHERE s.valeurStockCump IS NOT NULL ORDER BY s.valeurStockCump DESC")
        List<Stock> findAllByOrderByValeurStockCumpDesc();

        @Query("SELECT " +
                        "COALESCE(SUM(s.quantiteTheorique), 0) as totalStock, " +
                        "COALESCE(SUM(s.quantiteReservee), 0) as totalReserve, " +
                        "COALESCE(SUM(s.quantiteTheorique - s.quantiteReservee), 0) as totalDisponible, " +
                        "COUNT(DISTINCT s.depot.id) as nbDepots, " +
                        "COALESCE(AVG(s.quantiteTheorique), 0) as stockMoyen " +
                        "FROM Stock s WHERE s.article.id = :articleId")
        Map<String, Object> getStatistiquesStockArticle(@Param("articleId") UUID articleId);

        @Query("SELECT " +
                        "COALESCE(SUM(s.quantiteTheorique), 0), " + // [0] totalStock
                        "COALESCE(SUM(s.quantiteTheorique - s.quantiteReservee), 0), " + // [1] totalDisponible
                        "COALESCE(SUM(s.valeurStockCump), 0), " + // [2] valeurStock
                        "COALESCE(SUM(s.quantiteReservee), 0), " + // [3] totalReserve
                        "COUNT(DISTINCT s.depot.id) " + // [4] nbDepots
                        "FROM Stock s WHERE s.article.id = :articleId")
        List<Object[]> getStatistiquesStockArticleAsArray(@Param("articleId") UUID articleId);

        // NEW: Get average stock for an article
        @Query("SELECT COALESCE(AVG(s.quantiteTheorique), 0) FROM Stock s WHERE s.article.id = :articleId")
        BigDecimal getStockMoyenArticle(@Param("articleId") UUID articleId);

        @Query("SELECT s FROM Stock s WHERE s.article.id IN :articleIds")
        List<Stock> findByArticleIdIn(@Param("articleIds") List<UUID> articleIds);

        @Query("SELECT s FROM Stock s WHERE s.depot.id = :depotId AND (s.quantiteTheorique - s.quantiteReservee) > :quantite")
        List<Stock> findByDepotIdAndQuantiteDisponibleGreaterThan(
                        @Param("depotId") UUID depotId,
                        @Param("quantite") int quantite);

        @Query("SELECT s FROM Stock s WHERE s.quantiteTheorique < :seuil")
        List<Stock> findByQuantiteTheoriqueLessThan(@Param("seuil") Integer seuil);

        @Query("SELECT s FROM Stock s WHERE (s.quantiteTheorique - s.quantiteReservee) > :seuil")
        List<Stock> findByQuantiteDisponibleGreaterThan(@Param("seuil") Integer seuil);

        @Query("SELECT s FROM Stock s WHERE s.depot.id = :depotId AND s.article.categorie.id = :categorieId")
        List<Stock> findByDepotIdAndCategorieId(@Param("depotId") UUID depotId,
                        @Param("categorieId") UUID categorieId);

        @Query("SELECT s FROM Stock s WHERE s.depot.id = :depotId AND s.quantiteTheorique > 0")
        List<Stock> findWithStockByDepotId(@Param("depotId") UUID depotId);

        @Modifying
        @Transactional
        @Query("UPDATE Stock s SET s.valeurStockCump = :valeur " +
                        "WHERE s.article.id = :articleId AND s.depot.id = :depotId")
        void updateValeurStock(@Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("valeur") BigDecimal valeur);

        @Query("SELECT s FROM Stock s WHERE s.article.stockMinimum IS NOT NULL " +
                        "AND s.quantiteTheorique < s.article.stockMinimum")
        List<Stock> findStocksCritiques();

        @Query("SELECT s FROM Stock s WHERE s.dateDernierMouvement < :dateLimite " +
                        "AND s.quantiteTheorique > 0")
        List<Stock> findStocksObsoletes(@Param("dateLimite") LocalDateTime dateLimite);

        Page<Stock> findByDepotId(UUID depotId, Pageable pageable);

        // Méthode pour compter les stocks par dépôt
        Long countByDepotId(UUID depotId);

        @Modifying
        @Transactional
        @Query(value = """
                        INSERT INTO stocks (
                                id, article_id, depot_id,
                                quantite_theorique, quantite_physique, quantite_reservee,
                                valeur_stock_cump, date_dernier_mouvement, updated_at
                        )
                        VALUES (
                                gen_random_uuid(), :articleId, :depotId,
                                :quantite, :quantite, 0,
                                :valeurInitiale, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                        )
                        ON CONFLICT (article_id, depot_id)
                        DO UPDATE SET
                                quantite_theorique = stocks.quantite_theorique + EXCLUDED.quantite_theorique,
                                quantite_physique = stocks.quantite_physique + EXCLUDED.quantite_physique,
                                date_dernier_mouvement = EXCLUDED.date_dernier_mouvement,
                                updated_at = EXCLUDED.updated_at
                        """, nativeQuery = true)
        void upsertStockReception(
                        @Param("articleId") UUID articleId,
                        @Param("depotId") UUID depotId,
                        @Param("quantite") Integer quantite,
                        @Param("valeurInitiale") BigDecimal valeurInitiale);

        // Récupérer les articles avec stock bas
        // @Query("SELECT s FROM Stock s WHERE s.quantiteDisponible < s.article.stockMinimum AND s.article.actif = true")
        // List<Stock> findArticlesStockBas();

        // // Récupérer les articles en surstock
        // @Query("SELECT s FROM Stock s WHERE s.quantiteDisponible > s.article.stockMaximum AND s.article.stockMaximum > 0")
        // List<Stock> findArticlesSurStock();

        // Récupérer la valeur totale du stock
        // @Query("SELECT SUM(s.valeurStockCump) FROM Stock s")
        // BigDecimal getValeurTotaleStock();

        // // Récupérer la valeur du stock par dépôt
        // @Query("SELECT s.depot.nom, SUM(s.valeurStockCump) FROM Stock s GROUP BY s.depot.nom")
        // List<Object[]> getValeurStockParDepot();

        // // Compter les articles avec stock non nul
        // long countByQuantiteTheoriqueGreaterThan(int quantite);

        // Récupérer le stock par article avec informations détaillées
        // @Query("SELECT new map(" +
        //                 "s.article.id as articleId, " +
        //                 "s.depot.id as depotId, " +
        //                 "s.depot.nom as depotNom, " +
        //                 "s.quantiteTheorique as quantiteTheorique, " +
        //                 "s.quantitePhysique as quantitePhysique, " +
        //                 "s.quantiteReservee as quantiteReservee, " +
        //                 "s.quantiteDisponible as quantiteDisponible, " +
        //                 "s.coutUnitaireMoyen as coutUnitaireMoyen, " +
        //                 "s.valeurStockCump as valeurStockCump, " +
        //                 "s.dateDernierMouvement as dateDernierMouvement) " +
        //                 "FROM Stock s WHERE s.article.id = :articleId")
        // List<Object[]> getStockParDepotDetail(@Param("articleId") UUID articleId);
}