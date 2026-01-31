package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Article {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "code_article", unique = true, nullable = false)
    private String codeArticle;
    
    @Column(name = "code_barre")
    private String codeBarre;
    
    @Column(nullable = false, name = "libelle")
    private String libelle;
    
    @Column(columnDefinition = "TEXT", name= "description")
    private String description;
    
    @ManyToOne
    @JoinColumn(name = "categorie_id", nullable = false)
    private CategorieArticle categorie;
    
    @ManyToOne
    @JoinColumn(name = "unite_mesure_id", nullable = false)
    private UniteMesure uniteMesure;
    
    @Column(name = "gestion_par_lot")
    private boolean gestionParLot = false;
    
    @Column(name = "gestion_par_serie")
    private boolean gestionParSerie = false;
    
    @Column(name = "duree_vie_jours")
    private Integer dureeVieJours;
    
    @Column(name = "poids_kg", precision = 10, scale = 3)
    private BigDecimal poidsKg;
    
    @Column(name = "volume_m3", precision = 10, scale = 3)
    private BigDecimal volumeM3;
    
    @Column(name = "stock_minimum")
    private Integer stockMinimum = 0;
    
    @Column(name = "stock_maximum")
    private Integer stockMaximum;
    
    @Column(name = "stock_securite")
    private Integer stockSecurite = 0;
    
    @Column(name = "methode_valorisation")
    private String methodeValorisation = "CUMP"; // FIFO, CUMP, FEFO
    
    @Column(name = "cout_standard", precision = 15, scale = 4)
    private BigDecimal coutStandard;
    
    @Column(name = "prix_vente_ht", precision = 15, scale = 2)
    private BigDecimal prixVenteHt;
    
    @Column(name = "tva_pourcentage", precision = 5, scale = 2)
    private BigDecimal tvaPourcentage = new BigDecimal("20.00");
    
    @Column(name = "actif")
    private boolean actif = true;
    
    @Column(name = "obsolete")
    private boolean obsolete = false;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "updated_by")
    private UUID updatedBy;

    @Transient
    private Integer quantiteDisponible = 0;
    
    @Transient
    private Integer quantiteTheorique = 0;
    
    @Transient
    private LocalDateTime dateDernierMouvement;
    
    // Méthodes pour initialiser les champs transients
    public void setQuantites(Integer disponible, Integer theorique) {
        this.quantiteDisponible = disponible != null ? disponible : 0;
        this.quantiteTheorique = theorique != null ? theorique : 0;
    }
    
    public void setDateDernierMouvement(LocalDateTime date) {
        this.dateDernierMouvement = date;
    }
}