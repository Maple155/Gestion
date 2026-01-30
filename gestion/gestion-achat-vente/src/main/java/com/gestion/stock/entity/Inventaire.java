package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "inventaires")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Inventaire {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String reference; // INV-2024-0001
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TypeInventaire type;
    
    @ManyToOne
    @JoinColumn(name = "depot_id")
    private Depot depot;
    
    @ManyToOne
    @JoinColumn(name = "zone_id")
    private ZoneStockage zone;
    
    @ManyToOne
    @JoinColumn(name = "categorie_id")
    private CategorieArticle categorie;
    
    @Column(name = "date_debut", nullable = false)
    private LocalDate dateDebut;
    
    @Column(name = "date_fin")
    private LocalDate dateFin;
    
    @Column(name = "date_cloture")
    private LocalDateTime dateCloture;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private StatutInventaire statut = StatutInventaire.PLANIFIE;
    
    @ManyToOne
    @JoinColumn(name = "responsable_id", nullable = false)
    private Utilisateur responsable; // À adapter selon votre modèle d'utilisateur
    
    @Column(name = "nombre_articles_comptes")
    private Integer nombreArticlesComptes = 0;
    
    @Column(name = "valeur_ecart_total", precision = 15, scale = 2)
    private BigDecimal valeurEcartTotal = BigDecimal.ZERO;
    
    @Column(name = "taux_precision")
    private BigDecimal tauxPrecision = BigDecimal.ZERO;
    
    private String observations;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    public enum TypeInventaire {
        ANNUEL, TOURNANT, PARTIEL, CONTROLE, CYCLIQUE
    }
    
    public enum StatutInventaire {
        PLANIFIE, EN_COURS, TERMINE, VALIDE, CLOTURE, ANNULE
    }
}