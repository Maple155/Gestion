// MouvementController.java
package com.gestion.stock.controller.mouvement;

import com.gestion.stock.entity.Article;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequestMapping("/stock/mouvements")
@RequiredArgsConstructor
@Slf4j
public class MouvementController {

    private final MouvementService mouvementService;
    private final ArticleService articleService;
    private final DepotService depotService;
    private final LotService lotService;

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
}