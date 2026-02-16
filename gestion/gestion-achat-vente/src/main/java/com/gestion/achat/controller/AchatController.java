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
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
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
    private final BonCommandeRepository bonCommandeRepository;
    private final BonReceptionRepository bonReceptionRepo;
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
        checkAuth(session, "ADMIN", "ACHETEUR","RESPONSABLE_ACHATS");
        return ResponseEntity.ok(achatService.selectionnerMeilleureOffre(daId));
    }

    @PostMapping("/bons-commande/generer/{proformaId}")
    public ResponseEntity<BonCommande> genererBC(@PathVariable UUID proformaId, HttpSession session) {
        // Rôle ACHETEUR : transformation DA -> BC
        checkAuth(session, "ADMIN", "ACHETEUR","RESPONSABLE_ACHATS");
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
        checkAuth(session, "ADMIN", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "ACHETEUR","RESPONSABLE_ACHATS");
        BonReception br = achatService.enregistrerReception(bcId, conforme, "");
        stockService.creerEntreeStockFromReception(br.getId(), UUID.fromString(session.getAttribute("userId").toString()));
        return ResponseEntity.ok(br);
    }
    @PostMapping("/bons-commande/{bcId}/facturer")
    public ResponseEntity<FactureAchat> facturer(@PathVariable UUID bcId, @RequestParam String numFacture, HttpSession session) {
        // Rôle COMPTABLE ou DAF pour rapprochement 3-way match
        checkAuth(session, "ADMIN", "COMPTABLE", "DAF","RESPONSABLE_ACHATS");
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
    public ResponseEntity<FactureAchat> creerFacture(@RequestBody FactureAchat facture, HttpSession session) {
        // 1. Vérification des droits (Comptable ou DAF)
        checkAuth(session, "ADMIN", "COMPTABLE", "DAF","RESPONSABLE_ACHATS");
        
        // 2. Sécurité : Une facture neuve n'est JAMAIS payée à la création dans ce flux
        facture.setEstPayee(false);
        
        // 3. Optionnel : Recalculer le montant TTC depuis le BC pour éviter toute fraude via l'API
        BonCommande bc = bonCommandeRepository.findById(facture.getBonCommande().getId())
                .orElseThrow(() -> new RuntimeException("Bon de commande introuvable"));
        facture.setMontantTotalTtc(bc.getMontantTotalTtc());
        
        // 4. Sauvegarde
        FactureAchat nouvelleFacture = factureRepo.save(facture);
        
        log.info("Facture {} créée pour le BC {} - Statut: À RÉGLER", 
                facture.getNumeroFactureFournisseur(), bc.getReferenceBc());
                
        return ResponseEntity.status(HttpStatus.CREATED).body(nouvelleFacture);
    }
        // À ajouter dans AchatController.java
    @GetMapping("/bons-commande/{id}")
    public ResponseEntity<BonCommande> getBonCommandeById(@PathVariable UUID id, HttpSession session) {
        // Optionnel : vérifier si l'utilisateur est connecté
        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        return bonCommandeRepository.findById(id) // Assure-toi que cette méthode existe dans ton Service
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    @GetMapping("/receptions/{id}")
    public ResponseEntity<?> getReceptionDetail(@PathVariable UUID id) {
        return bonReceptionRepo.findById(id).map(br -> {
            Map<String, Object> dto = new java.util.HashMap<>();
            dto.put("id", br.getId());
            dto.put("dateReception", br.getDateReception());
            dto.put("observations", br.getObservations());
            dto.put("conforme", br.isConforme());

            // Navigation vers le Bon de Commande
            Map<String, Object> bc = new java.util.HashMap<>();
            bc.put("referenceBc", br.getBonCommande().getReferenceBc());
            
            // Navigation vers le Fournisseur
            Map<String, Object> fournisseur = new java.util.HashMap<>();
            fournisseur.put("nom", br.getBonCommande().getProforma().getFournisseur().getNom());
            
            // Navigation vers la Demande (pour le produit et la quantité)
            Map<String, Object> da = new java.util.HashMap<>();
            da.put("produitId", br.getBonCommande().getProforma().getDemandeAchat().getProduitId());
            da.put("quantiteDemandee", br.getBonCommande().getProforma().getDemandeAchat().getQuantiteDemandee());

            // Assemblage du DTO
            Map<String, Object> proforma = new java.util.HashMap<>();
            proforma.put("fournisseur", fournisseur);
            proforma.put("demandeAchat", da);
            bc.put("proforma", proforma);
            dto.put("bonCommande", bc);

            return ResponseEntity.ok(dto);
        }).orElse(ResponseEntity.notFound().build());
    }
}