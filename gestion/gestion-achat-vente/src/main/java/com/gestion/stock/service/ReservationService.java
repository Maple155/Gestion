package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private final ReservationStockRepository reservationRepository;
    private final StockRepository stockRepository;
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final SequenceGeneratorService sequenceService;
    
    @Transactional
    public ReservationStock reserverStock(UUID articleId, UUID depotId, 
                                         Integer quantite, UUID commandeClientId,
                                         UUID ligneCommandeId, UUID utilisateurId) {
        
        log.info("Réservation stock - Article: {}, Quantité: {}, Commande: {}", 
                articleId, quantite, commandeClientId);
        
        // 1. Vérifier disponibilité
        boolean disponible = verifierDisponibilite(articleId, depotId, quantite);
        if (!disponible) {
            throw new RuntimeException("Stock insuffisant pour l'article: " + articleId);
        }
        
        // 2. Récupérer article et dépôt
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        
        Depot depot = depotRepository.findById(depotId)
            .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));
        
        // 3. Allouer un lot selon FIFO/FEFO
        Lot lotAlloue = allouerLot(articleId, depotId, quantite);
        
        // 4. Créer la réservation
        ReservationStock reservation = ReservationStock.builder()
            .reference(genererReferenceReservation())
            .article(article)
            .depot(depot)
            .quantiteReservee(quantite)
            .lot(lotAlloue)
            .commandeClientId(commandeClientId)
            .ligneCommandeId(ligneCommandeId)
            .dateLivraisonPrevue(LocalDateTime.now().plusDays(3).toLocalDate())
            .statut(ReservationStock.ReservationStatus.ACTIVE)
            .utilisateurId(utilisateurId)
            .build();
        
        ReservationStock reservationSauvegarde = reservationRepository.save(reservation);
        
        // 5. Mettre à jour la quantité réservée dans le stock
        stockRepository.incrementerQuantiteReservee(articleId, depotId, quantite);
        
        log.info("Réservation créée: {} pour commande: {}", 
                reservationSauvegarde.getReference(), commandeClientId);
        
        return reservationSauvegarde;
    }
    
    /**
     * Allouer un lot selon FIFO ou FEFO
     */
    private Lot allouerLot(UUID articleId, UUID depotId, Integer quantiteRequise) {
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        
        // Si l'article n'est pas géré par lot, retourner null
        if (!article.isGestionParLot()) {
            return null;
        }
        
        List<Lot> lotsDisponibles;
        
        // Choix de la méthode d'allocation
        if ("FEFO".equals(article.getMethodeValorisation())) {
            // FEFO pour produits périssables (plus proche de la péremption)
            lotsDisponibles = lotRepository.findLotsFEFO(articleId, quantiteRequise);
        } else {
            // FIFO par défaut (plus ancien)
            lotsDisponibles = lotRepository.findByArticleIdAndStatutOrderByDateReceptionAsc(
                articleId, Lot.LotStatus.DISPONIBLE);
        }
        
        // Trouver le premier lot avec quantité suffisante
        for (Lot lot : lotsDisponibles) {
            if (lot.getQuantiteActuelle() >= quantiteRequise) {
                return lot;
            }
        }
        
        // Si aucun lot n'a la quantité suffisante, on peut combiner plusieurs lots
        // (implémentation simplifiée)
        if (!lotsDisponibles.isEmpty()) {
            log.warn("Aucun lot n'a la quantité suffisante, allocation partielle possible");
            return lotsDisponibles.get(0);
        }
        
        return null;
    }
    
    @Transactional
    public void annulerReservation(UUID reservationId) {
        ReservationStock reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));
        
        if (reservation.getStatut() != ReservationStock.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La réservation n'est pas active");
        }
        
        // Libérer la quantité réservée
        stockRepository.decrementerQuantiteReservee(
            reservation.getArticle().getId(),
            reservation.getDepot().getId(),
            reservation.getQuantiteReservee() - reservation.getQuantitePrelevee());
        
        // Marquer comme annulée
        reservation.setStatut(ReservationStock.ReservationStatus.ANNULEE);
        reservationRepository.save(reservation);
        
        log.info("Réservation annulée: {}", reservationId);
    }
    
    public boolean verifierDisponibilite(UUID articleId, UUID depotId, Integer quantiteRequise) {
        return stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .map(stock -> {
                Integer quantiteDisponible = stock.getQuantiteTheorique() - stock.getQuantiteReservee();
                return quantiteDisponible >= quantiteRequise;
            })
            .orElse(false);
    }
    
    private String genererReferenceReservation() {
        String annee = String.valueOf(LocalDateTime.now().getYear());
        String sequence = String.format("%04d", sequenceService.getNextReservationSequence());
        return "RES-" + annee + "-" + sequence;
    }
    
    @Transactional
    public void nettoyerReservationsExpirees() {
        List<ReservationStock> reservationsExpirees = reservationRepository
            .findReservationsExpirees(LocalDateTime.now());
        
        for (ReservationStock reservation : reservationsExpirees) {
            annulerReservation(reservation.getId());
        }
        
        log.info("{} réservations expirées nettoyées", reservationsExpirees.size());
    }
}