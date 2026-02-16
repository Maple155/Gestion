package com.gestion.stock.controller.lots;

import com.gestion.achat.entity.BonReception;
import com.gestion.achat.service.BonReceptionService;
import com.gestion.stock.dto.LotDTO;
import com.gestion.stock.dto.LotSearchCriteria;
import com.gestion.stock.dto.SerieDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.LotRepository;
import com.gestion.stock.repository.SerieRepository;
import com.gestion.stock.service.*;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
// import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/lots")
@RequiredArgsConstructor
public class LotController {

    private final LotService lotService;
    private final ArticleService articleService;
    private final DepotService depotService;
    private final EmplacementService emplacementService;
    private final BonReceptionService bonReceptionService;
    private final NotificationService notificationService;
    private final LotRepository lotRepository;
    private final SerieRepository serieRepository;

    private boolean hasAnyRole(HttpSession session, String... roles) {
        String userRole = (String) session.getAttribute("userRole");
        if (userRole == null)
            return false;
        return Arrays.asList(roles).contains(userRole);
    }

    // ========== PAGE : Liste des lots ==========
    @GetMapping
    // @PreAuthorize("hasAnyRole('GESTIONNAIRE_STOCK', 'RESPONSABLE_STOCK',
    // 'COMPTABLE', 'MANAGER')")
    public String listLots(
            @RequestParam(required = false) String numeroLot,
            @RequestParam(required = false) UUID articleId,
            @RequestParam(required = false) UUID depotId,
            @RequestParam(required = false) UUID zoneId,
            @RequestParam(required = false) UUID emplacementId,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePeremptionFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePeremptionTo,
            @RequestParam(required = false) Boolean prochePeremption,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "dateReception") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            HttpSession session,
            Model model) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Tous les rôles stock peuvent voir les lots
        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MAGASINIER",
                "MANAGER", "ADMIN", "COMPTABLE")) {
            return "redirect:/access-denied";
        }

        // Créer Pageable
        Sort sort = Sort.by(Sort.Direction.fromString(sortDir), sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Créer critères de recherche
        LotSearchCriteria criteria = new LotSearchCriteria();
        criteria.setNumeroLot(numeroLot);
        criteria.setArticleId(articleId);
        criteria.setDepotId(depotId);
        criteria.setZoneId(zoneId);
        criteria.setEmplacementId(emplacementId);
        if (statut != null && !statut.isEmpty()) {
            criteria.setStatut(Lot.LotStatus.valueOf(statut));
        }
        criteria.setDatePeremptionFrom(datePeremptionFrom);
        criteria.setDatePeremptionTo(datePeremptionTo);
        criteria.setProchePeremption(prochePeremption);

        // Rechercher les lots
        Page<Lot> lotsPage = lotService.searchLots(criteria, pageable);

        // Charger les données de référence pour les filtres
        loadReferenceData(model);

        // Ajouter les statistiques
        Map<String, Object> stats = lotService.getLotStatistics();
        model.addAttribute("stats", stats);

        // Ajouter les paramètres au modèle
        model.addAttribute("lots", lotsPage);
        model.addAttribute("numeroLot", numeroLot);
        model.addAttribute("articleId", articleId);
        model.addAttribute("depotId", depotId);
        model.addAttribute("statut", statut);
        model.addAttribute("datePeremptionFrom", datePeremptionFrom);
        model.addAttribute("datePeremptionTo", datePeremptionTo);
        model.addAttribute("prochePeremption", prochePeremption);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", lotsPage.getTotalPages());
        model.addAttribute("totalItems", lotsPage.getTotalElements());
        model.addAttribute("sortBy", sortBy);
        model.addAttribute("sortDir", sortDir);

        // Calculer les jours restants pour chaque lot
        Map<UUID, Long> joursRestantsMap = new HashMap<>();
        for (Lot lot : lotsPage.getContent()) {
            if (lot.getDatePeremption() != null) {
                long jours = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), lot.getDatePeremption());
                joursRestantsMap.put(lot.getId(), jours);
            }
        }
        model.addAttribute("joursRestantsMap", joursRestantsMap);

        return "stock/lots/list";
    }

    // ========== PAGE : Détail d'un lot ==========
    @GetMapping("/{id}")
    // @PreAuthorize("hasAnyRole('GESTIONNAIRE_STOCK', 'RESPONSABLE_STOCK',
    // 'COMPTABLE', 'MANAGER')")
    public String viewLot(@PathVariable UUID id, Model model) {
        Lot lot = lotService.getLotById(id);

        // Charger l'historique des mouvements du lot
        List<StockMovement> mouvements = lotService.findByLotId(id);

        // Calculer les statistiques
        long joursRestants = 0;
        if (lot.getDatePeremption() != null) {
            joursRestants = java.time.temporal.ChronoUnit.DAYS.between(
                    LocalDate.now(), lot.getDatePeremption());
        }

        model.addAttribute("lot", lot);
        model.addAttribute("mouvements", mouvements);
        model.addAttribute("joursRestants", joursRestants);
        model.addAttribute("valeurStock", lot.getCoutUnitaire()
                .multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));

        return "stock/lots/detail";
    }

    // ========== PAGE : Créer un lot ==========
    @GetMapping("/nouveau")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String showCreateForm(
            @RequestParam(required = false) UUID bonReceptionId,
            HttpSession session,
            Model model) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Seuls GESTIONNAIRE, RESPONSABLE, MANAGER, ADMIN peuvent créer des lots
        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }

        LotDTO.CreateLotDTO lotDTO = new LotDTO.CreateLotDTO();

        // Si un bon de réception est spécifié, pré-remplir certaines infos
        if (bonReceptionId != null) {
            BonReception bonReception = bonReceptionService.findById(bonReceptionId)
                    .orElseThrow(() -> new IllegalArgumentException("Bon de réception non trouvé"));

            // Ici vous pourriez pré-remplir certaines informations
            lotDTO.setBonReceptionId(bonReceptionId);
            model.addAttribute("bonReception", bonReception);
        }

        loadReferenceData(model);
        model.addAttribute("lotDTO", lotDTO);
        model.addAttribute("today", LocalDate.now());

        return "stock/lots/create";
    }

    @PostMapping("/nouveau")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String createLot(
            @ModelAttribute("lotDTO") LotDTO lotDTO,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Permission refusée");
            return "redirect:/lots";
        }

        try {
            Lot lot = lotService.createLot(lotDTO);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot créé avec succès: " + lot.getNumeroLot());

            return "redirect:/lots/" + lot.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la création du lot: " + e.getMessage());
            return "redirect:/lots/nouveau";
        }
    }

    // ========== PAGE : Modifier un lot ==========
    @GetMapping("/{id}/modifier")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String showEditForm(@PathVariable UUID id, Model model, HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }

        Lot lot = lotService.getLotById(id);

        // Convertir Lot en DTO pour le formulaire
        LotDTO.UpdateLotDTO lotDTO = new LotDTO.UpdateLotDTO();
        lotDTO.setQuantiteActuelle(lot.getQuantiteActuelle());
        lotDTO.setDatePeremption(lot.getDatePeremption());
        lotDTO.setStatut(lot.getStatut().name());
        lotDTO.setCertificatConformite(lot.getCertificatConformite());
        if (lot.getEmplacement() != null) {
            lotDTO.setEmplacementId(lot.getEmplacement().getId());
        }

        loadReferenceData(model);
        model.addAttribute("lot", lot);
        model.addAttribute("lotDTO", lotDTO);

        return "stock/lots/edit";
    }

    @PostMapping("/{id}/modifier")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String updateLot(
            @PathVariable UUID id,
            @ModelAttribute("lotDTO") LotDTO lotDTO,
            RedirectAttributes redirectAttributes) {

        try {
            Lot lot = lotService.updateLot(id, lotDTO);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot modifié avec succès: " + lot.getNumeroLot());

            return "redirect:/lots/" + lot.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la modification du lot: " + e.getMessage());
            return "redirect:/lots/" + id + "/modifier";
        }
    }

    // ========== PAGE : Alertes de péremption ==========
    @GetMapping("/alertes/peremption")
    // @PreAuthorize("hasAnyRole('GESTIONNAIRE_STOCK', 'RESPONSABLE_STOCK',
    // 'MANAGER')")
    public String showPeremptionAlerts(
            @RequestParam(defaultValue = "30") int joursAlerte,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            HttpSession session,
            Model model) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // GESTIONNAIRE, RESPONSABLE, MANAGER, ADMIN peuvent voir les alertes
        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Lot> lotsProchePeremption = lotService.getLotsProchePeremption(joursAlerte, pageable);

        // Grouper par nombre de jours restants
        Map<String, List<Lot>> lotsParUrgence = new LinkedHashMap<>();
        lotsParUrgence.put("Déjà périmés", new ArrayList<>());
        lotsParUrgence.put("1-7 jours", new ArrayList<>());
        lotsParUrgence.put("8-15 jours", new ArrayList<>());
        lotsParUrgence.put("16-30 jours", new ArrayList<>());

        for (Lot lot : lotsProchePeremption.getContent()) {
            if (lot.getDatePeremption() != null) {
                long joursRestants = java.time.temporal.ChronoUnit.DAYS.between(
                        LocalDate.now(), lot.getDatePeremption());

                if (joursRestants < 0) {
                    lotsParUrgence.get("Déjà périmés").add(lot);
                } else if (joursRestants <= 7) {
                    lotsParUrgence.get("1-7 jours").add(lot);
                } else if (joursRestants <= 15) {
                    lotsParUrgence.get("8-15 jours").add(lot);
                } else if (joursRestants <= 30) {
                    lotsParUrgence.get("16-30 jours").add(lot);
                }
            }
        }

        // Calculer la valeur totale à risque
        BigDecimal valeurRisque = BigDecimal.ZERO;
        for (List<Lot> lots : lotsParUrgence.values()) {
            for (Lot lot : lots) {
                valeurRisque = valeurRisque.add(
                        lot.getCoutUnitaire().multiply(BigDecimal.valueOf(lot.getQuantiteActuelle())));
            }
        }

        model.addAttribute("lotsParUrgence", lotsParUrgence);
        model.addAttribute("lotsProchePeremption", lotsProchePeremption);
        model.addAttribute("joursAlerte", joursAlerte);
        model.addAttribute("valeurRisque", valeurRisque);
        model.addAttribute("today", LocalDate.now());

        return "stock/lots/alertes-peremption";
    }

    // ========== PAGE : Gestion des numéros de série ==========
    @GetMapping("/series")
    // @PreAuthorize("hasAnyRole('GESTIONNAIRE_STOCK', 'RESPONSABLE_STOCK')")
    public String listSeries(
            @RequestParam(required = false) String numeroSerie,
            @RequestParam(required = false) UUID articleId,
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) String statut,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Model model) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Serie> seriesPage = lotService.searchSeries(numeroSerie, articleId, lotId, statut, pageable);

        loadReferenceData(model);
        model.addAttribute("series", seriesPage);
        model.addAttribute("numeroSerie", numeroSerie);
        model.addAttribute("articleId", articleId);
        model.addAttribute("lotId", lotId);
        model.addAttribute("statut", statut);
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", seriesPage.getTotalPages());

        return "stock/lots/series";
    }

    // ========== ACTIONS : Changement de statut ==========
    @PostMapping("/{id}/changer-statut")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String changerStatutLot(
            @PathVariable UUID id,
            @RequestParam String nouveauStatut,
            @RequestParam(required = false) String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {

            if (session.getAttribute("userId") == null) {
                return "redirect:/login";
            }

            // Seuls RESPONSABLE, MANAGER, ADMIN peuvent changer le statut
            if (!hasAnyRole(session, "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Permission refusée");
                return "redirect:/lots/" + id;
            }

            Lot lot = lotService.changerStatutLot(id, Lot.LotStatus.valueOf(nouveauStatut), motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Statut du lot changé en: " + nouveauStatut);

            return "redirect:/lots/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors du changement de statut: " + e.getMessage());
            return "redirect:/lots/" + id;
        }
    }

    @PostMapping("/{id}/bloquer")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String bloquerLot(
            @PathVariable UUID id,
            @RequestParam(required = false) String motif,
            RedirectAttributes redirectAttributes) {

        try {
            Lot lot = lotService.changerStatutLot(id, Lot.LotStatus.BLOQUE, motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot bloqué avec succès");

            return "redirect:/lots/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors du blocage du lot: " + e.getMessage());
            return "redirect:/lots/" + id;
        }
    }

    @PostMapping("/{id}/debloquer")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String debloquerLot(
            @PathVariable UUID id,
            @RequestParam(required = false) String motif,
            RedirectAttributes redirectAttributes) {

        try {
            Lot lot = lotService.changerStatutLot(id, Lot.LotStatus.DISPONIBLE, motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot débloqué avec succès");

            return "redirect:/lots/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors du déblocage du lot: " + e.getMessage());
            return "redirect:/lots/" + id;
        }
    }

    @PostMapping("/{id}/mettre-en-quarantaine")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String mettreEnQuarantaine(
            @PathVariable UUID id,
            @RequestParam String motif,
            RedirectAttributes redirectAttributes) {

        try {
            Lot lot = lotService.changerStatutLot(id, Lot.LotStatus.QUARANTAINE, motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot mis en quarantaine");

            return "redirect:/lots/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur: " + e.getMessage());
            return "redirect:/lots/" + id;
        }
    }

    // ========== ACTION : Fusionner des lots ==========
    @GetMapping("/{id}/fusionner")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String showFusionForm(@PathVariable UUID id, Model model) {
        Lot lotSource = lotRepository.findById(id).get();

        // Trouver les lots compatibles pour fusion (même article, même emplacement)
        List<Lot> lotsCompatibles = lotService.findLotsCompatiblesPourFusion(id);

        model.addAttribute("lotSource", lotSource);
        model.addAttribute("lotsCompatibles", lotsCompatibles);

        return "stock/lots/fusion";
    }

    @PostMapping("/{sourceId}/fusionner/{destinationId}")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String fusionnerLots(
            @PathVariable UUID sourceId,
            @PathVariable UUID destinationId,
            RedirectAttributes redirectAttributes) {

        try {
            Lot lotFusionne = lotService.fusionnerLots(sourceId, destinationId);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lots fusionnés avec succès dans: " + lotFusionne.getNumeroLot());

            return "redirect:/lots/" + destinationId;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la fusion: " + e.getMessage());
            return "redirect:/lots/" + sourceId + "/fusionner";
        }
    }

    // ========== ACTION : Supprimer un lot ==========
    @PostMapping("/{id}/supprimer")
    // @PreAuthorize("hasRole('RESPONSABLE_STOCK')")
    public String supprimerLot(
            @PathVariable UUID id,
            @RequestParam String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            if (session.getAttribute("userId") == null) {
                return "redirect:/login";
            }
    
            // Seuls RESPONSABLE, MANAGER, ADMIN peuvent supprimer
            if (!hasAnyRole(session, "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Permission refusée");
                return "redirect:/lots/" + id;
            }

            Lot lot = lotRepository.findById(id).get();

            // Vérifier si le lot peut être supprimé
            if (lot.getQuantiteActuelle() > 0) {
                throw new IllegalArgumentException("Impossible de supprimer un lot avec du stock");
            }

            lotService.deleteLot(id, motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot supprimé avec succès");

            return "redirect:/lots";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la suppression: " + e.getMessage());
            return "redirect:/lots/" + id;
        }
    }

    // ========== EXPORT ==========
    @GetMapping("/export")
    // @PreAuthorize("hasAnyRole('RESPONSABLE_STOCK', 'COMPTABLE', 'MANAGER')")
    public String exportLots(
            @RequestParam String format,
            @RequestParam(required = false) String numeroLot,
            @RequestParam(required = false) UUID articleId,
            @RequestParam(required = false) UUID depotId,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePeremptionFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate datePeremptionTo,
            RedirectAttributes redirectAttributes) {

        try {
            String filePath = lotService.exportLots(format, numeroLot, articleId,
                    depotId, statut, datePeremptionFrom, datePeremptionTo);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Export terminé. Fichier: " + filePath);

            return "redirect:/lots";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de l'export: " + e.getMessage());
            return "redirect:/lots";
        }
    }

    // ========== MÉTHODES UTILITAIRES ==========
    private void loadReferenceData(Model model) {
        // Charger les articles
        List<Article> articles = articleService.findByActifTrue();
        model.addAttribute("articles", articles);

        // Charger les dépôts
        List<Depot> depots = depotService.getDepotsActifs();
        model.addAttribute("depots", depots);

        // Charger les statuts de lot
        List<Lot.LotStatus> statuts = Arrays.asList(Lot.LotStatus.values());
        model.addAttribute("statuts", statuts);

        // Charger les emplacements
        List<Emplacement> emplacements = emplacementService.findAllActifs();
        model.addAttribute("emplacements", emplacements);

        // Charger les bons de réception récents
        List<BonReception> bonsReception = bonReceptionService.findRecent(50);
        model.addAttribute("bonsReception", bonsReception);

        // Statuts de série (si utilisé)
        List<String> statutsSerie = Arrays.asList("EN_STOCK", "VENDU", "RETOUR", "SAV", "REBUT");
        model.addAttribute("statutsSerie", statutsSerie);
    }

    // ========== PAGE : Détail d'une série ==========
    @GetMapping("/series/{id}")
    // @PreAuthorize("hasAnyRole('GESTIONNAIRE_STOCK', 'RESPONSABLE_STOCK')")
    public String viewSerie(@PathVariable UUID id, Model model) {
        Serie serie = lotService.findSerieById(id);

        // Charger l'historique
        // List<HistoriqueSerie> historique = lotService.getHistoriqueSerie(id);

        model.addAttribute("serie", serie);
        // model.addAttribute("historique", historique);
        model.addAttribute("statutsSerie", Arrays.asList("EN_STOCK", "VENDU", "RETOUR", "SAV", "REBUT"));

        return "stock/lots/serie-detail";
    }

    // ========== PAGE : Créer une série ==========
    @GetMapping("/series/nouveau")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String showCreateSerieForm(
            @RequestParam(required = false) UUID lotId,
            @RequestParam(required = false) UUID articleId,
            Model model) {

        SerieDTO serieDTO = new SerieDTO();
        Lot lot1 = lotService.getLotById(lotId);
        Article article = articleService.getArticleById(articleId);
        if (lotId != null) {
            serieDTO.setLot(lot1);

            try {
                Lot lot = lotService.getLotById(lotId);
                serieDTO.setArticle(lot.getArticle());
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else if (articleId != null) {
            serieDTO.setArticle(article);
        }

        loadReferenceData(model);

        // Charger les lots pour cet article si spécifié
        if (serieDTO.getArticle() != null) {
            List<Lot> lots = lotService.findByArticleId(serieDTO.getArticle().getId());
            model.addAttribute("lots", lots);
        } else {
            model.addAttribute("lots", Collections.emptyList());
        }

        model.addAttribute("serieDTO", serieDTO);
        model.addAttribute("statutsSerie", Arrays.asList("EN_STOCK", "VENDU", "RETOUR", "SAV", "REBUT"));

        return "stock/lots/serie-create";
    }

    @PostMapping("/series/nouveau")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String createSerie(
            @Valid @ModelAttribute("serieDTO") SerieDTO serieDTO,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {

        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Veuillez corriger les erreurs dans le formulaire");
            return "redirect:/lots/series/nouveau";
        }

        try {
            Serie serie = lotService.createSerie(serieDTO);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Numéro de série créé avec succès: " + serie.getNumeroSerie());

            return "redirect:/lots/series/" + serie.getId();
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors de la création du numéro de série: " + e.getMessage());
            return "redirect:/lots/series/nouveau";
        }
    }

    // ========== PAGE : Transférer un lot ==========
    @GetMapping("/{id}/transferer")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String showTransferForm(@PathVariable UUID id, Model model, HttpSession session, RedirectAttributes redirectAttributes) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // GESTIONNAIRE, RESPONSABLE, MANAGER, ADMIN peuvent transférer
        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            redirectAttributes.addFlashAttribute("errorMessage", "Permission refusée");
            return "redirect:/lots/" + id;
        }

        Lot lot = lotService.getLotById(id);

        // Charger les emplacements disponibles
        List<Emplacement> emplacementsDisponibles = emplacementService.findEmplacementsDisponibles(
                lot.getArticle(), lot.getQuantiteActuelle());

        // Charger les zones et dépôts pour les filtres
        // List<ZoneStockage> zones = zoneStockageService.findAllActifs();
        List<Depot> depots = depotService.getDepotsActifs();

        model.addAttribute("lot", lot);
        model.addAttribute("emplacements", emplacementsDisponibles);
        // model.addAttribute("zones", zones);
        model.addAttribute("depots", depots);

        return "stock/lots/transferer";
    }

    @PostMapping("/{id}/transferer")
    // @PreAuthorize("hasRole('GESTIONNAIRE_STOCK')")
    public String transfererLot(
            @PathVariable UUID id,
            @RequestParam UUID nouvelEmplacementId,
            @RequestParam(required = false) Integer quantite,
            @RequestParam String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            if (session.getAttribute("userId") == null) {
                return "redirect:/login";
            }
    
            // GESTIONNAIRE, RESPONSABLE, MANAGER, ADMIN peuvent transférer
            if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("errorMessage", "Permission refusée");
                return "redirect:/lots/" + id;
            }
            
            Lot lot = lotService.transfererLot(id, nouvelEmplacementId, quantite, motif);

            redirectAttributes.addFlashAttribute("successMessage",
                    "Lot transféré avec succès vers " + lot.getEmplacement().getCode());

            return "redirect:/lots/" + id;
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage",
                    "Erreur lors du transfert: " + e.getMessage());
            return "redirect:/lots/" + id + "/transferer";
        }
    }
}