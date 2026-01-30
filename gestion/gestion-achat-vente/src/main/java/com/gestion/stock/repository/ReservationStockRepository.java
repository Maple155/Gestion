package com.gestion.stock.repository;

import com.gestion.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface ReservationStockRepository extends JpaRepository<ReservationStock, UUID> {
    
    List<ReservationStock> findByCommandeClientId(UUID commandeClientId);
    
    List<ReservationStock> findByArticleIdAndDepotIdAndStatut(UUID articleId, UUID depotId, 
                                                             ReservationStock.ReservationStatus statut);
    
    List<ReservationStock> findByStatut(ReservationStock.ReservationStatus statut);
    
    @Query("SELECT SUM(r.quantiteReservee - r.quantitePrelevee) FROM ReservationStock r " +
           "WHERE r.article.id = :articleId AND r.depot.id = :depotId " +
           "AND r.statut = 'ACTIVE'")
    Long getQuantiteReserveeActive(@Param("articleId") UUID articleId, 
                                   @Param("depotId") UUID depotId);
    
    @Query("SELECT r FROM ReservationStock r WHERE r.dateExpiration < :now AND r.statut = 'ACTIVE'")
    List<ReservationStock> findReservationsExpirees(@Param("now") LocalDateTime now);
}