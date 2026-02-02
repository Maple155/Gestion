package com.gestion.stock.controller;

import com.gestion.stock.service.TransfertService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.*;
import com.gestion.stock.dto.*;
import com.gestion.stock.entity.Transfert;

@Controller
@RequestMapping("/stock/transferts")
@RequiredArgsConstructor
@Slf4j
public class TransfertController {
    
    private final TransfertService transfertService;
    
    /**
     * Liste des transferts
     */
    @GetMapping("/liste")
    public String listeTransferts(Model model,
                                 HttpSession session,
                                 @RequestParam(required = false) String statut,
                                 @RequestParam(required = false) String depotSource,
                                 @RequestParam(required = false) String depotDestination,
                                 @RequestParam(required = false) String dateDebut,
                                 @RequestParam(required = false) String dateFin) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Transferts entre Dépôts");
        model.addAttribute("activePage", "stock-transferts");
        
        // Récupérer les statistiques
        Map<String, Object> stats = transfertService.getStatistiquesTransferts(
            java.time.LocalDate.now().getMonthValue(),
            java.time.LocalDate.now().getYear());
        model.addAttribute("stats", stats);
        
        return "stock/transferts/liste";
    }
    
    /**
     * Formulaire création transfert
     */
    @GetMapping("/nouveau")
    public String formulaireNouveauTransfert(Model model,
                                            HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Nouveau Transfert");
        model.addAttribute("activePage", "stock-transferts");
        
        return "stock/transferts/nouveau";
    }
    
    /**
     * Créer un transfert
     */
    @PostMapping("/creer")
    public String creerTransfert(@RequestParam Map<String, String> params,
                                HttpSession session,
                                RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system";
            }
            
            String depotSourceId = params.get("depotSourceId");
            String depotDestinationId = params.get("depotDestinationId");
            String motif = params.get("motif");
            
            // Récupérer les lignes du transfert (simplifié)
            List<LigneTransfertDTO> lignes = new ArrayList<>();
            // À compléter selon la structure du formulaire
            
            transfertService.creerTransfert(UUID.fromString(depotSourceId), UUID.fromString(depotDestinationId), 
                                           lignes, motif, UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "✅ Transfert créé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur création transfert", e);
            redirectAttributes.addFlashAttribute("error", 
                "❌ Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/transferts/liste";
    }
    
    /**
     * Valider un transfert
     */
    @PostMapping("/valider/{id}")
    public String validerTransfert(@PathVariable String id,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            transfertService.validerTransfert(UUID.fromString(id), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Transfert validé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur validation transfert", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/transferts/details/" + id;
    }
    
    /**
     * Expédier un transfert
     */
    @PostMapping("/expedier/{id}")
    public String expedierTransfert(@PathVariable String id,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            transfertService.expedierTransfert(UUID.fromString(id), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "✅ Transfert expédié avec succès");
            
        } catch (Exception e) {
            log.error("Erreur expédition transfert", e);
            redirectAttributes.addFlashAttribute("error", 
                "❌ Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/transferts/details/" + id;
    }
    
    /**
     * Réceptionner un transfert
     */
    @PostMapping("/receptionner/{id}")
    public String receptionnerTransfert(@PathVariable String id,
                                       HttpSession session,
                                       RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            transfertService.receptionnerTransfert(UUID.fromString(id), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "✅ Transfert réceptionné avec succès");
            
        } catch (Exception e) {
            log.error("Erreur réception transfert", e);
            redirectAttributes.addFlashAttribute("error", 
                "❌ Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/transferts/details/" + id;
    }
    
    /**
     * Détails d'un transfert
     */
    @GetMapping("/details/{id}")
    public String detailsTransfert(@PathVariable String id,
                                  Model model,
                                  HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Détails Transfert");
        model.addAttribute("activePage", "stock-transferts");
        model.addAttribute("transfertId", id);
        
        return "stock/transferts/details";
    }
    
    /**
     * Annuler un transfert
     */
    @PostMapping("/annuler/{id}")
    public String annulerTransfert(@PathVariable String id,
                                  @RequestParam String motifAnnulation,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            transfertService.annulerTransfert(UUID.fromString(id), motifAnnulation, UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Transfert annulé avec succès");
            
        } catch (Exception e) {
            log.error("Erreur annulation transfert", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/transferts/liste";
    }













































    
     @PutMapping("/{id}/statut")
    public ResponseEntity<Transfert> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateTransfertStatusRequest request,
            @RequestHeader("X-USER-ID") UUID userId) {

        return ResponseEntity.ok(transfertService.updateStatus(id, request.getStatut(), userId));
    }
}