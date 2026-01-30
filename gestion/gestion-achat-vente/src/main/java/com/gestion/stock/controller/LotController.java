// LotController.java (Complété)
package com.gestion.stock.controller;

import com.gestion.achat.entity.BonReception;
import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.Depot;
import com.gestion.stock.entity.Emplacement;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.service.LotService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/stock/lots")
@RequiredArgsConstructor
@Slf4j
public class LotController {
    
    private final LotService lotService;
    
    /**
     * Liste des lots avec filtres
     */
    @GetMapping("/liste")
    public String listeLots(Model model,
                           HttpSession session,
                           @RequestParam(defaultValue = "0") int page,
                           @RequestParam(defaultValue = "20") int size,
                           @RequestParam(required = false) String numeroLot,
                           @RequestParam(required = false) String articleId,
                           @RequestParam(required = false) String statut,
                           @RequestParam(required = false) String depotId,
                           @RequestParam(required = false) Boolean prochePeremption,
                           @RequestParam(defaultValue = "datePeremption") String tri,
                           @RequestParam(defaultValue = "asc") String ordre) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Configurer la pagination
        Sort.Direction direction = "desc".equalsIgnoreCase(ordre) ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));
        
        // Récupérer les lots filtrés
        Page<Lot> pageLots = lotService.rechercherLots(
            numeroLot, UUID.fromString(articleId), statut, UUID.fromString(depotId), prochePeremption, pageable);
        
        // Statistiques
        Map<String, Object> stats = lotService.getStatistiquesLots(depotId);
        
        model.addAttribute("lots", pageLots);
        model.addAttribute("stats", stats);
        model.addAttribute("numeroLot", numeroLot);
        model.addAttribute("articleId", articleId);
        model.addAttribute("statut", statut);
        model.addAttribute("depotId", depotId);
        model.addAttribute("prochePeremption", prochePeremption);
        model.addAttribute("tri", tri);
        model.addAttribute("ordre", ordre);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", pageLots.getTotalPages());
        model.addAttribute("totalElements", pageLots.getTotalElements());
        
        model.addAttribute("title", "Gestion des Lots");
        model.addAttribute("activePage", "stock-lots");
        
        return "stock/lots/liste";
    }
    
    /**
     * Détails d'un lot
     */
    @GetMapping("/details/{id}")
    public String detailsLot(@PathVariable String id,
                            Model model,
                            HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        Lot lot = lotService.getLotById(UUID.fromString(id));
        Map<String, Object> details = lotService.getDetailsLot(UUID.fromString(id));
        
        // Mouvements associés
        List<StockMovement> mouvements = lotService.getMouvementsLot(UUID.fromString(id));
        
        model.addAttribute("lot", lot);
        model.addAttribute("details", details);
        model.addAttribute("mouvements", mouvements);
        
        model.addAttribute("title", "Détails Lot: " + lot.getNumeroLot());
        model.addAttribute("activePage", "stock-lots");
        
        return "stock/lots/details";
    }
    
    /**
     * Formulaire création lot
     */
    @GetMapping("/nouveau")
    public String formulaireNouveauLot(Model model,
                                      HttpSession session,
                                      @RequestParam(required = false) String articleId,
                                      @RequestParam(required = false) String bonReceptionId) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Pré-remplir si articleId fourni
        if (articleId != null) {
            model.addAttribute("articleId", articleId);
        }
        if (bonReceptionId != null) {
            model.addAttribute("bonReceptionId", bonReceptionId);
        }
        
        // Listes pour les selects
        List<Article> articles = lotService.getArticlesGestionLot();
        List<Depot> depots = lotService.getDepotsActifs();
        List<Emplacement> emplacements = lotService.getEmplacementsDisponibles();
        List<BonReception> bonsReception = lotService.getBonsReceptionRecents();
        
        model.addAttribute("articles", articles);
        model.addAttribute("depots", depots);
        model.addAttribute("emplacements", emplacements);
        model.addAttribute("bonsReception", bonsReception);
        model.addAttribute("aujourdhui", LocalDate.now());
        
        model.addAttribute("title", "Nouveau Lot");
        model.addAttribute("activePage", "stock-lots");
        
        return "stock/lots/formulaire";
    }
    
    /**
     * Créer un lot
     */
    @PostMapping("/creer")
    public String creerLot(@RequestParam Map<String, String> params,
                          HttpSession session,
                          RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            Lot lot = lotService.creerLot(params, utilisateurId);
            
            redirectAttributes.addFlashAttribute("success", 
                "Lot créé: " + lot.getNumeroLot());
            
            return "redirect:/stock/lots/details/" + lot.getId();
            
        } catch (Exception e) {
            log.error("Erreur création lot", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/lots/nouveau";
        }
    }
    
    /**
     * Modifier statut lot
     */
    @PostMapping("/changer-statut/{id}")
    public String changerStatutLot(@PathVariable String id,
                                  @RequestParam String nouveauStatut,
                                  @RequestParam(required = false) String motif,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            lotService.changerStatutLot(UUID.fromString(id), nouveauStatut, motif, UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Statut lot modifié");
                
        } catch (Exception e) {
            log.error("Erreur changement statut lot", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/lots/details/" + id;
    }
    
    /**
     * Lots proches péremption
     */
    @GetMapping("/alertes-peremption")
    public String alertesPeremption(Model model,
                                   HttpSession session,
                                   @RequestParam(defaultValue = "30") Integer jours,
                                   @RequestParam(required = false) String depotId) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        List<Lot> lotsAlertes = lotService.getLotsProchePeremption(jours, depotId);
        Map<String, Object> stats = lotService.getStatistiquesAlertesPeremption(jours);
        
        model.addAttribute("lotsAlertes", lotsAlertes);
        model.addAttribute("stats", stats);
        model.addAttribute("jours", jours);
        model.addAttribute("depotId", depotId);
        
        model.addAttribute("title", "Alertes Péremption");
        model.addAttribute("activePage", "stock-alertes");
        
        return "stock/lots/alertes";
    }
    
    /**
     * Fusionner des lots
     */
    @PostMapping("/fusionner")
    public String fusionnerLots(@RequestParam String lotSourceId,
                               @RequestParam String lotDestinationId,
                               @RequestParam(required = false) String motif,
                               HttpSession session,
                               RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            Lot lotResultat = lotService.fusionnerLots(UUID.fromString(lotSourceId), UUID.fromString(lotDestinationId), motif, UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Lots fusionnés. Nouveau lot: " + lotResultat.getNumeroLot());
            
        } catch (Exception e) {
            log.error("Erreur fusion lots", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/lots/liste";
    }
    
    /**
     Scanner lot (interface mobile)
     */
    @GetMapping("/scanner")
    public String interfaceScanner(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Scanner Lot");
        model.addAttribute("activePage", "stock-scanner");
        
        return "stock/lots/scanner";
    }
    
    @PostMapping("/scanner/process")
    @ResponseBody
    public Map<String, Object> processScanner(@RequestParam String numeroLot,
                                            @RequestParam(required = false) String codeBarre,
                                            HttpSession session) {
        
        Map<String, Object> response = new java.util.HashMap<>();
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            Lot lot = lotService.scannerLot(numeroLot, codeBarre, utilisateurId);
            
            response.put("success", true);
            response.put("message", "Lot scanné: " + lot.getNumeroLot());
            response.put("lot", lot);
            
        } catch (Exception e) {
            log.error("Erreur scan lot", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }
        
        return response;
    }
    
    /**
     * Export des lots
     */
    @GetMapping("/export")
    public String exporterLots(@RequestParam(required = false) String format,
                              @RequestParam(required = false) String statut,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        try {
            String fichier = lotService.exporterLots(format, statut);
            redirectAttributes.addFlashAttribute("success", 
                "Export terminé: " + fichier);
        } catch (Exception e) {
            log.error("Erreur export lots", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur export: " + e.getMessage());
        }
        
        return "redirect:/stock/lots/liste";
    }
}