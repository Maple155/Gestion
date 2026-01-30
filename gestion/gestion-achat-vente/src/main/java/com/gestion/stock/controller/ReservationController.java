package com.gestion.stock.controller;

import com.gestion.stock.service.ReservationService;
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
@RequestMapping("/stock/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {
    
    private final ReservationService reservationService;
    
    @GetMapping("/nouvelle")
    public String formulaireReservation(Model model,
                                       @RequestParam(required = false) String commandeClientId,
                                       HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Nouvelle Réservation");
        model.addAttribute("activePage", "stock-reservations");
        model.addAttribute("commandeClientId", commandeClientId);
        
        return "stock/reservations/nouvelle";
    }
    
    @PostMapping("/creer")
    public String creerReservation(@RequestParam Map<String, String> params,
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
            String ligneCommandeId = params.get("ligneCommandeId");
            
            reservationService.reserverStock(UUID.fromString(articleId), UUID.fromString(depotId), quantite, 
            UUID.fromString(commandeClientId), UUID.fromString(ligneCommandeId), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Réservation créée avec succès");
            
            if (commandeClientId != null && !commandeClientId.isEmpty()) {
                return "redirect:/ventes/commandes/details/" + commandeClientId;
            }
            
            return "redirect:/stock/reservations/liste";
            
        } catch (Exception e) {
            log.error("Erreur création réservation", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/reservations/nouvelle";
        }
    }
    
    @PostMapping("/annuler/{id}")
    public String annulerReservation(@PathVariable String id,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        
        try {
            reservationService.annulerReservation(UUID.fromString(id));
            redirectAttributes.addFlashAttribute("success", 
                "Réservation annulée avec succès");
        } catch (Exception e) {
            log.error("Erreur annulation réservation", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/reservations/liste";
    }
}