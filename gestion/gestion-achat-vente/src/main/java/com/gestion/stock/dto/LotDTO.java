package com.gestion.stock.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class LotDTO {
    private UUID id;
    private String numeroLot;
    private UUID articleId;
    private String articleCode;
    private String articleLibelle;
    private UUID bonReceptionId;
    private String bonReceptionReference;
    private Integer quantiteInitiale;
    private Integer quantiteActuelle;
    private LocalDate dateFabrication;
    private LocalDate dateReception;
    private LocalDate datePeremption;
    private LocalDate dluo;
    private BigDecimal coutUnitaire;
    private String statut;
    private String certificatConformite;
    private UUID emplacementId;
    private String emplacementCode;
    private UUID zoneId;
    private String zoneCode;
    private UUID depotId;
    private String depotNom;
    
    // Calculé
    private Integer joursRestants;
    private BigDecimal valeurStock;
    
    @Data
    public static class CreateLotDTO {
        private String numeroLot;
        private UUID articleId;
        private UUID bonReceptionId;
        private Integer quantiteInitiale;
        private Integer quantiteActuelle;
        private LocalDate dateFabrication;
        private LocalDate dateReception;
        private LocalDate datePeremption;
        private BigDecimal coutUnitaire;
        private String certificatConformite;
        private UUID emplacementId;
    }
    
    @Data
    public static class UpdateLotDTO {
        private Integer quantiteActuelle;
        private LocalDate datePeremption;
        private String statut;
        private String certificatConformite;
        private UUID emplacementId;
        private String motifChangement;
    }
}