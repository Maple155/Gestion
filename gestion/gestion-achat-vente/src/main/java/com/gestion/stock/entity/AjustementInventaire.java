package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ajustements_inventaire")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AjustementInventaire {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @OneToOne
    @JoinColumn(name = "ligne_inventaire_id", nullable = false)
    private LigneInventaire ligneInventaire;
    
    @ManyToOne
    @JoinColumn(name = "mouvement_stock_id")
    private StockMovement mouvementStock;
    
    @Column(name = "quantite_ajustee", nullable = false)
    private Integer quantiteAjustee;
    
    @Column(name = "valeur_ajustement", nullable = false, precision = 15, scale = 2)
    private BigDecimal valeurAjustement;
    
    @Column(name = "valideur_id", nullable = false)
    private UUID valideurId;
    
    @Column(name = "date_validation", nullable = false)
    private LocalDateTime dateValidation = LocalDateTime.now();
    
    @Column(nullable = false)
    private String motif;
    
    private String justification;
    
    @Column(name = "requiert_double_validation")
    private Boolean requiertDoubleValidation = false;
    
    @Column(name = "second_valideur_id")
    private UUID secondValideurId;
    
    @Column(name = "date_second_validation")
    private LocalDateTime dateSecondValidation;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}