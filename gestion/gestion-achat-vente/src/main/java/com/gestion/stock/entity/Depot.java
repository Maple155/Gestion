package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "depots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Depot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;
    
    @Column(unique = true, nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String nom;
    
    @Column(name = "type")
    private String type = "GENERAL"; // GENERAL, QUARANTAINE, PRODUITS_FINIS
    
    private String adresse;
    
    @Column(name = "capacite_m3", precision = 12, scale = 2)
    private BigDecimal capaciteM3;
    
    @Column(name = "actif")
    private boolean actif = true;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}