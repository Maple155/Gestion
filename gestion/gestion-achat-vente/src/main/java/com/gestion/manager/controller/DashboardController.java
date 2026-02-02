// package com.gestion.manager.controller;

// import java.util.List;
// import java.util.Map;
// import java.util.stream.Collectors;

// import org.springframework.stereotype.Controller;
// import org.springframework.ui.Model;
// import org.springframework.web.bind.annotation.GetMapping;
// import org.springframework.web.bind.annotation.RequestMapping;

// import com.gestion.achat.entity.BonCommande;
// import com.gestion.achat.enums.StatutFinance;
// import com.gestion.achat.service.BonCommandeService;
// import com.gestion.manager.dto.DashboardStatsDTO;
// import com.gestion.stock.entity.ReservationStock;
// import com.gestion.stock.entity.ReservationStock.ReservationStatus;
// import com.gestion.stock.entity.StockMovement;
// import com.gestion.stock.entity.StockMovement.MovementStatus;
// import com.gestion.stock.entity.Transfert;
// import com.gestion.stock.entity.Transfert.TransfertStatut;
// import com.gestion.stock.service.ReservationStockService;
// import com.gestion.stock.service.StockMovementService;
// import com.gestion.stock.service.TransfertService;

// import lombok.RequiredArgsConstructor;
// import lombok.extern.slf4j.Slf4j;

// /**
//  * Contrôleur principal du tableau de bord
//  * Centralise l'affichage de toutes les données de gestion
//  */
// @Controller
// @RequestMapping("/dashboard")
// @RequiredArgsConstructor
// @Slf4j
// public class DashboardController {

//     private final StockMovementService stockMovementService;
//     private final ReservationStockService reservationStockService;
//     private final TransfertService transfertService;
//     private final BonCommandeService bonCommandeService;

//     /**
//      * Affiche le tableau de bord principal avec toutes les données et statistiques
//      * 
//      * @param model Le modèle Spring MVC
//      * @return Le nom de la vue à afficher
//      */
//     @GetMapping
//     public String showDashboard(Model model) {
//         try {
//             log.info("Début du chargement du tableau de bord");
            
//             // Récupération de toutes les données
//             List<StockMovement> mouvements = stockMovementService.getAll();
//             List<ReservationStock> reservations = reservationStockService.getAll();
//             List<Transfert> transferts = transfertService.getAll();
//             List<BonCommande> bonsCommande = bonCommandeService.findAll();
            
//             // Ajout des listes complètes au modèle pour l'affichage des tableaux
//             model.addAttribute("mvtStock", mouvements);
//             model.addAttribute("resStock", reservations);
//             model.addAttribute("transferts", transferts);
//             model.addAttribute("bdc", bonsCommande);
            
//             // Calcul et ajout des statistiques
//             DashboardStatsDTO stats = calculateStatistics(mouvements, reservations, transferts, bonsCommande);
//             model.addAttribute("stats", stats);
            
//             log.info("Tableau de bord chargé avec succès - Mouvements: {}, Réservations: {}, Transferts: {}, Bons de commande: {}", 
//                      mouvements.size(), reservations.size(), transferts.size(), bonsCommande.size());
            
//             return "dashboard";
            
//         } catch (Exception e) {
//             log.error("Erreur lors du chargement du tableau de bord", e);
//             model.addAttribute("errorMessage", "Impossible de charger le tableau de bord. Veuillez réessayer.");
//             model.addAttribute("errorDetails", e.getMessage());
//             return "error";
//         }
//     }
    
//     /**
//      * Endpoint alternatif pour l'accueil
//      */
//     @GetMapping("/home")
//     public String showHome(Model model) {
//         return showDashboard(model);
//     }
    
//     /**
//      * Page d'accueil racine qui redirige vers le dashboard
//      */
//     @GetMapping("/")
//     public String index() {
//         return "redirect:/dashboard";
//     }
    
//     /**
//      * Calcule toutes les statistiques nécessaires pour le dashboard
//      * 
//      * @param mouvements Liste des mouvements de stock
//      * @param reservations Liste des réservations
//      * @param transferts Liste des transferts
//      * @param bonsCommande Liste des bons de commande
//      * @return Un DTO contenant toutes les statistiques calculées
//      */
//     private DashboardStatsDTO calculateStatistics(
//             List<StockMovement> mouvements,
//             List<ReservationStock> reservations,
//             List<Transfert> transferts,
//             List<BonCommande> bonsCommande) {
        
//         log.debug("Calcul des statistiques du dashboard");
        
//         // Statistiques par statut - Mouvements
//         Map<MovementStatus, Long> mouvementsByStatus = mouvements.stream()
//             .collect(Collectors.groupingBy(
//                 StockMovement::getStatut,
//                 Collectors.counting()
//             ));
        
//         // Statistiques par statut - Réservations
//         Map<ReservationStatus, Long> reservationsByStatus = reservations.stream()
//             .collect(Collectors.groupingBy(
//                 ReservationStock::getStatut,
//                 Collectors.counting()
//             ));
        
//         // Statistiques par statut - Transferts
//         Map<TransfertStatut, Long> transfertsByStatus = transferts.stream()
//             .collect(Collectors.groupingBy(
//                 Transfert::getStatut,
//                 Collectors.counting()
//             ));
        
//         // Statistiques par statut - Bons de commande
//         Map<StatutFinance, Long> bdcByStatus = bonsCommande.stream()
//             .collect(Collectors.groupingBy(
//                 BonCommande::getStatutFinance,
//                 Collectors.counting()
//             ));
        
//         // Calcul des statistiques spécifiques
//         long mouvementsEnAttente = mouvements.stream()
//             .filter(m -> m.getStatut() == MovementStatus.PENDING || m.getStatut() == MovementStatus.PLANNED)
//             .count();
        
//         long reservationsActives = reservations.stream()
//             .filter(r -> r.getStatut() == ReservationStatus.ACTIVE)
//             .count();
        
//         long transfertsEnCours = transferts.stream()
//             .filter(t -> t.getStatut() == TransfertStatut.EN_ATTENTE || 
//                         t.getStatut() == TransfertStatut.VALIDE || 
//                         t.getStatut() == TransfertStatut.EN_TRANSIT)
//             .count();
        
//         long bdcEnAttente = bonsCommande.stream()
//             .filter(bc -> bc.getStatutFinance() == StatutFinance.EN_ATTENTE)
//             .count();
        
//         // Construction du DTO avec toutes les statistiques
//         return DashboardStatsDTO.builder()
//             .totalMouvements(mouvements.size())
//             .totalReservations(reservations.size())
//             .totalTransferts(transferts.size())
//             .totalBonsCommande(bonsCommande.size())
//             .mouvementsByStatus(mouvementsByStatus)
//             .reservationsByStatus(reservationsByStatus)
//             .transfertsByStatus(transfertsByStatus)
//             .bdcByStatus(bdcByStatus)
//             .mouvementsEnAttente(mouvementsEnAttente)
//             .reservationsActives(reservationsActives)
//             .transfertsEnCours(transfertsEnCours)
//             .bdcEnAttente(bdcEnAttente)
//             .build();
//     }
// }