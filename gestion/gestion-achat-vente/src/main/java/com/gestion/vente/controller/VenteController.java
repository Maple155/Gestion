package com.gestion.vente.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.vente.dto.CreateAvoirRequest;
import com.gestion.vente.dto.CreateCommandeFromDevisRequest;
import com.gestion.vente.dto.CreateDevisRequest;
import com.gestion.vente.dto.CreateLivraisonRequest;
import com.gestion.vente.dto.CreatePaiementRequest;
import com.gestion.vente.entity.*;
import com.gestion.vente.service.VenteService;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/ventes")
@RequiredArgsConstructor
public class VenteController {

    private final VenteService venteService;

    private void requireRole(HttpSession session, String... allowedRoles) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) {
            throw new RuntimeException("Rôle utilisateur manquant");
        }
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) {
                return;
            }
        }
        throw new RuntimeException("Accès refusé pour le rôle: " + role);
    }

    @PostMapping("/devis")
    public ResponseEntity<DevisVente> creerDevis(@Valid @RequestBody CreateDevisRequest request, HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        return ResponseEntity.status(HttpStatus.CREATED).body(venteService.creerDevis(request));
    }

    @PostMapping("/devis/{id}/valider")
    public ResponseEntity<DevisVente> validerDevis(@PathVariable UUID id, @RequestParam UUID validePar, HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        return ResponseEntity.ok(venteService.validerDevis(id, validePar));
    }

    @PostMapping("/devis/{id}/commande")
    public ResponseEntity<CommandeClient> creerCommande(@PathVariable UUID id,
                                                        @RequestBody CreateCommandeFromDevisRequest request,
                                                        HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        return ResponseEntity.status(HttpStatus.CREATED).body(venteService.creerCommandeDepuisDevis(id, request));
    }

    @PostMapping("/commandes/{id}/livrer")
    public ResponseEntity<LivraisonClient> livrerCommande(@PathVariable UUID id,
                                                          @RequestBody CreateLivraisonRequest request,
                                                          HttpSession session) {
        requireRole(session, "ADMIN", "MAGASINIER_SORTIE");
        return ResponseEntity.ok(venteService.creerLivraison(id, request));
    }

    @PostMapping("/commandes/{id}/facturer")
    public ResponseEntity<FactureVente> facturerCommande(@PathVariable UUID id,
                                                         @RequestParam(required = false) UUID livraisonId,
                                                         HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        return ResponseEntity.ok(venteService.genererFacture(id, livraisonId));
    }

    @PostMapping("/factures/{id}/paiements")
    public ResponseEntity<PaiementClient> payerFacture(@PathVariable UUID id,
                                                       @RequestBody CreatePaiementRequest request,
                                                       HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        return ResponseEntity.ok(venteService.enregistrerPaiement(id, request));
    }

    @PostMapping("/factures/{id}/avoirs")
    public ResponseEntity<AvoirClient> creerAvoir(@PathVariable UUID id,
                                                  @RequestBody CreateAvoirRequest request,
                                                  HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        return ResponseEntity.ok(venteService.creerAvoir(id, request));
    }
}
