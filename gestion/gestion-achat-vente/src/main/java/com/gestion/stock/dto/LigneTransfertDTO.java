package com.gestion.stock.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import com.gestion.stock.entity.*;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LigneTransfertDTO {
    private UUID id;
    private Transfert transfert;
    private Article article;
    private Integer quantiteDemandee;
    private Integer quantiteExpediee;
    private Integer quantiteRecue;
    private Lot lot;
    private Emplacement emplacementSource;
    private Emplacement emplacementDestination;
    private String notes;
    private LocalDateTime createdAt;

    private Integer stockDisponible;
}