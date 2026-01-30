package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "historique_couts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistoriqueCout {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    
    @Column(name = "depot_id")
    private UUID depotId;
    
    @Column(name = "date_effet", nullable = false)
    private LocalDate dateEffet;
    
    @Column(name = "cout_unitaire_moyen", nullable = false, precision = 15, scale = 4)
    private BigDecimal coutUnitaireMoyen;
    
    @Column(name = "quantite_stock", nullable = false)
    private Integer quantiteStock;
    
    @Column(name = "valeur_stock", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurStock;
    
    @Column(name = "methode_valorisation", nullable = false)
    private String methodeValorisation;
    
    @Column(name = "mouvement_stock_id")
    private String mouvementStockId;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "created_by")
    private String createdBy;
}