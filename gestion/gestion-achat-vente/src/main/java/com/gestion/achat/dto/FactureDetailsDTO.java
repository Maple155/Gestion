package com.gestion.achat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record FactureDetailsDTO(
    @JsonProperty("numeroFacture") String numeroFacture,
    @JsonProperty("dateFacture") String dateFacture,
    @JsonProperty("fournisseurNom") String fournisseurNom,
    @JsonProperty("referenceBc") String referenceBc,
    @JsonProperty("montantTotal") BigDecimal montantTotal,
    @JsonProperty("estPayee") boolean estPayee
) {}