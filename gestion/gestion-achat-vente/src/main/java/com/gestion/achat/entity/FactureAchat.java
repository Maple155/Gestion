package com.gestion.achat.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "factures_achat")
@Getter @Setter @NoArgsConstructor
public class FactureAchat {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bon_commande_id", nullable = false)
    private BonCommande bonCommande;

    @Column(nullable = false)
    private String numeroFactureFournisseur; // Référence externe

    @Column(nullable = false)
    private BigDecimal montantTotalTtc;

    private boolean estPayee = false;

    private LocalDate dateFacture;

    @Transient
    private LocalDateTime dateEnregistrement;
}