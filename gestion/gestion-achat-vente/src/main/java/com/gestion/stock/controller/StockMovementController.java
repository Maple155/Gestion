package com.gestion.stock.controller;

import com.gestion.stock.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/stock/mouvements")
@RequiredArgsConstructor
@Slf4j
public class StockMovementController {
    
    private final StockService stockService;
    
    @PostMapping("/creer-entree/{bonReceptionId}")
    public String creerEntreeStock(@PathVariable String bonReceptionId,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system"; 
            }
            
            stockService.creerEntreeStockFromReception(UUID.fromString(bonReceptionId), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Entrée en stock créée avec succès");
            return "redirect:/achats/reception/liste";
            
        } catch (Exception e) {
            log.error("Erreur création entrée stock", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/achats/reception/liste";
        }
    }
}