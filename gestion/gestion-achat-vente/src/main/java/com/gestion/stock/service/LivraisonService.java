// LivraisonService.java
package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LivraisonService {

    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ReservationStockRepository reservationStockRepository;
    private final LotRepository lotRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SequenceGeneratorService sequenceService;

    /**
     * Créer une sortie de stock depuis une réservation
     */
    @Transactional
    public StockMovement creerSortieStock(UUID reservationId, UUID utilisateurId, String motif) {
        ReservationStock reservation = reservationStockRepository.findById(reservationId)
                .orElseThrow(() -> new RuntimeException("Réservation non trouvée: " + reservationId));

                int quantiteALivrer = reservation.getQuantiteRestante();

        log.info("Sortie stock depuis réservation {} - article: {} - depot: {} - reservee: {} - prelevee: {} - restante: {} - statut: {}",
                reservation.getId(),
                reservation.getArticle() != null ? reservation.getArticle().getId() : null,
                reservation.getDepot() != null ? reservation.getDepot().getId() : null,
                reservation.getQuantiteReservee(),
                reservation.getQuantitePrelevee(),
                reservation.getQuantiteRestante(),
                reservation.getStatut());

        // Vérifier si la réservation est toujours active
        if (!ReservationStock.ReservationStatus.ACTIVE.equals(reservation.getStatut())) {
                        log.warn("Réservation inactive: {} - statut: {}", reservation.getId(), reservation.getStatut());
            throw new RuntimeException("La réservation n'est pas active");
        }

        // Vérifier quantité restante
        if (quantiteALivrer <= 0) {
                        log.warn("Réservation sans quantité restante: {} - restante: {}",
                    reservation.getId(), quantiteALivrer);
            throw new RuntimeException("Aucune quantité à livrer");
        }

        // Vérifier stock disponible
        Stock stock = stockRepository.findByArticleIdAndDepotId(
                reservation.getArticle().getId(),
                reservation.getDepot().getId())
                .orElseThrow(() -> new RuntimeException("Stock non trouvé"));

        if (stock.getQuantiteTheorique() < quantiteALivrer) {
            log.warn("Stock insuffisant pour réservation {} - article: {} - depot: {} - theorique: {} - a_livrer: {}",
                    reservation.getId(),
                    reservation.getArticle().getId(),
                    reservation.getDepot().getId(),
                    stock.getQuantiteTheorique(),
                    quantiteALivrer);
            throw new RuntimeException("Stock insuffisant. Théorique: " + stock.getQuantiteTheorique());
        }

        // Allocation du lot (FIFO/FEFO)
        Lot lot = null;
        if (reservation.getLot() != null) {
            lot = reservation.getLot();
        } else {
            lot = allouerLotFEFO(reservation.getArticle(), reservation.getDepot(), quantiteALivrer);
        }

        // Créer le mouvement de sortie
        MovementType typeSortie = movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));

        BigDecimal coutUnitaire = lot != null ? lot.getCoutUnitaire() : stock.getCoutUnitaireMoyen();

        StockMovement mouvement = StockMovement.builder()
                .reference(("MVT-" + LocalDate.now().getYear() +
                                String.format("-%06d", sequenceService.getNextMovementSequence())))
                .type(typeSortie)
                .article(reservation.getArticle())
                .depot(reservation.getDepot())
                .quantite(quantiteALivrer)
                .coutUnitaire(coutUnitaire)
                .lot(lot)
                .commandeClientId(reservation.getCommandeClientId())
                .dateMouvement(LocalDateTime.now())
                .dateComptable(java.time.LocalDate.now())
                .utilisateurId(utilisateurId)
                .motif(motif != null ? motif : "Livraison commande " + reservation.getCommandeClientId())
                .statut(StockMovement.MovementStatus.VALIDE)
                .build();

        StockMovement mouvementCree = stockMovementRepository.save(mouvement);

        // Mettre à jour la réservation
        reservation.setQuantitePrelevee(reservation.getQuantiteReservee());
        reservation.setStatut(ReservationStock.ReservationStatus.PRELEVEE);
        reservationStockRepository.save(reservation);

        // stockRepository.decrementerQuantiteTheorique(
        //         reservation.getArticle().getId(),
        //         reservation.getDepot().getId(),
        //         quantiteALivrer);
        // stockRepository.decrementerQuantiteReservee(
        //         reservation.getArticle().getId(),
        //         reservation.getDepot().getId(),
        //         quantiteALivrer);

        // Mettre à jour la quantité du lot
        if (lot != null) {
                        lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantiteALivrer);
            if (lot.getQuantiteActuelle() == 0) {
                lot.setStatut(Lot.LotStatus.EPUISE);
            }
            lotRepository.save(lot);
        }

        log.info("Sortie stock créée: {} - Article: {} - Quantité: {}",
                mouvement.getReference(),
                reservation.getArticle().getCodeArticle(),
                quantiteALivrer);

        return mouvementCree;
    }

    /**
     * Livraison directe (sans réservation préalable)
     */
    @Transactional
    public StockMovement livrerDirectement(UUID articleId, UUID depotId, Integer quantite,
            UUID commandeClientId, UUID utilisateurId, String motif) {

        // Vérifier stock disponible
        Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                .orElseThrow(() -> new RuntimeException("Stock non trouvé"));

        if (stock.getQuantiteDisponible() < quantite) {
            throw new RuntimeException("Stock insuffisant. Disponible: " + stock.getQuantiteDisponible());
        }

        // Allocation FIFO/FEFO
        Lot lot = allouerLotFEFO(stock.getArticle(), stock.getDepot(), quantite);

        // Créer mouvement
        MovementType typeSortie = movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));

        BigDecimal coutUnitaire = lot != null ? lot.getCoutUnitaire() : stock.getCoutUnitaireMoyen();

        StockMovement mouvement = StockMovement.builder()
                .reference("MVT-" + LocalDate.now().getYear() +
                                String.format("-%06d", sequenceService.getNextMovementSequence()))
                .type(typeSortie)
                .article(stock.getArticle())
                .depot(stock.getDepot())
                .quantite(quantite)
                .coutUnitaire(coutUnitaire)
                .lot(lot)
                .commandeClientId(commandeClientId)
                .dateMouvement(LocalDateTime.now())
                .dateComptable(java.time.LocalDate.now())
                .utilisateurId(utilisateurId)
                .motif(motif != null ? motif : "Livraison directe commande " + commandeClientId)
                .statut(StockMovement.MovementStatus.VALIDE)
                .build();

        StockMovement mouvementCree = stockMovementRepository.save(mouvement);

        // Mettre à jour lot
        if (lot != null) {
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantite);
            if (lot.getQuantiteActuelle() == 0) {
                lot.setStatut(Lot.LotStatus.EPUISE);
            }
            lotRepository.save(lot);
        }

        log.info("Livraison directe: {} - Article: {} - Quantité: {}",
                mouvement.getReference(),
                stock.getArticle().getCodeArticle(),
                quantite);

        return mouvementCree;
    }

    /**
     * Allocation FEFO (First Expired First Out) pour produits périssables
     */
    private Lot allouerLotFEFO(Article article, Depot depot, Integer quantiteRequise) {
        List<Lot> lots = lotRepository.findByArticleAndStatutAndQuantiteActuelleGreaterThanEqual(
                article, Lot.LotStatus.DISPONIBLE, quantiteRequise);

        if (lots.isEmpty()) {
            return null;
        }

        // FEFO: tri par date de péremption la plus proche
        return lots.stream()
                .filter(lot -> lot.getDatePeremption() != null)
                .sorted((l1, l2) -> l1.getDatePeremption().compareTo(l2.getDatePeremption()))
                .findFirst()
                .orElseGet(() -> lots.stream()
                        .sorted((l1, l2) -> l1.getDateReception().compareTo(l2.getDateReception()))
                        .findFirst()
                        .orElse(null));
    }

    /**
     * Générer référence unique pour mouvement
     */
    private String genererReferenceMouvement() {
        String annee = String.valueOf(java.time.LocalDate.now().getYear());
        long sequence = stockMovementRepository.count() + 1;
        return String.format("MVT-%s-%06d", annee, sequence);
    }

    /**
     * Obtenir les réservations à livrer
     */
    public List<ReservationStock> getReservationsALivrer(UUID depotId) {
        if (depotId != null) {
            return reservationStockRepository.findByDepotIdAndStatut(
                    depotId, ReservationStock.ReservationStatus.ACTIVE);
        }
        return reservationStockRepository.findByStatut(ReservationStock.ReservationStatus.ACTIVE);
    }

    /**
     * Obtenir l'historique des livraisons
     */
    public List<StockMovement> getHistoriqueLivraisons(UUID depotId, java.time.LocalDate dateDebut,
            java.time.LocalDate dateFin) {
        MovementType typeLivraison = movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));

        if (depotId != null) {
            return stockMovementRepository.findByTypeAndDepotAndDateComptableBetween(
                    typeLivraison, depotId, dateDebut, dateFin);
        }
        return stockMovementRepository.findByTypeAndDateComptableBetween(
                typeLivraison, dateDebut, dateFin);
    }

    /**
     * Statistiques de livraison
     */
    public Map<String, Object> getStatistiquesLivraisons(java.time.LocalDate dateDebut,
            java.time.LocalDate dateFin) {
        MovementType typeLivraison = movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));

        List<StockMovement> mouvements = stockMovementRepository.findByTypeAndDateComptableBetween(
                typeLivraison, dateDebut, dateFin);

        // Calcul des statistiques
        Integer totalArticles = mouvements.stream()
                .mapToInt(StockMovement::getQuantite)
                .sum();

        BigDecimal totalValeur = mouvements.stream()
                .map(m -> m.getCoutUnitaire().multiply(BigDecimal.valueOf(m.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        long nombreLivraisons = mouvements.stream()
                .map(StockMovement::getCommandeClientId)
                .distinct()
                .count();

        return Map.of(
                "totalArticles", totalArticles,
                "totalValeur", totalValeur,
                "nombreLivraisons", nombreLivraisons,
                "moyenneParLivraison", totalArticles / Math.max(nombreLivraisons, 1));
    }

    public Map<String, Object> getCommandesAPreparer(UUID depotId) {

        List<ReservationStock> reservations;
        if (depotId != null) {
            reservations = reservationStockRepository.findByDepotIdAndStatut(
                    depotId, ReservationStock.ReservationStatus.ACTIVE);
        } else {
            reservations = reservationStockRepository.findByStatut(
                    ReservationStock.ReservationStatus.ACTIVE);
        }

        // Grouper par commande client
        Map<UUID, List<ReservationStock>> parCommande = reservations.stream()
                .collect(Collectors.groupingBy(ReservationStock::getCommandeClientId));

        // Calculer les priorités (urgence, ancienneté, etc.)
        List<Map<String, Object>> commandesAvecPriorite = new ArrayList<>();

        for (Map.Entry<UUID, List<ReservationStock>> entry : parCommande.entrySet()) {
            Map<String, Object> commande = new HashMap<>();
            commande.put("commandeId", entry.getKey());
            commande.put("reservations", entry.getValue());
            commande.put("nombreArticles", entry.getValue().size());
            commande.put("totalQuantite", entry.getValue().stream()
                    .mapToInt(ReservationStock::getQuantiteRestante)
                    .sum());

            // Calculer la priorité
            int priorite = calculerPriorite(entry.getValue());
            commande.put("priorite", priorite);
            commande.put("urgent", priorite >= 8); // Seuil pour "urgent"

            commandesAvecPriorite.add(commande);
        }

        // Trier par priorité décroissante
        commandesAvecPriorite.sort((a, b) -> ((Integer) b.get("priorite")).compareTo((Integer) a.get("priorite")));

        Map<String, Object> result = new HashMap<>();
        result.put("commandes", commandesAvecPriorite);
        result.put("statistiques", calculerStatistiques(reservations));

        return result;
    }

    /**
     * Calculer la priorité d'une commande
     * Facteurs : ancienneté, urgence client, type d'article, etc.
     */
    private int calculerPriorite(List<ReservationStock> reservations) {
        int score = 0;

        // Ancienneté (plus ancienne = plus prioritaire)
        long heuresDepuisReservation = reservations.stream()
                .mapToLong(r -> java.time.Duration.between(
                        r.getDateReservation(), java.time.LocalDateTime.now()).toHours())
                .max()
                .orElse(0);

        if (heuresDepuisReservation > 24)
            score += 3;
        else if (heuresDepuisReservation > 12)
            score += 2;
        else if (heuresDepuisReservation > 6)
            score += 1;

        // Articles périssables
        long articlesPerissables = reservations.stream()
                .filter(r -> r.getArticle().getDureeVieJours() != null)
                .count();

        if (articlesPerissables > 0)
            score += 2;

        // Quantité totale (petites commandes d'abord pour fluidité)
        int totalQuantite = reservations.stream()
                .mapToInt(ReservationStock::getQuantiteRestante)
                .sum();

        if (totalQuantite <= 5)
            score += 2;
        else if (totalQuantite <= 10)
            score += 1;

        return Math.min(score, 10); // Score sur 10
    }

    /**
     * Calculer les statistiques globales
     */
    private Map<String, Object> calculerStatistiques(List<ReservationStock> reservations) {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalReservations", reservations.size());
        stats.put("totalArticles", reservations.stream()
                .mapToInt(ReservationStock::getQuantiteRestante)
                .sum());
        stats.put("nombreCommandes", reservations.stream()
                .map(ReservationStock::getCommandeClientId)
                .distinct()
                .count());

        // Par dépôt
        Map<Object, Integer> parDepot = reservations.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getDepot().getId(),
                        Collectors.summingInt(ReservationStock::getQuantiteRestante)));
        stats.put("parDepot", parDepot);

        return stats;
    }

    /**
     * Obtenir le chemin de prélèvement optimal (algorithme de routing)
     */
    public List<Map<String, Object>> getCheminPrelevement(UUID depotId, List<UUID> reservationIds) {
        // Implémentation simplifiée d'un algorithme de routing

        // Récupérer les réservations
        List<ReservationStock> reservations = reservationIds.stream()
                .map(id -> reservationStockRepository.findById(id).orElseThrow())
                .collect(Collectors.toList());

        // Grouper par emplacement
        Map<String, List<ReservationStock>> parEmplacement = reservations.stream()
                .filter(r -> r.getLot() != null && r.getLot().getEmplacement() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getLot().getEmplacement().getCode()));

        // Ordonner par allée/travée/niveau (algorithme simplifié)
        List<Map<String, Object>> chemin = new ArrayList<>();

        parEmplacement.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .forEach(entry -> {
                    Map<String, Object> etape = new HashMap<>();
                    etape.put("emplacement", entry.getKey());
                    etape.put("articles", entry.getValue().stream()
                            .map(r -> Map.of(
                                    "article", r.getArticle().getCodeArticle(),
                                    "lot", r.getLot().getNumeroLot(),
                                    "quantite", r.getQuantiteRestante()))
                            .collect(Collectors.toList()));
                    chemin.add(etape);
                });

        return chemin;
    }
}