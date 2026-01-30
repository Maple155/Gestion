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
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class LivraisonService {
    
    private final StockMovementRepository movementRepository;
    private final ReservationStockRepository reservationRepository;
    private final StockRepository stockRepository;
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SequenceGeneratorService sequenceService;
    
    @Transactional
    public StockMovement creerSortieStock(UUID reservationId, UUID utilisateurId, String motif) {
        
        log.info("Création sortie stock pour réservation: {}", reservationId);
        
        // 1. Récupérer la réservation
        ReservationStock reservation = reservationRepository.findById(reservationId)
            .orElseThrow(() -> new RuntimeException("Réservation non trouvée"));
        
        // 2. Vérifier que la réservation est active
        if (reservation.getStatut() != ReservationStock.ReservationStatus.ACTIVE) {
            throw new RuntimeException("La réservation n'est pas active");
        }
        
        Integer quantiteALivrer = reservation.getQuantiteRestante();
        if (quantiteALivrer <= 0) {
            throw new RuntimeException("Aucune quantité à livrer");
        }
        
        // 3. Récupérer les données
        Article article = reservation.getArticle();
        Depot depot = reservation.getDepot();
        Lot lot = reservation.getLot();
        UUID commandeClientId = reservation.getCommandeClientId();
        
        // 4. Déterminer le coût unitaire (coût du lot ou CUMP)
        BigDecimal coutUnitaire;
        if (lot != null) {
            coutUnitaire = lot.getCoutUnitaire();
        } else {
            coutUnitaire = stockRepository.findByArticleIdAndDepotId(article.getId(), depot.getId())
                .map(Stock::getCoutUnitaireMoyen)
                .orElse(article.getCoutStandard());
        }
        
        // 5. Créer le mouvement de sortie
        StockMovement mouvement = StockMovement.builder()
            .reference(genererReferenceMouvement())
            .type(movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé")))
            .article(article)
            .depot(depot)
            .quantite(quantiteALivrer)
            .coutUnitaire(coutUnitaire)
            .lot(lot)
            .commandeClientId(commandeClientId)
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(utilisateurId)
            .motif(motif != null ? motif : "Livraison commande client " + commandeClientId)
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        StockMovement mouvementSauvegarde = movementRepository.save(mouvement);
        
        // 6. Mettre à jour le stock théorique
        stockRepository.decrementerQuantiteTheorique(article.getId(), depot.getId(), quantiteALivrer);
        
        // 7. Mettre à jour la quantité réservée
        stockRepository.decrementerQuantiteReservee(article.getId(), depot.getId(), quantiteALivrer);
        
        // 8. Mettre à jour le lot si nécessaire
        if (lot != null) {
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantiteALivrer);
            if (lot.getQuantiteActuelle() == 0) {
                lot.setStatut(Lot.LotStatus.EPUISE);
            }
            lotRepository.save(lot);
        }
        
        // 9. Mettre à jour la réservation
        int nouvelleQuantitePrelevee = reservation.getQuantitePrelevee() + quantiteALivrer;
        reservation.setQuantitePrelevee(nouvelleQuantitePrelevee);
        
        if (reservation.getQuantiteRestante() == 0) {
            reservation.setStatut(ReservationStock.ReservationStatus.PRELEVEE);
        }
        
        reservationRepository.save(reservation);
        
        log.info("Sortie stock créée avec succès: {} pour commande: {}", 
                mouvementSauvegarde.getReference(), commandeClientId);
        
        return mouvementSauvegarde;
    }
    
    @Transactional
    public StockMovement livrerDirectement(UUID articleId, UUID depotId, 
                                          Integer quantite, UUID commandeClientId,
                                          UUID utilisateurId, String motif) {
        
        // Vérifier disponibilité
        boolean disponible = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .map(stock -> stock.getQuantiteDisponible() >= quantite)
            .orElse(false);
        
        if (!disponible) {
            throw new RuntimeException("Stock insuffisant");
        }
        
        // Récupérer article, dépôt et déterminer lot
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        
        Depot depot = depotRepository.findById(depotId)
            .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));
        
        Lot lot = allouerLotPourLivraison(articleId, depotId, quantite);
        BigDecimal coutUnitaire = (lot != null) ? lot.getCoutUnitaire() : 
            stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                .map(Stock::getCoutUnitaireMoyen)
                .orElse(article.getCoutStandard());
        
        // Créer mouvement
        StockMovement mouvement = StockMovement.builder()
            .reference(genererReferenceMouvement())
            .type(movementTypeRepository.findByCode("LIVRAISON_CLIENT")
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé")))
            .article(article)
            .depot(depot)
            .quantite(quantite)
            .coutUnitaire(coutUnitaire)
            .lot(lot)
            .commandeClientId(commandeClientId)
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(utilisateurId)
            .motif(motif)
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        StockMovement mouvementSauvegarde = movementRepository.save(mouvement);
        
        // Mettre à jour stock
        stockRepository.decrementerQuantiteTheorique(articleId, depotId, quantite);
        
        // Mettre à jour lot si nécessaire
        if (lot != null) {
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - quantite);
            if (lot.getQuantiteActuelle() == 0) {
                lot.setStatut(Lot.LotStatus.EPUISE);
            }
            lotRepository.save(lot);
        }
        
        return mouvementSauvegarde;
    }
    
    private Lot allouerLotPourLivraison(UUID articleId, UUID depotId, Integer quantite) {
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        
        if (!article.isGestionParLot()) {
            return null;
        }
        
        List<Lot> lots = lotRepository.findByArticleIdAndStatutOrderByDateReceptionAsc(
            articleId, Lot.LotStatus.DISPONIBLE);
        
        for (Lot lot : lots) {
            if (lot.getQuantiteActuelle() >= quantite) {
                return lot;
            }
        }
        
        return null;
    }
    
    private String genererReferenceMouvement() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextMovementSequence());
        return "MVT-" + annee + "-" + sequence;
    }
}