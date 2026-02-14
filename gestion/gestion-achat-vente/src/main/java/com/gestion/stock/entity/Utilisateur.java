package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
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
    
    private String service;
    
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @PrePersist
    protected void onCreate() { createdAt = LocalDateTime.now(); }

    public enum Role {
        // --- ADMINISTRATION ---
        ADMIN,

        // --- MODULE ACHATS (Ton Cahier des Charges) ---
        DEMANDEUR,           // Créer DA, pas de validation
        APPROBATEUR_N1,      // Validation selon seuil bas
        APPROBATEUR_N2,      // Validation intermédiaire
        APPROBATEUR_N3,      // Validation haut niveau
        ACHETEUR,            // Transforme DA -> BC, gère fournisseurs
        RESPONSABLE_ACHATS,  // Valide BC au-delà seuil, débloque litiges
        DAF,                 // Finance : Confirmer fonds + Signataire légal
        DG,                  // Signataire légal (DG)

        // --- MODULE STOCK ---
        GESTIONNAIRE_STOCK,
        RESPONSABLE_STOCK,
        MAGASINIER_SORTIE,
        COMPTABLE,
        FINANCE,
        
        // --- MODULE VENTES ---
        COMMERCIAL,
        RESPONSABLE_VENTES,

        // --- BASE ---
        UTILISATEUR,

        MANAGER
    }
}