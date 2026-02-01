package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import com.gestion.vente.enums.StatutCommandeClient;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "commandes_clients")
@Getter
@Setter
@NoArgsConstructor
public class CommandeClient {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "devis_id")
    private DevisVente devis;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    private UUID depotLivraisonId;

    @CreationTimestamp
    private LocalDateTime dateCommande;

    private LocalDate dateLivraisonPrevue;

    @Enumerated(EnumType.STRING)
    private StatutCommandeClient statut = StatutCommandeClient.EN_ATTENTE;

    private BigDecimal totalHt = BigDecimal.ZERO;
    private BigDecimal totalTva = BigDecimal.ZERO;
    private BigDecimal totalTtc = BigDecimal.ZERO;
    private BigDecimal remiseGlobale = BigDecimal.ZERO;

    private String modeReservation = "IMMEDIATE";

    private UUID creePar;
    private UUID validePar;
    private LocalDateTime dateValidation;

    private String notes;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "commande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommandeClient> lignes = new ArrayList<>();
}
