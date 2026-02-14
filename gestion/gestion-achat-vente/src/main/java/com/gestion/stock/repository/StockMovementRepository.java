package com.gestion.stock.repository;

import com.gestion.stock.entity.MovementType;
import com.gestion.stock.entity.StockMovement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface StockMovementRepository
              extends JpaRepository<StockMovement, UUID>, JpaSpecificationExecutor<StockMovement> {

       StockMovement findByReference(String reference);

       List<StockMovement> findByArticleIdAndDepotIdOrderByDateMouvementDesc(UUID articleId, UUID depotId);

       List<StockMovement> findByBonReceptionId(UUID bonReceptionId);

       List<StockMovement> findByBonCommandeId(UUID bonCommandeId);

       List<StockMovement> findByTransfertId(UUID transfertId);

       List<StockMovement> findByDateComptableBetween(LocalDate start, LocalDate end);

       @Query("SELECT m FROM StockMovement m WHERE m.dateMouvement >= :debut AND m.dateMouvement <= :fin")
       List<StockMovement> findByDateMouvementBetween(@Param("debut") LocalDate debut,
                     @Param("fin") LocalDate fin);

       @Query("SELECT COALESCE(SUM(m.quantite), 0) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId AND m.depot.id = :depotId " +
                     "AND m.type.sens = 'ENTREE' AND m.statut = 'VALIDE'")
       Long sumEntreesByArticleAndDepot(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT COALESCE(SUM(m.quantite), 0) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId AND m.depot.id = :depotId " +
                     "AND m.type.sens = 'SORTIE' AND m.statut = 'VALIDE'")
       Long sumSortiesByArticleAndDepot(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT COALESCE(SUM(m.quantite * m.coutUnitaire), 0) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId AND m.depot.id = :depotId " +
                     "AND m.type.sens = 'ENTREE' AND m.statut = 'VALIDE'")
       BigDecimal sumValeurEntreesByArticleAndDepot(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT COUNT(m) FROM StockMovement m WHERE m.dateMouvement >= CURRENT_DATE")
       long countMouvementsAujourdhui();

       // NEW: Method to get recent movements
       List<StockMovement> findTop10ByOrderByDateMouvementDesc();

       // NEW: Get movements by date range
       List<StockMovement> findByDateMouvementBetween(LocalDateTime start, LocalDateTime end);

       // NEW: Get movements by article
       List<StockMovement> findByArticleIdOrderByDateMouvementDesc(UUID articleId);

       @Query("SELECT COUNT(m) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId " +
                     "AND m.type.sens = 'ENTREE' " +
                     "AND m.statut = 'VALIDE' " +
                     "AND m.dateMouvement >= :dateLimite")
       long countEntrees30Jours(@Param("articleId") UUID articleId,
                     @Param("dateLimite") LocalDateTime dateLimite);

       // Helper method for above
       default long countEntrees30Jours(UUID articleId) {
              return countEntrees30Jours(articleId, LocalDateTime.now().minusDays(30));
       }

       // NEW: Count exits in last 30 days for an article
       @Query("SELECT COUNT(m) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId " +
                     "AND m.type.sens = 'SORTIE' " +
                     "AND m.statut = 'VALIDE' " +
                     "AND m.dateMouvement >= :dateLimite")
       long countSorties30Jours(@Param("articleId") UUID articleId,
                     @Param("dateLimite") LocalDateTime dateLimite);

       // Helper method for above
       default long countSorties30Jours(UUID articleId) {
              return countSorties30Jours(articleId, LocalDateTime.now().minusDays(30));
       }

       // NEW: Count exits in current year for an article
       @Query("SELECT COUNT(m) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId " +
                     "AND m.type.sens = 'SORTIE' " +
                     "AND m.statut = 'VALIDE' " +
                     "AND EXTRACT(YEAR FROM m.dateMouvement) = EXTRACT(YEAR FROM CURRENT_DATE)")
       long countSortiesAnnee(@Param("articleId") UUID articleId);

       // NEW: Get last movement date for an article
       @Query("SELECT MAX(m.dateMouvement) FROM StockMovement m " +
                     "WHERE m.article.id = :articleId AND m.statut = 'VALIDE'")
       LocalDateTime getDernierMouvementDate(@Param("articleId") UUID articleId);

       // NEW: Get article history
       @Query("SELECT m FROM StockMovement m " +
                     "WHERE m.article.id = :articleId " +
                     "AND m.statut = 'VALIDE' " +
                     "ORDER BY m.dateMouvement DESC")
       List<StockMovement> getHistoriqueArticle(@Param("articleId") UUID articleId,
                     Pageable pageable);

       // Helper method with default page size
       default List<StockMovement> getHistoriqueArticle(UUID articleId, int limit) {
              return getHistoriqueArticle(articleId, PageRequest.of(0, limit));
       }

       List<StockMovement> findByLotId(UUID lotId);

       // NEW: Find movements by lot ID with details
       @Query("SELECT m FROM StockMovement m " +
                     "JOIN FETCH m.article " +
                     "JOIN FETCH m.depot " +
                     "JOIN FETCH m.type " +
                     "WHERE m.lot.id = :lotId " +
                     "ORDER BY m.dateMouvement DESC")
       List<StockMovement> findMouvementsByLotIdAvecDetails(@Param("lotId") UUID lotId);

       @Query("SELECT m FROM StockMovement m " +
                     "LEFT JOIN FETCH m.article " +
                     "LEFT JOIN FETCH m.depot " +
                     "LEFT JOIN FETCH m.type " +
                     "WHERE m.lot.id = :lotId " +
                     "ORDER BY m.dateMouvement DESC")
       List<StockMovement> findMouvementsByLotId(@Param("lotId") UUID lotId);

       // Trouver par type de mouvement
       List<StockMovement> findByTypeId(UUID typeId);

       // Trouver par article
       List<StockMovement> findByArticleId(UUID articleId);

       // Trouver par dépôt
       List<StockMovement> findByDepotId(UUID depotId);

       // Trouver par statut
       List<StockMovement> findByStatut(StockMovement.MovementStatus statut);

       // Derniers mouvements pour un article
       @Query("SELECT m FROM StockMovement m WHERE m.article.id = :articleId " +
                     "ORDER BY m.dateMouvement DESC")
       List<StockMovement> findDerniersMouvementsArticle(
                     @Param("articleId") UUID articleId, Pageable pageable);

       // Statistiques de mouvements par jour
       @Query("SELECT DATE(m.dateMouvement) as jour, " +
                     "COUNT(m) as nombreMouvements, " +
                     "SUM(m.quantite) as totalQuantite, " +
                     "SUM(m.coutUnitaire * m.quantite) as totalValeur " +
                     "FROM StockMovement m " +
                     "WHERE m.dateMouvement >= :debut AND m.dateMouvement <= :fin " +
                     "GROUP BY DATE(m.dateMouvement) " +
                     "ORDER BY jour")
       List<Object[]> getStatistiquesParJour(@Param("debut") java.time.LocalDateTime debut,
                     @Param("fin") java.time.LocalDateTime fin);

       // Total des entrées et sorties pour une période
       @Query("SELECT t.sens, " +
                     "COUNT(m) as count, " +
                     "SUM(m.quantite) as totalQuantite, " +
                     "SUM(m.coutUnitaire * m.quantite) as totalValeur " +
                     "FROM StockMovement m " +
                     "JOIN m.type t " +
                     "WHERE m.dateComptable BETWEEN :debut AND :fin " +
                     "GROUP BY t.sens")
       List<Object[]> getTotauxParSens(@Param("debut") LocalDate debut,
                     @Param("fin") LocalDate fin);

       Long countByDateComptable(LocalDate dateComptable);

       // Si vous avez besoin de compter par période
       Long countByDateComptableBetween(LocalDate dateDebut, LocalDate dateFin);

       // Autres méthodes utiles
       List<StockMovement> findByUtilisateurId(UUID utilisateurId);

       List<StockMovement> findByStatutAndDateComptableBetween(
                     StockMovement.MovementStatus statut, LocalDate dateDebut, LocalDate dateFin);

       // Pour les statistiques par type
       @Query("SELECT m.type, COUNT(m) FROM StockMovement m " +
                     "WHERE m.dateComptable BETWEEN :debut AND :fin " +
                     "GROUP BY m.type")
       List<Object[]> countByTypeBetweenDates(@Param("debut") LocalDate debut,
                     @Param("fin") LocalDate fin);

       Optional<StockMovement> findTopByArticleIdOrderByDateMouvementDesc(UUID articleId);

       // Méthode 2: Pour l'historique (avec pagination)
       List<StockMovement> findByArticleIdOrderByDateMouvementDesc(UUID articleId,
                     org.springframework.data.domain.Pageable pageable);

       // Méthode 3: Récupérer les mouvements récents
       @Query("SELECT sm FROM StockMovement sm WHERE sm.article.id = :articleId AND sm.dateMouvement > :since ORDER BY sm.dateMouvement DESC")
       List<StockMovement> findRecentMovementsByArticleId(@Param("articleId") UUID articleId,
                     @Param("since") LocalDateTime since);

       // Méthode 4: Compteur de mouvements
       @Query("SELECT COUNT(sm) FROM StockMovement sm WHERE sm.article.id = :articleId")
       Long countByArticleId(@Param("articleId") UUID articleId);

       // StockMovementRepository.java
       // Recherche avancée avec pagination
       @Query("SELECT m FROM StockMovement m WHERE " +
                     "(:typeId IS NULL OR m.type.id = :typeId) AND " +
                     "(:articleId IS NULL OR m.article.id = :articleId) AND " +
                     "(:depotId IS NULL OR m.depot.id = :depotId) AND " +
                     "(:dateDebut IS NULL OR m.dateMouvement >= :dateDebut) AND " +
                     "(:dateFin IS NULL OR m.dateMouvement <= :dateFin) AND " +
                     "(:statut IS NULL OR m.statut = :statut)")
       Page<StockMovement> rechercherMouvements(
                     @Param("typeId") UUID typeId,
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("dateDebut") LocalDateTime dateDebut,
                     @Param("dateFin") LocalDateTime dateFin,
                     @Param("statut") StockMovement.MovementStatus statut,
                     Pageable pageable);

       // AJOUTEZ CETTE NOUVELLE MÉTHODE AVEC STRING POUR LE STATUT :
       @Query("SELECT m FROM StockMovement m WHERE " +
                     "(:typeId IS NULL OR m.type.id = :typeId) AND " +
                     "(:articleId IS NULL OR m.article.id = :articleId) AND " +
                     "(:depotId IS NULL OR m.depot.id = :depotId) AND " +
                     "(:dateDebut IS NULL OR m.dateMouvement >= :dateDebut) AND " +
                     "(:dateFin IS NULL OR m.dateMouvement <= :dateFin) AND " +
                     "(:statut IS NULL OR m.statut = CAST(:statut AS string))")
       Page<StockMovement> rechercherMouvementsWithStringStatut(
                     @Param("typeId") UUID typeId,
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("dateDebut") LocalDateTime dateDebut,
                     @Param("dateFin") LocalDateTime dateFin,
                     @Param("statut") String statut,
                     Pageable pageable);

       @Query("SELECT sm FROM StockMovement sm WHERE sm.transfert.id = :transfertId " +
                     "AND sm.article.id = :articleId AND sm.depot.id = :depotId")
       Optional<StockMovement> findByTransfertIdAndArticleIdAndDepotId(
                     @Param("transfertId") UUID transfertId,
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query(value = "SELECT nextval('seq_mouvement_stock')", nativeQuery = true)
       Long getNextSequenceValue();

       @Query("SELECT m FROM StockMovement m WHERE m.type = :type " +
                     "AND m.dateComptable BETWEEN :dateDebut AND :dateFin " +
                     "AND m.statut = 'VALIDE'")
       List<StockMovement> findByTypeAndDateComptableBetween(
                     @Param("type") MovementType type,
                     @Param("dateDebut") LocalDate dateDebut,
                     @Param("dateFin") LocalDate dateFin);

       // Method 2: Find movements by type, depot and date range
       @Query("SELECT m FROM StockMovement m WHERE m.type = :type " +
                     "AND m.depot.id = :depotId " +
                     "AND m.dateComptable BETWEEN :dateDebut AND :dateFin " +
                     "AND m.statut = 'VALIDE'")
       List<StockMovement> findByTypeAndDepotAndDateComptableBetween(
                     @Param("type") MovementType type,
                     @Param("depotId") UUID depotId,
                     @Param("dateDebut") LocalDate dateDebut,
                     @Param("dateFin") LocalDate dateFin);

       @Query("SELECT m FROM StockMovement m " +
                     "JOIN m.article a " +
                     "WHERE m.type.sens = 'SORTIE' " +
                     "AND m.statut = 'VALIDE' " +
                     "AND a.methodeValorisation = :methode " +
                     "AND m.dateMouvement >= :dateDebut " +
                     "AND m.dateMouvement <= :dateFin " +
                     "ORDER BY m.dateMouvement DESC")
       List<StockMovement> findSortiesByMethodeValorisation(
                     @Param("methode") String methode,
                     @Param("dateDebut") LocalDateTime dateDebut,
                     @Param("dateFin") LocalDateTime dateFin);

    @Query("SELECT m FROM StockMovement m WHERE m.article.id = :articleId AND " +
           "m.depot.id = :depotId AND m.dateMouvement BETWEEN :debut AND :fin " +
           "ORDER BY m.dateMouvement DESC")
    List<StockMovement> findByArticleIdAndDepotIdAndDateMouvementBetween(
            @Param("articleId") UUID articleId,
            @Param("depotId") UUID depotId,
            @Param("debut") LocalDateTime debut,
            @Param("fin") LocalDateTime fin);
    
            @Query("SELECT m FROM StockMovement m WHERE m.article.id = :articleId AND " +
            "m.depot.id = :depotId AND m.dateMouvement BETWEEN :debut AND :fin " +
            "ORDER BY m.dateMouvement DESC")
     List<StockMovement> findByArticleIdAndDepotIdAndDateMouvementBetweenOrderByDateMouvementDesc(
             @Param("articleId") UUID articleId,
             @Param("depotId") UUID depotId,
             @Param("debut") LocalDateTime debut,
             @Param("fin") LocalDateTime fin);
}