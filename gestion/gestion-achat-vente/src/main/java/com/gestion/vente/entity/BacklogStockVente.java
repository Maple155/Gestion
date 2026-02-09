package com.gestion.vente.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "backlog_stock_vente")
@Getter
@Setter
@NoArgsConstructor
public class BacklogStockVente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "devis_id")
    private UUID devisId;

    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    @Column(name = "quantite_demandee", nullable = false)
    private Integer quantiteDemandee;

    @Column(name = "quantite_disponible", nullable = false)
    private Integer quantiteDisponible;

    @Column(name = "quantite_manquante", nullable = false)
    private Integer quantiteManquante;

    @Column(nullable = false)
    private String statut = "EN_ATTENTE";

    private String notes;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
}
