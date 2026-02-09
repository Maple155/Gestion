// ReservationController.java (version corrigée)
package com.gestion.stock.controller.reservation;

import com.gestion.stock.dto.CreateReservationRequest;
import com.gestion.stock.dto.ReservationDTO;
import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.service.ReservationService;
import com.gestion.stock.service.ReservationStockService;
import jakarta.servlet.http.HttpServletRequest;
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

import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock/reservations")
@RequiredArgsConstructor
@Slf4j
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationStockService reservationStockService;
    /**
     * Liste des réservations avec filtres
     */

    @GetMapping("/all")
    public String getAll(Model model){
        model.addAttribute("resStock", reservationStockService.getAll());
        return "/home";
    }

    @GetMapping("/liste")
    public String listeReservations(Model model,
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String articleId,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String commandeClientId,
            @RequestParam(defaultValue = "dateReservation") String tri,
            @RequestParam(defaultValue = "desc") String ordre) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Configurer la pagination
        Sort.Direction direction = "asc".equalsIgnoreCase(ordre) ? Sort.Direction.ASC : Sort.Direction.DESC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, tri));

        // Convertir les IDs
        UUID articleUuid = null;
        UUID depotUuid = null;
        UUID commandeUuid = null;

        try {
            if (articleId != null && !articleId.isEmpty()) {
                articleUuid = UUID.fromString(articleId);
            }
            if (depotId != null && !depotId.isEmpty()) {
                depotUuid = UUID.fromString(depotId);
            }
            if (commandeClientId != null && !commandeClientId.isEmpty()) {
                commandeUuid = UUID.fromString(commandeClientId);
            }
        } catch (IllegalArgumentException e) {
            log.warn("ID UUID invalide", e);
        }

        // Récupérer les réservations filtrées
        Page<ReservationStock> pageReservations = reservationService.rechercherReservations(
                articleUuid, depotUuid, statut, commandeUuid, pageable);

        // Convertir en DTOs
        Page<ReservationDTO> reservationsDTO = pageReservations.map(reservationService::toDTO);

        // Statistiques
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", pageReservations.getTotalElements());
        stats.put("actives", pageReservations.stream()
                .filter(r -> r.getStatut() == ReservationStock.ReservationStatus.ACTIVE)
                .count());
        stats.put("prelevees", pageReservations.stream()
                .filter(r -> r.getStatut() == ReservationStock.ReservationStatus.PRELEVEE)
                .count());

        model.addAttribute("reservations", reservationsDTO);
        model.addAttribute("stats", stats);
        model.addAttribute("articleId", articleId);
        model.addAttribute("depotId", depotId);
        model.addAttribute("statut", statut);
        model.addAttribute("commandeClientId", commandeClientId);
        model.addAttribute("tri", tri);
        model.addAttribute("ordre", ordre);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("totalPages", reservationsDTO.getTotalPages());
        model.addAttribute("totalElements", reservationsDTO.getTotalElements());

        // Liste des statuts pour le filtre
        model.addAttribute("statuts", Arrays.stream(ReservationStock.ReservationStatus.values())
                .map(Enum::name)
                .collect(Collectors.toList()));

        model.addAttribute("title", "Gestion des Réservations");
        model.addAttribute("activePage", "stock-reservations");

        return "stock/reservations/liste";
    }

    /**
     * Détails d'une réservation
     */
    @GetMapping("/details/{id}")
    public String detailsReservation(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            ReservationStock reservation = reservationService.getReservationById(UUID.fromString(id));
            ReservationDTO dto = reservationService.toDTO(reservation);

            model.addAttribute("reservation", dto);
            model.addAttribute("title", "Détails Réservation: " + dto.getReference());
            model.addAttribute("activePage", "stock-reservations");

        } catch (Exception e) {
            log.error("Erreur chargement réservation", e);
            model.addAttribute("error", "Réservation non trouvée: " + e.getMessage());
        }

        return "stock/reservations/details";
    }

    // ReservationController.java - formulaireNouvelleReservation method
    @GetMapping("/nouvelle")
    public String formulaireNouvelleReservation(Model model,
            @RequestParam(required = false) String commandeClientId,
            @RequestParam(required = false) String ligneCommandeId,
            HttpSession session,
            HttpServletRequest request) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Pass referer URL to the template
        String referer = request.getHeader("Referer");
        model.addAttribute("refererUrl", referer);

        model.addAttribute("title", "Nouvelle Réservation");
        model.addAttribute("activePage", "stock-reservations");
        model.addAttribute("commandeClientId", commandeClientId);
        model.addAttribute("ligneCommandeId", ligneCommandeId);
        model.addAttribute("aujourdhui", new Date());

        return "stock/reservations/nouvelle";
    }

    /**
     * Vérifier disponibilité (AJAX)
     */
    @PostMapping("/verifier-disponibilite")
    @ResponseBody
    public Map<String, Object> verifierDisponibilite(@RequestBody Map<String, String> data,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            String articleId = data.get("articleId");
            String depotId = data.get("depotId");
            Integer quantite = Integer.parseInt(data.get("quantite"));

            Map<String, Object> disponibilite = reservationService.verifierDisponibilite(
                    UUID.fromString(articleId), UUID.fromString(depotId), quantite);

            response.put("success", true);
            response.put("data", disponibilite);

        } catch (Exception e) {
            log.error("Erreur vérification disponibilité", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * Créer une réservation
     */
    @PostMapping("/creer")
    public String creerReservation(@ModelAttribute CreateReservationRequest request,
            @RequestParam(required = false) String redirectUrl,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            UUID utilisateurId = (UUID) session.getAttribute("userId");
            if (utilisateurId == null) {
                return "redirect:/login";
            }

            ReservationStock reservation = reservationService.reserverStock(
                    request.getArticleId(),
                    request.getDepotId(),
                    request.getQuantite(),
                    request.getCommandeClientId(),
                    request.getLigneCommandeId(),
                    utilisateurId);

            redirectAttributes.addFlashAttribute("success",
                    "Réservation créée avec succès: " + reservation.getReference());

            // Redirection intelligente
            if (redirectUrl != null && !redirectUrl.isEmpty()) {
                return "redirect:" + redirectUrl;
            }

            if (request.getCommandeClientId() != null) {
                return "redirect:/stock/reservations/details/" + request.getCommandeClientId();
            }

            return "redirect:/stock/reservations/details/" + reservation.getId();

        } catch (Exception e) {
            log.error("Erreur création réservation", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            redirectAttributes.addFlashAttribute("request", request);

            return "redirect:/stock/reservations/nouvelle" +
                    (request.getCommandeClientId() != null ? "?commandeClientId=" + request.getCommandeClientId() : "");
        }
    }

    /**
     * Annuler une réservation
     */
    @PostMapping("/annuler/{id}")
    public String annulerReservation(@PathVariable String id,
            @RequestParam(required = false) String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            reservationService.annulerReservation(UUID.fromString(id));
            redirectAttributes.addFlashAttribute("success",
                    "Réservation annulée avec succès");

        } catch (Exception e) {
            log.error("Erreur annulation réservation", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/reservations/details/" + id;
    }

    /**
     * Marquer comme prélevée
     */
    @PostMapping("/prelever/{id}")
    public String preleverReservation(@PathVariable String id,
            @RequestParam Integer quantitePrelevee,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            reservationService.marquerPrelevee(UUID.fromString(id), quantitePrelevee);
            redirectAttributes.addFlashAttribute("success",
                    "Réservation marquée comme prélevée");

        } catch (Exception e) {
            log.error("Erreur prélèvement réservation", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/reservations/details/" + id;
    }

    /**
     * Réservations par commande (API)
     */
    @GetMapping("/api/par-commande/{commandeId}")
    @ResponseBody
    public List<ReservationDTO> getReservationsByCommande(@PathVariable String commandeId) {
        List<ReservationStock> reservations = reservationService
                .getReservationsByCommande(UUID.fromString(commandeId));

        return reservations.stream()
                .map(reservationService::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Réservations actives pour un article/dépôt
     */
    @GetMapping("/api/actives")
    @ResponseBody
    public List<ReservationDTO> getReservationsActives(
            @RequestParam String articleId,
            @RequestParam String depotId) {

        List<ReservationStock> reservations = reservationService
                .getReservationsActives(UUID.fromString(articleId), UUID.fromString(depotId));

        return reservations.stream()
                .map(reservationService::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Interface de prélèvement (picking)
     */
    @GetMapping("/preparation/{id}")
    public String interfacePreparation(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            ReservationStock reservation = reservationService.getReservationById(UUID.fromString(id));
            ReservationDTO dto = reservationService.toDTO(reservation);

            model.addAttribute("reservation", dto);
            model.addAttribute("title", "Préparation Réservation: " + dto.getReference());
            model.addAttribute("activePage", "stock-preparation");

        } catch (Exception e) {
            log.error("Erreur chargement réservation", e);
            model.addAttribute("error", "Réservation non trouvée: " + e.getMessage());
        }

        return "stock/reservations/preparation";
    }

    /**
     * Prolonger une réservation
     */
    @PostMapping("/prolonger/{id}")
    public String prolongerReservation(@PathVariable String id,
            @RequestParam Integer heures,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            reservationService.prolongerReservation(UUID.fromString(id), heures);
            redirectAttributes.addFlashAttribute("success",
                    "Réservation prolongée de " + heures + " heures");

        } catch (Exception e) {
            log.error("Erreur prolongation réservation", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
        }

        return "redirect:/stock/reservations/details/" + id;
    }

    private final ReservationStockService service;

    @PostMapping("/{id}/statut")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam("statut") ReservationStock.ReservationStatus statut) {
        ReservationStock updated = service.updateStatus(id, statut);
        return "home";
    }

    @GetMapping("{id}")
    public String detailReservation(@PathVariable UUID id, Model model) {
        ReservationStock mvt = service.findById(id);
        model.addAttribute("res", mvt);
        return "stock/reservations/reservations-detail";
    }
}