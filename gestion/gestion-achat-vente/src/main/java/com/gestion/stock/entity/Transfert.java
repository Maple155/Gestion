package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "transferts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transfert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(unique = true, nullable = false)
    private String reference; // TRF-2024-0001
    
    @ManyToOne
    @JoinColumn(name = "depot_source_id", nullable = false)
    private Depot depotSource;
    
    @ManyToOne
    @JoinColumn(name = "depot_destination_id", nullable = false)
    private Depot depotDestination;
    
    @Column(name = "date_demande")
    private LocalDateTime dateDemande = LocalDateTime.now();
    
    @Column(name = "date_expedition")
    private LocalDate dateExpedition;
    
    @Column(name = "date_reception_prevue")
    private LocalDate dateReceptionPrevue;
    
    @Column(name = "date_reception_reelle")
    private LocalDateTime dateReceptionReelle;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private TransfertStatut statut = TransfertStatut.BROUILLON;
    
    private String motif;
    
    @Column(name = "demandeur_id", nullable = false)
    private UUID demandeurId;
    
    @Column(name = "valideur_id")
    private UUID valideurId;
    
    @Column(name = "date_validation")
    private LocalDateTime dateValidation;
    
    @Column(name = "expediteur_id")
    private UUID expediteurId;
    
    @Column(name = "receptionnaire_id")
    private UUID receptionnaireId;
    
    @Column(name = "motif_annulation")
    private String motifAnnulation;
    
    @Column(name = "date_annulation")
    private LocalDateTime dateAnnulation;
    
    @Column(name = "annulateur_id")
    private UUID annulateurId;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum TransfertStatut {
        BROUILLON, VALIDE, EXPEDIE, RECEPTIONNE, ANNULE
    }
}