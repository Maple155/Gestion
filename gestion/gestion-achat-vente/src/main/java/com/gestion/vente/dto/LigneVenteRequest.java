package com.gestion.vente.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LigneVenteRequest {
    @NotNull
    private UUID articleId;

    @NotNull
    @Min(1)
    private Integer quantite;

    @NotNull
    private BigDecimal prixUnitaireHt;

    private BigDecimal remisePourcentage = BigDecimal.ZERO;
    private BigDecimal tvaPourcentage = BigDecimal.valueOf(20.0);
}
