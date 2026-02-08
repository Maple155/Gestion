package com.gestion.achat.controller;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import com.gestion.achat.entity.*;
import com.gestion.achat.enums.StatutDemande;
import com.gestion.achat.repository.DemandeAchatRepository;
import com.gestion.achat.service.*;
import com.gestion.stock.service.StockService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
@Slf4j
@RestController
@RequestMapping("/api/achats")
@RequiredArgsConstructor
public class AchatController {

    private final AchatService achatService;
    private final DemandeAchatRepository demandeRepo;
    private final StockService stockService;
    // ... repositories ...

    private void checkAuth(HttpSession session, String... allowedRoles) {
        if (session.getAttribute("userId") == null) throw new RuntimeException("Non connecté");
        
        String role = (String) session.getAttribute("userRole");
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) return;
        }
        // Modifiez le message d'erreur pour voir le rôle fautif
        throw new RuntimeException("Accès refusé. Votre rôle est : " + role + ". Rôles autorisés : " + String.join(", ", allowedRoles));
    }
    @PostMapping("/demandes")
    public ResponseEntity<DemandeAchat> creerDemande(@RequestBody DemandeAchat demande, HttpSession session) {
        // Seul un utilisateur connecté peut créer
        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        
        // Initialisation forcée du statut pour la sécurité
        demande.setStatut(StatutDemande.EN_ATTENTE);
        
        // Sauvegarde via le repository
        DemandeAchat nouvelleDA = demandeRepo.save(demande);
        
        log.info("Nouvelle DA créée par l'utilisateur ID: {} pour l'article ID: {}", 
        session.getAttribute("userId"), demande.getProduitId());
                
        return ResponseEntity.status(HttpStatus.CREATED).body(nouvelleDA);
    }
    @PostMapping("/selectionner-offre/{daId}")
    public ResponseEntity<Proforma> selectionner(@PathVariable UUID daId, HttpSession session) {
        // Seul l'ACHETEUR peut sélectionner l'offre après négociation
        checkAuth(session, "ADMIN", "ACHETEUR");
        return ResponseEntity.ok(achatService.selectionnerMeilleureOffre(daId));
    }

    @PostMapping("/bons-commande/generer/{proformaId}")
    public ResponseEntity<BonCommande> genererBC(@PathVariable UUID proformaId, HttpSession session) {
        // Rôle ACHETEUR : transformation DA -> BC
        checkAuth(session, "ADMIN", "ACHETEUR");
        return ResponseEntity.status(HttpStatus.CREATED).body(achatService.genererBonCommande(proformaId));
    }

    @PatchMapping("/bons-commande/{bcId}/valider")
    public ResponseEntity<Void> validerBC(@PathVariable UUID bcId, HttpSession session) {
        // Approbation multiniveau + Finance (DAF)
        checkAuth(session, "ADMIN", "APPRO_N1", "APPRO_N2", "APPRO_N3", "RESPONSABLE_ACHATS", "DAF");
        achatService.validerAchat(bcId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/bons-commande/{bcId}/signer")
    public ResponseEntity<Void> signerLegal(@PathVariable UUID bcId, HttpSession session) {
        // DG ou DAF uniquement : Signataires légaux selon le cahier des charges
        checkAuth(session, "ADMIN", "DAF", "DG");
        // achatService.signerBC(bcId); // Logique de signature finale
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bons-commande/{bcId}/recevoir")
    public ResponseEntity<BonReception> recevoir(@PathVariable UUID bcId, @RequestParam boolean conforme, HttpSession session) {
        // Magasiniers
        checkAuth(session, "ADMIN", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK");
        stockService.creerEntreeStockFromReception(bcId, UUID.fromString(session.getAttribute("userId").toString()));
        return ResponseEntity.ok(achatService.enregistrerReception(bcId, conforme, ""));
    }

    @PostMapping("/bons-commande/{bcId}/facturer")
    public ResponseEntity<FactureAchat> facturer(@PathVariable UUID bcId, @RequestParam String numFacture, HttpSession session) {
        // Rôle COMPTABLE ou DAF pour rapprochement 3-way match
        checkAuth(session, "ADMIN", "COMPTABLE", "DAF");
        return ResponseEntity.ok(achatService.enregistrerFacture(bcId, numFacture, LocalDate.now()));
    }
}