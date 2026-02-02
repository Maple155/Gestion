package com.gestion.stock.service;

import com.gestion.stock.dto.LigneTransfertDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.entity.Transfert.TransfertStatut;
import com.gestion.stock.repository.*;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransfertService {
    
    private final TransfertRepository transfertRepository;
    private final LigneTransfertRepository ligneTransfertRepository;
    private final DepotRepository depotRepository;
    private final StockService stockService;
    private final LotRepository lotRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository stockMovementRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SequenceGeneratorService sequenceGeneratorService;
    private final EntityManager entityManager;
    
    public Transfert findById(UUID id) {
        return transfertRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Transfert introuvable"));
    }

    /**
     * Créer un transfert entre dépôts
     */
    @Transactional
    public Transfert creerTransfert(UUID depotSourceId, UUID depotDestinationId, 
                                   List<LigneTransfertDTO> lignesDTO, String motif, 
                                   UUID demandeurId) {
        
        Depot depotSource = depotRepository.findById(depotSourceId)
            .orElseThrow(() -> new RuntimeException("Dépôt source non trouvé"));
        Depot depotDestination = depotRepository.findById(depotDestinationId)
            .orElseThrow(() -> new RuntimeException("Dépôt destination non trouvé"));
        
        // Vérifier que ce n'est pas le même dépôt
        if (depotSourceId.equals(depotDestinationId)) {
            throw new RuntimeException("Le dépôt source et destination doivent être différents");
        }
        
        // Générer référence unique
        String reference = genererReferenceTransfert();
        
        // Créer le transfert
        Transfert transfert = Transfert.builder()
            .reference(reference)
            .depotSource(depotSource)
            .depotDestination(depotDestination)
            .motif(motif)
            .demandeurId(demandeurId)
            .statut(Transfert.TransfertStatut.BROUILLON)
            .dateDemande(LocalDateTime.now())
            .build();
        
        transfert = transfertRepository.save(transfert);
        
        // Créer les lignes de transfert
        List<LigneTransfert> lignes = new ArrayList<>();
        for (LigneTransfertDTO dto : lignesDTO) {
            LigneTransfert ligne = creerLigneTransfert(transfert, dto);
            lignes.add(ligne);
        }
        
        transfertRepository.save(transfert);
        log.info("Transfert créé : {} avec {} lignes", reference, lignes.size());
        
        return transfert;
    }
    
    /**
     * Créer une ligne de transfert
     */
    private LigneTransfert creerLigneTransfert(Transfert transfert, LigneTransfertDTO dto) {
        
        // Vérifier disponibilité stock source
        Stock stockSource = stockRepository.findByArticleIdAndDepotId(
            dto.getArticle().getId(), transfert.getDepotSource().getId())
            .orElseThrow(() -> new RuntimeException(
                String.format("Stock insuffisant ou inexistant pour l'article %s dans le dépôt source", 
                dto.getArticle().getCodeArticle())));
        
        if (stockSource.getQuantiteDisponible() < dto.getQuantiteDemandee()) {
            throw new RuntimeException(
                String.format("Stock insuffisant. Disponible: %d, Demandé: %d",
                stockSource.getQuantiteDisponible(), dto.getQuantiteDemandee()));
        }
        
        // Allouer des lots si spécifié
        Lot lot = null;
        if (dto.getLot() != null) {
            lot = lotRepository.findById(dto.getLot().getId())
                .orElseThrow(() -> new RuntimeException("Lot non trouvé"));
            
            if (lot.getQuantiteActuelle() < dto.getQuantiteDemandee()) {
                throw new RuntimeException("Quantité du lot insuffisante");
            }
        }
        
        LigneTransfert ligne = LigneTransfert.builder()
            .transfert(transfert)
            .article(stockSource.getArticle())
            .quantiteDemandee(dto.getQuantiteDemandee())
            .quantiteExpediee(0)
            .quantiteRecue(0)
            .lot(lot)
            .notes(dto.getNotes())
            .build();
        
        return ligneTransfertRepository.save(ligne);
    }
    
    /**
     * Valider un transfert
     */
    @Transactional
    public Transfert validerTransfert(UUID transfertId, UUID valideurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est en brouillon
        if (transfert.getStatut() != Transfert.TransfertStatut.BROUILLON) {
            throw new RuntimeException("Le transfert doit être en statut BROUILLON pour être validé");
        }
        
        // Vérifier séparation des tâches (demandeur ≠ valideur)
        if (transfert.getDemandeurId().equals(valideurId)) {
            throw new RuntimeException("Violation séparation des tâches : le demandeur ne peut pas valider son propre transfert");
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.VALIDE);
        transfert.setValideurId(valideurId);
        transfert.setDateValidation(LocalDateTime.now());
        
        log.info("Transfert {} validé par {}", transfert.getReference(), valideurId);
        return transfertRepository.save(transfert);
    }
    
    /**
     * Expédier un transfert (sortie du dépôt source)
     */
    @Transactional
    public Transfert expedierTransfert(UUID transfertId, UUID expediteurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est validé
        if (transfert.getStatut() != Transfert.TransfertStatut.VALIDE) {
            throw new RuntimeException("Le transfert doit être validé avant expédition");
        }
        
        List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
        
        // Pour chaque ligne, créer un mouvement de sortie
        for (LigneTransfert ligne : lignes) {
            expedierLigne(ligne, expediteurId);
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.EXPEDIE);
        // transfert.setExpediteurId(expediteurId);
        transfert.setDateExpedition(LocalDate.now());
        
        log.info("Transfert {} expédié par {}", transfert.getReference(), expediteurId);
        return transfertRepository.save(transfert);
    }
    
    /**
     * Expédier une ligne spécifique
     */
    private void expedierLigne(LigneTransfert ligne, UUID expediteurId) {
        // Créer mouvement de sortie
        MovementType typeSortie = movementTypeRepository.findByCode("TRANSFERT_SORTANT")
            .orElseThrow(() -> new RuntimeException("Type mouvement TRANSFERT_SORTANT non trouvé"));
        
        // Utiliser le coût moyen du stock source
        Stock stockSource = stockRepository.findByArticleIdAndDepotId(
            ligne.getArticle().getId(), 
            ligne.getTransfert().getDepotSource().getId())
            .orElseThrow(() -> new RuntimeException("Stock source non trouvé"));
        
        StockMovement mouvementSortie = StockMovement.builder()
            .reference(genererReferenceMouvement())
            .type(typeSortie)
            .article(ligne.getArticle())
            .depot(ligne.getTransfert().getDepotSource())
            .quantite(ligne.getQuantiteDemandee())
            .coutUnitaire(stockSource.getCoutUnitaireMoyen())
            .lot(ligne.getLot())
            .transfert(ligne.getTransfert())
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(expediteurId)
            .motif("Expédition transfert " + ligne.getTransfert().getReference())
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        stockMovementRepository.save(mouvementSortie);
        
        // Mettre à jour la quantité expédiée
        ligne.setQuantiteExpediee(ligne.getQuantiteDemandee());
        ligneTransfertRepository.save(ligne);
        
        // Mettre à jour le lot si spécifié
        if (ligne.getLot() != null) {
            Lot lot = ligne.getLot();
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() - ligne.getQuantiteDemandee());
            if (lot.getQuantiteActuelle() == 0) {
                lot.setStatut(Lot.LotStatus.EPUISE);
            }
            lotRepository.save(lot);
        }
        
        log.info("Ligne {} expédiée : {} unités", ligne.getId(), ligne.getQuantiteDemandee());
    }
    
    /**
     * Réceptionner un transfert (entrée dans le dépôt destination)
     */
    @Transactional
    public Transfert receptionnerTransfert(UUID transfertId, UUID receptionnaireId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert est expédié
        if (transfert.getStatut() != Transfert.TransfertStatut.EXPEDIE) {
            throw new RuntimeException("Le transfert doit être expédié avant réception");
        }
        
        List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
        
        // Pour chaque ligne, créer un mouvement d'entrée
        for (LigneTransfert ligne : lignes) {
            receptionnerLigne(ligne, receptionnaireId);
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.RECEPTIONNE);
        // transfert.setReceptionnaireId(receptionnaireId);
        transfert.setDateReceptionReelle(LocalDateTime.now());
        
        log.info("Transfert {} réceptionné par {}", transfert.getReference(), receptionnaireId);
        return transfertRepository.save(transfert);
    }
    
    /**
     * Réceptionner une ligne spécifique
     */
    private void receptionnerLigne(LigneTransfert ligne, UUID receptionnaireId) {
        // Récupérer le coût du mouvement de sortie correspondant
        Optional<StockMovement> mouvementSortie = stockMovementRepository
            .findByTransfertIdAndArticleIdAndDepotId(
                ligne.getTransfert().getId(),
                ligne.getArticle().getId(),
                ligne.getTransfert().getDepotSource().getId());
        
        BigDecimal coutUnitaire = mouvementSortie
            .map(StockMovement::getCoutUnitaire)
            .orElseGet(() -> {
                // Fallback : coût moyen du stock source
                Stock stockSource = stockRepository.findByArticleIdAndDepotId(
                    ligne.getArticle().getId(),
                    ligne.getTransfert().getDepotSource().getId())
                    .orElseThrow(() -> new RuntimeException("Stock source non trouvé"));
                return stockSource.getCoutUnitaireMoyen();
            });
        
        // Créer mouvement d'entrée
        MovementType typeEntree = movementTypeRepository.findByCode("TRANSFERT_ENTRANT")
            .orElseThrow(() -> new RuntimeException("Type mouvement TRANSFERT_ENTRANT non trouvé"));
        
        StockMovement mouvementEntree = StockMovement.builder()
            .reference(genererReferenceMouvement())
            .type(typeEntree)
            .article(ligne.getArticle())
            .depot(ligne.getTransfert().getDepotDestination())
            .quantite(ligne.getQuantiteExpediee())
            .coutUnitaire(coutUnitaire)
            .lot(ligne.getLot())
            .transfert(ligne.getTransfert())
            .dateMouvement(LocalDateTime.now())
            .dateComptable(LocalDate.now())
            .utilisateurId(receptionnaireId)
            .motif("Réception transfert " + ligne.getTransfert().getReference())
            .statut(StockMovement.MovementStatus.VALIDE)
            .build();
        
        stockMovementRepository.save(mouvementEntree);
        
        // Mettre à jour la quantité reçue
        ligne.setQuantiteRecue(ligne.getQuantiteExpediee());
        ligneTransfertRepository.save(ligne);
        
        log.info("Ligne {} réceptionnée : {} unités", ligne.getId(), ligne.getQuantiteExpediee());
    }
    
    /**
     * Annuler un transfert
     */
    @Transactional
    public Transfert annulerTransfert(UUID transfertId, String motifAnnulation, UUID annulateurId) {
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        // Vérifier que le transfert peut être annulé
        if (transfert.getStatut() == Transfert.TransfertStatut.RECEPTIONNE) {
            throw new RuntimeException("Impossible d'annuler un transfert déjà réceptionné");
        }
        
        if (transfert.getStatut() == Transfert.TransfertStatut.EXPEDIE) {
            // Si déjà expédié, on doit annuler les mouvements de sortie
            annulerMouvementsTransfert(transfertId);
        }
        
        // Mettre à jour le transfert
        transfert.setStatut(Transfert.TransfertStatut.ANNULE);
        // transfert.setMotifAnnulation(motifAnnulation);
        // transfert.setDateAnnulation(LocalDateTime.now());
        
        log.info("Transfert {} annulé par {}", transfert.getReference(), annulateurId);
        return transfertRepository.save(transfert);
    }
    
    /**
     * Annuler les mouvements liés à un transfert
     */
    private void annulerMouvementsTransfert(UUID transfertId) {
        List<StockMovement> mouvements = stockMovementRepository.findByTransfertId(transfertId);
        
        for (StockMovement mouvement : mouvements) {
            mouvement.setStatut(StockMovement.MovementStatus.ANNULE);
            stockMovementRepository.save(mouvement);
            
            // Annuler l'impact sur le stock
            annulerImpactStock(mouvement);
        }
    }
    
    /**
     * Annuler l'impact d'un mouvement sur le stock
     */
    private void annulerImpactStock(StockMovement mouvement) {
        // Cette logique dépend de votre implémentation de StockService
        // Exemple simplifié :
        Stock stock = stockRepository.findByArticleIdAndDepotId(
            mouvement.getArticle().getId(), mouvement.getDepot().getId())
            .orElseThrow(() -> new RuntimeException("Stock non trouvé"));
        
        if (mouvement.getType().getSens() == MovementType.SensMouvement.ENTREE) {
            stock.setQuantiteTheorique(stock.getQuantiteTheorique() - mouvement.getQuantite());
            stock.setQuantitePhysique(stock.getQuantitePhysique() - mouvement.getQuantite());
        } else {
            stock.setQuantiteTheorique(stock.getQuantiteTheorique() + mouvement.getQuantite());
            stock.setQuantitePhysique(stock.getQuantitePhysique() + mouvement.getQuantite());
        }
        
        stockRepository.save(stock);
    }
    
    public List<Transfert> rechercherTransferts(String statut, UUID depotSourceId, 
                                               UUID depotDestinationId, 
                                               LocalDate dateDebut, LocalDate dateFin) {
        
        log.info("Recherche transferts avec filtres: statut={}, depotSource={}, depotDestination={}, dateDebut={}, dateFin={}", 
                 statut, depotSourceId, depotDestinationId, dateDebut, dateFin);
        
        try {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Transfert> query = cb.createQuery(Transfert.class);
            Root<Transfert> transfert = query.from(Transfert.class);
            
            // Joins pour charger les relations (évite les LazyInitializationException)
            Join<Transfert, Depot> depotSourceJoin = transfert.join("depotSource", JoinType.LEFT);
            Join<Transfert, Depot> depotDestinationJoin = transfert.join("depotDestination", JoinType.LEFT);
            
            List<Predicate> predicates = new ArrayList<>();
            
            // Filtre par statut
            if (statut != null && !statut.trim().isEmpty()) {
                try {
                    Transfert.TransfertStatut statutEnum = Transfert.TransfertStatut.valueOf(statut.toUpperCase());
                    predicates.add(cb.equal(transfert.get("statut"), statutEnum));
                } catch (IllegalArgumentException e) {
                    log.warn("Statut '{}' invalide. Statuts valides: {}", statut, 
                             Arrays.toString(Transfert.TransfertStatut.values()));
                }
            }
            
            // Filtre par dépôt source
            if (depotSourceId != null) {
                predicates.add(cb.equal(depotSourceJoin.get("id"), depotSourceId));
            }
            
            // Filtre par dépôt destination
            if (depotDestinationId != null) {
                predicates.add(cb.equal(depotDestinationJoin.get("id"), depotDestinationId));
            }
            
            // Filtre par date début
            if (dateDebut != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    transfert.get("dateDemande").as(LocalDate.class), dateDebut));
            }
            
            // Filtre par date fin
            if (dateFin != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    transfert.get("dateDemande").as(LocalDate.class), dateFin));
            }
            
            // Appliquer les prédicats
            if (!predicates.isEmpty()) {
                query.where(cb.and(predicates.toArray(new Predicate[0])));
            }
            
            // Trier par date de demande décroissante
            query.orderBy(cb.desc(transfert.get("dateDemande")));
            
            // Exécuter la requête
            List<Transfert> result = entityManager.createQuery(query).getResultList();
            
            log.info("Nombre de transferts trouvés: {}", result.size());
            return result;
            
        } catch (Exception e) {
            log.error("Erreur dans rechercherTransferts avec Criteria API: ", e);
            throw new RuntimeException("Erreur lors de la recherche des transferts: " + e.getMessage(), e);
        }
    }
    
    /**
     * Obtenir les statistiques des transferts
     */
    public Map<String, Object> getStatistiquesTransferts(Integer mois, Integer annee) {
        Map<String, Object> stats = new HashMap<>();
        
        // Transferts du mois
        List<Transfert> transfertsMois = transfertRepository.findByMonth(mois, annee);
        
        stats.put("nombreTransferts", transfertsMois.size());
        stats.put("nombreExpedies", transfertsMois.stream()
            .filter(t -> t.getStatut() == Transfert.TransfertStatut.EXPEDIE)
            .count());
        stats.put("nombreReceptionnes", transfertsMois.stream()
            .filter(t -> t.getStatut() == Transfert.TransfertStatut.RECEPTIONNE)
            .count());
        stats.put("nombreEnAttente", transfertsMois.stream()
            .filter(t -> t.getStatut() == Transfert.TransfertStatut.VALIDE)
            .count());
        
        // Valeur totale des transferts expédiés
        BigDecimal valeurTotale = transfertsMois.stream()
            .filter(t -> t.getStatut() == Transfert.TransfertStatut.EXPEDIE)
            .flatMap(t -> ligneTransfertRepository.findByTransfertId(t.getId()).stream())
            .map(ligne -> {
                Stock stock = stockRepository.findByArticleIdAndDepotId(
                    ligne.getArticle().getId(), 
                    ligne.getTransfert().getDepotSource().getId())
                    .orElse(new Stock());
                return stock.getCoutUnitaireMoyen()
                    .multiply(BigDecimal.valueOf(ligne.getQuantiteExpediee()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        
        stats.put("valeurTotale", valeurTotale);
        
        // Top 5 dépôts sources
        List<Object[]> topDepots = transfertRepository.findTopDepotsSources(mois, annee);
        stats.put("topDepotsSources", topDepots);
        
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
            default -> {}
        }

        trf.setStatut(newStatus);
        return transfertRepository.save(trf);
    }
    
    /**
     * Vérifier la disponibilité pour un transfert
     */
    public Map<UUID, Integer> verifierDisponibiliteTransfert(UUID depotSourceId, 
                                                           List<LigneTransfertDTO> lignes) {
        Map<UUID, Integer> indisponibilites = new HashMap<>();
        
        for (LigneTransfertDTO ligne : lignes) {
            Stock stock = stockRepository.findByArticleIdAndDepotId(
                ligne.getArticle().getId(), depotSourceId)
                .orElse(null);
            
            if (stock == null || stock.getQuantiteDisponible() < ligne.getQuantiteDemandee()) {
                int disponible = stock != null ? stock.getQuantiteDisponible() : 0;
                indisponibilites.put(ligne.getArticle().getId(), disponible);
            }
        }
        
        return indisponibilites;
    }
    
    /**
     * Générer une référence unique pour les transferts
     */
    private String genererReferenceTransfert() {
        String prefix = "TRF";
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        
        Long sequence = sequenceGeneratorService.getNextTransfertSequence();
        if (sequence == null) sequence = 1L;
        
        return String.format("%s-%d-%04d", prefix, year, sequence);
    }
    
    /**
     * Générer une référence unique pour les mouvements
     */
    private String genererReferenceMouvement() {
        String prefix = "MVT";
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        
        Long sequence = sequenceGeneratorService.getNextMovementSequence();
        if (sequence == null) sequence = 1L;
        
        return String.format("%s-%d-%06d", prefix, year, sequence);
    }

    public List<Transfert> getAll(){
        return transfertRepository.findAll();
    }
}