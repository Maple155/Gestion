package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.entity.Transfert.TransfertStatut;
import com.gestion.stock.repository.*;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import com.gestion.stock.dto.LigneTransfertDTO;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransfertService {
    
    private final TransfertRepository transfertRepository;
    private final LigneTransfertRepository ligneTransfertRepository;
    private final StockMovementRepository mouvementRepository;
    private final StockRepository stockRepository;
    private final LotRepository lotRepository;
    private final DepotRepository depotRepository;
    private final ArticleRepository articleRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final LotService lotService;
    private final SequenceGeneratorService sequenceService;
    
    /**
     * Créer une demande de transfert
     */
    @Transactional
    public Transfert creerTransfert(UUID depotSourceId, UUID depotDestinationId,
                                   List<LigneTransfertDTO> lignes, String motif,
                                   UUID demandeurId) {
        
        log.info("Création transfert: {} → {}", depotSourceId, depotDestinationId);
        
        // Vérifier que les dépôts sont différents
        if (depotSourceId.equals(depotDestinationId)) {
            throw new RuntimeException("Les dépôts source et destination doivent être différents");
        }
        
        // Vérifier la disponibilité des stocks pour chaque ligne
        for (LigneTransfertDTO ligne : lignes) {
            boolean disponible = verifierDisponibilite(
                UUID.fromString(ligne.getArticleId()), depotSourceId, ligne.getQuantiteDemandee());
            
            if (!disponible) {
                Article article = articleRepository.findById(UUID.fromString(ligne.getArticleId()))
                    .orElseThrow(() -> new RuntimeException("Article non trouvé"));
                throw new RuntimeException("Stock insuffisant pour l'article: " + article.getCodeArticle());
            }
        }
        
        // Créer le transfert
        String reference = "TRF-" + LocalDate.now().getYear() + 
                          String.format("-%04d", sequenceService.getNextTransfertSequence());
        
        Depot depotSource = depotRepository.findById(depotSourceId)
            .orElseThrow(() -> new RuntimeException("Dépôt source non trouvé"));
        Depot depotDestination = depotRepository.findById(depotDestinationId)
            .orElseThrow(() -> new RuntimeException("Dépôt destination non trouvé"));
        
        Transfert transfert = Transfert.builder()
            .reference(reference)
            .depotSource(depotSource)
            .depotDestination(depotDestination)
            .dateDemande(LocalDateTime.now())
            .dateReceptionPrevue(LocalDate.now().plusDays(2))
            .statut(Transfert.TransfertStatut.BROUILLON)
            .motif(motif)
            .demandeurId(demandeurId)
            .build();
        
        Transfert transfertSauvegarde = transfertRepository.save(transfert);
        
        // Créer les lignes de transfert
        for (LigneTransfertDTO ligneDTO : lignes) {
            Article article = articleRepository.findById(UUID.fromString(ligneDTO.getArticleId()))
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));
            
            LigneTransfert ligne = LigneTransfert.builder()
                .transfert(transfertSauvegarde)
                .article(article)
                .quantiteDemandee(ligneDTO.getQuantiteDemandee())
                .quantiteExpediee(0)
                .quantiteRecue(0)
                .lot(ligneDTO.getLotId() != null ? 
                     lotRepository.findById(UUID.fromString(ligneDTO.getLotId())).orElse(null) : null)
                .build();
            
            ligneTransfertRepository.save(ligne);
        }
        
        log.info("Transfert créé: {} avec {} lignes", reference, lignes.size());
        return transfertSauvegarde;
    }
    
    /**
     * Valider un transfert (première validation)
     */
    @Transactional
    public void validerTransfert(UUID transfertId, UUID valideurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est en statut BROUILLON
        if (transfert.getStatut() != Transfert.TransfertStatut.BROUILLON) {
            throw new RuntimeException("Le transfert n'est pas en statut BROUILLON");
        }
        
        // Vérifier séparation des tâches (demandeur ≠ valideur)
        if (transfert.getDemandeurId().equals(valideurId)) {
            throw new RuntimeException("Le demandeur ne peut pas valider son propre transfert");
        }
        
        // Mettre à jour le statut
        transfert.setStatut(Transfert.TransfertStatut.VALIDE);
        transfert.setValideurId(valideurId);
        transfert.setDateValidation(LocalDateTime.now());
        
        transfertRepository.save(transfert);
        log.info("Transfert validé: {} par {}", transfert.getReference(), valideurId);
    }
    
    /**
     * Expédier un transfert (créer mouvements de sortie)
     */
    @Transactional
    public void expedierTransfert(UUID transfertId, UUID expéditeurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est VALIDE
        if (transfert.getStatut() != Transfert.TransfertStatut.VALIDE) {
            throw new RuntimeException("Le transfert doit être validé avant expédition");
        }
        
        List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
        
        // Pour chaque ligne, créer un mouvement de sortie
        for (LigneTransfert ligne : lignes) {
            // Vérifier disponibilité finale
            boolean disponible = verifierDisponibilite(
                ligne.getArticle().getId(), 
                transfert.getDepotSource().getId(), 
                ligne.getQuantiteDemandee());
            
            if (!disponible) {
                throw new RuntimeException("Stock insuffisant pour expédier l'article: " + 
                    ligne.getArticle().getCodeArticle());
            }
            
            // Créer mouvement de sortie
            StockMovement mouvementSortie = creerMouvementTransfertSortant(
                ligne, transfert, expéditeurId);
            
            // Mettre à jour la ligne avec la quantité expédiée
            ligne.setQuantiteExpediee(ligne.getQuantiteDemandee());
            ligneTransfertRepository.save(ligne);
            
            // Mettre à jour le stock source
            stockRepository.decrementerQuantiteTheorique(
                ligne.getArticle().getId(),
                transfert.getDepotSource().getId(),
                ligne.getQuantiteDemandee());
            
            // Mettre à jour le lot si nécessaire
            if (ligne.getLot() != null) {
                lotService.mettreAJourQuantiteLot(
                    ligne.getLot().getId(), ligne.getQuantiteDemandee(), true);
            }
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.EXPEDIE);
        transfert.setDateExpedition(LocalDate.now());
        transfert.setExpediteurId(expéditeurId);
        transfertRepository.save(transfert);
        
        log.info("Transfert expédié: {}", transfert.getReference());
    }
    
    /**
     * Réceptionner un transfert (créer mouvements d'entrée)
     */
    @Transactional
    public void receptionnerTransfert(UUID transfertId, UUID receptionnaireId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est EXPEDIE
        if (transfert.getStatut() != Transfert.TransfertStatut.EXPEDIE) {
            throw new RuntimeException("Le transfert doit être expédié avant réception");
        }
        
        List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
        
        // Pour chaque ligne, créer un mouvement d'entrée
        for (LigneTransfert ligne : lignes) {
            // Créer mouvement d'entrée
            StockMovement mouvementEntree = creerMouvementTransfertEntrant(
                ligne, transfert, receptionnaireId);
            
            // Mettre à jour la ligne avec la quantité reçue
            ligne.setQuantiteRecue(ligne.getQuantiteExpediee());
            ligneTransfertRepository.save(ligne);
            
            // Mettre à jour le stock destination
            stockRepository.incrementerQuantiteTheorique(
                ligne.getArticle().getId(),
                transfert.getDepotDestination().getId(),
                ligne.getQuantiteExpediee());
            
            // Créer un nouveau lot dans le dépôt destination si nécessaire
            if (ligne.getLot() != null) {
                creerLotDestination(ligne, transfert.getDepotDestination());
            }
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.RECEPTIONNE);
        transfert.setDateReceptionReelle(LocalDateTime.now());
        transfert.setReceptionnaireId(receptionnaireId);
        transfertRepository.save(transfert);
        
        log.info("Transfert réceptionné: {}", transfert.getReference());
    }
    
    /**
     * Annuler un transfert
     */
    @Transactional
    public void annulerTransfert(UUID transfertId, String motifAnnulation, UUID utilisateurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert peut être annulé
        if (transfert.getStatut() == Transfert.TransfertStatut.RECEPTIONNE) {
            throw new RuntimeException("Un transfert réceptionné ne peut pas être annulé");
        }
        
        // Si le transfert a été expédié, il faut annuler les mouvements
        if (transfert.getStatut() == Transfert.TransfertStatut.EXPEDIE) {
            annulerMouvementsTransfert(transfertId);
            
            // Restaurer les stocks source
            List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
            for (LigneTransfert ligne : lignes) {
                stockRepository.incrementerQuantiteTheorique(
                    ligne.getArticle().getId(),
                    transfert.getDepotSource().getId(),
                    ligne.getQuantiteExpediee());
                
                // Restaurer le lot si nécessaire
                if (ligne.getLot() != null) {
                    lotService.mettreAJourQuantiteLot(
                        ligne.getLot().getId(), ligne.getQuantiteExpediee(), false);
                }
            }
        }
        
        // Marquer comme annulé
        transfert.setStatut(Transfert.TransfertStatut.ANNULE);
        transfert.setMotifAnnulation(motifAnnulation);
        transfert.setDateAnnulation(LocalDateTime.now());
        transfert.setAnnulateurId(utilisateurId);
        
        transfertRepository.save(transfert);
        log.info("Transfert annulé: {}", transfert.getReference());
    }
    
    /**
     * Vérifier disponibilité stock
     */
    private boolean verifierDisponibilite(UUID articleId, UUID depotId, Integer quantite) {
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);
        return stockOpt.map(stock -> stock.getQuantiteDisponible() >= quantite)
                      .orElse(false);
    }
    
    /**
     * Créer mouvement de transfert sortant
     */
    private StockMovement creerMouvementTransfertSortant(LigneTransfert ligne, 
                                                         Transfert transfert, 
                                                         UUID utilisateurId) {
        
        MovementType type = movementTypeRepository.findByCode("TRANSFERT_SORTANT")
            .orElseThrow(() -> new RuntimeException("Type mouvement TRANSFERT_SORTANT non trouvé"));
        
        // Déterminer le coût unitaire
        BigDecimal coutUnitaire = ligne.getLot() != null ? 
            ligne.getLot().getCoutUnitaire() :
            stockRepository.findByArticleIdAndDepotId(
                ligne.getArticle().getId(), transfert.getDepotSource().getId())
                .map(Stock::getCoutUnitaireMoyen)
                .orElse(ligne.getArticle().getCoutStandard() != null ? 
                    ligne.getArticle().getCoutStandard() : BigDecimal.ZERO);
        
        String reference = "MVT-TRF-SORT-" + LocalDate.now().getYear() + 
                          String.format("-%06d", sequenceService.getNextMovementSequence());
        
        StockMovement mouvement = StockMovement.builder()
            .reference(reference)
            .type(type)
            .article(ligne.getArticle())
            .depot(transfert.getDepotSource())
            .quantite(ligne.getQuantiteDemandee())
            .coutUnitaire(coutUnitaire)
            .lot(ligne.getLot())
            .transfert(transfert)
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(utilisateurId)
            .motif("Transfert sortant: " + transfert.getReference() + " - " + transfert.getMotif())
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        return mouvementRepository.save(mouvement);
    }
    
    /**
     * Créer mouvement de transfert entrant
     */
    private StockMovement creerMouvementTransfertEntrant(LigneTransfert ligne, 
                                                        Transfert transfert, 
                                                        UUID utilisateurId) {
        
        MovementType type = movementTypeRepository.findByCode("TRANSFERT_ENTRANT")
            .orElseThrow(() -> new RuntimeException("Type mouvement TRANSFERT_ENTRANT non trouvé"));
        
        // Le coût unitaire est le même que celui du mouvement sortant
        BigDecimal coutUnitaire = ligne.getLot() != null ? 
            ligne.getLot().getCoutUnitaire() :
            stockRepository.findByArticleIdAndDepotId(
                ligne.getArticle().getId(), transfert.getDepotSource().getId())
                .map(Stock::getCoutUnitaireMoyen)
                .orElse(ligne.getArticle().getCoutStandard() != null ? 
                    ligne.getArticle().getCoutStandard() : BigDecimal.ZERO);
        
        String reference = "MVT-TRF-ENTR-" + LocalDate.now().getYear() + 
                          String.format("-%06d", sequenceService.getNextMovementSequence());
        
        StockMovement mouvement = StockMovement.builder()
            .reference(reference)
            .type(type)
            .article(ligne.getArticle())
            .depot(transfert.getDepotDestination())
            .quantite(ligne.getQuantiteExpediee())
            .coutUnitaire(coutUnitaire)
            .transfert(transfert)
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(utilisateurId)
            .motif("Transfert entrant: " + transfert.getReference() + " - " + transfert.getMotif())
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        return mouvementRepository.save(mouvement);
    }
    
    /**
     * Créer un lot dans le dépôt destination
     */
    private void creerLotDestination(LigneTransfert ligne, Depot depotDestination) {
        Lot lotSource = ligne.getLot();
        
        String nouveauNumeroLot = "TRF-" + LocalDate.now().getYear() + 
                                 String.format("%02d", LocalDate.now().getMonthValue()) +
                                 "-" + lotSource.getNumeroLot().substring(
                                     Math.max(0, lotSource.getNumeroLot().length() - 4));
        
        Lot lotDestination = Lot.builder()
            .numeroLot(nouveauNumeroLot)
            .article(lotSource.getArticle())
            .quantiteInitiale(ligne.getQuantiteExpediee())
            .quantiteActuelle(ligne.getQuantiteExpediee())
            .dateFabrication(lotSource.getDateFabrication())
            .dateReception(LocalDate.now())
            .datePeremption(lotSource.getDatePeremption())
            .coutUnitaire(lotSource.getCoutUnitaire())
            .statut(Lot.LotStatus.DISPONIBLE)
            .build();
        
        lotRepository.save(lotDestination);
        log.info("Lot créé en destination: {} (source: {})", 
                nouveauNumeroLot, lotSource.getNumeroLot());
    }
    
    /**
     * Annuler tous les mouvements d'un transfert
     */
    private void annulerMouvementsTransfert(UUID transfertId) {
        List<StockMovement> mouvements = mouvementRepository.findByTransfertId(transfertId);
        
        for (StockMovement mouvement : mouvements) {
            mouvement.setStatut(StockMovement.MovementStatus.ANNULE);
            mouvementRepository.save(mouvement);
        }
        
        log.info("{} mouvements annulés pour transfert {}", mouvements.size(), transfertId);
    }
    
    /**
     * Obtenir les statistiques des transferts
     */
    public Map<String, Object> getStatistiquesTransferts(Integer mois, Integer annee) {
        Map<String, Object> stats = new HashMap<>();
        
        // Compter les transferts par statut
        long totalTransferts = transfertRepository.count();
        long transfertsBrouillon = transfertRepository.countByStatut(Transfert.TransfertStatut.BROUILLON);
        long transfertsValides = transfertRepository.countByStatut(Transfert.TransfertStatut.VALIDE);
        long transfertsExpedies = transfertRepository.countByStatut(Transfert.TransfertStatut.EXPEDIE);
        long transfertsReceptionnes = transfertRepository.countByStatut(Transfert.TransfertStatut.RECEPTIONNE);
        long transfertsAnnules = transfertRepository.countByStatut(Transfert.TransfertStatut.ANNULE);
        
        // Calculer le taux de complétion
        double tauxCompletion = totalTransferts > 0 ? 
            (double) transfertsReceptionnes / totalTransferts * 100 : 0;
        
        stats.put("totalTransferts", totalTransferts);
        stats.put("transfertsBrouillon", transfertsBrouillon);
        stats.put("transfertsValides", transfertsValides);
        stats.put("transfertsExpedies", transfertsExpedies);
        stats.put("transfertsReceptionnes", transfertsReceptionnes);
        stats.put("transfertsAnnules", transfertsAnnules);
        stats.put("tauxCompletion", String.format("%.1f%%", tauxCompletion));
        stats.put("periode", mois + "/" + annee);
        
        return stats;
    }

    @Transactional
    public Transfert updateStatus(UUID id, TransfertStatut newStatus, UUID userId) {
        Transfert trf = transfertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfert introuvable"));

        if (trf.getStatut() == TransfertStatut.ANNULE ||
            trf.getStatut() == TransfertStatut.RECEPTIONNE) {
            throw new IllegalStateException("Transfert finalisé");
        }

        switch (newStatus) {
            case VALIDE -> {
                trf.setValideurId(userId);
                trf.setDateValidation(LocalDateTime.now());
            }
            case ANNULE -> {
                trf.setAnnulateurId(userId);
                trf.setDateAnnulation(LocalDateTime.now());
            }
            case RECEPTIONNE -> {
                trf.setReceptionnaireId(userId);
                trf.setDateReceptionReelle(LocalDateTime.now());
            }
            default -> {}
        }

        trf.setStatut(newStatus);
        return transfertRepository.save(trf);
    }
}