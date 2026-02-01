package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "utilisateurs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Utilisateur {
    
    @Id
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String username;
    
    @Column(unique = true, nullable = false)
    private String email;
    
    @Column(nullable = false)
    private String password;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(nullable = false)
    private String prenom;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.UTILISATEUR;
    
    @Column(nullable = false)
    private boolean actif = true;
    
    private String telephone;
    private String poste;
    private String service;
    
    // @ManyToOne(fetch = FetchType.LAZY)
    // @JoinColumn(name = "depot_id")
    // private Depot depot;
    
    // private LocalDateTime dernierLogin;
    // private Integer loginTentatives = 0;
    // private Boolean compteVerrouille = false;
    // private LocalDateTime dateVerrouillage;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Énumération des rôles
    public enum Role {
        ADMIN,
        GESTIONNAIRE_STOCK,
        RESPONSABLE_STOCK,
        COMMERCIAL,
        RESPONSABLE_VENTES,
        MAGASINIER_SORTIE,
        COMPTABLE_CLIENT,
        COMPTABLE,
        MANAGER,
        UTILISATEUR,
        COMPTEUR_INVENTAIRE,
        VALIDEUR_INVENTAIRE
    }
}