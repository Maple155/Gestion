package com.gestion.stock.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "lignes_transfert")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneTransfert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "transfert_id", nullable = false)
    private Transfert transfert;
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @Column(name = "quantite_demandee", nullable = false)
    private Integer quantiteDemandee;
    
    @Column(name = "quantite_expedie")
    private Integer quantiteExpediee = 0;
    
    @Column(name = "quantite_recue")
    private Integer quantiteRecue = 0;
    
    @ManyToOne
    @JoinColumn(name = "lot_id")
    private Lot lot;
    
    @ManyToOne
    @JoinColumn(name = "emplacement_source_id")
    private Emplacement emplacementSource;
    
    @ManyToOne
    @JoinColumn(name = "emplacement_destination_id")
    private Emplacement emplacementDestination;
    
    @Column(name = "notes")
    private String notes;
}