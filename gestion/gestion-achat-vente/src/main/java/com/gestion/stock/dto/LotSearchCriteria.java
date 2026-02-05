package com.gestion.stock.dto;

import com.gestion.stock.entity.Lot.LotStatus;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class LotSearchCriteria {
    private String numeroLot;
    private UUID articleId;
    private UUID depotId;
    private UUID zoneId;
    private UUID emplacementId;
    private LotStatus statut;
    private LocalDate datePeremptionFrom;
    private LocalDate datePeremptionTo;
    private Boolean prochePeremption;
    private Integer joursAlertePeremption = 30;
}