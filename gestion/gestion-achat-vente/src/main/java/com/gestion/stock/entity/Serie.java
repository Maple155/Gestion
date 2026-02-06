package com.gestion.stock.entity;

import com.gestion.achat.entity.*;
import com.gestion.stock.entity.Lot.LotStatus;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import java.math.BigDecimal;

@Entity
@Table(name = "series")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Serie {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "numero_serie", nullable = false)
    private String numeroSerie; 
    
    @ManyToOne
    @JoinColumn(name = "article_id", nullable = false)
    private Article article;

    @ManyToOne
    @JoinColumn(name = "lot_id", nullable = false)
    private Lot lot;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private SerieStatus statut = SerieStatus.EN_STOCK;

    @ManyToOne
    @JoinColumn(name = "bon_reception_id", nullable = false)
    private BonReception bonReception;

    @Column(name = "date_reception", nullable = false)
    private LocalDate dateReception = LocalDate.now();

    @Column(name = "commande_client_id")
    private UUID commandeClientId;

    @Column(name = "date_sortie", nullable = false)
    private LocalDate dateSortie = LocalDate.now();

    @ManyToOne
    @JoinColumn(name = "emplacement_id")
    private Emplacement emplacement;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    public enum SerieStatus {
        EN_STOCK, VENDU, RETOUR, SAV, REBUT
    }
}
