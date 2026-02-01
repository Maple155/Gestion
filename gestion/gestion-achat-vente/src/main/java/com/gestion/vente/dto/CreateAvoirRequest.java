package com.gestion.vente.dto;

import java.math.BigDecimal;
import java.util.UUID;

import lombok.Data;

@Data
public class CreateAvoirRequest {
    private UUID clientId;
    private BigDecimal montant;
    private String motif;
}
