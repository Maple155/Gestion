// DashboardController.java - Version complète
package com.gestion.stock.controller;

import com.gestion.stock.entity.Inventaire;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/main/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {
    
    private final ReportingService reportingService;
    private final ValorisationService valorisationService;
    private final StockService stockService;
    private final InventaireService inventaireService;
    private final LotService lotService;
    
    /**
     * Dashboard principal - Vue d'ensemble
     */
    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());
        
        // 1. KPI globaux
        Map<String, Object> kpis = reportingService.getDashboardKPIs();
        model.addAttribute("kpis", kpis);
        
        // 2. Valorisation FIFO/FEFO/CUMP
        Map<String, Object> valorisation = valorisationService.getSyntheseValorisation();
        model.addAttribute("valorisation", valorisation);
        
        // 3. Évolution valeur stock (12 mois)
        List<Map<String, Object>> evolution = reportingService.getEvolutionValeurStock(12);
        model.addAttribute("evolution", evolution);
        
        // 4. Top 10 articles par valeur
        List<Map<String, Object>> topArticles = valorisationService.getTopArticlesParValeur(10);
        model.addAttribute("topArticles", topArticles);
        
        // 5. Alertes urgentes
        Map<String, Object> alertes = reportingService.getAlertes();
        model.addAttribute("alertes", alertes);
        
        // 6. Statistiques des lots
        Map<String, Object> statsLots = lotService.getStatistiquesLots(null);
        model.addAttribute("statsLots", statsLots);
        
        // 7. Inventaires en cours
        List<Inventaire> inventairesEnCours = inventaireService.getInventairesEnCours();
        model.addAttribute("inventairesEnCours", inventairesEnCours);
        
        // 8. Derniers mouvements
        List<StockMovement> derniersMouvements = stockService.getDerniersMouvements(10);
        model.addAttribute("derniersMouvements", derniersMouvements);
        
        model.addAttribute("title", "Dashboard Stock");
        model.addAttribute("activePage", "stock-dashboard");
        model.addAttribute("dateJour", LocalDate.now());
        
        return "stock/dashboard/index";
    }
    
    /**
     * Vue synthétique valorisation
     */
    @GetMapping("/valorisation")
    public String dashboardValorisation(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Synthèse des méthodes de valorisation
        Map<String, Object> valorisation = valorisationService.getSyntheseValorisation();
        model.addAttribute("valorisation", valorisation);
        
        // Détail par méthode
        model.addAttribute("detailFifo", valorisationService.getDetailValorisationParMethode("FIFO"));
        model.addAttribute("detailFefo", valorisationService.getDetailValorisationParMethode("FEFO"));
        model.addAttribute("detailCump", valorisationService.getDetailValorisationParMethode("CUMP"));
        
        // Évolution historique
        Map<String, Object> evolution = valorisationService.getEvolutionValorisation(6);
        model.addAttribute("evolution", evolution);
        
        model.addAttribute("title", "Dashboard Valorisation");
        model.addAttribute("activePage", "stock-valorisation");
        
        return "stock/dashboard/valorisation";
    }
    
    /**
     * Vue synthétique mouvements
     */
    @GetMapping("/mouvements")
    public String dashboardMouvements(Model model,
                                     HttpSession session,
                                     @RequestParam(defaultValue = "7") int jours) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Statistiques des mouvements
        Map<String, Object> statsMouvements = stockService.getStatistiquesMouvements(jours);
        model.addAttribute("stats", statsMouvements);
        
        // Derniers mouvements détaillés
        List<StockMovement> derniersMouvements = stockService.getDerniersMouvements(50);
        model.addAttribute("mouvements", derniersMouvements);
        
        // Répartition par type
        Map<String, Long> repartitionType = stockService.getRepartitionMouvementsParType(jours);
        model.addAttribute("repartitionType", repartitionType);
        
        model.addAttribute("title", "Dashboard Mouvements");
        model.addAttribute("activePage", "stock-mouvements");
        model.addAttribute("jours", jours);
        
        return "stock/dashboard/mouvements";
    }
    
    /**
     * Vue synthétique pour un article spécifique
     */
    @GetMapping("/article/{articleId}")
    public String dashboardArticle(@PathVariable String articleId,
                                  Model model,
                                  HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Informations article
        Map<String, Object> articleInfo = stockService.getInformationsArticle(UUID.fromString(articleId));
        model.addAttribute("article", articleInfo);
        
        // Stock par dépôt
        List<Map<String, Object>> stockParDepot = stockService.getStockParDepot(UUID.fromString(articleId));
        model.addAttribute("stockParDepot", stockParDepot);
        
        // Valorisation détaillée
        Map<String, Object> valorisation = valorisationService.getDetailValorisationArticle(UUID.fromString(articleId));
        model.addAttribute("valorisation", valorisation);
        
        // Historique des mouvements
        List<StockMovement> historique = stockService.getHistoriqueMouvementsArticle(UUID.fromString(articleId), 30);
        model.addAttribute("historique", historique);
        
        model.addAttribute("title", "Dashboard Article");
        model.addAttribute("activePage", "stock-dashboard");
        
        return "stock/dashboard/article";
    }
    
    /**
     * API pour données dashboard (AJAX)
     */
    @GetMapping("/api/kpi")
    @ResponseBody
    public Map<String, Object> getKPIApi() {
        return reportingService.getDashboardKPIs();
    }
    
    @GetMapping("/api/valorisation")
    @ResponseBody
    public Map<String, Object> getValorisationApi() {
        return valorisationService.getSyntheseValorisation();
    }
    
    @GetMapping("/api/mouvements/recent")
    @ResponseBody
    public List<StockMovement> getMouvementsRecentsApi(
            @RequestParam(defaultValue = "10") int limit) {
        return stockService.getDerniersMouvements(limit);
    }
    
    @GetMapping("/api/alertes")
    @ResponseBody
    public Map<String, Object> getAlertesApi() {
        return reportingService.getAlertes();
    }
}