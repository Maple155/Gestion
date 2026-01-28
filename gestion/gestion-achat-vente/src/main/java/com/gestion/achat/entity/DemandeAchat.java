package com.gestion.achat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.achat.enums.StatutDemande;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "demandes_achat")
@Getter @Setter @NoArgsConstructor
public class DemandeAchat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID produitId; // Référence vers le module Stock

    @Column(nullable = false)
    private Integer quantiteDemandee;

    private String motif;

    @Enumerated(EnumType.STRING)
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    @CreationTimestamp
    private LocalDateTime dateDemande;
}