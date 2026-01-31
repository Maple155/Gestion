// CreateReservationRequest.java
package com.gestion.stock.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class CreateReservationRequest {
    private UUID articleId;
    private UUID depotId;
    private Integer quantite;
    private UUID commandeClientId;
    private UUID ligneCommandeId;
    private String motif;
}