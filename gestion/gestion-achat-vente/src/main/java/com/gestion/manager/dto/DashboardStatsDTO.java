package com.gestion.manager.dto;

import java.util.Map;

import com.gestion.achat.enums.StatutFinance;
import com.gestion.stock.entity.ReservationStock.ReservationStatus;
import com.gestion.stock.entity.StockMovement.MovementStatus;
import com.gestion.stock.entity.Transfert.TransfertStatut;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO pour les statistiques du dashboard
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardStatsDTO {
    
    // Totaux généraux
    private int totalMouvements;
    private int totalReservations;
    private int totalTransferts;
    private int totalBonsCommande;
    
    // Statistiques détaillées par statut
    private Map<MovementStatus, Long> mouvementsByStatus;
    private Map<ReservationStatus, Long> reservationsByStatus;
    private Map<TransfertStatut, Long> transfertsByStatus;
    private Map<StatutFinance, Long> bdcByStatus;
    
    // Statistiques additionnelles (optionnel)
    private Long mouvementsEnAttente;
    private Long reservationsActives;
    private Long transfertsEnCours;
    private Long bdcEnAttente;
}