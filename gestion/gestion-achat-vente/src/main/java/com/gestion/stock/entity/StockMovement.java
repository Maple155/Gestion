package com.gestion.stock.entity;

import com.gestion.achat.entity.*;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "mouvements_stock")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StockMovement {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String reference; // MVT-2026-000001
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "type_mouvement_id", nullable = false)
    private MovementType type;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "emplacement_id")
    private Emplacement emplacement;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventaire_id")
    private Inventaire inventaire; 

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transfert_id")
    private Transfert transfert; 

    @Column(nullable = false)
    private Integer quantite;
    
    @Column(name = "cout_unitaire", nullable = false, precision = 15, scale = 4)
    private BigDecimal coutUnitaire;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lot_id")
    private Lot lot;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_reception_id")
    private BonReception bonReception;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_commande_id")
    private BonCommande bonCommande;
    
    @Column(name = "commande_client_id")
    private UUID commandeClientId;
    
    @Column(name = "date_mouvement", nullable = false)
    private LocalDateTime dateMouvement;
    
    @Column(name = "date_comptable", nullable = false)
    private LocalDate dateComptable;
    
    @Column(name = "utilisateur_id", nullable = false)
    private UUID utilisateurId;
    
    private String motif;
    
    @Column(name = "modifiable")
    private Boolean modifiable = true;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private MovementStatus statut = MovementStatus.VALIDE;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Champ calculé
    @Transient
    public BigDecimal getValeurMouvement() {
        return coutUnitaire.multiply(BigDecimal.valueOf(quantite));
    }
    
    public enum MovementStatus {
        BROUILLON, VALIDE, ANNULE
    }
}