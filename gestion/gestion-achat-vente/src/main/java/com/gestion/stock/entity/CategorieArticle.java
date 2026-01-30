package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "categories_articles")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategorieArticle {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String libelle;
    
    private String description;
    
    @ManyToOne
    @JoinColumn(name = "categorie_parent_id")
    private CategorieArticle categorieParent;
    
    @Column(name = "necessite_tracabilite_lot")
    private boolean necessiteTracabiliteLot = false;
    
    @Column(name = "methode_valorisation")
    private String methodeValorisation = "CUMP";
    
    @Column(name = "actif")
    private boolean actif = true;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}