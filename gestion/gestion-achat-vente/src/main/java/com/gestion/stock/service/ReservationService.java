// ReservationService.java (version corrigée)
package com.gestion.stock.service;

import com.gestion.stock.dto.ReservationDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReservationService {
    
    private final ReservationRepository reservationRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final LotRepository lotRepository;
    private final StockRepository stockRepository;
    private final SequenceGeneratorService sequenceService;
    
    /**
     * Obtenir une réservation par ID
     */
    public ReservationStock getReservationById(UUID reservationId) {
        return reservationRepository.findById(reservationId)
            .orElseThrow(() -> new RuntimeException("Réservation non trouvée: " + reservationId));
    }
    
    /**
     * Obtenir les réservations actives pour un article/dépôt
     */
    public List<ReservationStock> getReservationsActives(UUID articleId, UUID depotId) {
        return reservationRepository.findByArticleIdAndDepotId(articleId, depotId).stream()
            .filter(r -> r.getStatut() == ReservationStock.ReservationStatus.ACTIVE)
            .collect(Collectors.toList());
    }
    
    /**
     * Réserver du stock pour une commande client
     */
    @Transactional
    public ReservationStock reserverStock(UUID articleId, UUID depotId, Integer quantite,
                                         UUID commandeClientId, UUID ligneCommandeId,
                                         UUID utilisateurId) {
        
        // 1. Vérifier l'article
        Article article = articleRepository.findById(articleId)
            .orElseThrow(() -> new RuntimeException("Article non trouvé: " + articleId));
        
        // 2. Vérifier le dépôt
        Depot depot = depotRepository.findById(depotId)
            .orElseThrow(() -> new RuntimeException("Dépôt non trouvé: " + depotId));
        
        // 3. Vérifier stock disponible
        Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .orElseThrow(() -> new RuntimeException("Stock non trouvé pour cet article/dépôt"));
        
        // Calculer stock réellement disponible (théorique - réservé)
        Integer quantiteReserveeActive = reservationRepository
            .findQuantiteReserveeActive(articleId, depotId) != null ? 
            reservationRepository.findQuantiteReserveeActive(articleId, depotId) : 0;
        
        Integer quantiteDisponibleReelle = stock.getQuantiteTheorique() - quantiteReserveeActive;
        
        if (quantiteDisponibleReelle < quantite) {
            throw new RuntimeException(
                String.format("Stock insuffisant. Disponible: %d, Demandé: %d", 
                    quantiteDisponibleReelle, quantite)
            );
        }
        
        // 4. Allocation FIFO/FEFO selon méthode
        Lot lotAlloue = null;
        if (article.isGestionParLot()) {
            lotAlloue = allouerLot(articleId, depotId, quantite, article.getMethodeValorisation());
        }
        
        // 5. Créer la réservation
        ReservationStock reservation = ReservationStock.builder()
            .reference(genererReferenceReservation())
            .article(article)
            .depot(depot)
            .quantiteReservee(quantite)
            .lot(lotAlloue)
            .commandeClientId(commandeClientId)
            .ligneCommandeId(ligneCommandeId)
            .statut(ReservationStock.ReservationStatus.ACTIVE)
            .utilisateurId(utilisateurId)
            .dateReservation(LocalDateTime.now())
            .dateExpiration(LocalDateTime.now().plusHours(24)) // Expire dans 24h par défaut
            .build();
        
        ReservationStock savedReservation = reservationRepository.save(reservation);
        
        log.info("Réservation créée: {} - {} x {} pour commande {}", 
            savedReservation.getReference(), quantite, article.getCodeArticle(), commandeClientId);
        
        return savedReservation;
    }
    
    /**
     * Allocation de lot selon méthode FIFO/FEFO
     */
    private Lot allouerLot(UUID articleId, UUID depotId, Integer quantite, String methode) {
        List<Lot> lotsDisponibles = lotRepository.findLotsDisponiblesByArticleAndDepot(
            articleId, depotId, quantite);
        
        if (lotsDisponibles.isEmpty()) {
            throw new RuntimeException("Aucun lot disponible pour allocation");
        }
        
        // Trier selon la méthode
        Comparator<Lot> comparator;
        if ("FEFO".equalsIgnoreCase(methode)) {
            // FEFO: First Expired First Out (pour périssables)
            comparator = Comparator.comparing(Lot::getDatePeremption, 
                Comparator.nullsLast(Comparator.naturalOrder()));
        } else {
            // FIFO par défaut: First In First Out
            comparator = Comparator.comparing(Lot::getDateReception);
        }
        
        return lotsDisponibles.stream()
            .sorted(comparator)
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Erreur lors de l'allocation du lot"));
    }
    
    /**
     * Annuler une réservation
     */
    @Transactional
    public void annulerReservation(UUID reservationId) {
        ReservationStock reservation = getReservationById(reservationId);
        
        if (reservation.getStatut() != ReservationStock.ReservationStatus.ACTIVE) {
            throw new RuntimeException("Seules les réservations ACTIVES peuvent être annulées");
        }
        
        reservation.setStatut(ReservationStock.ReservationStatus.ANNULEE);
        reservationRepository.save(reservation);
        
        log.info("Réservation annulée: {}", reservation.getReference());
    }
    
    /**
     * Marquer une réservation comme prélevée (après livraison)
     */
    @Transactional
    public void marquerPrelevee(UUID reservationId, Integer quantitePrelevee) {
        ReservationStock reservation = getReservationById(reservationId);
        
        if (quantitePrelevee > reservation.getQuantiteReservee()) {
            throw new RuntimeException("Quantité prélevée supérieure à la quantité réservée");
        }
        
        reservation.setQuantitePrelevee(quantitePrelevee);
        
        // Si toute la quantité est prélevée, changer le statut
        if (quantitePrelevee.equals(reservation.getQuantiteReservee())) {
            reservation.setStatut(ReservationStock.ReservationStatus.PRELEVEE);
        }
        
        reservationRepository.save(reservation);
        
        log.info("Réservation prélevée: {} - {}/{}", 
            reservation.getReference(), quantitePrelevee, reservation.getQuantiteReservee());
    }
    
    /**
     * Recherche avec filtres
     */
    public Page<ReservationStock> rechercherReservations(UUID articleId, UUID depotId, 
                                                        String statutStr, UUID commandeClientId,
                                                        Pageable pageable) {
        
        ReservationStock.ReservationStatus statut = null;
        if (statutStr != null && !statutStr.isEmpty()) {
            try {
                statut = ReservationStock.ReservationStatus.valueOf(statutStr);
            } catch (IllegalArgumentException e) {
                log.warn("Statut invalide: {}", statutStr);
            }
        }
        
        return reservationRepository.findWithFilters(
            articleId, depotId, statut, commandeClientId, pageable);
    }
    
    /**
     * Obtenir les réservations d'une commande
     */
    public List<ReservationStock> getReservationsByCommande(UUID commandeClientId) {
        return reservationRepository.findByCommandeClientId(commandeClientId);
    }
    
    /**
     * Nettoyer les réservations expirées (batch quotidien)
     */
    @Transactional
    public int nettoyerReservationsExpirees() {
        List<ReservationStock> expired = reservationRepository
            .findExpiredReservations(LocalDateTime.now());
        
        expired.forEach(r -> r.setStatut(ReservationStock.ReservationStatus.EXPIREE));
        reservationRepository.saveAll(expired);
        
        log.info("{} réservations expirées nettoyées", expired.size());
        return expired.size();
    }
    
    /**
     * Vérifier disponibilité pour réservation
     */
    public Map<String, Object> verifierDisponibilite(UUID articleId, UUID depotId, Integer quantite) {
        Map<String, Object> result = new HashMap<>();
        
        Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .orElse(null);
        
        if (stock == null) {
            result.put("disponible", false);
            result.put("quantiteDisponible", 0);
            result.put("message", "Aucun stock pour cet article/dépôt");
            return result;
        }
        
        Integer quantiteReserveeActive = reservationRepository
            .findQuantiteReserveeActive(articleId, depotId) != null ? 
            reservationRepository.findQuantiteReserveeActive(articleId, depotId) : 0;
        
        Integer quantiteDisponibleReelle = stock.getQuantiteTheorique() - quantiteReserveeActive;
        
        result.put("disponible", quantiteDisponibleReelle >= quantite);
        result.put("quantiteDisponible", quantiteDisponibleReelle);
        result.put("quantiteTheorique", stock.getQuantiteTheorique());
        result.put("quantiteReservee", quantiteReserveeActive);
        
        if (quantiteDisponibleReelle < quantite) {
            result.put("message", 
                String.format("Stock insuffisant. Disponible: %d, Demandé: %d", 
                    quantiteDisponibleReelle, quantite));
        } else {
            result.put("message", "Stock disponible");
        }
        
        return result;
    }
    
    /**
     * Convertir Reservation en DTO
     */
    public ReservationDTO toDTO(ReservationStock reservation) {
        if (reservation == null) {
            return null;
        }
        
        return ReservationDTO.builder()
            .id(reservation.getId())
            .reference(reservation.getReference())
            .articleId(reservation.getArticle() != null ? reservation.getArticle().getId() : null)
            .articleCode(reservation.getArticle() != null ? reservation.getArticle().getCodeArticle() : null)
            .articleLibelle(reservation.getArticle() != null ? reservation.getArticle().getLibelle() : null)
            .depotId(reservation.getDepot() != null ? reservation.getDepot().getId() : null)
            .depotCode(reservation.getDepot() != null ? reservation.getDepot().getCode() : null)
            .depotNom(reservation.getDepot() != null ? reservation.getDepot().getNom() : null)
            .quantiteReservee(reservation.getQuantiteReservee())
            .quantitePrelevee(reservation.getQuantitePrelevee())
            .quantiteRestante(reservation.getQuantiteRestante())
            .lotId(reservation.getLot() != null ? reservation.getLot().getId() : null)
            .lotNumero(reservation.getLot() != null ? reservation.getLot().getNumeroLot() : null)
            .commandeClientId(reservation.getCommandeClientId())
            .ligneCommandeId(reservation.getLigneCommandeId())
            .dateReservation(reservation.getDateReservation())
            .dateExpiration(reservation.getDateExpiration())
            .statut(reservation.getStatut() != null ? reservation.getStatut().toString() : null)
            .utilisateurId(reservation.getUtilisateurId())
            .createdAt(reservation.getCreatedAt())
            .build();
    }
    
    /**
     * Générer référence unique
     */
    private String genererReferenceReservation() {
        Long sequence = sequenceService.getNextReservationSequence();
        return String.format("RES-%d-%06d", 
            LocalDateTime.now().getYear(), sequence);
    }
    
    /**
     * Recherche des réservations avec critères multiples
     */
    public List<ReservationDTO> searchReservations(UUID articleId, UUID depotId, 
                                                  String statut, LocalDateTime dateFrom, 
                                                  LocalDateTime dateTo) {
        
        List<ReservationStock> reservations;
        
        if (articleId != null && depotId != null) {
            reservations = reservationRepository.findByArticleIdAndDepotId(articleId, depotId);
        } else if (articleId != null) {
            // Implémenter findByArticleId si nécessaire
            reservations = reservationRepository.findAll().stream()
                .filter(r -> r.getArticle().getId().equals(articleId))
                .collect(Collectors.toList());
        } else if (depotId != null) {
            // Implémenter findByDepotId si nécessaire
            reservations = reservationRepository.findAll().stream()
                .filter(r -> r.getDepot().getId().equals(depotId))
                .collect(Collectors.toList());
        } else {
            reservations = reservationRepository.findAll();
        }
        
        // Filtrer par statut si fourni
        if (statut != null && !statut.isEmpty()) {
            try {
                ReservationStock.ReservationStatus statusEnum = 
                    ReservationStock.ReservationStatus.valueOf(statut);
                reservations = reservations.stream()
                    .filter(r -> r.getStatut() == statusEnum)
                    .collect(Collectors.toList());
            } catch (IllegalArgumentException e) {
                log.warn("Statut invalide pour le filtre: {}", statut);
            }
        }
        
        // Filtrer par date
        if (dateFrom != null) {
            reservations = reservations.stream()
                .filter(r -> r.getDateReservation() != null && 
                           r.getDateReservation().isAfter(dateFrom))
                .collect(Collectors.toList());
        }
        
        if (dateTo != null) {
            reservations = reservations.stream()
                .filter(r -> r.getDateReservation() != null && 
                           r.getDateReservation().isBefore(dateTo))
                .collect(Collectors.toList());
        }
        
        return reservations.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    /**
     * Obtenir le total des quantités réservées pour un article/dépôt
     */
    public Integer getQuantiteTotaleReservee(UUID articleId, UUID depotId) {
        List<ReservationStock> reservationsActives = getReservationsActives(articleId, depotId);
        return reservationsActives.stream()
            .mapToInt(ReservationStock::getQuantiteReservee)
            .sum();
    }
    
    /**
     * Étendre la durée d'une réservation
     */
    @Transactional
    public void prolongerReservation(UUID reservationId, Integer heuresSupplementaires) {
        ReservationStock reservation = getReservationById(reservationId);
        
        if (reservation.getStatut() != ReservationStock.ReservationStatus.ACTIVE) {
            throw new RuntimeException("Seules les réservations ACTIVES peuvent être prolongées");
        }
        
        LocalDateTime nouvelleExpiration = reservation.getDateExpiration() != null ?
            reservation.getDateExpiration().plusHours(heuresSupplementaires) :
            LocalDateTime.now().plusHours(heuresSupplementaires);
            
        reservation.setDateExpiration(nouvelleExpiration);
        reservationRepository.save(reservation);
        
        log.info("Réservation {} prolongée jusqu'au {}", 
            reservation.getReference(), nouvelleExpiration);
    }
}