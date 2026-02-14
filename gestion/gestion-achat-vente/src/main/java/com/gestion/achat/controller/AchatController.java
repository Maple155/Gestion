package com.gestion.achat.controller;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import com.gestion.achat.entity.*;
import com.gestion.achat.enums.StatutDemande;
import com.gestion.achat.repository.DemandeAchatRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.achat.repository.ProformaRepository;
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
    private final ProformaRepository proformaRepo;
    private final FactureAchatRepository factureRepo;
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
        BonReception br = achatService.enregistrerReception(bcId, conforme, "");
        stockService.creerEntreeStockFromReception(br.getId(), UUID.fromString(session.getAttribute("userId").toString()));
        return ResponseEntity.ok(br);
    }
    @PostMapping("/bons-commande/{bcId}/facturer")
    public ResponseEntity<FactureAchat> facturer(@PathVariable UUID bcId, @RequestParam String numFacture, HttpSession session) {
        // Rôle COMPTABLE ou DAF pour rapprochement 3-way match
        checkAuth(session, "ADMIN", "COMPTABLE", "DAF");
        return ResponseEntity.ok(achatService.enregistrerFacture(bcId, numFacture, LocalDate.now()));
    }
    @PostMapping("/proformas")
    public ResponseEntity<Proforma> creerProforma(@RequestBody Proforma proforma, HttpSession session) {
        // Seul l'acheteur ou l'admin peut saisir les offres reçues
        checkAuth(session, "ADMIN", "ACHETEUR", "RESPONSABLE_ACHATS");
        
        log.info("Saisie d'un nouveau proforma pour la DA: {}", proforma.getDemandeAchat().getId());
        
        // Sauvegarde simple via repository
        Proforma nouveauProforma = proformaRepo.save(proforma);
        return ResponseEntity.status(HttpStatus.CREATED).body(nouveauProforma);
    }
    @GetMapping("/proformas/{id}")
    public ResponseEntity<Proforma> getProformaById(@PathVariable UUID id) {
        return proformaRepo.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/factures/{id}")
    public ResponseEntity<?> getFactureById(@PathVariable UUID id) {
        // 1. On cherche la facture
        java.util.Optional<FactureAchat> factureOpt = factureRepo.findById(id);
        
        // 2. Si elle n'existe pas, on sort tout de suite
        if (factureOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Facture non trouvée");
        }

        // 3. Si elle existe, on construit le DTO manuellement
        FactureAchat f = factureOpt.get();
        java.util.Map<String, Object> dto = new java.util.HashMap<>();
        
        dto.put("id", f.getId());
        dto.put("numeroFacture", f.getNumeroFactureFournisseur());
        dto.put("dateFacture", f.getDateFacture());
        // On descend dans les relations pour récupérer le nom du fournisseur
        dto.put("fournisseurNom", f.getBonCommande().getProforma().getFournisseur().getNom());
        dto.put("referenceBc", f.getBonCommande().getReferenceBc());
        dto.put("montantTotal", f.getMontantTotalTtc());
        dto.put("estPayee", f.isEstPayee());

        // 4. On retourne le JSON
        return ResponseEntity.ok(dto);
    }
    @PostMapping("/factures")
    public ResponseEntity<?> creerFacture(@RequestBody FactureAchat facture, HttpSession session) {
        checkAuth(session, "ADMIN", "COMPTABLE", "DAF", "ACHETEUR");
        
        // On peut ajouter une vérification ici pour s'assurer qu'un BC n'a pas déjà de facture
        if (factureRepo.existsByBonCommandeId(facture.getBonCommande().getId())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Ce Bon de Commande est déjà facturé."));
        }
        
        return ResponseEntity.status(HttpStatus.CREATED).body(factureRepo.save(facture));
    }
}