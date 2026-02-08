// MouvementController.java
package com.gestion.stock.controller.mouvement;

import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.MovementType;
import com.gestion.stock.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import com.gestion.stock.entity.*;

@Controller
@RequestMapping("/stock/mouvements")
@RequiredArgsConstructor
@Slf4j
public class MouvementController {

    private final MouvementService mouvementService;
    private final ArticleService articleService;
    private final DepotService depotService;
    private final LotService lotService;
    private final StockMovementService stockMovementService;

    @GetMapping("/all")
    public String getAll(Model model){
        model.addAttribute("mvtStock", stockMovementService.getAll());
        return "home";
    }

    @GetMapping("{id}")
public String detailMouvement(@PathVariable UUID id, Model model) {
    StockMovement mvt = stockMovementService.findById(id);
    model.addAttribute("mvt", mvt);
    return "stock/mouvements/mouvement-detail";
}

    /**
     * Journal des mouvements
     */
    @GetMapping("/journal")
    public String journalMouvements(Model model,
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) String typeMouvement,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "dateMouvement") String tri,
            @RequestParam(defaultValue = "desc") String ordre) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        UUID typeMouvementUuid = null;
        UUID articleUuid = null;
        UUID depotUuid = null;

        try {
            if (typeMouvement != null && !typeMouvement.isEmpty()) {
                typeMouvementUuid = UUID.fromString(typeMouvement);
            }
            if (articleId != null && !articleId.isEmpty()) {
                articleUuid = UUID.fromString(articleId);
            }
            if (depotId != null && !depotId.isEmpty()) {
                depotUuid = UUID.fromString(depotId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("UUID invalide dans les paramètres de recherche", e);
        }

        // Configurer pagination
        Sort.Direction direction = "asc".equalsIgnoreCase(ordre) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));

        // Récupérer les mouvements
        Page<Map<String, Object>> pageMouvements = mouvementService.rechercherMouvements(
                typeMouvementUuid, articleUuid, depotUuid, dateDebut, dateFin, pageable);

        // Statistiques
        Map<String, Object> stats = mouvementService.getStatistiquesMouvements(
                dateDebut != null ? dateDebut : LocalDate.now().minusDays(30),
                dateFin != null ? dateFin : LocalDate.now());

        model.addAttribute("mouvements", pageMouvements);
        model.addAttribute("stats", stats);
        model.addAttribute("typesMouvement", mouvementService.getTypesMouvement());
        model.addAttribute("articles", articleService.getArticlesActifs());
        model.addAttribute("depots", depotService.getDepotsActifs());
        model.addAttribute("typeMouvement", typeMouvement);
        model.addAttribute("articleId", articleId);
        model.addAttribute("depotId", depotId);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("tri", tri);
        model.addAttribute("ordre", ordre);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", pageMouvements.getTotalPages());
        model.addAttribute("totalElements", pageMouvements.getTotalElements());

        model.addAttribute("title", "Journal des Mouvements");
        model.addAttribute("activePage", "stock-mouvements");

        return "stock/mouvements/journal";
    }

    /**
     * Formulaire d'entrée
     */
    @GetMapping("/entree")
    public String formulaireEntree(Model model,
            HttpSession session,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String bonReceptionId,
            @RequestParam(required = false) String bonCommandeId) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        List<Article> articles = articleService.getArticlesActifs();
        log.info("=== DEBUG ARTICLES ===");
        log.info("Nombre d'articles: {}", articles.size());

        for (Article article : articles) {
            log.info("Article: ID={} (type: {}), Code={}, Libellé={}",
                    article.getId(),
                    article.getId() != null ? article.getId().getClass().getName() : "null",
                    article.getCodeArticle(),
                    article.getLibelle());
        }

        // Types de mouvement d'entrée
        List<MovementType> typesEntree = mouvementService.getTypesMouvementEntree();

        // Si articleId fourni, pré-remplir
        if (articleId != null) {
            model.addAttribute("article", articleService.getArticleById(UUID.fromString(articleId)));
        }

        // Si depotId fourni, pré-remplir
        if (depotId != null) {
            model.addAttribute("depot", depotService.getDepotById(UUID.fromString(depotId)));
        }

        model.addAttribute("typesEntree", typesEntree);
        model.addAttribute("articles", articles);
        model.addAttribute("depots", depotService.getDepotsActifs());
        model.addAttribute("aujourdhui", LocalDate.now());

        // Si bon de réception fourni, récupérer les infos
        if (bonReceptionId != null) {
            Map<String, Object> detailsBR = mouvementService.getDetailsBonReception(bonReceptionId);
            model.addAttribute("bonReception", detailsBR);
        }

        model.addAttribute("title", "Entrée de Stock");
        model.addAttribute("activePage", "stock-mouvements");

        return "stock/mouvements/formulaire-entree";
    }

    /**
     * Formulaire de sortie
     */
    @GetMapping("/sortie")
    public String formulaireSortie(Model model,
            HttpSession session,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String lotId,
            @RequestParam(required = false) String commandeClientId,
            @RequestParam(required = false) String reservationId) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Types de mouvement de sortie
        List<MovementType> typesSortie = mouvementService.getTypesMouvementSortie();

        // Si articleId fourni
        if (articleId != null) {
            Map<String, Object> articleInfo = articleService.getDetailsArticle(UUID.fromString(articleId));
            model.addAttribute("article", articleInfo);

            // Lots disponibles pour cet article
            if (depotId != null) {
                List<Map<String, Object>> lots = lotService.getLotsDisponiblesArticle(UUID.fromString(articleId),
                        UUID.fromString(depotId));
                model.addAttribute("lots", lots);
            }
        }

        // Si lotId fourni
        if (lotId != null) {
            Map<String, Object> lotInfo = lotService.getDetailsLot(UUID.fromString(lotId));
            model.addAttribute("lot", lotInfo);
        }

        // Si réservation fournie
        if (reservationId != null) {
            Map<String, Object> reservation = mouvementService.getDetailsReservation(reservationId);
            model.addAttribute("reservation", reservation);
        }

        model.addAttribute("typesSortie", typesSortie);
        model.addAttribute("articles", articleService.getArticlesActifs());
        model.addAttribute("depots", depotService.getDepotsActifs());
        model.addAttribute("aujourdhui", LocalDate.now());

        model.addAttribute("title", "Sortie de Stock");
        model.addAttribute("activePage", "stock-mouvements");

        return "stock/mouvements/formulaire-sortie";
    }

    /**
     * Créer un mouvement d'entrée
     */
    @PostMapping("/creer-entree")
    public String creerEntree(@RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurUuid = (UUID) session.getAttribute("userId");
            Map<String, Object> resultat = mouvementService.creerMouvementEntree(params, utilisateurUuid);

            redirectAttributes.addFlashAttribute("success",
                    "Entrée créée: " + resultat.get("reference"));

            return "redirect:/stock/mouvements/details/" + resultat.get("id");

        } catch (Exception e) {
            log.error("Erreur création entrée", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/mouvements/entree";
        }
    }

    /**
     * Créer un mouvement de sortie
     */
    @PostMapping("/creer-sortie")
    public String creerSortie(@RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurUuid = (UUID) session.getAttribute("userId");
            Map<String, Object> resultat = mouvementService.creerMouvementSortie(params, utilisateurUuid);

            redirectAttributes.addFlashAttribute("success",
                    "Sortie créée: " + resultat.get("reference"));

            return "redirect:/stock/mouvements/details/" + resultat.get("id");

        } catch (Exception e) {
            log.error("Erreur création sortie", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/mouvements/sortie";
        }
    }

    /**
     * Détails d'un mouvement
     */
    @GetMapping("/details/{id}")
    public String detailsMouvement(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        Map<String, Object> mouvement = mouvementService.getDetailsMouvement(UUID.fromString(id));

        model.addAttribute("mouvement", mouvement);
        model.addAttribute("title", "Détails Mouvement: " + mouvement.get("reference"));
        model.addAttribute("activePage", "stock-mouvements");

        return "stock/mouvements/details";
    }

    /**
     * Annuler un mouvement
     */
    @PostMapping("/annuler/{id}")
    public String annulerMouvement(@PathVariable String id,
            @RequestParam String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurUuid = (UUID) session.getAttribute("userId");
            mouvementService.annulerMouvement(UUID.fromString(id), motif, utilisateurUuid);

            redirectAttributes.addFlashAttribute("success",
                    "Mouvement annulé");

        } catch (Exception e) {
            log.error("Erreur annulation mouvement", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/mouvements/details/" + id;
    }

    // MouvementController.java - Ajoutez ces méthodes

    /**
     * API pour rechercher des articles (pour modal)
     */
    @GetMapping("/api/articles/rechercher")
    @ResponseBody
    public Map<String, Object> rechercherArticlesAPI(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(required = false) String categorieId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID categorieUUID = null;
            if (categorieId != null && !categorieId.isEmpty()) {
                try {
                    categorieUUID = UUID.fromString(categorieId);
                } catch (IllegalArgumentException e) {
                    log.warn("UUID invalide pour categorieId: {}", categorieId);
                }
            }

            Pageable pageable = PageRequest.of(page, size);

            // Appeler le service existant
            Page<Article> pageArticles = articleService.rechercherArticles(
                    search,
                    categorieId,
                    true, // actifs seulement
                    null, // pas de filtre gestionParLot
                    pageable);

            // Transformer les articles en format API
            List<Map<String, Object>> articlesList = pageArticles.getContent().stream()
                    .map(article -> {
                        Map<String, Object> articleMap = new HashMap<>();
                        articleMap.put("id", article.getId());
                        articleMap.put("codeArticle", article.getCodeArticle());
                        articleMap.put("codeBarre", article.getCodeBarre());
                        articleMap.put("libelle", article.getLibelle());
                        articleMap.put("gestionParLot", article.isGestionParLot());
                        articleMap.put("gestionParSerie", article.isGestionParSerie());

                        if (article.getCategorie() != null) {
                            articleMap.put("categorieLibelle", article.getCategorie().getLibelle());
                        }

                        // Ajouter les infos de stock
                        Map<String, Object> stockInfo = articleService.getStockInfoForArticle(article.getId());
                        articleMap.put("quantiteDisponible", stockInfo.get("quantiteDisponible"));
                        articleMap.put("quantiteTheorique", stockInfo.get("quantiteTheorique"));
                        articleMap.put("hasStock", stockInfo.get("hasStock"));
                        articleMap.put("dateDernierMouvement", stockInfo.get("dateDernierMouvement"));

                        return articleMap;
                    })
                    .collect(Collectors.toList());

            response.put("content", articlesList);
            response.put("totalElements", pageArticles.getTotalElements());
            response.put("totalPages", pageArticles.getTotalPages());
            response.put("number", pageArticles.getNumber());
            response.put("size", pageArticles.getSize());
            response.put("first", pageArticles.isFirst());
            response.put("last", pageArticles.isLast());

        } catch (Exception e) {
            log.error("Erreur recherche articles", e);
            response.put("error", e.getMessage());
            response.put("content", new ArrayList<>());
            response.put("totalElements", 0);
        }

        return response;
    }

    /**
     * API pour auto-complétion des articles
     */
    @GetMapping("/api/articles/autocomplete")
    @ResponseBody
    public List<Map<String, Object>> autocompleteArticles(
            @RequestParam String search,
            @RequestParam(defaultValue = "10") int limit) {

        try {
            List<Article> articles = articleService.searchArticles(search, limit);

            return articles.stream()
                    .map(article -> {
                        Map<String, Object> articleMap = new HashMap<>();
                        articleMap.put("id", article.getId());
                        articleMap.put("code", article.getCodeArticle());
                        articleMap.put("libelle", article.getLibelle());
                        articleMap.put("categorie",
                                article.getCategorie() != null ? article.getCategorie().getLibelle() : "");
                        articleMap.put("gestionParLot", article.isGestionParLot());

                        // Obtenir stock disponible
                        Map<String, Object> stockInfo = articleService.getStockInfoForArticle(article.getId());
                        articleMap.put("stockDisponible", stockInfo.get("quantiteDisponible"));

                        return articleMap;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erreur autocomplete articles", e);
            return Collections.emptyList();
        }
    }

    /**
     * API pour rechercher des lots
     */
    @GetMapping("/api/lots/rechercher")
    @ResponseBody
    public List<Map<String, Object>> rechercherLotsAPI(
            @RequestParam(required = false) String numeroLot,
            @RequestParam(required = false) String articleSearch,
            @RequestParam(required = false) String depotId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        try {
            UUID depotUUID = null;
            if (depotId != null && !depotId.isEmpty()) {
                depotUUID = UUID.fromString(depotId);
            }

            UUID articleUUID = null;
            if (articleSearch != null && !articleSearch.isEmpty()) {
                // Essayer de trouver l'article par code ou libellé
                List<Article> articles = articleService.searchArticles(articleSearch, 5);
                if (!articles.isEmpty()) {
                    articleUUID = articles.get(0).getId();
                }
            }

            Pageable pageable = PageRequest.of(page, size, Sort.by("dateReception").descending());
            Page<Lot> pageLots = lotService.rechercherLots(
                    numeroLot,
                    articleUUID,
                    "DISPONIBLE",
                    depotUUID,
                    null, // prochePeremption
                    pageable);

            return pageLots.getContent().stream()
                    .map(lot -> {
                        Map<String, Object> lotMap = new HashMap<>();
                        lotMap.put("id", lot.getId());
                        lotMap.put("numeroLot", lot.getNumeroLot());
                        lotMap.put("quantiteActuelle", lot.getQuantiteActuelle());
                        lotMap.put("quantiteInitiale", lot.getQuantiteInitiale());
                        lotMap.put("datePeremption", lot.getDatePeremption());
                        lotMap.put("dateReception", lot.getDateReception());
                        lotMap.put("coutUnitaire", lot.getCoutUnitaire());
                        lotMap.put("statut", lot.getStatut().toString());

                        if (lot.getArticle() != null) {
                            lotMap.put("articleId", lot.getArticle().getId());
                            lotMap.put("articleCode", lot.getArticle().getCodeArticle());
                            lotMap.put("articleLibelle", lot.getArticle().getLibelle());
                        }

                        if (lot.getEmplacement() != null &&
                                lot.getEmplacement().getZone() != null &&
                                lot.getEmplacement().getZone().getDepot() != null) {
                            lotMap.put("depotId", lot.getEmplacement().getZone().getDepot().getId());
                            lotMap.put("depotNom", lot.getEmplacement().getZone().getDepot().getNom());
                        }

                        if (lot.getBonReception() != null) {
                            lotMap.put("bonReceptionRef", lot.getBonReception().toString());
                        }

                        // Calculer jours restants
                        if (lot.getDatePeremption() != null) {
                            LocalDate aujourdhui = LocalDate.now();
                            long joursRestants = ChronoUnit.DAYS.between(aujourdhui, lot.getDatePeremption());
                            lotMap.put("joursRestants", joursRestants);
                            lotMap.put("estPerime", joursRestants < 0);
                        }

                        return lotMap;
                    })
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Erreur recherche lots", e);
            return Collections.emptyList();
        }
    }

    /**
     * API pour les lots disponibles par article et dépôt
     */
    @GetMapping("/api/lots/disponibles")
    @ResponseBody
    public List<Map<String, Object>> getLotsDisponibles(
            @RequestParam String articleId,
            @RequestParam(required = false) String depotId) {

        try {
            UUID articleUUID = UUID.fromString(articleId);
            UUID depotUUID = null;
            if (depotId != null && !depotId.isEmpty()) {
                depotUUID = UUID.fromString(depotId);
            }

            List<Map<String, Object>> lots = lotService.getLotsDisponiblesArticle(articleUUID, depotUUID);
            return lots;

        } catch (Exception e) {
            log.error("Erreur récupération lots disponibles", e);
            return Collections.emptyList();
        }
    }

    /**
     * API pour vérifier le stock disponible
     */
    @GetMapping("/api/stock/disponible")
    @ResponseBody
    public Map<String, Object> verifierStockDisponible(
            @RequestParam String articleId,
            @RequestParam String depotId) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID articleUUID = UUID.fromString(articleId);
            UUID depotUUID = UUID.fromString(depotId);

            Map<String, Object> stockInfo = articleService.getStockInfoForArticle(articleUUID);

            // Pour être plus précis, vérifier le stock par dépôt
            // Vous devriez avoir une méthode spécifique pour cela
            response.put("articleId", articleId);
            response.put("depotId", depotId);
            response.put("quantiteDisponible", stockInfo.get("quantiteDisponible"));
            response.put("quantiteTheorique", stockInfo.get("quantiteTheorique"));
            response.put("hasStock", stockInfo.get("hasStock"));
            response.put("success", true);

        } catch (Exception e) {
            log.error("Erreur vérification stock", e);
            response.put("success", false);
            response.put("error", e.getMessage());
            response.put("quantiteDisponible", 0);
        }

        return response;
    }

    /**
     * API pour rechercher des réservations
     */
    @GetMapping("/api/reservations/rechercher")
    @ResponseBody
    public List<Map<String, Object>> rechercherReservationsAPI(
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) String articleSearch,
            @RequestParam(defaultValue = "ACTIVE") String statut) {

        try {
            // Cette méthode nécessite une implémentation complète
            // Pour l'instant, retourner une liste vide

            List<Map<String, Object>> reservations = new ArrayList<>();

            // Exemple de structure
            /*
             * Map<String, Object> res = new HashMap<>();
             * res.put("id", "123");
             * res.put("reference", "RES-2024-0001");
             * res.put("articleCode", "ART-001");
             * res.put("articleLibelle", "Article test");
             * res.put("depotNom", "Entrepôt Central");
             * res.put("quantiteReservee", 10);
             * res.put("quantitePrelevee", 0);
             * res.put("commandeClientRef", "CMD-001");
             * res.put("dateReservation", LocalDateTime.now().minusDays(1));
             * res.put("dateLivraisonPrevue", LocalDate.now().plusDays(2));
             * res.put("statut", "ACTIVE");
             * reservations.add(res);
             */

            return reservations;

        } catch (Exception e) {
            log.error("Erreur recherche réservations", e);
            return Collections.emptyList();
        }
    }
}