package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "historique_couts", indexes = {
    @Index(name = "idx_historique_article", columnList = "article_id"),
    @Index(name = "idx_historique_date", columnList = "date_effet"),
    @Index(name = "idx_historique_annee_mois", columnList = "annee,mois")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueCout {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @Column(name = "date_effet", nullable = false)
    private LocalDate dateEffet;
    
    @Column(name = "annee", nullable = false)
    private Integer annee;
    
    @Column(name = "mois", nullable = false)
    private Integer mois;
    
    @Column(name = "cout_unitaire_moyen", nullable = false, precision = 15, scale = 4)
    private BigDecimal coutUnitaireMoyen;
    
    @Column(name = "quantite_stock", nullable = false)
    private Integer quantiteStock;
    
    @Column(name = "valeur_stock", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurStock;
    
    @Column(name = "methode_valorisation", nullable = false, length = 20)
    private String methodeValorisation;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mouvement_stock_id")
    private StockMovement mouvementStock;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloture_mensuelle_id")
    private ClotureMensuelle clotureMensuelle;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;
    
    // Pré-calcul de l'année et mois avant persistence
    @PrePersist
    @PreUpdate
    private void prePersist() {
        if (dateEffet != null) {
            this.annee = dateEffet.getYear();
            this.mois = dateEffet.getMonthValue();
        }
    }
}