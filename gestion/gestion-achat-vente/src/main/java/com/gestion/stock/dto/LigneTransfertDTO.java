package com.gestion.stock.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneTransfertDTO {
    private String articleId;
    private String lotId;
    private Integer quantiteDemandee;
    private String emplacementSourceId;
    private String emplacementDestinationId;
}