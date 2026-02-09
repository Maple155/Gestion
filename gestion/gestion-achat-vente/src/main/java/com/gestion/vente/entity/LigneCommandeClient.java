package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.util.UUID;

import com.gestion.vente.enums.StatutLigneCommande;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lignes_commandes_clients")
@Getter
@Setter
@NoArgsConstructor
public class LigneCommandeClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeClient commande;

    @Column(nullable = false)
    private UUID articleId;

    @Column(nullable = false)
    private Integer quantite;

    @Column(nullable = false)
    private BigDecimal prixUnitaireHt;

    private BigDecimal remisePourcentage = BigDecimal.ZERO;
    private BigDecimal tvaPourcentage = BigDecimal.valueOf(20.0);

    @Column(nullable = false)
    private BigDecimal totalHt;

    @Column(nullable = false)
    private BigDecimal totalTtc;

    private UUID reservationStockId;

    @Enumerated(EnumType.STRING)
    private StatutLigneCommande statut = StatutLigneCommande.EN_ATTENTE;
}
