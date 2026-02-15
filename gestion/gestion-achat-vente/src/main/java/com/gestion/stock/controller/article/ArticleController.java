// ArticleController.java
package com.gestion.stock.controller.article;

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
import java.util.stream.Collectors;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.repository.StockMovementRepository;
@Controller
@RequestMapping("/stock/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleController {

    private final ArticleService articleService;
    private final StockService stockService;
    private final StockMovementRepository stockMovementRepository;
    private final ArticleRepository articleRepository;

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
        Sort.Direction direction = "desc".equalsIgnoreCase(ordre) ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));

        // Récupérer les articles filtrés
        Page<Article> pageArticles = articleService.rechercherArticles(
                recherche, categorieId, actif, gestionParLot, pageable);

        // Pour chaque article, récupérer les informations de stock
        Map<UUID, Map<String, Object>> stockInfoMap = articleService
                .getStockInfoForArticles(
                        pageArticles.getContent().stream()
                                .map(Article::getId)
                                .collect(Collectors.toList()));

        // Assigner les données aux articles
        pageArticles.getContent().forEach(article -> {
            Map<String, Object> info = stockInfoMap.get(article.getId());
            if (info != null) {
                article.setQuantites(
                        (Integer) info.get("quantiteDisponible"),
                        (Integer) info.get("quantiteTheorique"));

                // Récupérer la date du dernier mouvement pour cet article
                Optional<StockMovement> dernierMouvement = stockMovementRepository
                        .findTopByArticleIdOrderByDateMouvementDesc(article.getId());

                dernierMouvement.ifPresent(mvt -> article.setDateDernierMouvement(mvt.getDateMouvement()));
            }
        });

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
        
        // Calculer les jours restants pour chaque lot
        LocalDate aujourdhui = LocalDate.now();
        lots.forEach(lot -> {
            if (lot.getDatePeremption() != null) {
                long joursRestants = ChronoUnit.DAYS.between(aujourdhui, lot.getDatePeremption());
                // Ajouter les jours restants comme attribut temporaire (peut nécessiter un DTO)
                // Ou utiliser un Map pour stocker ces informations
            }
        });
    
        // Historique des mouvements
        List<StockMovement> historique = articleService.getHistoriqueArticle(UUID.fromString(id), 30);
    
        model.addAttribute("article", article);
        model.addAttribute("details", details);
        model.addAttribute("stockParDepot", stockParDepot);
        model.addAttribute("lots", lots);
        model.addAttribute("historique", historique);
        model.addAttribute("aujourdhui", aujourdhui); // Ajouter la date d'aujourd'hui
    
        model.addAttribute("title", "Détails Article: " + article.getCodeArticle());
        model.addAttribute("activePage", "stock-articles");
    
        return "stock/articles/details";
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

    // 

    // Ajoutez ces méthodes à votre ArticleController.java existant

    /**
     * Affiche le formulaire de création d'article
     */
    @GetMapping("/nouveau")
    public String formulaireNouvelArticle(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            List<CategorieArticle> categories = articleService.getCategoriesActives();
            List<UniteMesure> unites = articleService.getUnitesMesure();
            List<Map<String, Object>> fournisseurs = articleService.getFournisseurs();
            
            // Valeurs par défaut
            Map<String, Object> defaults = new HashMap<>();
            defaults.put("actif", true);
            defaults.put("methodeValorisation", "CUMP");
            defaults.put("tvaPourcentage", new BigDecimal("20.00"));
            defaults.put("stockMinimum", 0);
            defaults.put("stockSecurite", 0);

            model.addAttribute("categories", categories);
            model.addAttribute("unites", unites);
            model.addAttribute("fournisseurs", fournisseurs);
            model.addAttribute("methodesValorisation", Arrays.asList("CUMP", "FIFO", "FEFO"));
            model.addAttribute("defaults", defaults);
            model.addAttribute("title", "Nouvel Article");
            model.addAttribute("activePage", "stock-articles");
            model.addAttribute("isNew", true);

        } catch (Exception e) {
            log.error("Erreur lors du chargement du formulaire de création", e);
            model.addAttribute("error", "Erreur lors du chargement du formulaire");
        }

        return "stock/articles/formulaire";
    }

    /**
     * Affiche le formulaire de modification d'article
     */
    @GetMapping("/editer/{id}")
    public String formulaireEditerArticle(@PathVariable String id,
                                          Model model,
                                          HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            UUID articleId = UUID.fromString(id);
            Article article = articleService.getArticleById(articleId);
            
            if (article == null) {
                model.addAttribute("error", "Article non trouvé");
                return "redirect:/stock/articles/liste";
            }
            
            List<CategorieArticle> categories = articleService.getCategoriesActives();
            List<UniteMesure> unites = articleService.getUnitesMesure();
            List<Map<String, Object>> fournisseurs = articleService.getFournisseurs();
            
            // Récupérer les informations de stock pour l'affichage
            List<Map<String, Object>> stockParDepot = stockService.getStockParDepot(articleId);
            
            // Récupérer les lots si gestion par lot
            List<Lot> lots = new ArrayList<>();
            if (article.isGestionParLot()) {
                lots = articleService.getLotsArticle(articleId);
            }

            model.addAttribute("article", article);
            model.addAttribute("categories", categories);
            model.addAttribute("unites", unites);
            model.addAttribute("fournisseurs", fournisseurs);
            model.addAttribute("methodesValorisation", Arrays.asList("CUMP", "FIFO", "FEFO"));
            model.addAttribute("stockParDepot", stockParDepot);
            model.addAttribute("lots", lots);
            model.addAttribute("title", "Éditer Article: " + article.getCodeArticle());
            model.addAttribute("activePage", "stock-articles");
            model.addAttribute("isNew", false);

        } catch (IllegalArgumentException e) {
            log.error("ID article invalide: {}", id);
            model.addAttribute("error", "ID article invalide");
            return "redirect:/stock/articles/liste";
        } catch (Exception e) {
            log.error("Erreur lors du chargement du formulaire de modification", e);
            model.addAttribute("error", "Erreur lors du chargement du formulaire: " + e.getMessage());
            return "redirect:/stock/articles/liste";
        }

        return "stock/articles/formulaire";
    }

    /**
     * Sauvegarde un article (création ou modification)
     */
    @PostMapping("/sauvegarder")
    public String sauvegarderArticle(@RequestParam Map<String, String> params,
                                      HttpSession session,
                                      RedirectAttributes redirectAttributes) {
        
        // Vérification de la session
        UUID utilisateurId = (UUID) session.getAttribute("userId");
        if (utilisateurId == null) {
            return "redirect:/login";
        }

        try {
            String articleId = params.get("id");
            Article article;

            // Validation des champs requis
            if (params.get("codeArticle") == null || params.get("codeArticle").trim().isEmpty()) {
                throw new IllegalArgumentException("Le code article est requis");
            }
            if (params.get("libelle") == null || params.get("libelle").trim().isEmpty()) {
                throw new IllegalArgumentException("Le libellé est requis");
            }
            if (params.get("categorieId") == null || params.get("categorieId").trim().isEmpty()) {
                throw new IllegalArgumentException("La catégorie est requise");
            }
            if (params.get("uniteMesureId") == null || params.get("uniteMesureId").trim().isEmpty()) {
                throw new IllegalArgumentException("L'unité de mesure est requise");
            }

            if (articleId == null || articleId.isEmpty()) {
                // Création
                article = articleService.creerArticle(params, utilisateurId);
                redirectAttributes.addFlashAttribute("success", 
                        "Article créé avec succès: " + article.getCodeArticle());
                log.info("Article créé par utilisateur {}: {}", utilisateurId, article.getCodeArticle());
            } else {
                // Modification
                article = articleService.modifierArticle(UUID.fromString(articleId), params, utilisateurId);
                redirectAttributes.addFlashAttribute("success", 
                        "Article modifié avec succès: " + article.getCodeArticle());
                log.info("Article modifié par utilisateur {}: {}", utilisateurId, article.getCodeArticle());
            }

            return "redirect:/stock/articles/details/" + article.getId();

        } catch (IllegalArgumentException e) {
            log.error("Erreur de validation: {}", e.getMessage());
            redirectAttributes.addFlashAttribute("error", "Erreur de validation: " + e.getMessage());
            return "redirect:/stock/articles/nouveau";
        } catch (Exception e) {
            log.error("Erreur lors de la sauvegarde de l'article", e);
            redirectAttributes.addFlashAttribute("error", "Erreur lors de la sauvegarde: " + e.getMessage());
            return "redirect:/stock/articles/nouveau";
        }
    }

    /**
     * Vérifie la validité des données avant soumission (appel AJAX)
     */
    @PostMapping("/verifier")
    @ResponseBody
    public Map<String, Object> verifierArticle(@RequestParam Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        List<String> erreurs = new ArrayList<>();
        List<String> avertissements = new ArrayList<>();

        try {
            // Vérifier l'unicité du code article (sauf si c'est le même article)
            String codeArticle = params.get("codeArticle");
            String articleId = params.get("id");
            
            if (codeArticle != null && !codeArticle.trim().isEmpty()) {
                Article existing = null;
                try {
                    existing = articleRepository.findByCodeArticle(codeArticle).orElse(null);
                } catch (Exception e) {
                    // Ignorer, le repository peut ne pas être injecté ici
                }
                
                if (existing != null && (articleId == null || !existing.getId().toString().equals(articleId))) {
                    erreurs.add("Le code article '" + codeArticle + "' est déjà utilisé");
                }
            }

            // Vérifier la cohérence des seuils
            try {
                int min = Integer.parseInt(params.getOrDefault("stockMinimum", "0"));
                int sec = Integer.parseInt(params.getOrDefault("stockSecurite", "0"));
                String maxStr = params.get("stockMaximum");
                
                if (sec < min) {
                    avertissements.add("Le stock de sécurité devrait être supérieur au stock minimum");
                }
                
                if (maxStr != null && !maxStr.isEmpty()) {
                    int max = Integer.parseInt(maxStr);
                    if (max <= sec) {
                        avertissements.add("Le stock maximum devrait être supérieur au stock de sécurité");
                    }
                }
            } catch (NumberFormatException e) {
                // Ignorer les erreurs de conversion
            }

            result.put("valid", erreurs.isEmpty());
            result.put("erreurs", erreurs);
            result.put("avertissements", avertissements);

        } catch (Exception e) {
            log.error("Erreur lors de la vérification de l'article", e);
            result.put("valid", false);
            result.put("erreurs", Arrays.asList("Erreur lors de la vérification: " + e.getMessage()));
            result.put("avertissements", new ArrayList<>());
        }

        return result;
    }
}