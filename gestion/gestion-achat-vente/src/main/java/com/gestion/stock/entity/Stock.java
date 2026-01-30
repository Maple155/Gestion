package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stocks", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"article_id", "depot_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Stock {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @Column(name = "quantite_physique", nullable = false)
    private Integer quantitePhysique = 0;
    
    @Column(name = "quantite_theorique", nullable = false)
    private Integer quantiteTheorique = 0;
    
    @Column(name = "quantite_reservee", nullable = false)
    private Integer quantiteReservee = 0;
    
    @Column(name = "valeur_stock_cump", precision = 15, scale = 2)
    private BigDecimal valeurStockCump = BigDecimal.ZERO;
    
    @Column(name = "date_dernier_mouvement")
    private LocalDateTime dateDernierMouvement;
    
    @Column(name = "date_dernier_inventaire")
    private LocalDateTime dateDernierInventaire;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    // Champ calculé
    @Transient
    public Integer getQuantiteDisponible() {
        return quantiteTheorique - quantiteReservee;
    }
    
    // Champ calculé
    @Transient
    public BigDecimal getCoutUnitaireMoyen() {
        if (quantiteTheorique == 0) {
            return BigDecimal.ZERO;
        }
        return valeurStockCump.divide(BigDecimal.valueOf(quantiteTheorique), 4, BigDecimal.ROUND_HALF_UP);
    }
}