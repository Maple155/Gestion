package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lignes_devis_vente")
@Getter
@Setter
@NoArgsConstructor
public class LigneDevisVente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devis_id", nullable = false)
    private DevisVente devis;

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
}
