package com.gestion.achat.controller;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.gestion.achat.dto.FactureDetailsDTO;
import com.gestion.achat.entity.*;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.achat.repository.ProformaRepository;
import com.gestion.achat.service.*;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/achats")
@RequiredArgsConstructor
public class AchatController {

    private final AchatService achatService;
    private final ProformaRepository proformaRepo;
    private final BonCommandeRepository bcRepo;
    private final BonReceptionRepository brRepo;
    private final FactureAchatRepository factureRepository;
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
    @GetMapping("/proformas/{id}")
    @ResponseBody // Très important pour renvoyer du JSON
    public ResponseEntity<Proforma> getProformaDetails(@PathVariable UUID id) {
        return proformaRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/bons-commande/{id}")
    public ResponseEntity<BonCommande> getBonCommande(@PathVariable UUID id) {
        return bcRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/receptions/{id}")
    public ResponseEntity<BonReception> getReception(@PathVariable UUID id) {
        return brRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/factures/{id}")
    @Transactional(readOnly = true)
    public ResponseEntity<FactureDetailsDTO> getFacture(@PathVariable UUID id) {
        FactureAchat f = factureRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Facture introuvable"));
        
        // On construit le DTO en "touchant" aux relations Lazy
        FactureDetailsDTO dto = new FactureDetailsDTO(
            f.getNumeroFactureFournisseur(),
            f.getDateFacture().toString(),
            f.getBonCommande().getProforma().getFournisseur().getNom(),
            f.getBonCommande().getReferenceBc(),
            f.getMontantTotalTtc(),
            f.isEstPayee()
        );
        
        return ResponseEntity.ok(dto);
    }
}