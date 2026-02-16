package com.gestion.stock.controller.dashboard;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.stock.service.ValorisationDetailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Controller
@RequestMapping("/stock/valorisation")
@RequiredArgsConstructor
@Slf4j
public class ValorisationDetailController {

    private final ValorisationDetailService valorisationDetailService;
    private final DepotRepository depotRepository;
    private final ArticleRepository articleRepository;

    private boolean hasAnyRole(HttpSession session, String... roles) {
        String userRole = (String) session.getAttribute("userRole");
        if (userRole == null) return false;
        return Arrays.asList(roles).contains(userRole);
    }

    /**
     * Page principale de valorisation détaillée
     */
    @GetMapping("/detail")
    public String pageValorisationDetail(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

            // Seuls COMPTABLE, DAF, MANAGER, ADMIN peuvent voir la valorisation détaillée
    if (!hasAnyRole(session, "COMPTABLE", "DAF", "MANAGER", "ADMIN", "RESPONSABLE_STOCK")) {
        return "redirect:/access-denied";
    }
    
        try {
            // Charger la liste des dépôts pour les filtres
            List<Depot> depots = depotRepository.findAllByActifTrue();
            model.addAttribute("depots", depots != null ? depots : new ArrayList<>());

            // Statistiques globales pour les cartes
            Map<String, Object> statsGlobales = valorisationDetailService.getStatistiquesGlobales();
            model.addAttribute("statsGlobales", statsGlobales != null ? statsGlobales : new HashMap<>());

        } catch (Exception e) {
            log.error("Erreur lors du chargement de la page de valorisation", e);
            model.addAttribute("depots", new ArrayList<>());
            model.addAttribute("statsGlobales", new HashMap<>());
        }

        model.addAttribute("title", "Valorisation détaillée des stocks");
        model.addAttribute("activePage", "stock-valorisation-detail");
        
        return "stock/valorisation/detail";
    }

    /**
     * API pour obtenir les données filtrées du tableau
     */
    @GetMapping("/api/detail-filtre")
    @ResponseBody
    public ResponseEntity<?> getValorisationDetailFiltre(
            @RequestParam(required = false) UUID depotId,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String methode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFin,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Non authentifié"));
        }

        try {
            log.info("Recherche valorisation avec filtres - depot: {}, article: {}, methode: {}, dateDebut: {}, dateFin: {}",
                    depotId, article, methode, dateDebut, dateFin);

            List<Map<String, Object>> resultats = valorisationDetailService.getValorisationDetailFiltre(
                    depotId, article, methode, dateDebut, dateFin);
            
            return ResponseEntity.ok(resultats != null ? resultats : new ArrayList<>());
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des données de valorisation", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonList(
                            Collections.singletonMap("error", "Erreur: " + e.getMessage())));
        }
    }

    /**
     * API pour obtenir les suggestions d'articles (autocomplete)
     */
    @GetMapping("/api/articles/suggestions")
    @ResponseBody
    public ResponseEntity<?> getSuggestionsArticles(
            @RequestParam String query,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.emptyList());
        }

        try {
            if (query == null || query.length() < 2) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            List<Map<String, Object>> suggestions = valorisationDetailService.getSuggestionsArticles(query);
            return ResponseEntity.ok(suggestions);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des suggestions", e);
            return ResponseEntity.ok(Collections.emptyList());
        }
    }

    /**
     * API pour obtenir les détails d'un article spécifique
     */
    @GetMapping("/api/article/{articleId}/details")
    @ResponseBody
    public ResponseEntity<?> getDetailsArticle(
            @PathVariable UUID articleId,
            @RequestParam(required = false) UUID depotId,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Non authentifié"));
        }

        try {
            Map<String, Object> details = valorisationDetailService.getDetailsArticle(articleId, depotId);
            return ResponseEntity.ok(details);
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des détails de l'article", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    /**
     * API pour exporter les données en CSV
     */
    @GetMapping("/api/export/csv")
    public void exportCSV(
            @RequestParam(required = false) UUID depotId,
            @RequestParam(required = false) String article,
            @RequestParam(required = false) String methode,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate dateFin,
            HttpSession session,
            HttpServletResponse response) throws IOException {

        if (session.getAttribute("userId") == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        try {
            List<Map<String, Object>> donnees = valorisationDetailService.getValorisationDetailFiltre(
                    depotId, article, methode, dateDebut, dateFin);

            String csv = valorisationDetailService.genererCSV(donnees);
            
            response.setContentType("text/csv");
            response.setHeader("Content-Disposition", 
                "attachment; filename=valorisation_stock_" + 
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv");
            
            PrintWriter writer = response.getWriter();
            writer.write(csv);
            writer.flush();
            
        } catch (Exception e) {
            log.error("Erreur lors de l'export CSV", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * API pour les statistiques globales (mise à jour AJAX)
     */
    @GetMapping("/api/statistiques")
    @ResponseBody
    public ResponseEntity<?> getStatistiquesGlobales(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("error", "Non authentifié"));
        }
        
        try {
            Map<String, Object> stats = valorisationDetailService.getStatistiquesGlobales();
            return ResponseEntity.ok(stats != null ? stats : new HashMap<>());
            
        } catch (Exception e) {
            log.error("Erreur lors de la récupération des statistiques", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("error", e.getMessage()));
        }
    }
}