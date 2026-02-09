package com.gestion.vente.dto;

import java.util.UUID;

import lombok.Data;

@Data
public class CreateLivraisonRequest {
    private UUID utilisateurId;
    private String transporteur;
    private String notes;
}
