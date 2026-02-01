package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.ModePaiement;
import com.gestion.vente.enums.StatutPaiement;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "paiements_clients")
@Getter
@Setter
@NoArgsConstructor
public class PaiementClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "facture_id", nullable = false)
    private FactureVente facture;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private LocalDate datePaiement = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private ModePaiement modePaiement = ModePaiement.VIREMENT;

    @Column(nullable = false)
    private BigDecimal montant;

    @Enumerated(EnumType.STRING)
    private StatutPaiement statut = StatutPaiement.ENREGISTRE;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
