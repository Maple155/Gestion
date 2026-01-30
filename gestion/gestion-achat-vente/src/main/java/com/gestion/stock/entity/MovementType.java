package com.gestion.stock.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "types_mouvement")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MovementType {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String libelle;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SensMouvement sens;
    
    @Column(name = "impact_valorisation")
    private boolean impactValorisation = true;
    
    private String description;
    
    public enum SensMouvement {
        ENTREE, SORTIE
    }
}