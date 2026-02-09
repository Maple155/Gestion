package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;
import java.time.LocalDate;

@Entity
@Table(name = "reservations_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReservationStock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String reference; // RES-2026-0001
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @Column(name = "quantite_reservee", nullable = false)
    private Integer quantiteReservee = 0;
    
    @Column(name = "quantite_prelevee")
    private Integer quantitePrelevee = 0;
    
    @ManyToOne
    @JoinColumn(name = "lot_id")
    private Lot lot;
    
    @Column(name = "commande_client_id", nullable = false)
    private UUID commandeClientId;
    
    @Column(name = "ligne_commande_id")
    private UUID ligneCommandeId;
    
    @Column(name = "date_reservation", nullable = false)
    private LocalDateTime dateReservation = LocalDateTime.now();
    
    @Column(name = "date_expiration")
    private LocalDateTime dateExpiration;
    
    @Column(name = "date_livraison_prevue")
    private LocalDate dateLivraisonPrevue;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private ReservationStatus statut = ReservationStatus.ACTIVE;
    
    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    public Integer getQuantitePrelevee() {
        return quantitePrelevee != null ? quantitePrelevee : 0;
    }
    
    // Champ calculé
    @Transient
    public Integer getQuantiteRestante() {
        if (quantiteReservee == null) return 0;
        if (quantitePrelevee == null) return quantiteReservee;
        return quantiteReservee - quantitePrelevee;
    }
    
    public enum ReservationStatus {
        ACTIVE, PRELEVEE, ANNULEE, EXPIREE
    }
}