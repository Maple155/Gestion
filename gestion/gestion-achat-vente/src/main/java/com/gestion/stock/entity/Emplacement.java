package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "emplacements")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Emplacement {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "zone_id", nullable = false)
    private ZoneStockage zone;
    
    @Column(nullable = false)
    private String code; // Ex: A-01-02-03 (Allée-Travée-Niveau-Position)
    
    private String allee;
    
    private String travee;
    
    private String niveau;
    
    private String position;
    
    @Column(name = "capacite_poids_kg", precision = 10, scale = 2)
    private BigDecimal capacitePoidsKg;
    
    @Column(name = "capacite_volume_m3", precision = 10, scale = 2)
    private BigDecimal capaciteVolumeM3;
    
    @Column(name = "actif")
    private boolean actif = true;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}