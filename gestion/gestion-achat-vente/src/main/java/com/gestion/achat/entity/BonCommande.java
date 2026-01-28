package com.gestion.achat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.achat.enums.StatutFinance;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "bons_commande")
@Getter @Setter @NoArgsConstructor
public class BonCommande {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proforma_id", unique = true, nullable = false)
    private Proforma proforma;

    @Column(unique = true, nullable = false)
    private String referenceBc; // Exemple: BC-2026-001

    @Enumerated(EnumType.STRING)
    private StatutFinance statutFinance = StatutFinance.EN_ATTENTE_VALIDATION;

    @Column(nullable = false)
    private BigDecimal montantTotalTtc;

    @CreationTimestamp
    private LocalDateTime dateEmission;

    private LocalDate dateLivraisonEstimee;
}