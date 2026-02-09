package com.gestion.vente.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.StatutLivraison;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "livraisons_clients")
@Getter
@Setter
@NoArgsConstructor
public class LivraisonClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "commande_id", nullable = false)
    private CommandeClient commande;

    private LocalDateTime datePreparation;
    private LocalDateTime dateLivraison;

    @Enumerated(EnumType.STRING)
    private StatutLivraison statut = StatutLivraison.EN_PREPARATION;

    private String transporteur;
    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "livraison", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneLivraisonClient> lignes = new ArrayList<>();
}
