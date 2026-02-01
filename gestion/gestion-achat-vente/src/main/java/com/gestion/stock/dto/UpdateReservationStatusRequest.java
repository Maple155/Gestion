package com.gestion.stock.dto;

import com.gestion.stock.entity.ReservationStock.ReservationStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateReservationStatusRequest {

    @NotNull
    private ReservationStatus statut;
}
