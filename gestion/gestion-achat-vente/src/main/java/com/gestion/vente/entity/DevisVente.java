package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.StatutDevis;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "devis_vente")
@Getter
@Setter
@NoArgsConstructor
public class DevisVente {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @CreationTimestamp
    private LocalDateTime dateDevis;

    private Integer validiteJours = 15;

    @Enumerated(EnumType.STRING)
    private StatutDevis statut = StatutDevis.BROUILLON;

    private BigDecimal totalHt = BigDecimal.ZERO;
    private BigDecimal totalTva = BigDecimal.ZERO;
    private BigDecimal totalTtc = BigDecimal.ZERO;
    private BigDecimal remiseGlobale = BigDecimal.ZERO;

    private UUID creePar;
    private UUID validePar;
    private LocalDateTime dateValidation;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "devis", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneDevisVente> lignes = new ArrayList<>();
}
