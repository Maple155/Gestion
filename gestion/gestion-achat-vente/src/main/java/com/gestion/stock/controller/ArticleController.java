// ArticleController.java
package com.gestion.stock.controller;

import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.CategorieArticle;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.entity.UniteMesure;
import com.gestion.stock.service.ArticleService;
import com.gestion.stock.service.StockService;
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
import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/stock/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleController {
    
    private final ArticleService articleService;
    private final StockService stockService;
    
    /**
     * Liste des articles avec recherche et filtres
     */
    @GetMapping("/liste")
    public String listeArticles(Model model,
                               HttpSession session,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               @RequestParam(required = false) String recherche,
                               @RequestParam(required = false) String categorieId,
                               @RequestParam(required = false) Boolean actif,
                               @RequestParam(required = false) Boolean gestionParLot,
                               @RequestParam(defaultValue = "codeArticle") String tri,
                               @RequestParam(defaultValue = "asc") String ordre) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Configurer la pagination
        Sort.Direction direction = "desc".equalsIgnoreCase(ordre) ? 
            Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));
        
        // Récupérer les articles filtrés
        Page<Article> pageArticles = articleService.rechercherArticles(
            recherche, categorieId, actif, gestionParLot, pageable);
        
        // Récupérer les catégories pour le filtre
        List<CategorieArticle> categories = articleService.getCategoriesActives();
        
        // Statistiques
        Map<String, Object> stats = articleService.getStatistiquesArticles();
        
        model.addAttribute("articles", pageArticles);
        model.addAttribute("categories", categories);
        model.addAttribute("stats", stats);
        model.addAttribute("recherche", recherche);
        model.addAttribute("categorieId", categorieId);
        model.addAttribute("actif", actif);
        model.addAttribute("gestionParLot", gestionParLot);
        model.addAttribute("tri", tri);
        model.addAttribute("ordre", ordre);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", pageArticles.getTotalPages());
        model.addAttribute("totalElements", pageArticles.getTotalElements());
        
        model.addAttribute("title", "Articles");
        model.addAttribute("activePage", "stock-articles");
        
        return "stock/articles/liste";
    }
    
    /**
     * Détails d'un article
     */
    @GetMapping("/details/{id}")
    public String detailsArticle(@PathVariable String id,
                                Model model,
                                HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        Article article = articleService.getArticleById(UUID.fromString(id));
        Map<String, Object> details = articleService.getDetailsArticle(UUID.fromString(id));
        
        // Stock par dépôt
        List<Map<String, Object>> stockParDepot = stockService.getStockParDepot(UUID.fromString(id));
        
        // Lots associés
        List<Lot> lots = articleService.getLotsArticle(UUID.fromString(id));
        
        // Historique des mouvements
        List<StockMovement> historique = articleService.getHistoriqueArticle(UUID.fromString(id), 30);
        
        model.addAttribute("article", article);
        model.addAttribute("details", details);
        model.addAttribute("stockParDepot", stockParDepot);
        model.addAttribute("lots", lots);
        model.addAttribute("historique", historique);
        
        model.addAttribute("title", "Détails Article: " + article.getCodeArticle());
        model.addAttribute("activePage", "stock-articles");
        
        return "stock/articles/details";
    }
    
    /**
     * Formulaire création article
     */
    @GetMapping("/nouveau")
    public String formulaireNouvelArticle(Model model, HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        List<CategorieArticle> categories = articleService.getCategoriesActives();
        List<UniteMesure> unites = articleService.getUnitesMesure();
        List<Map<String, Object>> fournisseurs = articleService.getFournisseurs();
        
        model.addAttribute("categories", categories);
        model.addAttribute("unites", unites);
        model.addAttribute("fournisseurs", fournisseurs);
        model.addAttribute("methodesValorisation", Arrays.asList("CUMP", "FIFO", "FEFO"));
        
        model.addAttribute("title", "Nouvel Article");
        model.addAttribute("activePage", "stock-articles");
        
        return "stock/articles/formulaire";
    }
    
    /**
     * Formulaire modification article
     */
    @GetMapping("/editer/{id}")
    public String formulaireEditerArticle(@PathVariable String id,
                                         Model model,
                                         HttpSession session) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        Article article = articleService.getArticleById(UUID.fromString(id));
        List<CategorieArticle> categories = articleService.getCategoriesActives();
        List<UniteMesure> unites = articleService.getUnitesMesure();
        List<Map<String, Object>> fournisseurs = articleService.getFournisseurs();
        
        model.addAttribute("article", article);
        model.addAttribute("categories", categories);
        model.addAttribute("unites", unites);
        model.addAttribute("fournisseurs", fournisseurs);
        model.addAttribute("methodesValorisation", Arrays.asList("CUMP", "FIFO", "FEFO"));
        
        model.addAttribute("title", "Éditer Article: " + article.getCodeArticle());
        model.addAttribute("activePage", "stock-articles");
        
        return "stock/articles/formulaire";
    }
    
    /**
     * Créer ou mettre à jour un article
     */
    @PostMapping("/sauvegarder")
    public String sauvegarderArticle(@RequestParam Map<String, String> params,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        
        try {
            String utilisateurId = (String) session.getAttribute("userId");
            String articleId = params.get("id");
            
            if (articleId == null || articleId.isEmpty()) {
                // Création
                Article article = articleService.creerArticle(params, UUID.fromString(utilisateurId));
                redirectAttributes.addFlashAttribute("success", 
                    "Article créé: " + article.getCodeArticle());
            } else {
                // Modification
                Article article = articleService.modifierArticle(UUID.fromString(articleId), params, UUID.fromString(utilisateurId));
                redirectAttributes.addFlashAttribute("success", 
                    "Article modifié: " + article.getCodeArticle());
            }
            
            return "redirect:/stock/articles/liste";
            
        } catch (Exception e) {
            log.error("Erreur sauvegarde article", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
            return "redirect:/stock/articles/nouveau";
        }
    }
    
    /**
     * Activer/désactiver un article
     */
    @PostMapping("/toggle-actif/{id}")
    public String toggleActifArticle(@PathVariable String id,
                                    HttpSession session,
                                    RedirectAttributes redirectAttributes) {
        
        try {
            articleService.toggleActifArticle(UUID.fromString(id));
            redirectAttributes.addFlashAttribute("success", 
                "Statut article modifié");
        } catch (Exception e) {
            log.error("Erreur changement statut article", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur: " + e.getMessage());
        }
        
        return "redirect:/stock/articles/details/" + id;
    }
    
    /**
     * Export des articles
     */
    @GetMapping("/export")
    public String exporterArticles(@RequestParam(required = false) String format,
                                  HttpSession session,
                                  RedirectAttributes redirectAttributes) {
        
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        try {
            String fichier = articleService.exporterArticles(format);
            redirectAttributes.addFlashAttribute("success", 
                "Export terminé: " + fichier);
        } catch (Exception e) {
            log.error("Erreur export articles", e);
            redirectAttributes.addFlashAttribute("error", 
                "Erreur export: " + e.getMessage());
        }
        
        return "redirect:/stock/articles/liste";
    }
}