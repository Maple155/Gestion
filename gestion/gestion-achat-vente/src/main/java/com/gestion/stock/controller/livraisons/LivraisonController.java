// LivraisonController.java (complété)
package com.gestion.stock.controller.livraisons;

import com.gestion.stock.entity.Depot;
import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.entity.Stock;
import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.repository.*;
import com.gestion.stock.service.LivraisonService;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/stock/livraisons")
@RequiredArgsConstructor
@Slf4j
public class LivraisonController {

    private final LivraisonService livraisonService;
    private final StockMovementRepository stockMovementRepository;
    private final StockRepository stockRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final ReservationStockRepository reservationStockRepository;
    private final DepotRepository depotRepository;

    private boolean hasAnyRole(HttpSession session, String... roles) {
        String userRole = (String) session.getAttribute("userRole");
        if (userRole == null) return false;
        return Arrays.asList(roles).contains(userRole);
    }
    
    @GetMapping("/liste")
    public String listeLivraisons(Model model,
            HttpSession session,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String dateDebut,
            @RequestParam(required = false) String dateFin) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "MAGASINIER", 
        "MANAGER", "ADMIN")) {
        return "redirect:/access-denied";
        }

        LocalDate debut = dateDebut != null ? LocalDate.parse(dateDebut) : LocalDate.now().minusDays(30);
        LocalDate fin = dateFin != null ? LocalDate.parse(dateFin) : LocalDate.now();

        // Obtenir les statistiques
        Map<String, Object> stats = livraisonService.getStatistiquesLivraisons(debut, fin);

        // Obtenir l'historique (simplifié - à adapter avec pagination)
        var historique = livraisonService.getHistoriqueLivraisons(
                depotId != null ? UUID.fromString(depotId) : null,
                debut,
                fin);

        model.addAttribute("historique", historique);
        model.addAttribute("stats", stats);
        model.addAttribute("depotId", depotId);
        model.addAttribute("dateDebut", debut);
        model.addAttribute("dateFin", fin);
        model.addAttribute("page", page);
        model.addAttribute("size", size);

        model.addAttribute("title", "Historique des Livraisons");
        model.addAttribute("activePage", "stock-livraisons");

        return "stock/livraisons/liste";
    }

    @GetMapping("/nouvelle")
    public String formulaireLivraison(Model model,
            @RequestParam(required = false) String commandeClientId,
            @RequestParam(required = false) String reservationId,
            @RequestParam(required = false) String depotId,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
        "MANAGER", "ADMIN")) {
        return "redirect:/access-denied";
        }

        // Obtenir les réservations actives
        var reservations = livraisonService.getReservationsALivrer(
                depotId != null ? UUID.fromString(depotId) : null);

        model.addAttribute("title", "Nouvelle Livraison");
        model.addAttribute("activePage", "stock-livraisons");
        model.addAttribute("commandeClientId", commandeClientId);
        model.addAttribute("reservationId", reservationId);
        model.addAttribute("depotId", depotId);
        model.addAttribute("reservations", reservations);

        return "stock/livraisons/nouvelle";
    }

    @PostMapping("/creer-depuis-reservation")
    public String creerLivraisonReservation(@RequestParam String reservationId,
            @RequestParam(required = false) String motif,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system";
            }

            var mouvement = livraisonService.creerSortieStock(
                    UUID.fromString(reservationId),
                    UUID.fromString(utilisateurId),
                    motif);

            redirectAttributes.addFlashAttribute("success",
                    "Livraison créée avec succès: " + mouvement.getReference());
            return "redirect:/stock/livraisons/liste";

        } catch (Exception e) {
            log.error("Erreur création livraison", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/livraisons/nouvelle";
        }
    }

    @PostMapping("/livrer-directement")
    public String livrerDirectement(@RequestParam Map<String, String> params,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            String utilisateurId = (String) session.getAttribute("userId");
            if (utilisateurId == null) {
                utilisateurId = "system";
            }

            String articleId = params.get("articleId");
            String depotId = params.get("depotId");
            Integer quantite = Integer.parseInt(params.get("quantite"));
            String commandeClientId = params.get("commandeClientId");
            String motif = params.get("motif");
            String lotId = params.get("lotId");

            // Pour simplicité, utilisation de la méthode sans lot spécifique
            // Vous pourriez créer une méthode spécifique pour gérer un lot précis
            var mouvement = livraisonService.livrerDirectement(
                    UUID.fromString(articleId),
                    UUID.fromString(depotId),
                    quantite,
                    UUID.fromString(commandeClientId),
                    UUID.fromString(utilisateurId),
                    motif);

            redirectAttributes.addFlashAttribute("success",
                    "Livraison directe effectuée: " + mouvement.getReference());
            return "redirect:/stock/livraisons/liste";

        } catch (Exception e) {
            log.error("Erreur livraison directe", e);
            redirectAttributes.addFlashAttribute("error",
                    "Erreur: " + e.getMessage());
            return "redirect:/stock/livraisons/nouvelle";
        }
    }

    @GetMapping("/details/{id}")
    public String detailsLivraison(@PathVariable String id,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        try {
            // Récupérer la livraison (mouvement de stock)
            StockMovement livraison = stockMovementRepository.findById(UUID.fromString(id))
                    .orElseThrow(() -> new RuntimeException("Livraison non trouvée"));

            // Vérifier que c'est bien une livraison
            if (!"LIVRAISON_CLIENT".equals(livraison.getType().getCode())) {
                throw new RuntimeException("Ce n'est pas une livraison client");
            }

            // Récupérer les informations complémentaires
            Map<String, Object> details = new HashMap<>();

            // Stock avant/après (simplifié)
            Stock stockAvant = stockRepository.findByArticleIdAndDepotId(
                    livraison.getArticle().getId(),
                    livraison.getDepot().getId())
                    .orElse(null);

            // Récupérer le nom de l'utilisateur
            String utilisateurNom = utilisateurRepository.findById(livraison.getUtilisateurId())
                    .map(u -> u.getNom() + " " + u.getPrenom())
                    .orElse("Utilisateur inconnu");

            String valideurNom = null;

            model.addAttribute("livraison", livraison);
            model.addAttribute("stockAvant", stockAvant);
            model.addAttribute("utilisateurNom", utilisateurNom);
            model.addAttribute("valideurNom", valideurNom);
            model.addAttribute("title", "Détails Livraison: " + livraison.getReference());
            model.addAttribute("activePage", "stock-livraisons");

            return "stock/livraisons/details";

        } catch (Exception e) {
            log.error("Erreur chargement détails livraison", e);
            return "redirect:/stock/livraisons/liste";
        }
    }

    @GetMapping("/preparation")
    public String preparationCommandes(Model model,
            @RequestParam(required = false) String depotId,
            @RequestParam(required = false) String statut,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        if (!hasAnyRole(session, "MAGASINIER", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", 
        "MANAGER", "ADMIN")) {
        return "redirect:/access-denied";
        }
        
        try {
            // Récupérer les réservations à préparer
            List<ReservationStock> reservations = reservationStockRepository.findByStatut(
                    ReservationStock.ReservationStatus.ACTIVE);

            // Grouper par commande client
            Map<UUID, List<ReservationStock>> reservationsParCommande = reservations.stream()
                    .collect(Collectors.groupingBy(ReservationStock::getCommandeClientId));

            // Calculer les statistiques
            Map<String, Object> stats = new HashMap<>();
            stats.put("nombreCommandes", reservationsParCommande.size());
            stats.put("nombreArticles", reservations.stream()
                    .mapToInt(r -> r.getQuantiteRestante())
                    .sum());
            stats.put("nombreReservations", reservations.size());

            // Récupérer les dépôts pour le filtre
            List<Depot> depots = depotRepository.findByActifTrue();

            model.addAttribute("reservationsParCommande", reservationsParCommande);
            model.addAttribute("stats", stats);
            model.addAttribute("depots", depots);
            model.addAttribute("depotId", depotId);
            model.addAttribute("statut", statut);
            model.addAttribute("title", "Préparation de Commandes");
            model.addAttribute("activePage", "stock-livraisons");

            return "stock/livraisons/preparation";

        } catch (Exception e) {
            log.error("Erreur chargement page préparation", e);
            model.addAttribute("error", "Erreur: " + e.getMessage());
            return "stock/livraisons/preparation";
        }
    }

    @PostMapping("/preparer-commande")
    @ResponseBody
    public Map<String, Object> preparerCommande(@RequestParam UUID commandeClientId,
            @RequestParam UUID utilisateurId) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Récupérer toutes les réservations pour cette commande
            List<ReservationStock> reservations = reservationStockRepository
                    .findByCommandeClientId(commandeClientId);

            // Filtrer celles qui sont actives
            List<ReservationStock> reservationsActives = reservations.stream()
                    .filter(r -> ReservationStock.ReservationStatus.ACTIVE.equals(r.getStatut()))
                    .collect(Collectors.toList());

            if (reservationsActives.isEmpty()) {
                throw new RuntimeException("Aucune réservation active pour cette commande");
            }

            // Créer une session de préparation
            Map<String, Object> sessionPreparation = new HashMap<>();
            sessionPreparation.put("commandeClientId", commandeClientId);
            sessionPreparation.put("utilisateurId", utilisateurId);
            sessionPreparation.put("dateDebut", LocalDateTime.now());
            sessionPreparation.put("reservations", reservationsActives);

            // Retourner les informations pour l'interface
            response.put("success", true);
            response.put("session", sessionPreparation);
            response.put("nombreArticles", reservationsActives.size());
            response.put("message", "Session de préparation démarrée");

        } catch (Exception e) {
            log.error("Erreur démarrage préparation", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }

    @PostMapping("/valider-preparation")
    @ResponseBody
    public Map<String, Object> validerPreparation(@RequestBody Map<String, Object> donnees,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            UUID commandeClientId = UUID.fromString((String) donnees.get("commandeClientId"));
            List<Map<String, Object>> itemsValides = (List<Map<String, Object>>) donnees.get("items");
            String commentaire = (String) donnees.get("commentaire");

            String utilisateurId = (String) session.getAttribute("userId");

            // Pour chaque item validé, créer la livraison
            for (Map<String, Object> item : itemsValides) {
                UUID reservationId = UUID.fromString((String) item.get("reservationId"));
                Integer quantite = (Integer) item.get("quantite");

                // Créer la livraison
                livraisonService.creerSortieStock(reservationId, UUID.fromString(utilisateurId),
                        "Préparation commande " + commandeClientId);
            }

            response.put("success", true);
            response.put("message", "Préparation validée et livraisons créées");

        } catch (Exception e) {
            log.error("Erreur validation préparation", e);
            response.put("success", false);
            response.put("message", "Erreur: " + e.getMessage());
        }

        return response;
    }
}