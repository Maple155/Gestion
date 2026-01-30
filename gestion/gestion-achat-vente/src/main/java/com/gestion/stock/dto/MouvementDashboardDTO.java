package com.gestion.stock.dto;

import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * DTO pour afficher les mouvements de stock dans le dashboard
 * Évite les problèmes de lazy loading et simplifie l'accès aux propriétés dans Thymeleaf
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MouvementDashboardDTO {
    
    private String reference;
    private String sens; // ENTREE ou SORTIE
    private String typeLibelle; // Libellé du type de mouvement
    private String articleCode;
    private String articleLibelle;
    private String depotCode;
    private String depotNom;
    private Integer quantite;
    private BigDecimal coutUnitaire;
    private BigDecimal valeurMouvement;
    private LocalDateTime date;
    private String statut;
    
    // Propriété pour l'affichage formaté
    public String getSensLibelle() {
        return "ENTREE".equals(sens) ? "Entrée" : "Sortie";
    }
    
    public String getSensCssClass() {
        return "ENTREE".equals(sens) ? "badge bg-success" : "badge bg-danger";
    }
}
