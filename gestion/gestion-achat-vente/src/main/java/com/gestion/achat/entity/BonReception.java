package com.gestion.achat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bons_reception")
@Getter @Setter @NoArgsConstructor
public class BonReception {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_commande_id", nullable = false)
    private BonCommande bonCommande;

    @CreationTimestamp
    private LocalDateTime dateReception;

    private boolean conforme = true; // Permet de bloquer le stock si non conforme
    
    private String observations;
}
