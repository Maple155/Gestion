package com.gestion.stock.controller.inventaires;

import com.gestion.stock.entity.*;
import com.gestion.stock.service.InventaireService;

import jakarta.servlet.http.HttpServletRequest;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock/inventaires")
@RequiredArgsConstructor
@Slf4j
public class InventaireController {

    private final InventaireService inventaireService;

    private boolean hasAnyRole(HttpSession session, String... roles) {
        String userRole = (String) session.getAttribute("userRole");
        if (userRole == null) return false;
        return Arrays.asList(roles).contains(userRole);
    }

    /**
     * Liste paginée des inventaires avec filtres
     */
    @GetMapping("/liste")
    public String listeInventaires(
            Model model,
            HttpSession session,
            HttpServletRequest request,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String reference,
            @RequestParam(required = false) Inventaire.TypeInventaire type,
            @RequestParam(required = false) Inventaire.StatutInventaire statut,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String responsableId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin,
            @RequestParam(defaultValue = "dateDebut") String tri,
            @RequestParam(defaultValue = "desc") String ordre) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MAGASINIER", 
        "MANAGER", "ADMIN", "COMPTABLE")) {
            return "redirect:/access-denied";
        }

        UUID userId = UUID.fromString(session.getAttribute("userId").toString());
        String userRole = session.getAttribute("userRole") != null ? session.getAttribute("userRole").toString()
                : "UTILISATEUR";

        // Pagination avec tri
        Sort.Direction direction = "asc".equalsIgnoreCase(ordre) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));

        // Récupération filtrée
        Page<Inventaire> pageInventaires = inventaireService.getInventairesFiltres(
                reference, type, statut, depotId, responsableId, dateDebut, dateFin, pageable);

        // Calcul statistiques
        Map<String, Object> stats = inventaireService.getStatistiquesInventaires();

        // Inventaires en cours pour notification
        List<Inventaire> inventairesEnCours = inventaireService.getInventairesEnCours();

        model.addAttribute("inventaires", pageInventaires);
        model.addAttribute("stats", stats);
        model.addAttribute("inventairesEnCours", inventairesEnCours);

        // Filtres pour réaffichage
        model.addAttribute("reference", reference);
        model.addAttribute("type", type);
        model.addAttribute("statut", statut);
        model.addAttribute("depotId", depotId);
        model.addAttribute("responsableId", responsableId);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("tri", tri);
        model.addAttribute("ordre", ordre);

        // Pagination
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", pageInventaires.getTotalPages());
        model.addAttribute("totalElements", pageInventaires.getTotalElements());

        // Types et statuts pour selects
        model.addAttribute("typesInventaire", Inventaire.TypeInventaire.values());
        model.addAttribute("statutsInventaire", Inventaire.StatutInventaire.values());

        model.addAttribute("request", request);

        model.addAttribute("title", "Inventaires Physiques");
        model.addAttribute("activePage", "stock-inventaires");

        return "stock/inventaires/liste";
    }

    /**
     * Formulaire création inventaire
     */
    @GetMapping("/nouveau")
    public String formulaireNouvelInventaire(
            Model model,
            HttpSession session,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String categorieId) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            model.addAttribute("error", "Vous n'avez pas les permissions nécessaires");
            return "redirect:/stock/inventaires/liste";
        }

        UUID userId = UUID.fromString(session.getAttribute("userId").toString());
        String userRole = session.getAttribute("userRole") != null ? session.getAttribute("userRole").toString()
                : "UTILISATEUR";

        // Vérifier permission
        if (!userRole.equals("RESPONSABLE_STOCK") && !userRole.equals("MANAGER") && !userRole.equals("ADMIN")) {
            model.addAttribute("error", "Vous n'avez pas les permissions nécessaires");
            return "redirect:/stock/inventaires/liste";
        }

        // Pré-remplir si paramètres fournis
        if (depotId != null)
            model.addAttribute("depotId", depotId);
        if (zoneId != null)
            model.addAttribute("zoneId", zoneId);
        if (categorieId != null)
            model.addAttribute("categorieId", categorieId);

        // Données pour les selects
        model.addAttribute("types", Inventaire.TypeInventaire.values());
        model.addAttribute("depots", inventaireService.getDepotsActifs());
        model.addAttribute("zones", inventaireService.getZonesStockage());
        model.addAttribute("categories", inventaireService.getCategoriesArticles());

        // Date par défaut
        model.addAttribute("dateDebutDefault", LocalDate.now());
        model.addAttribute("dateFinDefault", LocalDate.now().plusDays(7));

        model.addAttribute("title", "Nouvel Inventaire");
        model.addAttribute("activePage", "stock-inventaires");

        return "stock/inventaires/nouveau";
    }

    /**
     * Créer un inventaire
     */
    @PostMapping("/creer")
    public String creerInventaire(
            @RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            // Extraction des paramètres
            String typeStr = params.get("type");
            String depotId = params.get("depotId");
            String zoneId = params.get("zoneId");
            String categorieId = params.get("categorieId");
            LocalDate dateDebut = LocalDate.parse(params.get("dateDebut"));

            LocalDate dateFin = null;
            if (params.get("dateFin") != null && !params.get("dateFin").isEmpty()) {
                dateFin = LocalDate.parse(params.get("dateFin"));
            }

            String observations = params.get("observations");
            String modeSaisie = params.get("modeSaisie"); // MANUEL, PAR_LOT, PAR_EMP

            // Création de l'inventaire
            Inventaire inventaire = inventaireService.creerInventaire(
                    Inventaire.TypeInventaire.valueOf(typeStr),
                    depotId != null && !depotId.isEmpty() ? UUID.fromString(depotId) : null,
                    zoneId != null && !zoneId.isEmpty() ? UUID.fromString(zoneId) : null,
                    categorieId != null && !categorieId.isEmpty() ? UUID.fromString(categorieId) : null,
                    dateDebut,
                    dateFin,
                    utilisateurId,
                    observations,
                    modeSaisie);

            redirectAttributes.addFlashAttribute("success",
                    "Inventaire créé: " + inventaire.getReference() +
                            " - " + inventaire.getNombreArticlesComptes() + " articles à compter");

            return "redirect:/stock/inventaires/details/" + inventaire.getId();

        } catch (Exception e) {
            log.error("Erreur création inventaire", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/inventaires/nouveau";
        }
    }

    /**
     * Détails d'un inventaire avec pagination des lignes
     */
    @GetMapping("/details/{id}")
    public String detailsInventaire(
            @PathVariable String id,
            Model model,
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(required = false) LigneInventaire.StatutLigneInventaire statutLigne,
            @RequestParam(required = false) Boolean avecEcart) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        UUID userId = UUID.fromString(session.getAttribute("userId").toString());

        // Récupérer l'inventaire
        Inventaire inventaire = inventaireService.getInventaireById(UUID.fromString(id));
        if (inventaire == null) {
            return "redirect:/stock/inventaires/liste";
        }

        // Récupérer les lignes paginées
        Pageable pageable = PageRequest.of(page, size);
        Page<LigneInventaire> lignesPage = inventaireService.getLignesInventairePaginees(
                UUID.fromString(id), statutLigne, avecEcart, pageable);

        // Statistiques de l'inventaire
        Map<String, Object> stats = inventaireService.getStatistiquesInventaire(UUID.fromString(id));

        // Récupérer les ajustements
        List<AjustementInventaire> ajustements = inventaireService.getAjustementsInventaire(UUID.fromString(id));

        // Calculer les totaux manquants
        calculerTotauxInventaire(model, lignesPage.getContent(), inventaire);

        model.addAttribute("inventaire", inventaire);
        model.addAttribute("lignes", lignesPage.getContent());
        model.addAttribute("stats", stats);
        model.addAttribute("ajustements", ajustements);

        // Pagination
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", lignesPage.getTotalPages());
        model.addAttribute("totalElements", lignesPage.getTotalElements());

        // Filtres
        model.addAttribute("statutLigne", statutLigne);
        model.addAttribute("avecEcart", avecEcart);

        // Statuts pour select
        model.addAttribute("statutsLigne", LigneInventaire.StatutLigneInventaire.values());

        // Permissions
        String userRole = session.getAttribute("userRole") != null ? session.getAttribute("userRole").toString()
                : "UTILISATEUR";
        model.addAttribute("canEdit", canEditInventaire(inventaire, userRole));
        model.addAttribute("canClose", canCloseInventaire(userRole));
        model.addAttribute("canCancel", canCancelInventaire(userRole));

        model.addAttribute("title", "Inventaire " + inventaire.getReference());
        model.addAttribute("activePage", "stock-inventaires");

        return "stock/inventaires/details";
    }

    /**
     * Méthode utilitaire pour calculer les totaux de l'inventaire
     */
    private void calculerTotauxInventaire(Model model, List<LigneInventaire> lignes, Inventaire inventaire) {
        BigDecimal valeurTotaleTheorique = BigDecimal.ZERO;
        BigDecimal valeurTotaleComptee = BigDecimal.ZERO;
        BigDecimal valeurTotaleEcart = BigDecimal.ZERO;

        int lignesAvecEcart = 0;
        int lignesComptees = 0;
        int lignesValidees = 0;
        int lignesExclues = 0;

        for (LigneInventaire ligne : lignes) {
            // Calcul des valeurs monétaires
            if (ligne.getCoutUnitaire() != null) {
                // Valeur théorique
                if (ligne.getQuantiteTheorique() != null) {
                    valeurTotaleTheorique = valeurTotaleTheorique.add(
                            ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ligne.getQuantiteTheorique())));
                }

                // Valeur comptée
                if (ligne.getQuantiteCompteeFinale() != null) {
                    valeurTotaleComptee = valeurTotaleComptee.add(
                            ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ligne.getQuantiteCompteeFinale())));
                }

                // Valeur écart
                if (ligne.getEcartValeur() != null) {
                    valeurTotaleEcart = valeurTotaleEcart.add(ligne.getEcartValeur());
                }
            }

            // Statistiques lignes
            if (ligne.getEcart() != null && ligne.getEcart() != 0) {
                lignesAvecEcart++;
            }

            if (ligne.getQuantiteCompteeFinale() != null) {
                lignesComptees++;
            }

            if (ligne.getStatut() == LigneInventaire.StatutLigneInventaire.VALIDE ||
                    ligne.getStatut() == LigneInventaire.StatutLigneInventaire.AJUSTE) {
                lignesValidees++;
            }

            if (ligne.getStatut() == LigneInventaire.StatutLigneInventaire.EXCLU) {
                lignesExclues++;
            }
        }

        // Calcul de la précision
        BigDecimal precision = BigDecimal.ZERO;
        if (valeurTotaleTheorique.compareTo(BigDecimal.ZERO) > 0) {
            precision = BigDecimal.ONE.subtract(
                    valeurTotaleEcart.abs().divide(valeurTotaleTheorique, 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100));
        }

        model.addAttribute("valeurTotaleTheorique", valeurTotaleTheorique);
        model.addAttribute("valeurTotaleComptee", valeurTotaleComptee);
        model.addAttribute("valeurTotaleEcart", valeurTotaleEcart);
        model.addAttribute("precision", precision);
        model.addAttribute("lignesAvecEcart", lignesAvecEcart);
        model.addAttribute("lignesComptees", lignesComptees);
        model.addAttribute("lignesValidees", lignesValidees);
        model.addAttribute("lignesExclues", lignesExclues);
        model.addAttribute("lignesTotal", lignes.size());
    }

    /**
     * Vérifier les permissions d'édition
     */
    private boolean canEditInventaire(Inventaire inventaire, String userRole) {
        if (inventaire.getStatut() != Inventaire.StatutInventaire.EN_COURS) {
            return false;
        }

        return userRole.equals("RESPONSABLE_STOCK") ||
                userRole.equals("MANAGER") ||
                userRole.equals("ADMIN");
    }

    /**
     * Vérifier les permissions de clôture
     */
    private boolean canCloseInventaire(String userRole) {
        return userRole.equals("RESPONSABLE_STOCK") ||
                userRole.equals("MANAGER") ||
                userRole.equals("ADMIN");
    }

    /**
     * Vérifier les permissions d'annulation
     */
    private boolean canCancelInventaire(String userRole) {
        return userRole.equals("MANAGER") || userRole.equals("ADMIN");
    }

    /**
     * Interface de comptage (optimisée pour mobile/tablette)
     */
    @GetMapping("/comptage/{inventaireId}")
    public String interfaceComptage(
            @PathVariable String inventaireId,
            Model model,
            HttpSession session,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String emplacementId,
            @RequestParam(defaultValue = "false") boolean showOnlyPending) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
        "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }

        UUID userId = UUID.fromString(session.getAttribute("userId").toString());

        // Récupérer l'inventaire
        Inventaire inventaire = inventaireService.getInventaireById(UUID.fromString(inventaireId));
        if (inventaire == null || !inventaire.getStatut().equals(Inventaire.StatutInventaire.EN_COURS)) {
            model.addAttribute("error", "Inventaire non disponible pour comptage");
            return "redirect:/stock/inventaires/details/" + inventaireId;
        }

        // Récupérer la prochaine ligne à compter
        LigneInventaire prochaineLigne = inventaireService.getProchaineLigneACompter(
                UUID.fromString(inventaireId), userId);

        // Ou une ligne spécifique si demandée
        if (articleId != null && emplacementId != null) {
            LigneInventaire ligneSpecifique = inventaireService.getLigneInventaire(
                    UUID.fromString(inventaireId),
                    UUID.fromString(articleId),
                    UUID.fromString(emplacementId));

            if (ligneSpecifique != null) {
                prochaineLigne = ligneSpecifique;
            }
        }

        model.addAttribute("inventaire", inventaire);
        model.addAttribute("ligne", prochaineLigne);
        model.addAttribute("userId", userId);
        model.addAttribute("showOnlyPending", showOnlyPending);

        // Statistiques de progression
        Map<String, Object> progression = inventaireService.getProgressionComptage(UUID.fromString(inventaireId),
                userId);
        model.addAttribute("progression", progression);

        model.addAttribute("title", "Comptage Inventaire " + inventaire.getReference());
        model.addAttribute("activePage", "stock-comptage");

        return "stock/inventaires/comptage";
    }

    /**
     * Enregistrer un comptage (AJAX)
     */
    @PostMapping("/enregistrer-comptage")
    @ResponseBody
    public Map<String, Object> enregistrerComptage(
            @RequestBody Map<String, String> data,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
            "MANAGER", "ADMIN")) {
            response.put("success", false);
            response.put("message", "Permission refusée");
            return response;
            }

            String ligneId = data.get("ligneId");
            Integer quantite = Integer.parseInt(data.get("quantite"));
            boolean estRecomptage = Boolean.parseBoolean(data.get("estRecomptage"));
            String observations = data.get("observations");
            String codeBarreScanner = data.get("codeBarreScanner");

            // Enregistrement du comptage
            LigneInventaire ligne = inventaireService.enregistrerComptage(
                    UUID.fromString(ligneId),
                    quantite,
                    utilisateurId,
                    estRecomptage,
                    observations,
                    codeBarreScanner);

            // Vérifier si besoin de recomptage automatique
            boolean necessiteRecomptage = false;
            if (!estRecomptage && ligne.getEcart() != null) {
                BigDecimal valeurEcart = ligne.getEcartValeur() != null ? ligne.getEcartValeur().abs()
                        : BigDecimal.ZERO;

                // Règle métier : si écart > 10% ou valeur > 1000€ → recomptage
                if (Math.abs(ligne.getEcart()) > ligne.getQuantiteTheorique() * 0.1 ||
                        valeurEcart.compareTo(new BigDecimal("1000")) > 0) {
                    necessiteRecomptage = true;
                }
            }

            response.put("success", true);
            response.put("message", "Comptage enregistré");
            response.put("ligne", ligne);
            response.put("necessiteRecomptage", necessiteRecomptage);
            response.put("inventaireId", ligne.getInventaire().getId());

            // Prochaine ligne pour chainage automatique
            // CORRECTION ICI : Utiliser getOrDefault au lieu de get avec deux paramètres
            String autoNextValue = data.getOrDefault("autoNext", "false");
            if (Boolean.parseBoolean(autoNextValue)) {
                LigneInventaire prochaineLigne = inventaireService.getProchaineLigneACompter(
                        ligne.getInventaire().getId(), utilisateurId);
                response.put("prochaineLigne", prochaineLigne);
            }

        } catch (Exception e) {
            log.error("Erreur enregistrement comptage", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Démarrer un inventaire
     */
    @PostMapping("/demarrer/{id}")
    public String demarrerInventaire(
            @PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            Inventaire inventaire = inventaireService.demarrerInventaire(
                    UUID.fromString(id), utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Inventaire démarré: " + inventaire.getReference());

        } catch (Exception e) {
            log.error("Erreur démarrage inventaire", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/inventaires/details/" + id;
    }

    /**
     * Valider une ligne individuellement
     */
    @PostMapping("/valider-ligne")
    public String validerLigne(
            @RequestParam String ligneId,
            @RequestParam(required = false) String quantiteFinale,
            @RequestParam(required = false) String causeEcart,
            @RequestParam(required = false) String decision, // AJUSTER, EXCLURE, ACCEPTER
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error", "Permission refusée");
                return "redirect:/stock/inventaires/liste";
            }

            LigneInventaire ligne = inventaireService.validerLigne(
                    UUID.fromString(ligneId),
                    quantiteFinale != null ? Integer.parseInt(quantiteFinale) : null,
                    causeEcart,
                    decision,
                    utilisateurId);

            UUID inventaireId = ligne.getInventaire().getId();

            redirectAttributes.addFlashAttribute("success",
                    "Ligne validée et " +
                            ("AJUSTER".equals(decision) ? "ajustement demandé"
                                    : "EXCLURE".equals(decision) ? "exclue" : "acceptée"));

            return "redirect:/stock/inventaires/details/" + inventaireId + "?page=0";

        } catch (Exception e) {
            log.error("Erreur validation ligne", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());

            // Essayer de récupérer l'ID de l'inventaire
            try {
                UUID inventaireId = inventaireService.getInventaireIdByLigneId(UUID.fromString(ligneId));
                return "redirect:/stock/inventaires/details/" + inventaireId;
            } catch (Exception ex) {
                return "redirect:/stock/inventaires/liste";
            }
        }
    }

    /**
     * Valider plusieurs lignes en batch
     */
    @PostMapping("/valider-batch")
    @ResponseBody
    public Map<String, Object> validerLignesBatch(
            @RequestBody Map<String, Object> data,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            @SuppressWarnings("unchecked")
            List<String> ligneIds = (List<String>) data.get("ligneIds");
            String decision = (String) data.get("decision");
            String causeEcart = (String) data.get("causeEcart");

            List<UUID> ids = ligneIds.stream()
                    .map(UUID::fromString)
                    .collect(Collectors.toList());

            int nbValidees = inventaireService.validerLignesBatch(ids, decision, causeEcart, utilisateurId);

            response.put("success", true);
            response.put("message", nbValidees + " ligne(s) validée(s)");
            response.put("nombreValidees", nbValidees);

        } catch (Exception e) {
            log.error("Erreur validation batch", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Créer un ajustement pour une ligne
     */
    @PostMapping("/creer-ajustement")
    public String creerAjustement(
            @RequestParam String ligneId,
            @RequestParam Integer quantiteAjustee,
            @RequestParam String motif,
            @RequestParam(required = false) String justification,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            AjustementInventaire ajustement = inventaireService.creerAjustement(
                    UUID.fromString(ligneId),
                    quantiteAjustee,
                    motif,
                    justification,
                    utilisateurId);

            UUID inventaireId = inventaireService.getInventaireIdByLigneId(UUID.fromString(ligneId));

            redirectAttributes.addFlashAttribute("success",
                    "Ajustement créé (valeur: " + ajustement.getValeurAjustement() + " €)");

            return "redirect:/stock/inventaires/details/" + inventaireId;

        } catch (Exception e) {
            log.error("Erreur création ajustement", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());

            try {
                UUID inventaireId = inventaireService.getInventaireIdByLigneId(UUID.fromString(ligneId));
                return "redirect:/stock/inventaires/details/" + inventaireId;
            } catch (Exception ex) {
                return "redirect:/stock/inventaires/liste";
            }
        }
    }

    /**
     * Clôturer un inventaire
     */
    @PostMapping("/cloturer/{id}")
    public String cloturerInventaire(
            @PathVariable String id,
            @RequestParam(required = false) String motifCloture,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());
            
            if (!hasAnyRole(session, "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error",
                        "Permission refusée. Seuls les responsables peuvent clôturer un inventaire");
                return "redirect:/stock/inventaires/details/" + id;
            }

            String userRole = session.getAttribute("userRole") != null ? session.getAttribute("userRole").toString()
                    : "UTILISATEUR";

            // Vérifier permission (seulement responsables)
            if (!userRole.equals("RESPONSABLE_STOCK") && !userRole.equals("MANAGER") && !userRole.equals("ADMIN")) {
                redirectAttributes.addFlashAttribute("error",
                        "Permission refusée. Seuls les responsables peuvent clôturer un inventaire");
                return "redirect:/stock/inventaires/details/" + id;
            }

            Inventaire inventaire = inventaireService.cloturerInventaire(
                    UUID.fromString(id), utilisateurId, motifCloture);

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
     * Annuler un inventaire
     */
    @PostMapping("/annuler/{id}")
    public String annulerInventaire(
            @PathVariable String id,
            @RequestParam String motifAnnulation,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());
            
            if (!hasAnyRole(session, "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error",
                        "Permission refusée. Seuls les managers et administrateurs peuvent annuler un inventaire");
                return "redirect:/stock/inventaires/details/" + id;
            }

            String userRole = session.getAttribute("userRole") != null ? session.getAttribute("userRole").toString()
                    : "UTILISATEUR";

            // Vérifier permission (admin ou manager seulement)
            if (!userRole.equals("MANAGER") && !userRole.equals("ADMIN")) {
                redirectAttributes.addFlashAttribute("error",
                        "Permission refusée. Seuls les managers et administrateurs peuvent annuler un inventaire");
                return "redirect:/stock/inventaires/details/" + id;
            }

            Inventaire inventaire = inventaireService.annulerInventaire(
                    UUID.fromString(id), utilisateurId, motifAnnulation);

            redirectAttributes.addFlashAttribute("success",
                    "Inventaire annulé: " + inventaire.getReference());

        } catch (Exception e) {
            log.error("Erreur annulation inventaire", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/inventaires/details/" + id;
    }

    /**
     * Générer rapport d'inventaire (PDF/Excel)
     */
    @GetMapping("/rapport/{id}")
    public String rapportInventaire(
            @PathVariable String id,
            @RequestParam(required = false) String format,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            String fichier = inventaireService.genererRapportInventaire(
                    UUID.fromString(id),
                    format != null ? format : "PDF");

            if ("HTML".equals(format)) {
                // Retourner la vue HTML
                Inventaire inventaire = inventaireService.getInventaireById(UUID.fromString(id));
                List<LigneInventaire> lignes = inventaireService.getLignesInventaire(UUID.fromString(id));
                Map<String, Object> stats = inventaireService.getStatistiquesInventaire(UUID.fromString(id));

                Model model = null;
                // Ces lignes nécessitent d'être dans une méthode avec Model paramètre
                // model.addAttribute("inventaire", inventaire);
                // model.addAttribute("lignes", lignes);
                // model.addAttribute("stats", stats);
                // model.addAttribute("title", "Rapport Inventaire " +
                // inventaire.getReference());

                return "stock/inventaires/rapport-html";
            } else {
                // Pour PDF/Excel, rediriger vers le fichier
                redirectAttributes.addFlashAttribute("success",
                        "Rapport généré: " + fichier);
                redirectAttributes.addFlashAttribute("fichierRapport", fichier);

                return "redirect:/stock/inventaires/details/" + id;
            }

        } catch (Exception e) {
            log.error("Erreur génération rapport", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur génération rapport: " + e.getMessage());
            return "redirect:/stock/inventaires/details/" + id;
        }
    }

    /**
     * Exporter les lignes d'inventaire (CSV/Excel)
     */
    @GetMapping("/export-lignes/{inventaireId}")
    @ResponseBody
    public Map<String, Object> exporterLignesInventaire(
            @PathVariable String inventaireId,
            @RequestParam String format,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (session.getAttribute("userId") == null) {
                response.put("success", false);
                response.put("message", "Non authentifié");
                return response;
            }

            String fichier = inventaireService.exporterLignesInventaire(
                    UUID.fromString(inventaireId), format);

            response.put("success", true);
            response.put("fichier", fichier);
            response.put("message", "Export terminé");

        } catch (Exception e) {
            log.error("Erreur export lignes inventaire", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Scanner pour inventaire (API pour mobile)
     */
    @PostMapping("/scanner")
    @ResponseBody
    public Map<String, Object> scannerInventaire(
            @RequestBody Map<String, String> data,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            String inventaireId = data.get("inventaireId");
            String codeBarre = data.get("codeBarre");
            String typeScan = data.get("typeScan"); // ARTICLE, LOT, EMPLACEMENT

            Map<String, Object> resultatScan = inventaireService.scannerPourInventaire(
                    UUID.fromString(inventaireId), codeBarre, typeScan, utilisateurId);

            response.put("success", true);
            response.put("data", resultatScan);

        } catch (Exception e) {
            log.error("Erreur scan inventaire", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Synchroniser les données d'inventaire (pour travail hors ligne)
     */
    @PostMapping("/synchroniser/{inventaireId}")
    @ResponseBody
    public Map<String, Object> synchroniserInventaire(
            @PathVariable String inventaireId,
            @RequestBody List<Map<String, Object>> donneesHorsLigne,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            int nbSynchronises = inventaireService.synchroniserDonneesHorsLigne(
                    UUID.fromString(inventaireId), donneesHorsLigne, utilisateurId);

            response.put("success", true);
            response.put("message", nbSynchronises + " comptage(s) synchronisé(s)");
            response.put("nombreSynchronises", nbSynchronises);

        } catch (Exception e) {
            log.error("Erreur synchronisation inventaire", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Dashboard des inventaires
     */
    @GetMapping("/dashboard")
    public String dashboardInventaires(
            Model model,
            HttpSession session,
            @RequestParam(required = false) Integer mois,
            @RequestParam(required = false) Integer annee) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        UUID userId = UUID.fromString(session.getAttribute("userId").toString());

        // Par défaut mois/année courants
        if (mois == null)
            mois = LocalDate.now().getMonthValue();
        if (annee == null)
            annee = LocalDate.now().getYear();

        // Statistiques globales
        Map<String, Object> statsGlobales = inventaireService.getStatistiquesGlobales(mois, annee);

        // Top 5 écarts
        List<Map<String, Object>> topEcarts = inventaireService.getTopEcartsInventaire(mois, annee);

        // Évolution mensuelle
        Map<String, Object> evolution = inventaireService.getEvolutionInventaires(annee);

        // Inventaires récents
        List<Inventaire> inventairesRecents = inventaireService.getInventairesRecents();

        model.addAttribute("statsGlobales", statsGlobales);
        model.addAttribute("topEcarts", topEcarts);
        model.addAttribute("evolution", evolution);
        model.addAttribute("inventairesRecents", inventairesRecents);
        model.addAttribute("mois", mois);
        model.addAttribute("annee", annee);
        model.addAttribute("moisCourant", LocalDate.now().getMonthValue());
        model.addAttribute("anneeCourante", LocalDate.now().getYear());

        model.addAttribute("title", "Dashboard Inventaires");
        model.addAttribute("activePage", "stock-dashboard");

        return "stock/inventaires/dashboard";
    }
}