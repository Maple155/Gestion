package com.gestion.stock.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultatInventaireDTO {
    private String inventaireId;
    private String reference;
    private BigDecimal tauxPrecision;
    private BigDecimal valeurEcartTotal;
    private Integer nombreLignes;
    private Integer nombreEcarts;
    private Integer nombreAjustements;
    private LocalDate dateCloture;
}