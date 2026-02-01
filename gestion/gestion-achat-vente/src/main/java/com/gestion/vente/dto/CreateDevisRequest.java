package com.gestion.vente.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateDevisRequest {
    @NotNull
    private UUID clientId;

    private Integer validiteJours = 15;

    private BigDecimal remiseGlobale = BigDecimal.ZERO;

    private List<LigneVenteRequest> lignes;

    private UUID creePar;

    private String notes;
}
