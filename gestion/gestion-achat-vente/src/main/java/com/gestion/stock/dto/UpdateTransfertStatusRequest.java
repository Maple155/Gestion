package com.gestion.stock.dto;

import com.gestion.stock.entity.Transfert.TransfertStatut;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateTransfertStatusRequest {

    @NotNull
    private TransfertStatut statut;
}
