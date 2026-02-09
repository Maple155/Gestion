package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.StatutAvoir;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "avoirs_clients")
@Getter
@Setter
@NoArgsConstructor
public class AvoirClient {
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

    private LocalDate dateAvoir = LocalDate.now();

    @Column(nullable = false)
    private BigDecimal montant;

    private String motif;

    @Enumerated(EnumType.STRING)
    private StatutAvoir statut = StatutAvoir.EMIS;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
