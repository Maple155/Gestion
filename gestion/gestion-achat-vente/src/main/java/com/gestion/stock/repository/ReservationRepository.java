// ReservationRepository.java
package com.gestion.stock.repository;

import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.entity.ReservationStock.ReservationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationRepository extends JpaRepository<ReservationStock, UUID> {
    
    // Trouver par commande client
    List<ReservationStock> findByCommandeClientId(UUID commandeClientId);
    
    // Trouver par article et dépôt
    List<ReservationStock> findByArticleIdAndDepotId(UUID articleId, UUID depotId);
    
    // Trouver par statut
    List<ReservationStock> findByStatut(ReservationStatus statut);
    
    // Trouver par lot
    List<ReservationStock> findByLotId(UUID lotId);
    
    // Réservations expirées
    @Query("SELECT r FROM ReservationStock r WHERE r.dateExpiration < :now AND r.statut = 'ACTIVE'")
    List<ReservationStock> findExpiredReservations(@Param("now") LocalDateTime now);
    
    // Réservations actives pour un article dans un dépôt
    @Query("SELECT SUM(r.quantiteReservee - r.quantitePrelevee) FROM ReservationStock r " +
           "WHERE r.article.id = :articleId " +
           "AND r.depot.id = :depotId " +
           "AND r.statut = 'ACTIVE'")
    Integer findQuantiteReserveeActive(@Param("articleId") UUID articleId, 
                                      @Param("depotId") UUID depotId);
    
    // Recherche avancée avec filtres
    @Query("SELECT r FROM ReservationStock r WHERE " +
           "(:articleId IS NULL OR r.article.id = :articleId) AND " +
           "(:depotId IS NULL OR r.depot.id = :depotId) AND " +
           "(:statut IS NULL OR r.statut = :statut) AND " +
           "(:commandeClientId IS NULL OR r.commandeClientId = :commandeClientId)")
    Page<ReservationStock> findWithFilters(@Param("articleId") UUID articleId,
                                          @Param("depotId") UUID depotId,
                                          @Param("statut") ReservationStatus statut,
                                          @Param("commandeClientId") UUID commandeClientId,
                                          Pageable pageable);
    
    // Vérifier si une ligne de commande est déjà réservée
    @Query("SELECT r FROM ReservationStock r WHERE r.ligneCommandeId = :ligneCommandeId")
    List<ReservationStock> findByLigneCommandeId(@Param("ligneCommandeId") UUID ligneCommandeId);
}