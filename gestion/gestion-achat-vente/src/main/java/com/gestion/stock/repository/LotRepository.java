package com.gestion.stock.repository;

import com.gestion.stock.entity.Lot;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LotRepository extends JpaRepository<Lot, UUID> {

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

       // @Query("SELECT l FROM Lot l WHERE " +
       // "(:numeroLot IS NULL OR LOWER(l.numeroLot) LIKE LOWER(CONCAT('%', :numeroLot,
       // '%'))) " +
       // "AND (:articleId IS NULL OR l.article.id = :articleId) " +
       // "AND (:depotId IS NULL OR l.emplacement.zone.depot.id = :depotId) " +
       // "AND (:statut IS NULL OR l.statut = :statut) " +
       // "AND (:datePeremptionFrom IS NULL OR l.datePeremption >= :datePeremptionFrom)
       // " +
       // "AND (:datePeremptionTo IS NULL OR l.datePeremption <= :datePeremptionTo)")
       // Page<Lot> rechercherAvecFiltres(
       // @Param("numeroLot") String numeroLot,
       // @Param("articleId") String articleId,
       // @Param("depotId") String depotId,
       // @Param("statut") Lot.LotStatus statut,
       // @Param("datePeremptionFrom") LocalDate datePeremptionFrom,
       // @Param("datePeremptionTo") LocalDate datePeremptionTo,
       // Pageable pageable);

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
}