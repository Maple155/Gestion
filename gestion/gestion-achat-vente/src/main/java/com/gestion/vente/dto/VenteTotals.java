package com.gestion.vente.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class VenteTotals {
    private BigDecimal totalHt;
    private BigDecimal totalTva;
    private BigDecimal totalTtc;
}
