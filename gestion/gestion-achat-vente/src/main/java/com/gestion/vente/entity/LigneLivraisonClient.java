package com.gestion.vente.entity;

import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lignes_livraisons_clients")
@Getter
@Setter
@NoArgsConstructor
public class LigneLivraisonClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "livraison_id", nullable = false)
    private LivraisonClient livraison;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_commande_id", nullable = false)
    private LigneCommandeClient ligneCommande;

    @Column(nullable = false)
    private UUID articleId;

    @Column(nullable = false)
    private Integer quantiteLivree;
}
