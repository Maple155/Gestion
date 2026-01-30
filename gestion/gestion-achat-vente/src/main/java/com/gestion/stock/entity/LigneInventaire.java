package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "lignes_inventaire")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneInventaire {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "inventaire_id", nullable = false)
    private Inventaire inventaire;
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @ManyToOne
    @JoinColumn(name = "emplacement_id")
    private Emplacement emplacement;
    
    @ManyToOne
    @JoinColumn(name = "lot_id")
    private Lot lot;
    
    @Column(name = "quantite_theorique", nullable = false)
    private Integer quantiteTheorique;
    
    @Column(name = "quantite_comptee_1")
    private Integer quantiteComptee1;
    
    @Column(name = "quantite_comptee_2")
    private Integer quantiteComptee2;
    
    @Column(name = "quantite_comptee_finale")
    private Integer quantiteCompteeFinale;
    
    @Column(name = "ecart")
    private Integer ecart;
    
    @Column(name = "ecart_valeur", precision = 15, scale = 2)
    private BigDecimal ecartValeur;
    
    @Column(name = "cout_unitaire", precision = 15, scale = 4)
    private BigDecimal coutUnitaire;
    
    @Column(name = "compteur_1_id")
    private UUID compteur1Id;
    
    @Column(name = "date_comptage_1")
    private LocalDateTime dateComptage1;
    
    @Column(name = "compteur_2_id")
    private UUID compteur2Id;
    
    @Column(name = "date_comptage_2")
    private LocalDateTime dateComptage2;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutLigneInventaire statut = StatutLigneInventaire.A_COMPTER;
    
    private String observations;
    
    @Column(name = "cause_ecart")
    private String causeEcart;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    // Calcul de l'écart
    @PreUpdate
    @PrePersist
    private void calculerEcart() {
        if (quantiteCompteeFinale != null && quantiteTheorique != null) {
            this.ecart = quantiteCompteeFinale - quantiteTheorique;
            if (coutUnitaire != null) {
                this.ecartValeur = coutUnitaire.multiply(BigDecimal.valueOf(ecart));
            }
        }
    }
    
    public enum StatutLigneInventaire {
        A_COMPTER, COMPTE, ECART_A_RECOMPTER, VALIDE, AJUSTE, EXCLU
    }
}