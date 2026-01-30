package com.gestion.stock.controller;

import com.gestion.stock.service.InventaireService;
import com.gestion.stock.entity.Inventaire;
import com.gestion.stock.entity.LigneInventaire;
import com.gestion.stock.entity.AjustementInventaire;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.util.*;

@Controller
@RequestMapping("/stock/inventaires")
@RequiredArgsConstructor
@Slf4j
public class InventaireController {
    
    private final InventaireService inventaireService;
    
    /**
     * Liste des inventaires
     */
    @GetMapping("/liste")
    public String listeInventaires(Model model,
                                  HttpSession session,
                                  @RequestParam(required = false) String statut,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String depotId,
                                  @RequestParam(required = false) 
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                                  @RequestParam(required = false) 
                                  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Inventaires Physiques");
        model.addAttribute("activePage", "stock-inventaires");
        
        // Récupérer les inventaires selon les filtres
        // inventaireRepository.findBy... selon les paramètres
        
        return "stock/inventaires/liste";
    }
    
    /**
     * Formulaire création inventaire
     */
    @GetMapping("/nouveau")
    public String formulaireNouvelInventaire(Model model, HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Nouvel Inventaire");
        model.addAttribute("activePage", "stock-inventaires");
        model.addAttribute("types", Inventaire.TypeInventaire.values());
        
        return "stock/inventaires/nouveau";
    }
    
    /**
     * Créer un inventaire
     */
    @PostMapping("/creer")
    public String creerInventaire(@RequestParam Map<String, String> params,
                                 HttpSession session,
                                 RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            
            String type = params.get("type");
            String depotId = params.get("depotId");
            String zoneId = params.get("zoneId");
            String categorieId = params.get("categorieId");
            LocalDate dateDebut = LocalDate.parse(params.get("dateDebut"));
            LocalDate dateFin = params.get("dateFin") != null ? 
                LocalDate.parse(params.get("dateFin")) : null;
            String observations = params.get("observations");
            
            Inventaire inventaire = inventaireService.creerInventaire(
                type, UUID.fromString(depotId), UUID.fromString(zoneId), UUID.fromString(categorieId), dateDebut, dateFin, 
                UUID.fromString(utilisateurId), observations);
            
            redirectAttributes.addFlashAttribute("success", 
                "Inventaire créé: " + inventaire.getReference());
            
            return "redirect:/stock/inventaires/details/" + inventaire.getId();
            
        } catch (Exception e) {
            log.error("Erreur création inventaire", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/inventaires/nouveau";
        }
    }
    
    /**
     * Détails d'un inventaire
     */
    @GetMapping("/details/{id}")
    public String detailsInventaire(@PathVariable String id,
                                   Model model,
                                   HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Récupérer l'inventaire et ses lignes
        // Inventaire inventaire = inventaireRepository.findById(id).orElse(null);
        // List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(id);
        
        model.addAttribute("title", "Détails Inventaire");
        model.addAttribute("activePage", "stock-inventaires");
        // model.addAttribute("inventaire", inventaire);
        // model.addAttribute("lignes", lignes);
        
        return "stock/inventaires/details";
    }
    
    /**
     * Interface de comptage (pour mobile/tablette)
     */
    @GetMapping("/comptage/{inventaireId}")
    public String interfaceComptage(@PathVariable String inventaireId,
                                   Model model,
                                   HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Comptage Inventaire");
        model.addAttribute("activePage", "stock-inventaires");
        model.addAttribute("inventaireId", inventaireId);
        
        return "stock/inventaires/comptage";
    }
    
    /**
     * Enregistrer un comptage
     */
    @PostMapping("/enregistrer-comptage")
    @ResponseBody
    public Map<String, Object> enregistrerComptage(@RequestBody Map<String, String> data,
                                                  HttpSession session) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            String ligneId = data.get("ligneId");
            Integer quantite = Integer.parseInt(data.get("quantite"));
            boolean recomptage = Boolean.parseBoolean(data.get("recomptage"));
            
            LigneInventaire ligne = inventaireService.enregistrerComptage(
                UUID.fromString(ligneId), quantite, UUID.fromString(utilisateurId), recomptage);
            
            response.put("success", true);
            response.put("message", "Comptage enregistré");
            response.put("ligne", ligne);
            
        } catch (Exception e) {
            log.error("Erreur enregistrement comptage", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return response;
    }
    
    /**
     * Valider une ligne
     */
    @PostMapping("/valider-ligne")
    public String validerLigne(@RequestParam String ligneId,
                              @RequestParam(required = false) String quantiteFinale,
                              @RequestParam(required = false) String causeEcart,
                              HttpSession session,
                              RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            
            LigneInventaire ligne = inventaireService.validerLigne(
                UUID.fromString(ligneId), quantiteFinale, causeEcart, UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Ligne validée");
            
        } catch (Exception e) {
            log.error("Erreur validation ligne", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/inventaires/details/" + 
            inventaireService.getInventaireIdByLigneId(UUID.fromString(ligneId));
    }
    
    /**
     * Clôturer un inventaire
     */
    @PostMapping("/cloturer/{id}")
    public String cloturerInventaire(@PathVariable String id,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            
            Inventaire inventaire = inventaireService.cloturerInventaire(UUID.fromString(id), UUID.fromString(utilisateurId));
            
            redirectAttributes.addFlashAttribute("success", 
                "Inventaire clôturé: " + inventaire.getReference());
            
        } catch (Exception e) {
            log.error("Erreur clôture inventaire", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/inventaires/details/" + id;
    }
    
    /**
     * Rapport d'inventaire
     */
    @GetMapping("/rapport/{id}")
    public String rapportInventaire(@PathVariable String id,
                                   Model model,
                                   HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        model.addAttribute("title", "Rapport Inventaire");
        model.addAttribute("activePage", "stock-inventaires");
        
        return "stock/inventaires/rapport";
    }
}