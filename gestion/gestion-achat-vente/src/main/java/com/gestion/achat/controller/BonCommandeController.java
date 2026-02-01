package com.gestion.achat.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.achat.dto.UpdateStatutFinanceRequest;
import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.service.BonCommandeService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/bons-commandes")
@RequiredArgsConstructor
public class BonCommandeController {

    private final BonCommandeService bonCommandeService;

    @PutMapping("/{id}/statut-finance")
    public ResponseEntity<BonCommande> updateStatutFinance(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateStatutFinanceRequest request) {

        BonCommande updated = bonCommandeService.updateStatutFinance(
                id,
                request.getStatutFinance()
        );

        return ResponseEntity.ok(updated);
    }
}
