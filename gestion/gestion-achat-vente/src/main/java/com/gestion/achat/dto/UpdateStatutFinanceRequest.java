package com.gestion.achat.dto;

import com.gestion.achat.enums.StatutFinance;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class UpdateStatutFinanceRequest {

    @NotNull
    private StatutFinance statutFinance;
}
