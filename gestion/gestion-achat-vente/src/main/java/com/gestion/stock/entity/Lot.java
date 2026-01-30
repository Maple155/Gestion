package com.gestion.stock.entity;

import com.gestion.achat.entity.*;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "lots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "numero_lot", nullable = false)
    private String numeroLot; // LOT-2026-0001
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;
    
    @ManyToOne
    @JoinColumn(name = "bon_reception_id")
    private BonReception bonReception;
    
    @Column(name = "quantite_initiale", nullable = false)
    private Integer quantiteInitiale;
    
    @Column(name = "quantite_actuelle", nullable = false)
    private Integer quantiteActuelle;
    
    @Column(name = "date_fabrication")
    private LocalDate dateFabrication;
    
    @Column(name = "date_reception", nullable = false)
    private LocalDate dateReception = LocalDate.now();
    
    @Column(name = "date_peremption")
    private LocalDate datePeremption; // DLC
    
    @Column(name = "cout_unitaire", nullable = false, precision = 15, scale = 4)
    private BigDecimal coutUnitaire;
    
    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private LotStatus statut = LotStatus.DISPONIBLE;
    
    @Column(name="dluo")
    private LocalDate dluo; 

    @Column(name="certificat_conformite")
    private String certificatConformite;

    @ManyToOne
    @JoinColumn(name = "emplacement_id")
    private Emplacement emplacement;
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    

    public enum LotStatus {
        DISPONIBLE, QUARANTAINE, BLOQUE, PERIME, EPUISE, FUSIONNE
    }
}