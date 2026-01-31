// DashboardController.java - Version complète
package com.gestion.stock.controller.dashboard;

import com.gestion.stock.entity.Inventaire;
import com.gestion.stock.entity.MovementType;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.repository.StockMovementRepository;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final StockMovementRepository stockMovementRepository;

    /**
     * Dashboard principal - Vue d'ensemble
     */
    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        try {
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
        } catch (Exception e) {
            log.error("ERROR in dashboard method", e);

            // Ajouter des données par défaut pour éviter l'erreur
            model.addAttribute("kpis", new HashMap<String, Object>());
            model.addAttribute("valorisation", new HashMap<String, Object>());
            model.addAttribute("evolution", new ArrayList<Map<String, Object>>());
            model.addAttribute("topArticles", new ArrayList<Map<String, Object>>());
            model.addAttribute("alertes", new HashMap<String, Object>());
            model.addAttribute("inventairesEnCours", new ArrayList<Inventaire>());
            model.addAttribute("derniersMouvements", new ArrayList<StockMovement>());
            model.addAttribute("title", "Dashboard Stock");
            model.addAttribute("activePage", "stock-dashboard");
            model.addAttribute("dateJour", LocalDate.now());

            return "stock/dashboard/index";
        }
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
            @RequestParam(defaultValue = "50") int jours) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Statistiques des mouvements
        Map<String, Object> statsMouvements = stockService.getStatistiquesMouvements(jours);
        model.addAttribute("stats", statsMouvements);

        // Derniers mouvements détaillés
        // List<StockMovement> derniersMouvements =
        // stockService.getDerniersMouvements(50, jours);
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

    @GetMapping("/api/valorisation/detail")
    @ResponseBody
    public List<Map<String, Object>> getValorisationDetailApi() {
        return valorisationService.getValorisationDetailForDashboard();
    }

    @GetMapping("/api/alertes")
    @ResponseBody
    public Map<String, Object> getAlertesApi() {
        return reportingService.getAlertes();
    }

    @GetMapping("/api/mouvements/journalier")
    @ResponseBody
    public Map<String, Object> getMouvementsJournalierApi(
            @RequestParam(defaultValue = "30") int jours) {

        Map<String, Object> response = new HashMap<>();

        // Récupérer les mouvements des X derniers jours
        LocalDateTime dateDebut = LocalDateTime.now().minusDays(jours);
        List<StockMovement> mouvements = stockMovementRepository.findByDateMouvementBetween(
                dateDebut, LocalDateTime.now());

        // Grouper par jour
        Map<LocalDate, Long> entreesParJour = new LinkedHashMap<>();
        Map<LocalDate, Long> sortiesParJour = new LinkedHashMap<>();

        // Initialiser tous les jours
        for (int i = jours - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            entreesParJour.put(date, 0L);
            sortiesParJour.put(date, 0L);
        }

        // Compter les mouvements par jour
        for (StockMovement mvt : mouvements) {
            LocalDate date = mvt.getDateMouvement().toLocalDate();
            if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE) {
                entreesParJour.put(date, entreesParJour.getOrDefault(date, 0L) + 1);
            } else {
                sortiesParJour.put(date, sortiesParJour.getOrDefault(date, 0L) + 1);
            }
        }

        // Préparer les labels (dates)
        List<String> labels = new ArrayList<>();
        List<Long> entrees = new ArrayList<>();
        List<Long> sorties = new ArrayList<>();

        for (int i = jours - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            labels.add(date.format(DateTimeFormatter.ofPattern("dd/MM")));
            entrees.add(entreesParJour.get(date));
            sorties.add(sortiesParJour.get(date));
        }

        response.put("labels", labels);
        response.put("entrees", entrees);
        response.put("sorties", sorties);
        response.put("jours", jours);

        return response;
    }
}