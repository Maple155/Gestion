package com.gestion.stock.controller;

import com.gestion.stock.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/stock/livraisons")
@RequiredArgsConstructor
@Slf4j
public class LivraisonController {
    
    private final LivraisonService livraisonService;
    
    @GetMapping("/nouvelle")
    public String formulaireLivraison(Model model,
                                     @RequestParam(required = false) String commandeClientId,
                                     @RequestParam(required = false) String reservationId,
                                     HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Nouvelle Livraison");
        model.addAttribute("activePage", "stock-livraisons");
        model.addAttribute("commandeClientId", commandeClientId);
        model.addAttribute("reservationId", reservationId);
        
        return "stock/livraisons/nouvelle";
    }
    
    @PostMapping("/creer-depuis-reservation")
    public String creerLivraisonReservation(@RequestParam String reservationId,
                                           @RequestParam(required = false) String motif,
                                           HttpSession session,
                                           RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system";
            }
            
            livraisonService.creerSortieStock(UUID.fromString(reservationId), UUID.fromString(utilisateurId), motif);
            
            redirectAttributes.addFlashAttribute("success", 
                "Livraison créée avec succès");
            return "redirect:/stock/livraisons/liste";
            
        } catch (Exception e) {
            log.error("Erreur création livraison", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/livraisons/nouvelle";
        }
    }
    
    @PostMapping("/livrer-directement")
    public String livrerDirectement(@RequestParam Map<String, String> params,
                                   HttpSession session,
                                   RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system";
            }
            
            String articleId = params.get("articleId");
            String depotId = params.get("depotId");
            Integer quantite = Integer.parseInt(params.get("quantite"));
            String commandeClientId = params.get("commandeClientId");
            String motif = params.get("motif");
            
            livraisonService.livrerDirectement(UUID.fromString(articleId), UUID.fromString(depotId), quantite, 
            UUID.fromString(commandeClientId), UUID.fromString(utilisateurId), motif);
            
            redirectAttributes.addFlashAttribute("success", 
                "Livraison directe effectuée avec succès");
            return "redirect:/stock/livraisons/liste";
            
        } catch (Exception e) {
            log.error("Erreur livraison directe", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/livraisons/nouvelle";
        }
    }
    
}