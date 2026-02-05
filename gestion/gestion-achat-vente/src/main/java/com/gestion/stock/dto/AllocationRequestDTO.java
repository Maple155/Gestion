package com.gestion.stock.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class AllocationRequestDTO {
    private UUID articleId;
    private UUID depotId;
    private Integer quantite;
    private String methode; // FIFO ou FEFO
    private UUID commandeClientId;
    private UUID reservationId;
}