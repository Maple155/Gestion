package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.repository.BonReceptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MouvementService {

    private final StockMovementRepository mouvementRepository;
    private final MovementTypeRepository typeMouvementRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final LotRepository lotRepository;
    private final EmplacementRepository emplacementRepository;
    private final BonReceptionRepository bonReceptionRepository;
    private final StockRepository stockRepository;

    private static final String PREFIX_REFERENCE = "MVT";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * Générer une référence unique pour un mouvement
     */
    private String genererReference() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long count = mouvementRepository.countByDateComptable(LocalDate.now());
        return String.format("%s-%s-%06d", PREFIX_REFERENCE, datePart, count + 1);
    }

    /**
     * Récupérer tous les types de mouvement
     */
    public List<MovementType> getTypesMouvement() {
        return typeMouvementRepository.findAll();
    }

    /**
     * Récupérer les types de mouvement d'entrée
     */
    public List<MovementType> getTypesMouvementEntree() {
        return typeMouvementRepository.findBySens(MovementType.SensMouvement.ENTREE);
    }

    /**
     * Récupérer les types de mouvement de sortie
     */
    public List<MovementType> getTypesMouvementSortie() {
        return typeMouvementRepository.findBySens(MovementType.SensMouvement.SORTIE);
    }

    public Page<Map<String, Object>> rechercherMouvements(
            UUID typeMouvement, UUID articleId, UUID depotId,
            LocalDate dateDebut, LocalDate dateFin, Pageable pageable) {

        LocalDateTime debut = dateDebut != null ? dateDebut.atStartOfDay() : null;
        LocalDateTime fin = dateFin != null ? dateFin.plusDays(1).atStartOfDay() : null;

        Specification<StockMovement> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (typeMouvement != null) {
                predicates.add(cb.equal(root.get("type").get("id"), typeMouvement));
            }
            if (articleId != null) {
                predicates.add(cb.equal(root.get("article").get("id"), articleId));
            }
            if (depotId != null) {
                predicates.add(cb.equal(root.get("depot").get("id"), depotId));
            }
            if (debut != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("dateMouvement"), debut));
            }
            if (fin != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("dateMouvement"), fin));
            }
            // si vous avez un filtre statut plus tard, ajoutez-le de la même façon :
            // if (statut != null) { predicates.add(cb.equal(root.get("statut"), statut)); }

            query.orderBy(cb.desc(root.get("dateMouvement")));
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<StockMovement> mouvements = mouvementRepository.findAll(spec, pageable);
        return mouvements.map(this::convertirMouvementEnMap);
    }

    /**
     * Convertir un mouvement en Map pour l'API
     */
    private Map<String, Object> convertirMouvementEnMap(StockMovement mouvement) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", mouvement.getId());
        map.put("reference", mouvement.getReference());
        map.put("typeMouvement", mouvement.getType() != null ? mouvement.getType().getLibelle() : "");
        map.put("article", mouvement.getArticle() != null ? mouvement.getArticle().getLibelle() : "");
        map.put("articleCode", mouvement.getArticle() != null ? mouvement.getArticle().getCodeArticle() : "");
        map.put("depot", mouvement.getDepot() != null ? mouvement.getDepot().getNom() : "");
        map.put("quantite", mouvement.getQuantite());
        map.put("coutUnitaire", mouvement.getCoutUnitaire());
        map.put("valeurMouvement", mouvement.getValeurMouvement());
        map.put("lot", mouvement.getLot() != null ? mouvement.getLot().getNumeroLot() : "");
        map.put("dateMouvement", mouvement.getDateMouvement());
        map.put("statut", mouvement.getStatut().toString());
        map.put("dateComptable", mouvement.getDateComptable());
        map.put("utilisateurId", mouvement.getUtilisateurId());

        return map;
    }

    /**
     * Créer un mouvement d'entrée
     */
    @Transactional
    public Map<String, Object> creerMouvementEntree(Map<String, String> params, UUID utilisateurId) {
        log.info("Création mouvement entrée par utilisateur: {}", utilisateurId);
        log.info("Paramètres reçus: {}", params);

        // Validation des paramètres obligatoires
        String typeMouvementId = params.get("typeMouvementId");
        String articleId = params.get("articleId");
        String depotId = params.get("depotId");
        String quantiteStr = params.get("quantite");
        String coutUnitaireStr = params.get("coutUnitaire");

        if (typeMouvementId == null || articleId == null || depotId == null ||
                quantiteStr == null || coutUnitaireStr == null) {
            throw new RuntimeException("Paramètres obligatoires manquants");
        }

        try {
            UUID.fromString(typeMouvementId);
            UUID.fromString(articleId);
            UUID.fromString(depotId);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException("Format UUID invalide: " + e.getMessage());
        }

        // Création du mouvement
        StockMovement mouvement = new StockMovement();

        // Générer référence
        mouvement.setReference(genererReference());

        // Type de mouvement
        MovementType type = typeMouvementRepository.findById(UUID.fromString(typeMouvementId))
                .orElseThrow(() -> new RuntimeException("Type de mouvement non trouvé"));
        mouvement.setType(type);

        // Article
        Article article = articleRepository.findById(UUID.fromString(articleId))
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        mouvement.setArticle(article);

        // Dépôt
        Depot depot = depotRepository.findById(UUID.fromString(depotId))
                .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));
        mouvement.setDepot(depot);

        // Quantité et coût
        Integer quantite = Integer.parseInt(quantiteStr);
        BigDecimal coutUnitaire = new BigDecimal(coutUnitaireStr);
        mouvement.setQuantite(quantite);
        mouvement.setCoutUnitaire(coutUnitaire);

        // Dates
        mouvement.setDateMouvement(LocalDateTime.now());
        mouvement.setDateComptable(LocalDate.now());

        // Utilisateur
        mouvement.setUtilisateurId(utilisateurId);

        // Emplacement (optionnel)
        String emplacementId = params.get("emplacementId");
        if (emplacementId != null && !emplacementId.isEmpty()) {
            Emplacement emplacement = emplacementRepository.findById(UUID.fromString(emplacementId))
                    .orElseThrow(() -> new RuntimeException("Emplacement non trouvé"));
            mouvement.setEmplacement(emplacement);
        }

        // Lot (optionnel)
        String lotId = params.get("lotId");
        if (lotId != null && !lotId.isEmpty()) {
            Lot lot = lotRepository.findById(UUID.fromString(lotId))
                    .orElseThrow(() -> new RuntimeException("Lot non trouvé"));
            mouvement.setLot(lot);

            // Mettre à jour la quantité du lot
            lot.setQuantiteActuelle(lot.getQuantiteActuelle() + quantite);
            lotRepository.save(lot);
        }

        // Bon de réception (optionnel)
        String bonReceptionId = params.get("bonReceptionId");
        if (bonReceptionId != null && !bonReceptionId.isEmpty()) {
            BonReception bonReception = bonReceptionRepository.findById(java.util.UUID.fromString(bonReceptionId))
                    .orElseThrow(() -> new RuntimeException("Bon de réception non trouvé"));
            mouvement.setBonReception(bonReception);
        }

        // Motif (optionnel)
        mouvement.setMotif(params.get("motif"));

        // Statut
        mouvement.setStatut(StockMovement.MovementStatus.VALIDE);

        // Sauvegarder
        StockMovement mouvementSauvegarde = mouvementRepository.save(mouvement);

        // Mettre à jour le stock
        mettreAJourStock(mouvementSauvegarde);

        log.info("Mouvement d'entrée créé: {} - {} x {}",
                mouvement.getReference(), quantite, article.getCodeArticle());

        return convertirMouvementEnMap(mouvementSauvegarde);
    }

    @Transactional
    public Map<String, Object> creerMouvementSortie(Map<String, String> params, UUID utilisateurId) {
        log.info("Création mouvement sortie par utilisateur: {}", utilisateurId);
        log.info("Paramètres reçus: {}", params);

        // Validation des paramètres obligatoires
        String typeMouvementId = params.get("typeMouvementId");
        String articleId = params.get("articleId");
        String depotId = params.get("depotId");
        String quantiteStr = params.get("quantite");

        if (typeMouvementId == null || articleId == null || depotId == null || quantiteStr == null) {
            throw new RuntimeException("Paramètres obligatoires manquants");
        }

        Integer quantite = Integer.parseInt(quantiteStr);

        // Vérifier si le stock existe pour cet article et dépôt
        Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(
                UUID.fromString(articleId),
                UUID.fromString(depotId));

        // Si le stock n'existe pas, créer un enregistrement avec quantité 0
        Stock stock;
        if (!stockOpt.isPresent()) {
            log.warn("Aucun stock trouvé pour article {} et dépôt {}. Création d'un enregistrement vide.",
                    articleId, depotId);

            Article article = articleRepository.findById(UUID.fromString(articleId))
                    .orElseThrow(() -> new RuntimeException("Article non trouvé"));

            Depot depot = depotRepository.findById(UUID.fromString(depotId))
                    .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));

            stock = Stock.builder()
                    .article(article)
                    .depot(depot)
                    .quantitePhysique(0)
                    .quantiteTheorique(0)
                    .quantiteReservee(0)
                    .valeurStockCump(BigDecimal.ZERO)
                    .dateDernierMouvement(null)
                    .dateDernierInventaire(null)
                    .updatedAt(LocalDateTime.now())
                    .build();

            stock = stockRepository.save(stock);
            log.info("Nouvel enregistrement de stock créé pour article {}, dépôt {}", articleId, depotId);
        } else {
            stock = stockOpt.get();
        }

        // Maintenant vérifier si le stock disponible est suffisant
        if (stock.getQuantiteDisponible() < quantite) {
            throw new RuntimeException("Stock insuffisant. Disponible: " +
                    stock.getQuantiteDisponible() + ", Demandé: " + quantite);
        }

        // Création du mouvement
        StockMovement mouvement = new StockMovement();

        // Générer référence
        mouvement.setReference(genererReference());

        // Type de mouvement
        MovementType type = typeMouvementRepository.findById(UUID.fromString(typeMouvementId))
                .orElseThrow(() -> new RuntimeException("Type de mouvement non trouvé"));
        mouvement.setType(type);

        // Article
        Article article = articleRepository.findById(UUID.fromString(articleId))
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));
        mouvement.setArticle(article);

        // Dépôt
        Depot depot = depotRepository.findById(UUID.fromString(depotId))
                .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));
        mouvement.setDepot(depot);

        // Quantité et coût (coût moyen pour les sorties)
        mouvement.setQuantite(quantite);
        mouvement.setCoutUnitaire(stock.getCoutUnitaireMoyen());

        // Dates
        mouvement.setDateMouvement(LocalDateTime.now());
        mouvement.setDateComptable(LocalDate.now());

        // Utilisateur
        mouvement.setUtilisateurId(utilisateurId);

        // Gestion par lot (si applicable)
        if (article.isGestionParLot()) {
            String lotId = params.get("lotId");
            if (lotId == null || lotId.isEmpty()) {
                // Trouver automatiquement le lot selon FIFO/FEFO
                Lot lot = trouverLotPourSortie(UUID.fromString(articleId), UUID.fromString(depotId), quantite);
                if (lot == null) {
                    throw new RuntimeException("Aucun lot disponible pour cet article");
                }
                mouvement.setLot(lot);
            } else {
                Lot lot = lotRepository.findById(UUID.fromString(lotId))
                        .orElseThrow(() -> new RuntimeException("Lot non trouvé"));
                mouvement.setLot(lot);
            }
        }

        // Commande client (optionnel)
        String commandeClientId = params.get("commandeClientId");
        if (commandeClientId != null && !commandeClientId.isEmpty()) {
            try {
                mouvement.setCommandeClientId(UUID.fromString(commandeClientId));
            } catch (IllegalArgumentException e) {
                log.warn("UUID invalide pour commandeClientId: {}", commandeClientId);
            }
        }

        // Motif
        mouvement.setMotif(params.get("motif"));

        // Statut
        mouvement.setStatut(StockMovement.MovementStatus.VALIDE);

        // Sauvegarder
        StockMovement mouvementSauvegarde = mouvementRepository.save(mouvement);

        // Mettre à jour le stock
        mettreAJourStock(mouvementSauvegarde);

        log.info("Mouvement de sortie créé: {} - {} x {}",
                mouvement.getReference(), quantite, article.getCodeArticle());

        return convertirMouvementEnMap(mouvementSauvegarde);
    }

    /**
     * Trouver un lot pour sortie selon FIFO/FEFO
     */
    private Lot trouverLotPourSortie(UUID articleId, UUID depotId, Integer quantite) {
        Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

        try {
            // Pour les produits périssables, utiliser FEFO
            if (article.getCategorie() != null &&
                    article.getCategorie().isNecessiteTracabiliteLot()) {
                // Essayer de trouver un lot avec la quantité exacte
                Optional<Lot> lotOpt = lotRepository.findLotFEFO(articleId, depotId, quantite);
                if (lotOpt.isPresent()) {
                    return lotOpt.get();
                }

                // Si pas de lot avec quantité suffisante, prendre le premier disponible
                List<Lot> lots = lotRepository.findLotsPourSortieFEFO(articleId, depotId);
                if (!lots.isEmpty()) {
                    return lots.get(0);
                }
            } else {
                // FIFO par défaut
                Optional<Lot> lotOpt = lotRepository.findLotFIFO(articleId, depotId, quantite);
                if (lotOpt.isPresent()) {
                    return lotOpt.get();
                }

                // Si pas de lot avec quantité suffisante, prendre le premier disponible
                List<Lot> lots = lotRepository.findLotsPourSortieFIFO(articleId, depotId);
                if (!lots.isEmpty()) {
                    return lots.get(0);
                }
            }
        } catch (Exception e) {
            log.error("Erreur lors de la recherche de lot pour sortie", e);
        }

        throw new RuntimeException("Aucun lot disponible pour cet article dans le dépôt spécifié");
    }

    private void mettreAJourStock(StockMovement mouvement) {
        UUID articleId = mouvement.getArticle().getId();
        UUID depotId = mouvement.getDepot().getId();
        Integer quantite = mouvement.getQuantite();

        Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                .orElse(null);

        boolean estEntree = mouvement.getType().getSens() == MovementType.SensMouvement.ENTREE;

        if (stock == null) {
            // Pour une entrée, créer un nouveau stock
            if (estEntree) {
                stock = Stock.builder()
                        .article(mouvement.getArticle())
                        .depot(mouvement.getDepot())
                        .quantiteTheorique(quantite)
                        .quantitePhysique(quantite)
                        .quantiteReservee(0)
                        .valeurStockCump(BigDecimal.ZERO)
                        .dateDernierMouvement(mouvement.getDateMouvement())
                        .updatedAt(LocalDateTime.now())
                        .build();

                // Calculer la valeur initiale pour les entrées
                BigDecimal valeurEntree = mouvement.getCoutUnitaire()
                        .multiply(BigDecimal.valueOf(quantite));
                stock.setValeurStockCump(valeurEntree);

                stockRepository.save(stock);
                log.info("Nouveau stock créé pour article {}, dépôt {} (entrée)", articleId, depotId);
            } else {
                // Pour une sortie, on ne peut pas créer un stock négatif
                throw new RuntimeException("Stock insuffisant. L'article n'existe pas dans ce dépôt.");
            }
        } else {
            // Mettre à jour le stock existant
            if (estEntree) {
                // Entrée : ajouter la quantité
                stock.setQuantiteTheorique(stock.getQuantiteTheorique() + quantite);
                stock.setQuantitePhysique(stock.getQuantitePhysique() + quantite);

                // Mettre à jour la valeur du stock (CUMP)
                BigDecimal nouvelleValeur = stock.getValeurStockCump()
                        .add(mouvement.getValeurMouvement());
                stock.setValeurStockCump(nouvelleValeur);
            } else {
                // Sortie : vérifier le stock disponible
                if (stock.getQuantiteTheorique() < quantite) {
                    throw new RuntimeException("Stock insuffisant. Disponible: " +
                            stock.getQuantiteTheorique() + ", Demandé: " + quantite);
                }

                // Soustraire la quantité
                stock.setQuantiteTheorique(stock.getQuantiteTheorique() - quantite);
                stock.setQuantitePhysique(stock.getQuantitePhysique() - quantite);

                // Ajuster la valeur du stock proportionnellement
                if (stock.getQuantiteTheorique() > 0) {
                    BigDecimal proportion = BigDecimal.valueOf(quantite)
                            .divide(BigDecimal.valueOf(stock.getQuantiteTheorique() + quantite), 4,
                                    RoundingMode.HALF_UP);
                    BigDecimal valeurSortie = stock.getValeurStockCump().multiply(proportion);
                    stock.setValeurStockCump(stock.getValeurStockCump().subtract(valeurSortie));
                } else {
                    stock.setValeurStockCump(BigDecimal.ZERO);
                }
            }

            // Mettre à jour la date du dernier mouvement
            stock.setDateDernierMouvement(mouvement.getDateMouvement());
            stock.setUpdatedAt(LocalDateTime.now());

            stockRepository.save(stock);
            log.info("Stock mis à jour pour article {}, dépôt {}: quantité = {}",
                    articleId, depotId, stock.getQuantiteTheorique());
        }
    }

    /**
     * Obtenir les détails d'un mouvement
     */
    public Map<String, Object> getDetailsMouvement(UUID id) {
        StockMovement mouvement = mouvementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mouvement non trouvé"));

        Map<String, Object> details = convertirMouvementEnMap(mouvement);

        // Ajouter des informations supplémentaires
        details.put("createdAt", mouvement.getCreatedAt());
        details.put("motif", mouvement.getMotif());

        if (mouvement.getLot() != null) {
            Map<String, Object> lotInfo = new HashMap<>();
            lotInfo.put("id", mouvement.getLot().getId());
            lotInfo.put("numeroLot", mouvement.getLot().getNumeroLot());
            lotInfo.put("datePeremption", mouvement.getLot().getDatePeremption());
            details.put("lotDetails", lotInfo);
        }

        if (mouvement.getBonReception() != null) {
            details.put("bonReceptionReference", mouvement.getBonReception().toString());
        }

        return details;
    }

    /**
     * Annuler un mouvement
     */
    @Transactional
    public void annulerMouvement(UUID id, String motif, UUID utilisateurId) {
        StockMovement mouvement = mouvementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mouvement non trouvé"));

        if (mouvement.getStatut() == StockMovement.MovementStatus.ANNULE) {
            throw new RuntimeException("Le mouvement est déjà annulé");
        }

        // Créer un mouvement inverse
        StockMovement mouvementInverse = new StockMovement();
        mouvementInverse.setReference(genererReference() + "-ANN");
        mouvementInverse.setType(mouvement.getType());
        mouvementInverse.setArticle(mouvement.getArticle());
        mouvementInverse.setDepot(mouvement.getDepot());
        mouvementInverse.setEmplacement(mouvement.getEmplacement());
        mouvementInverse.setQuantite(mouvement.getQuantite());
        mouvementInverse.setCoutUnitaire(mouvement.getCoutUnitaire());
        mouvementInverse.setLot(mouvement.getLot());
        mouvementInverse.setDateMouvement(LocalDateTime.now());
        mouvementInverse.setDateComptable(LocalDate.now());
        mouvementInverse.setUtilisateurId(utilisateurId);
        mouvementInverse.setMotif("Annulation: " + motif);
        mouvementInverse.setStatut(StockMovement.MovementStatus.VALIDE);

        // Inverser le type de mouvement
        MovementType typeInverse = trouverTypeInverse(mouvement.getType());
        mouvementInverse.setType(typeInverse);

        // Sauvegarder le mouvement inverse
        mouvementRepository.save(mouvementInverse);

        // Marquer le mouvement original comme annulé
        mouvement.setStatut(StockMovement.MovementStatus.ANNULE);
        mouvement.setMotif(motif);
        mouvementRepository.save(mouvement);

        // Mettre à jour le stock
        mettreAJourStock(mouvementInverse);

        log.info("Mouvement annulé: {} par utilisateur {}", id, utilisateurId);
    }

    /**
     * Trouver le type de mouvement inverse
     */
    private MovementType trouverTypeInverse(MovementType type) {
        // Déterminer le sens inverse
        String sensInverse = "ENTREE".equals(type.getSens()) ? "SORTIE" : "ENTREE";

        // Chercher un type avec le même code + "_ANNULATION"
        String codeInverse = type.getCode() + "_ANNULATION";
        Optional<MovementType> typeOpt = typeMouvementRepository.findByCode(codeInverse);

        if (typeOpt.isPresent()) {
            return typeOpt.get();
        }

        // Sinon, chercher un type avec le sens inverse et un nom similaire
        String libelleInverse = "Annulation " + type.getLibelle();
        typeOpt = typeMouvementRepository.findByLibelle(libelleInverse);

        if (typeOpt.isPresent()) {
            return typeOpt.get();
        }

        // En dernier recours, prendre le premier type avec le sens inverse
        List<MovementType> typesInverse = typeMouvementRepository.findBySensString(sensInverse);
        if (!typesInverse.isEmpty()) {
            // Chercher un type d'annulation ou ajustement
            for (MovementType t : typesInverse) {
                if (t.getCode().contains("AJUSTEMENT") || t.getCode().contains("ANNULATION")) {
                    return t;
                }
            }
            return typesInverse.get(0);
        }

        throw new RuntimeException("Type de mouvement inverse non trouvé pour: " + type.getCode());
    }

    /**
     * Statistiques des mouvements
     */
    public Map<String, Object> getStatistiquesMouvements(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();

        // Calculer les totaux par sens
        List<Object[]> totauxParSens = mouvementRepository.getTotauxParSens(dateDebut, dateFin);

        long totalEntrees = 0;
        long totalSorties = 0;
        BigDecimal valeurEntrees = BigDecimal.ZERO;
        BigDecimal valeurSorties = BigDecimal.ZERO;

        for (Object[] row : totauxParSens) {
            // row[0] peut être MovementType.SensMouvement (enum) ou String selon JPA /
            // dialecte
            Object sensObj = row[0];
            String sens;
            if (sensObj instanceof com.gestion.stock.entity.MovementType.SensMouvement) {
                sens = ((com.gestion.stock.entity.MovementType.SensMouvement) sensObj).name();
            } else if (sensObj instanceof String) {
                sens = (String) sensObj;
            } else {
                sens = sensObj != null ? sensObj.toString() : "";
            }

            Long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            Long quantite = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            BigDecimal valeur = row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO;

            if ("ENTREE".equalsIgnoreCase(sens)) {
                totalEntrees = count;
                valeurEntrees = valeur != null ? valeur : BigDecimal.ZERO;
            } else if ("SORTIE".equalsIgnoreCase(sens)) {
                totalSorties = count;
                valeurSorties = valeur != null ? valeur : BigDecimal.ZERO;
            }
        }

        stats.put("dateDebut", dateDebut);
        stats.put("dateFin", dateFin);
        stats.put("totalMouvements", totalEntrees + totalSorties);
        stats.put("totalEntrees", totalEntrees);
        stats.put("totalSorties", totalSorties);
        stats.put("valeurEntrees", valeurEntrees);
        stats.put("valeurSorties", valeurSorties);
        stats.put("soldeValeur", valeurEntrees.subtract(valeurSorties));

        // Statistiques par jour
        java.time.LocalDateTime debutDateTime = dateDebut != null ? dateDebut.atStartOfDay()
                : java.time.LocalDateTime.MIN;
        java.time.LocalDateTime finDateTime = dateFin != null ? dateFin.plusDays(1).atStartOfDay().minusNanos(1)
                : java.time.LocalDateTime.MAX;
        List<Object[]> statsParJour = mouvementRepository.getStatistiquesParJour(debutDateTime, finDateTime);
        stats.put("statistiquesParJour", statsParJour);

        log.info("Statistiques mouvements: {} entrées, {} sorties", totalEntrees, totalSorties);

        return stats;
    }

    /**
     * Obtenir les mouvements récents d'un article
     */
    public List<Map<String, Object>> getMouvementsRecentsArticle(UUID articleId, int limit) {
        Pageable pageable = PageRequest.of(0, limit);
        List<StockMovement> mouvements = mouvementRepository.findDerniersMouvementsArticle(articleId, pageable);

        return mouvements.stream()
                .map(this::convertirMouvementEnMap)
                .collect(Collectors.toList());
    }

    /**
     * Obtenir les détails d'un bon de réception
     */
    public Map<String, Object> getDetailsBonReception(String bonReceptionId) {
        // Cette méthode nécessite une intégration avec le module achat
        Map<String, Object> details = new HashMap<>();
        details.put("id", bonReceptionId);
        details.put("reference", "BR-" + bonReceptionId.substring(0, 8));
        details.put("statut", "RECU");
        return details;
    }

    /**
     * Obtenir les détails d'une réservation
     */
    public Map<String, Object> getDetailsReservation(String reservationId) {
        // Cette méthode nécessite une intégration avec le module vente
        Map<String, Object> details = new HashMap<>();
        details.put("id", reservationId);
        details.put("reference", "RES-" + reservationId.substring(0, 8));
        details.put("statut", "ACTIVE");
        return details;
    }
}