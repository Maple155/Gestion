package com.gestion.vente.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.gestion.vente.enums.ModePaiement;

import lombok.Data;

@Data
public class CreatePaiementRequest {
    private UUID clientId;
    private BigDecimal montant;
    private ModePaiement modePaiement = ModePaiement.VIREMENT;
    private String notes;
}
