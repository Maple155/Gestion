package com.gestion.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "zones_stockage")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneStockage {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne
    @JoinColumn(name = "depot_id", nullable = false)
    private Depot depot;
    
    @Column(nullable = false)
    private String code;
    
    @Column(nullable = false)
    private String libelle;
    
    private String type; // RECEPTION, STOCKAGE, EXPEDITION, QUARANTAINE
    
    @Column(name = "capacite_m3", precision = 12, scale = 2)
    private BigDecimal capaciteM3;
    
    @Column(name = "temperature_min", precision = 5, scale = 2)
    private BigDecimal temperatureMin;
    
    @Column(name = "temperature_max", precision = 5, scale = 2)
    private BigDecimal temperatureMax;
}