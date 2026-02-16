package com.gestion.stock.controller.transferts;

import com.gestion.stock.dto.LigneTransfertDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.stock.service.*;
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
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock/transferts")
@RequiredArgsConstructor
@Slf4j
public class TransfertController {

    private final TransfertService transfertService;
    private final DepotRepository depotRepository;
    private final ArticleService articleService;
    private final LotService lotService;

    private boolean hasAnyRole(HttpSession session, String... roles) {
        String userRole = (String) session.getAttribute("userRole");
        if (userRole == null) return false;
        return Arrays.asList(roles).contains(userRole);
    }

    @GetMapping("/all")
    public String getAll(Model model){
        model.addAttribute("transferts", transfertService.getAll());
        return "/home";
    }

    @GetMapping("{id}")
    public String detailTransfert(@PathVariable UUID id, Model model) {
        Transfert mvt = transfertService.findById(id);
        model.addAttribute("trf", mvt);
        return "stock/transferts/transferts-detail";
    }

    @PostMapping("/{id}/statut")
    public String updateStatusPost(
        @PathVariable UUID id,
        @RequestParam("statut") Transfert.TransfertStatut statut,
        HttpSession session    
    ) {
            UUID userId = (UUID) session.getAttribute("userId");
            Transfert updated = transfertService.updateStatus(id, statut, userId);
            return "/home";
        }
    @GetMapping("/liste")
    public String listeTransferts(Model model,
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String depotSource,
            @RequestParam(required = false) String depotDestination,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFin) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MAGASINIER", 
        "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }
    
        UUID depotSourceUuid = null;
        UUID depotDestinationUuid = null;
        
        try {
            if (depotSource != null && !depotSource.trim().isEmpty()) {
                depotSourceUuid = UUID.fromString(depotSource);
            }
            if (depotDestination != null && !depotDestination.trim().isEmpty()) {
                depotDestinationUuid = UUID.fromString(depotDestination);
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID format in filter parameters", e);
            // Ajouter un message d'erreur
            model.addAttribute("error", "Format d'ID de dépôt invalide");
        }

        // Récupérer les transferts selon les filtres
        List<Transfert> transferts = transfertService.rechercherTransferts(
                statut,
                depotSourceUuid,
                depotDestinationUuid,
                dateDebut, dateFin);

        // Pagination manuelle
        int start = Math.min(page * size, transferts.size());
        int end = Math.min((page + 1) * size, transferts.size());
        List<Transfert> transfertsPage = transferts.subList(start, end);

        // Créer la liste des transferts avec leurs détails
        List<Map<String, Object>> transfertsWithDetails = transfertsPage.stream()
                .map(transfert -> {
                    Map<String, Object> details = new HashMap<>();
                    details.put("transfert", transfert);

                    // Récupérer les lignes du transfert
                    List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfert.getId());

                    // Calculer les totaux
                    int totalArticles = lignes.stream()
                            .mapToInt(LigneTransfert::getQuantiteDemandee)
                            .sum();
                    int nombreLignes = lignes.size();

                    details.put("totalArticles", totalArticles);
                    details.put("nombreLignes", nombreLignes);

                    return details;
                })
                .collect(Collectors.toList());

        // Récupérer les statistiques
        Map<String, Object> stats = transfertService.getStatistiquesTransferts(
                LocalDate.now().getMonthValue(),
                LocalDate.now().getYear());

        // Liste des dépôts pour les filtres
        List<Depot> depots = depotRepository.findByActifTrue();

        // Ajouter les attributs au modèle
        model.addAttribute("transfertsWithDetails", transfertsWithDetails);
        model.addAttribute("depots", depots);
        model.addAttribute("stats", stats);
        model.addAttribute("statut", statut);
        model.addAttribute("depotSource", depotSource);
        model.addAttribute("depotDestination", depotDestination);
        model.addAttribute("dateDebut", dateDebut);
        model.addAttribute("dateFin", dateFin);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", (int) Math.ceil((double) transferts.size() / size));
        model.addAttribute("totalElements", transferts.size());

        model.addAttribute("title", "Transferts entre Dépôts");
        model.addAttribute("activePage", "stock-transferts");

        return "stock/transferts/liste";
    }

    /**
     * Formulaire création transfert
     */
    @GetMapping("/nouveau")
    public String formulaireNouveauTransfert(Model model,
            HttpSession session,
            @RequestParam(required = false) String depotSourceId,
            @RequestParam(required = false) String depotDestinationId) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
            return "redirect:/access-denied";
        }

        // Liste des dépôts actifs
        List<Depot> depots = depotRepository.findByActifTrue();

        // Si des dépôts sont pré-sélectionnés via les paramètres
        if (depotSourceId != null) {
            model.addAttribute("depotSourceId", depotSourceId);
        }
        if (depotDestinationId != null) {
            model.addAttribute("depotDestinationId", depotDestinationId);
        }

        model.addAttribute("depots", depots);
        model.addAttribute("title", "Nouveau Transfert");
        model.addAttribute("activePage", "stock-transferts");

        return "stock/transferts/nouveau";
    }

    /**
     * Créer un transfert
     */
    @PostMapping("/creer")
    public String creerTransfert(@RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");
            if (utilisateurId == null) {
                return "redirect:/login";
            }

            // Récupérer les paramètres de base
            String depotSourceId = params.get("depotSourceId");
            String depotDestinationId = params.get("depotDestinationId");
            String motif = params.get("motif");

            // Récupérer les lignes du transfert
            List<LigneTransfertDTO> lignes = extraireLignesFromParams(params);

            Transfert transfert = transfertService.creerTransfert(
                    UUID.fromString(depotSourceId),
                    UUID.fromString(depotDestinationId),
                    lignes,
                    motif,
                    utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Transfert créé avec succès : " + transfert.getReference());

            return "redirect:/stock/transferts/details/" + transfert.getId();

        } catch (Exception e) {
            log.error("Erreur création transfert", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/transferts/nouveau";
        }
    }

    /**
     * Extraire les lignes de transfert des paramètres
     */
    private List<LigneTransfertDTO> extraireLignesFromParams(Map<String, String> params) {
        List<LigneTransfertDTO> lignes = new ArrayList<>();

        // Compter le nombre de lignes
        int ligneCount = 0;
        for (String key : params.keySet()) {
            if (key.startsWith("lignes[") && key.contains("].articleId")) {
                ligneCount++;
            }
        }

        // Extraire chaque ligne
        for (int i = 0; i < ligneCount; i++) {
            String prefix = "lignes[" + i + "].";

            String articleId = params.get(prefix + "articleId");
            String quantiteDemandeeStr = params.get(prefix + "quantiteDemandee");
            String lotId = params.get(prefix + "lotId");
            String notes = params.get(prefix + "notes");

            Article article = articleService.getArticleById(
                    UUID.fromString(articleId));
            Lot lot = lotId != null && !lotId.isEmpty() ? lotService.getLotById(UUID.fromString(lotId)) : null;

            if (articleId != null && quantiteDemandeeStr != null) {
                LigneTransfertDTO ligne = LigneTransfertDTO.builder()
                        .article(article)
                        .quantiteDemandee(Integer.parseInt(quantiteDemandeeStr))
                        .lot(lot)
                        .notes(notes)
                        .build();

                lignes.add(ligne);
            }
        }

        return lignes;
    }

    /**
     * Valider un transfert
     */
    @PostMapping("/valider/{id}")
    public String validerTransfert(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");

            if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error", "Permission refusée");
                return "redirect:/stock/transferts/details/" + id;
            }

            Transfert transfert = transfertService.validerTransfert(
                    UUID.fromString(id), utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Transfert validé avec succès");

        } catch (Exception e) {
            log.error("Erreur validation transfert", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/transferts/details/" + id;
    }

    /**
     * Expédier un transfert
     */
    @PostMapping("/expedier/{id}")
    public String expedierTransfert(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");

            if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
            "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error", "Permission refusée");
                return "redirect:/stock/transferts/details/" + id;
            }
        
            Transfert transfert = transfertService.expedierTransfert(
                    UUID.fromString(id), utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Transfert expédié avec succès");

        } catch (Exception e) {
            log.error("Erreur expédition transfert", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/transferts/details/" + id;
    }

    /**
     * Réceptionner un transfert
     */
    @PostMapping("/receptionner/{id}")
    public String receptionnerTransfert(@PathVariable String id,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");

            if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
            "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error", "Permission refusée");
                return "redirect:/stock/transferts/details/" + id;
            }

            Transfert transfert = transfertService.receptionnerTransfert(
                    UUID.fromString(id), utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Transfert réceptionné avec succès");

        } catch (Exception e) {
            log.error("Erreur réception transfert", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/transferts/details/" + id;
    }

    /**
     * Détails d'un transfert
     */
    @GetMapping("/details/{id}")
    public String detailsTransfert(@PathVariable String id,
            @RequestParam(required = false) String action,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            UUID transfertId = UUID.fromString(id);

            // Récupérer le transfert
            Optional<Transfert> transfertOpt = transfertRepository.findById(transfertId);
            if (transfertOpt.isEmpty()) {
                throw new RuntimeException("Transfert non trouvé");
            }

            Transfert transfert = transfertOpt.get();

            // Récupérer les lignes
            List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);

            // Récupérer les mouvements associés
            List<StockMovement> mouvements = stockMovementRepository.findByTransfertId(transfertId);

            model.addAttribute("transfert", transfert);
            model.addAttribute("lignes", lignes);
            model.addAttribute("mouvements", mouvements);

            // Vérifier les permissions
            UUID userId = (UUID) session.getAttribute("userId");
            model.addAttribute("canValidate", canValidateTransfert(transfert, userId));
            model.addAttribute("canExpedite", canExpediteTransfert(transfert, userId));
            model.addAttribute("canReceive", canReceiveTransfert(transfert, userId));
            model.addAttribute("canCancel", canCancelTransfert(userId));

            // Gérer les actions rapides
            if ("valider".equals(action) && canValidateTransfert(transfert, userId)) {
                return "redirect:/stock/transferts/valider/" + id;
            }
            if ("expedier".equals(action) && canExpediteTransfert(transfert, userId)) {
                return "redirect:/stock/transferts/expedier/" + id;
            }
            if ("receptionner".equals(action) && canReceiveTransfert(transfert, userId)) {
                return "redirect:/stock/transferts/receptionner/" + id;
            }

            model.addAttribute("title", "Détails Transfert: " + transfert.getReference());
            model.addAttribute("activePage", "stock-transferts");

            return "stock/transferts/details";

        } catch (Exception e) {
            log.error("Erreur chargement détails transfert", e);
            return "redirect:/stock/transferts/liste?error=" + e.getMessage();
        }
    }

    /**
     * Annuler un transfert
     */
    @PostMapping("/annuler/{id}")
    public String annulerTransfert(@PathVariable String id,
            @RequestParam String motifAnnulation,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");

            if (!hasAnyRole(session, "RESPONSABLE_STOCK", "MANAGER", "ADMIN")) {
                redirectAttributes.addFlashAttribute("error", "Permission refusée");
                return "redirect:/stock/transferts/details/" + id;
            }
            
            Transfert transfert = transfertService.annulerTransfert(
                    UUID.fromString(id), motifAnnulation, utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Transfert annulé avec succès");

        } catch (Exception e) {
            log.error("Erreur annulation transfert", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/transferts/details/" + id;
    }

    /**
     * Interface d'expédition (pour scanner)
     */
    @GetMapping("/expedition/{id}")
    public String interfaceExpedition(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            Transfert transfert = transfertRepository.findById(UUID.fromString(id))
                    .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));

            if (transfert.getStatut() != Transfert.TransfertStatut.VALIDE) {
                throw new RuntimeException("Le transfert doit être validé avant expédition");
            }

            List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(UUID.fromString(id));

            model.addAttribute("transfert", transfert);
            model.addAttribute("lignes", lignes);
            model.addAttribute("title", "Expédition Transfert: " + transfert.getReference());
            model.addAttribute("activePage", "stock-transferts");

            return "stock/transferts/expedition";

        } catch (Exception e) {
            log.error("Erreur chargement interface expédition", e);
            return "redirect:/stock/transferts/details/" + id + "?error=" + e.getMessage();
        }
    }

    /**
     * Interface de réception (pour scanner)
     */
    @GetMapping("/reception/{id}")
    public String interfaceReception(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            Transfert transfert = transfertRepository.findById(UUID.fromString(id))
                    .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));

            if (transfert.getStatut() != Transfert.TransfertStatut.EXPEDIE) {
                throw new RuntimeException("Le transfert doit être expédié avant réception");
            }

            List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(UUID.fromString(id));

            model.addAttribute("transfert", transfert);
            model.addAttribute("lignes", lignes);
            model.addAttribute("title", "Réception Transfert: " + transfert.getReference());
            model.addAttribute("activePage", "stock-transferts");

            return "stock/transferts/reception";

        } catch (Exception e) {
            log.error("Erreur chargement interface réception", e);
            return "redirect:/stock/transferts/details/" + id + "?error=" + e.getMessage();
        }
    }

    /**
     * Scanner un article lors de l'expédition
     */
    @PostMapping("/scanner-expedition/{transfertId}")
    @ResponseBody
    public Map<String, Object> scannerExpedition(@PathVariable String transfertId,
            @RequestParam String codeBarre,
            @RequestParam(required = false) String lotNumero,
            @RequestParam Integer quantite,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            String utilisateurId = (String) session.getAttribute("userId");

            // Logique de scan pour l'expédition
            // À implémenter selon votre système de scan

            response.put("success", true);
            response.put("message", "Article scanné avec succès");

        } catch (Exception e) {
            log.error("Erreur scan expédition", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Scanner un article lors de la réception
     */
    @PostMapping("/scanner-reception/{transfertId}")
    @ResponseBody
    public Map<String, Object> scannerReception(@PathVariable String transfertId,
            @RequestParam String codeBarre,
            @RequestParam(required = false) String lotNumero,
            @RequestParam Integer quantite,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            String utilisateurId = (String) session.getAttribute("userId");

            // Logique de scan pour la réception
            // À implémenter selon votre système de scan

            response.put("success", true);
            response.put("message", "Article réceptionné avec succès");

        } catch (Exception e) {
            log.error("Erreur scan réception", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    /**
     * Export des transferts
     */
    @GetMapping("/export")
    public String exporterTransferts(@RequestParam(required = false) String format,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String depotSource,
            @RequestParam(required = false) String depotDestination,
            @RequestParam(required = false) LocalDate dateDebut,
            @RequestParam(required = false) LocalDate dateFin,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            // Récupérer les transferts filtrés
            List<Transfert> transferts = transfertService.rechercherTransferts(
                    statut,
                    depotSource != null ? UUID.fromString(depotSource) : null,
                    depotDestination != null ? UUID.fromString(depotDestination) : null,
                    dateDebut, dateFin);

            // Générer le fichier d'export
            String fileName = exportTransfertsToFile(transferts, format);

            redirectAttributes.addFlashAttribute("success",
                    "Export terminé: " + fileName);

        } catch (Exception e) {
            log.error("Erreur export transferts", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur export: " + e.getMessage());
        }

        return "redirect:/stock/transferts/liste";
    }

    /**
     * Méthodes utilitaires pour vérifier les permissions
     */
    private boolean canValidateTransfert(Transfert transfert, UUID userId) {
        // Le valideur ne doit pas être le demandeur
        return transfert.getStatut() == Transfert.TransfertStatut.BROUILLON &&
                !transfert.getDemandeurId().toString().equals(userId);
    }

    private boolean canExpediteTransfert(Transfert transfert, UUID userId) {
        return transfert.getStatut() == Transfert.TransfertStatut.VALIDE;
        // Ajouter vérification que l'utilisateur appartient au dépôt source
    }

    private boolean canReceiveTransfert(Transfert transfert, UUID userId) {
        return transfert.getStatut() == Transfert.TransfertStatut.EXPEDIE;
        // Ajouter vérification que l'utilisateur appartient au dépôt destination
    }

    private boolean canCancelTransfert(UUID userId) {
        // Seuls les managers et admins peuvent annuler
        // À implémenter selon vos rôles
        return true;
    }

    /**
     * Méthode d'export (à implémenter)
     */
    private String exportTransfertsToFile(List<Transfert> transferts, String format) {
        // Implémenter l'export Excel ou PDF
        // Utiliser Apache POI pour Excel ou iText pour PDF

        return "transferts_" + LocalDate.now() + "." + (format != null ? format : "xlsx");
    }

    // Injection des repositories manquants
    private final TransfertRepository transfertRepository;
    private final LigneTransfertRepository ligneTransfertRepository;
    private final StockMovementRepository stockMovementRepository;
}