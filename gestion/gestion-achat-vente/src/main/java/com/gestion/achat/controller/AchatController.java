package com.gestion.achat.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.achat.entity.*;
import com.gestion.achat.service.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/achats")
@RequiredArgsConstructor
public class AchatController {

    private final AchatService achatService;

    @PostMapping("/selectionner-offre/{daId}")
    public ResponseEntity<Proforma> selectionner(@PathVariable UUID daId) {
        return ResponseEntity.ok(achatService.selectionnerMeilleureOffre(daId));
    }

    @PostMapping("/bons-commande/generer/{proformaId}")
    public ResponseEntity<BonCommande> genererBC(@PathVariable UUID proformaId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(achatService.genererBonCommande(proformaId));
    }

    @PatchMapping("/bons-commande/{bcId}/valider")
    public ResponseEntity<Void> validerFinance(@PathVariable UUID bcId) {
        achatService.validerAchat(bcId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bons-commande/{bcId}/recevoir")
    public ResponseEntity<BonReception> recevoir(@PathVariable UUID bcId, 
                                                @RequestParam boolean conforme,
                                                @RequestParam(required = false) String obs) {
        return ResponseEntity.ok(achatService.enregistrerReception(bcId, conforme, obs));
    }

    @PostMapping("/bons-commande/{bcId}/facturer")
    public ResponseEntity<FactureAchat> facturer(@PathVariable UUID bcId,
                                                @RequestParam String numFacture,
                                                @RequestParam String dateFact) {
        return ResponseEntity.ok(achatService.enregistrerFacture(bcId, numFacture, LocalDate.parse(dateFact)));
    }
}