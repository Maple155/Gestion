package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unites_mesure")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UniteMesure {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String libelle;
    
    @Column(nullable = false)
    private String type; // POIDS, VOLUME, QUANTITE
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}