// ReservationDTO.java
package com.gestion.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReservationDTO {
    private UUID id;
    private String reference;
    
    private UUID articleId;
    private String articleCode;
    private String articleLibelle;
    
    private UUID depotId;
    private String depotCode;
    private String depotNom;
    
    private Integer quantiteReservee;
    private Integer quantitePrelevee;
    private Integer quantiteRestante;
    
    private UUID lotId;
    private String lotNumero;
    
    private UUID commandeClientId;
    private UUID ligneCommandeId;
    
    private LocalDateTime dateReservation;
    private LocalDateTime dateExpiration;
    private String statut;
    
    private UUID utilisateurId;
    private LocalDateTime createdAt;
}