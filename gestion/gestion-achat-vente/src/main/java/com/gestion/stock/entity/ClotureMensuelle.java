package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "clotures_mensuelles", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"annee", "mois"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ClotureMensuelle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false)
    private Integer annee;
    
    @Column(nullable = false)
    private Integer mois;
    
    @Column(name = "date_debut_periode", nullable = false)
    private LocalDate dateDebutPeriode;
    
    @Column(name = "date_fin_periode", nullable = false)
    private LocalDate dateFinPeriode;
    
    @Column(name = "date_cloture", nullable = false)
    private LocalDateTime dateCloture;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cloture_par_id", nullable = false)
    private Utilisateur cloturePar;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutCloture statut = StatutCloture.OUVERTE;
    
    // Statistiques globales
    @Column(name = "nombre_articles")
    private Integer nombreArticles;
    
    @Column(name = "nombre_articles_valorises")
    private Integer nombreArticlesValorises;
    
    @Column(name = "valeur_stock_total", precision = 15, scale = 2)
    private BigDecimal valeurStockTotal;
    
    @Column(name = "nombre_mouvements")
    private Integer nombreMouvements;
    
    @Column(name = "valeur_mouvements_entree", precision = 15, scale = 2)
    private BigDecimal valeurMouvementsEntree;
    
    @Column(name = "valeur_mouvements_sortie", precision = 15, scale = 2)
    private BigDecimal valeurMouvementsSortie;
    
    @Column(name = "ecart_valorisation", precision = 15, scale = 2)
    private BigDecimal ecartValorisation;
    
    @Column(name = "taux_couverture")
    private BigDecimal tauxCouverture;
    
    // Validation
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "valideur_id")
    private Utilisateur valideur;
    
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;
    
    @Column(columnDefinition = "TEXT")
    private String commentaires;
    
    @Column(name = "rapport_generes", columnDefinition = "TEXT")
    private String rapportGeneres; // URLs ou chemins des rapports
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum StatutCloture {
        OUVERTE,         // Période en cours
        EN_COURS,        // Clôture en cours
        CLOTUREE,        // Clôture terminée
        VALIDEE,         // Validée par la direction
        REJETEE,         // Rejetée, à reprendre
        ARCHIVEE         // Archivée après validation
    }
    
    // Méthodes utilitaires
    public String getPeriodeFormat() {
        return String.format("%02d/%04d", mois, annee);
    }
    
    public boolean isCloturable() {
        return statut == StatutCloture.OUVERTE || statut == StatutCloture.REJETEE;
    }
    
    public boolean isValidee() {
        return statut == StatutCloture.VALIDEE;
    }
}