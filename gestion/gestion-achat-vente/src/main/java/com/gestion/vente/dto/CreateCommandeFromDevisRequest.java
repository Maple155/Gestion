package com.gestion.vente.dto;

import java.time.LocalDate;
import java.util.UUID;

import lombok.Data;

@Data
public class CreateCommandeFromDevisRequest {
    private UUID depotLivraisonId;
    private String modeReservation = "IMMEDIATE";
    private LocalDate dateLivraisonPrevue;
    private UUID creePar;
}
