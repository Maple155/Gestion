package com.gestion.stock.dto;

import com.gestion.stock.entity.StockMovement.MovementStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateMovementStatusRequest {

    @NotNull
    private MovementStatus statut;
}
