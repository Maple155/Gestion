package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.StatutFactureVente;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "factures_vente")
@Getter
@Setter
@NoArgsConstructor
public class FactureVente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeClient commande;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livraison_id")
    private LivraisonClient livraison;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private LocalDate dateFacture = LocalDate.now();

    @Enumerated(EnumType.STRING)
    private StatutFactureVente statut = StatutFactureVente.EMISE;

    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "facture", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneFactureVente> lignes = new ArrayList<>();
}
