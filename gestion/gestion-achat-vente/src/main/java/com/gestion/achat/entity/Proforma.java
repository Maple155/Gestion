package com.gestion.achat.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "proformas")
@Getter @Setter @NoArgsConstructor
public class Proforma {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "demande_achat_id", nullable = false)
    private DemandeAchat demandeAchat;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fournisseur_id", nullable = false)
    private Fournisseur fournisseur;

    @Column(nullable = false)
    private BigDecimal prixUnitaireHt;

    private BigDecimal tvaPourcentage = BigDecimal.valueOf(20.0);

    private Integer delaiLivraisonJours;

    private boolean estSelectionne = false;

    private String documentUrl;

    @CreationTimestamp
    private LocalDateTime dateReception;
}