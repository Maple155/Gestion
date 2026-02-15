package com.gestion.stock.repository;

import com.gestion.stock.dto.LotSearchCriteria;
import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.Lot.LotStatus;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotRepository extends JpaRepository<Lot, UUID>, JpaSpecificationExecutor<Lot> {

       Optional<Lot> findByNumeroLotAndArticleId(String numeroLot, UUID articleId);

       List<Lot> findByArticleIdAndStatutOrderByDateReceptionAsc(UUID articleId, Lot.LotStatus statut);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId AND l.statut = :statut " +
                     "ORDER BY l.datePeremption ASC")
       List<Lot> findByArticleIdAndStatutOrderByDatePeremptionAsc(
                     @Param("articleId") UUID articleId,
                     @Param("statut") Lot.LotStatus statut);

       List<Lot> findByArticleIdAndStatut(UUID articleId, Lot.LotStatus statut);

       List<Lot> findByDatePeremptionBetween(LocalDate start, LocalDate end);

       List<Lot> findByDatePeremptionLessThanEqual(LocalDate date);

       @Query("SELECT l FROM Lot l WHERE l.datePeremption <= :dateLimite " +
                     "AND l.statut = 'DISPONIBLE' AND l.quantiteActuelle > 0")
       List<Lot> findLotsProchePeremption(@Param("dateLimite") LocalDate dateLimite);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND l.statut = 'DISPONIBLE' AND l.quantiteActuelle >= :quantite " +
                     "ORDER BY l.datePeremption ASC")
       List<Lot> findLotsFEFO(@Param("articleId") UUID articleId,
                     @Param("quantite") Integer quantite);

       @Query("SELECT COUNT(l) FROM Lot l WHERE l.statut = :statut")
       long countByStatut(@Param("statut") Lot.LotStatus statut);

       @Query("SELECT l FROM Lot l WHERE " +
                     "l.article.id = :articleId AND " +
                     "l.emplacement.zone.depot.id = :depotId AND " +
                     "l.statut = :statut " +
                     "ORDER BY l.dateReception ASC")
       List<Lot> findByArticleAndDepotAndStatut(
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("statut") Lot.LotStatus statut);

       // NEW: Method to find lots by article ID
       List<Lot> findByArticleId(UUID articleId);

       @Query("SELECT l FROM Lot l " +
                     "WHERE l.article.id = :articleId " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.datePeremption ASC")
       List<Lot> findLotsActifsByArticle(@Param("articleId") UUID articleId);

       @Query("SELECT l FROM Lot l WHERE " +
                     "(:numeroLot IS NULL OR LOWER(l.numeroLot) LIKE LOWER(CONCAT('%', :numeroLot, '%'))) AND " +
                     "(:articleId IS NULL OR l.article.id = :articleId) AND " +
                     "(:statut IS NULL OR l.statut = :statut) AND " +
                     "(:actif IS NULL OR (:actif = true AND l.quantiteActuelle > 0) OR (:actif = false AND l.quantiteActuelle <= 0)) AND "
                     + // CORRECTION ICI
                     "(:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId)")
       Page<Lot> rechercherAvecFiltres(
                     @Param("numeroLot") String numeroLot,
                     @Param("articleId") UUID articleId,
                     @Param("statut") String statut,
                     @Param("actif") Boolean actif,
                     @Param("depotId") UUID depotId,
                     Pageable pageable);

       // NEW: Check if lot number exists
       boolean existsByNumeroLot(String numeroLot);

       // NEW: Find lots close to expiration with details
       @Query("SELECT l FROM Lot l " +
                     "LEFT JOIN FETCH l.article a " +
                     "LEFT JOIN FETCH l.emplacement e " +
                     "LEFT JOIN FETCH e.zone z " +
                     "LEFT JOIN FETCH z.depot d " +
                     "WHERE l.datePeremption <= :dateLimite " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle > 0")
       List<Lot> findLotsProchePeremptionAvecDetails(@Param("dateLimite") LocalDate dateLimite);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND (:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.datePeremption ASC")
       List<Lot> findLotsDisponiblesByArticleAndDepot(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND (:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle >= :quantiteRequise " +
                     "ORDER BY l.datePeremption ASC")
       Optional<Lot> findLotFEFO(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("quantiteRequise") Integer quantiteRequise);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND (:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle >= :quantiteRequise " +
                     "ORDER BY l.dateReception ASC")
       Optional<Lot> findLotFIFO(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("quantiteRequise") Integer quantiteRequise);

       // Version alternative qui retourne plusieurs lots
       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND l.emplacement.zone.depot.id = :depotId " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.datePeremption ASC, l.dateReception ASC")
       List<Lot> findLotsPourSortieFEFO(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND l.emplacement.zone.depot.id = :depotId " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.dateReception ASC, l.datePeremption ASC")
       List<Lot> findLotsPourSortieFIFO(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       // Nouvelles méthodes utiles
       @Query("SELECT l FROM Lot l WHERE l.emplacement.zone.depot.id = :depotId")
       List<Lot> findByDepotId(@Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE l.emplacement.zone.depot.id = :depotId " +
                     "AND l.statut = 'DISPONIBLE'")
       List<Lot> findDisponiblesByDepotId(@Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE l.bonReception.id = :bonReceptionId")
       List<Lot> findByBonReceptionId(@Param("bonReceptionId") UUID bonReceptionId);

       // Pour la valorisation
       @Query("SELECT COALESCE(SUM(l.quantiteActuelle * l.coutUnitaire), 0) FROM Lot l " +
                     "WHERE l.article.id = :articleId AND l.statut = 'DISPONIBLE'")
       BigDecimal getValeurTotaleByArticleId(@Param("articleId") UUID articleId);

       @Query("SELECT COALESCE(SUM(l.quantiteActuelle * l.coutUnitaire), 0) FROM Lot l " +
                     "WHERE l.emplacement.zone.depot.id = :depotId AND l.statut = 'DISPONIBLE'")
       BigDecimal getValeurTotaleByDepotId(@Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE " +
                     "l.article.id = :articleId AND " +
                     "l.statut = 'DISPONIBLE' AND " +
                     "l.quantiteActuelle >= :quantite AND " +
                     "(l.emplacement.zone.depot.id = :depotId OR :depotId IS NULL) " +
                     "ORDER BY l.dateReception ASC")
       List<Lot> findLotsDisponiblesByArticleAndDepot(
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("quantite") Integer quantite);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId AND l.emplacement.zone.depot.id = :depotId AND l.statut = :statut AND l.quantiteActuelle > 0")
       List<Lot> findByArticleIdAndDepotIdAndStatut(
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId,
                     @Param("statut") Lot.LotStatus statut);

       @Query("SELECT l FROM Lot l WHERE l.article = :article " +
                     "AND l.statut = :statut " +
                     "AND l.quantiteActuelle >= :quantite")
       List<Lot> findByArticleAndStatutAndQuantiteActuelleGreaterThanEqual(
                     @Param("article") Article article,
                     @Param("statut") Lot.LotStatus statut,
                     @Param("quantite") Integer quantite);

       List<Lot> findByNumeroLotContainingIgnoreCase(String numeroLot);

       @Query("SELECT l FROM Lot l WHERE " +
                     "(:numeroLot IS NULL OR LOWER(l.numeroLot) LIKE LOWER(CONCAT('%', :numeroLot, '%'))) AND " +
                     "(:articleId IS NULL OR l.article.id = :articleId) AND " +
                     "(:statut IS NULL OR l.statut = :statut) AND " +
                     "(:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) AND " +
                     "(:dateFrom IS NULL OR l.datePeremption >= :dateFrom) AND " +
                     "(:dateTo IS NULL OR l.datePeremption <= :dateTo)")
       Page<Lot> searchLots(
                     @Param("numeroLot") String numeroLot,
                     @Param("articleId") UUID articleId,
                     @Param("statut") LotStatus statut,
                     @Param("depotId") UUID depotId,
                     @Param("dateFrom") LocalDate dateFrom,
                     @Param("dateTo") LocalDate dateTo,
                     Pageable pageable);

       // Quantité disponible par article et dépôt
       @Query("SELECT SUM(l.quantiteActuelle) FROM Lot l JOIN l.emplacement e JOIN e.zone z JOIN z.depot d " +
                     "WHERE l.article.id = :articleId AND d.id = :depotId AND l.statut = 'DISPONIBLE'")
       Integer getQuantiteDisponibleByArticleAndDepot(@Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       // Lots disponibles pour allocation FIFO/FEFO
       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId AND l.statut = 'DISPONIBLE' AND l.quantiteActuelle > 0 "
                     +
                     "ORDER BY " +
                     "CASE WHEN :method = 'FEFO' THEN l.datePeremption END ASC NULLS LAST, " +
                     "CASE WHEN :method = 'FIFO' THEN l.dateReception END ASC")
       List<Lot> findLotsForAllocation(
                     @Param("articleId") UUID articleId,
                     @Param("method") String method);

       @Query("SELECT l FROM Lot l WHERE " +
                     "l.article.id = :articleId AND " +
                     "(:emplacementId IS NULL OR l.emplacement.id = :emplacementId) AND " +
                     "l.statut = 'DISPONIBLE' AND " +
                     "l.id != :excludeId AND " +
                     "l.quantiteActuelle > 0 " +
                     "ORDER BY l.dateReception ASC")
       List<Lot> findLotsCompatiblesPourFusion(
                     @Param("articleId") UUID articleId,
                     @Param("emplacementId") UUID emplacementId,
                     @Param("excludeId") UUID excludeId);

       @Query("SELECT l FROM Lot l WHERE " +
                     "(:numeroLot IS NULL OR LOWER(l.numeroLot) LIKE LOWER(CONCAT('%', :numeroLot, '%'))) AND " +
                     "(:articleId IS NULL OR l.article.id = :articleId) AND " +
                     "(:statut IS NULL OR l.statut = :statut) AND " +
                     "(:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) AND " +
                     "(:datePeremptionFrom IS NULL OR l.datePeremption >= :datePeremptionFrom) AND " +
                     "(:datePeremptionTo IS NULL OR l.datePeremption <= :datePeremptionTo)")
       List<Lot> findAllByCriteria(
                     @Param("numeroLot") String numeroLot,
                     @Param("articleId") UUID articleId,
                     @Param("statut") LotStatus statut,
                     @Param("depotId") UUID depotId,
                     @Param("datePeremptionFrom") LocalDate datePeremptionFrom,
                     @Param("datePeremptionTo") LocalDate datePeremptionTo);

       default List<Lot> findAllByCriteria(LotSearchCriteria criteria) {
              return findAllByCriteria(
                            criteria.getNumeroLot(),
                            criteria.getArticleId(),
                            criteria.getStatut(),
                            criteria.getDepotId(),
                            criteria.getDatePeremptionFrom(),
                            criteria.getDatePeremptionTo());
       }

       @Query("SELECT l FROM Lot l WHERE " +
                     "l.datePeremption <= :dateLimite AND " +
                     "l.statut = 'DISPONIBLE' AND " +
                     "l.quantiteActuelle > 0")
       Page<Lot> findLotsProchePeremptionPage(
                     @Param("dateLimite") LocalDate dateLimite,
                     Pageable pageable);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId AND " +
                     "l.emplacement.zone.depot.id = :depotId AND l.statut = 'DISPONIBLE' AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.dateReception ASC")
       List<Lot> findByArticleIdAndDepotOrderByDateReceptionAsc(
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId AND " +
                     "l.emplacement.zone.depot.id = :depotId AND l.statut = 'DISPONIBLE' AND l.quantiteActuelle > 0 " +
                     "ORDER BY l.datePeremption ASC NULLS LAST")
       List<Lot> findByArticleIdAndDepotOrderByDatePeremptionAsc(
                     @Param("articleId") UUID articleId,
                     @Param("depotId") UUID depotId);

       //

       // Récupérer un lot par son numéro
       Optional<Lot> findByNumeroLot(String numeroLot);

       // Récupérer les lots par statut
       List<Lot> findByStatut(String statut);

       // Récupérer les lots proches de la péremption
       @Query("SELECT l FROM Lot l WHERE l.datePeremption BETWEEN :start AND :end AND l.quantiteActuelle > 0 ORDER BY l.datePeremption ASC")
       List<Lot> findLotsProchesPeremption(@Param("start") LocalDate start, @Param("end") LocalDate end);

       // Récupérer les lots périmés
       @Query("SELECT l FROM Lot l WHERE l.datePeremption < :date AND l.quantiteActuelle > 0")
       List<Lot> findLotsPerimes(@Param("date") LocalDate date);

       // Récupérer les lots disponibles par article pour sortie FIFO
       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND l.quantiteActuelle > 0 " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "ORDER BY l.dateReception ASC")
       List<Lot> findLotsDisponiblesFIFO(@Param("articleId") UUID articleId);

       // Récupérer les lots disponibles par article pour sortie FEFO
       @Query("SELECT l FROM Lot l WHERE l.article.id = :articleId " +
                     "AND l.quantiteActuelle > 0 " +
                     "AND l.statut = 'DISPONIBLE' " +
                     "AND l.datePeremption IS NOT NULL " +
                     "ORDER BY l.datePeremption ASC")
       List<Lot> findLotsDisponiblesFEFO(@Param("articleId") UUID articleId);

       // Récupérer les lots d'un emplacement
       List<Lot> findByEmplacementId(UUID emplacementId);

       // Compter les lots par statut
       @Query("SELECT l.statut, COUNT(l) FROM Lot l GROUP BY l.statut")
       List<Object[]> countLotsByStatut();

       // // Récupérer les lots avec leurs mouvements
       // @Query("SELECT l FROM Lot l LEFT JOIN FETCH l.mouvements WHERE l.id = :id")
       // Optional<Lot> findByIdWithMouvements(@Param("id") UUID id);

       // Récupérer la quantité totale par article
       @Query("SELECT SUM(l.quantiteActuelle) FROM Lot l WHERE l.article.id = :articleId")
       Long getQuantiteTotaleByArticle(@Param("articleId") UUID articleId);
}