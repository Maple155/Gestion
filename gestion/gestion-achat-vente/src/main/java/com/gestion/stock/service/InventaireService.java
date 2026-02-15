package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventaireService {

    private final InventaireRepository inventaireRepository;
    private final LigneInventaireRepository ligneInventaireRepository;
    private final AjustementInventaireRepository ajustementInventaireRepository;
    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final StockMovementRepository mouvementRepository;
    private final MovementTypeRepository movementTypeRepository;
    private final SequenceGeneratorService sequenceService;
    private final StockService stockService;
    private final UtilisateurRepository utilisateurRepository;
    private final ZoneStockageRepository zoneStockageRepository;
    private final CategorieArticleRepository categorieArticleRepository;
    private final EmplacementRepository emplacementRepository;

    /**
     * Créer une nouvelle campagne d'inventaire
     */
    @Transactional
    public Inventaire creerInventaire(Inventaire.TypeInventaire type,
            UUID depotId,
            UUID zoneId,
            UUID categorieId,
            LocalDate dateDebut,
            LocalDate dateFin,
            UUID responsableId,
            String observations,
            String modeSaisie) {

        log.info("Création inventaire type: {}, dépôt: {}", type, depotId);

        // Générer référence
        String reference = "INV-" + LocalDate.now().getYear() +
                String.format("-%04d", sequenceService.getNextInventaireSequence());

        Depot depot = depotId != null ? depotRepository.findById(depotId).orElse(null) : null;
        ZoneStockage zone = zoneId != null ? zoneStockageRepository.findById(zoneId).orElse(null) : null;
        CategorieArticle categorie = categorieId != null ? categorieArticleRepository.findById(categorieId).orElse(null)
                : null;
        Utilisateur responsable = getUtilisateurById(responsableId);

        Inventaire inventaire = Inventaire.builder()
                .reference(reference)
                .type(type)
                .depot(depot)
                .zone(zone)
                .categorie(categorie)
                .dateDebut(dateDebut)
                .dateFin(dateFin)
                .statut(Inventaire.StatutInventaire.PLANIFIE)
                .responsable(responsable)
                .observations(observations)
                .build();

        Inventaire inventaireSauvegarde = inventaireRepository.save(inventaire);

        // Initialiser les lignes d'inventaire
        initialiserLignesInventaire(inventaireSauvegarde.getId(), depotId, zoneId, categorieId);

        log.info("Inventaire créé: {} avec {} lignes", reference,
                ligneInventaireRepository.findByInventaireId(inventaireSauvegarde.getId()).size());

        return inventaireSauvegarde;
    }

    /**
     * Initialiser les lignes d'inventaire avec le stock théorique
     */
    @Transactional
    public void initialiserLignesInventaire(UUID inventaireId, UUID depotId,
            UUID zoneId, UUID categorieId) {

        log.info("Initialisation lignes inventaire: {}", inventaireId);

        // Récupérer les stocks selon les critères
        List<Stock> stocks = getStocksPourInventaire(depotId, zoneId, categorieId);
        Inventaire inventaire = inventaireRepository.findById(inventaireId).orElseThrow();

        for (Stock stock : stocks) {
            // Vérifier si l'article est actif
            if (!stock.getArticle().isActif()) {
                continue;
            }

            LigneInventaire ligne = LigneInventaire.builder()
                    .inventaire(inventaire)
                    .article(stock.getArticle())
                    .depot(stock.getDepot())
                    .quantiteTheorique(stock.getQuantiteTheorique())
                    .coutUnitaire(stock.getCoutUnitaireMoyen())
                    .statut(LigneInventaire.StatutLigneInventaire.A_COMPTER)
                    .build();

            ligneInventaireRepository.save(ligne);
        }

        // Mettre à jour le nombre d'articles
        long nombreLignes = ligneInventaireRepository.findByInventaireId(inventaireId).size();
        inventaire.setNombreArticlesComptes((int) nombreLignes);
        inventaireRepository.save(inventaire);

        log.info("{} lignes initialisées pour inventaire {}", nombreLignes, inventaireId);
    }

    /**
     * Enregistrer un comptage
     */
    @Transactional
    public LigneInventaire enregistrerComptage(UUID ligneId, Integer quantite,
            UUID compteurId, boolean estRecomptage,
            String observations, String codeBarreScanner) {

        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
                .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));

        // Vérifier le code-barre scanné si fourni
        if (codeBarreScanner != null && !codeBarreScanner.isEmpty()) {
            if (!codeBarreScanner.equals(ligne.getArticle().getCodeBarre())) {
                throw new RuntimeException("Code-barre scanné ne correspond pas à l'article attendu");
            }
        }

        if (estRecomptage) {
            ligne.setQuantiteComptee2(quantite);
            ligne.setCompteur2Id(compteurId);
            ligne.setDateComptage2(LocalDateTime.now());
            ligne.setObservations(observations);
        } else {
            ligne.setQuantiteComptee1(quantite);
            ligne.setCompteur1Id(compteurId);
            ligne.setDateComptage1(LocalDateTime.now());
            ligne.setObservations(observations);
        }

        // Déterminer la quantité finale
        determinerQuantiteFinale(ligne);

        ligne.calculerEcart();

        // Vérifier si besoin de recomptage
        if (doitEtreRecompte(ligne) && !estRecomptage) {
            ligne.setStatut(LigneInventaire.StatutLigneInventaire.ECART_A_RECOMPTER);
        } else {
            ligne.setStatut(LigneInventaire.StatutLigneInventaire.COMPTE);
        }

        return ligneInventaireRepository.save(ligne);
    }

    /**
     * Valider une ligne d'inventaire
     */
    @Transactional
    public LigneInventaire validerLigne(UUID ligneId, Integer quantiteFinale,
            String causeEcart, String decision,
            UUID valideurId) {

        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
                .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));

        if (quantiteFinale != null) {
            ligne.setQuantiteCompteeFinale(quantiteFinale);
        }

        ligne.calculerEcart();
        ligne.setCauseEcart(causeEcart);

        switch (decision) {
            case "AJUSTER":
                ligne.setStatut(LigneInventaire.StatutLigneInventaire.AJUSTE);
                if (ligne.getEcart() != 0) {
                    creerAjustement(ligne, valideurId);
                }
                break;
            case "EXCLURE":
                ligne.setStatut(LigneInventaire.StatutLigneInventaire.EXCLU);
                break;
            case "ACCEPTER":
            default:
                ligne.setStatut(LigneInventaire.StatutLigneInventaire.VALIDE);
                // Si écart, créer ajustement
                if (ligne.getEcart() != 0) {
                    creerAjustement(ligne, valideurId);
                }
                break;
        }

        return ligneInventaireRepository.save(ligne);
    }

    /**
     * Valider plusieurs lignes en batch
     */
    @Transactional
    public int validerLignesBatch(List<UUID> ligneIds, String decision,
            String causeEcart, UUID valideurId) {

        int nbValidees = 0;

        for (UUID ligneId : ligneIds) {
            try {
                validerLigne(ligneId, null, causeEcart, decision, valideurId);
                nbValidees++;
            } catch (Exception e) {
                log.error("Erreur validation ligne {}: {}", ligneId, e.getMessage());
            }
        }

        return nbValidees;
    }

    /**
     * Clôturer un inventaire
     */
    @Transactional
    public Inventaire cloturerInventaire(UUID inventaireId, UUID utilisateurId,
            String motifCloture) {

        Inventaire inventaire = inventaireRepository.findById(inventaireId)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));

        // Vérifier que toutes les lignes sont validées
        long lignesNonValidees = ligneInventaireRepository.findByInventaireId(inventaireId)
                .stream()
                .filter(l -> l.getStatut() != LigneInventaire.StatutLigneInventaire.VALIDE
                        && l.getStatut() != LigneInventaire.StatutLigneInventaire.EXCLU
                        && l.getStatut() != LigneInventaire.StatutLigneInventaire.AJUSTE)
                .count();

        if (lignesNonValidees > 0) {
            throw new RuntimeException(lignesNonValidees + " lignes non validées");
        }

        // Calculer les statistiques finales
        calculerStatistiquesInventaire(inventaireId);

        // Mettre à jour le statut
        inventaire.setStatut(Inventaire.StatutInventaire.CLOTURE);
        inventaire.setDateCloture(LocalDateTime.now());

        // Mettre à jour les stocks physiques
        mettreAJourStocksPhysiques(inventaireId);

        log.info("Inventaire clôturé: {}", inventaire.getReference());

        return inventaireRepository.save(inventaire);
    }

    /**
     * Annuler un inventaire
     */
    @Transactional
    public Inventaire annulerInventaire(UUID inventaireId, UUID utilisateurId,
            String motifAnnulation) {

        Inventaire inventaire = inventaireRepository.findById(inventaireId)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));

        // Ne peut annuler que si en cours ou planifié
        if (inventaire.getStatut() != Inventaire.StatutInventaire.PLANIFIE
                && inventaire.getStatut() != Inventaire.StatutInventaire.EN_COURS) {
            throw new RuntimeException("Seuls les inventaires planifiés ou en cours peuvent être annulés");
        }

        // Supprimer les lignes d'inventaire
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);
        ligneInventaireRepository.deleteAll(lignes);

        // Mettre à jour le statut
        inventaire.setStatut(Inventaire.StatutInventaire.ANNULE);
        inventaire.setDateCloture(LocalDateTime.now());

        return inventaireRepository.save(inventaire);
    }

    /**
     * Démarrer un inventaire
     */
    @Transactional
    public Inventaire demarrerInventaire(UUID inventaireId, UUID utilisateurId) {

        Inventaire inventaire = inventaireRepository.findById(inventaireId)
                .orElseThrow(() -> new RuntimeException("Inventaire non trouvé"));

        // Vérifier que l'inventaire est planifié
        if (inventaire.getStatut() != Inventaire.StatutInventaire.PLANIFIE) {
            throw new RuntimeException("L'inventaire doit être planifié pour être démarré");
        }

        // Vérifier qu'il y a des lignes
        long nombreLignes = ligneInventaireRepository.countByInventaireId(inventaireId);
        if (nombreLignes == 0) {
            throw new RuntimeException("Aucune ligne d'inventaire à compter");
        }

        // Mettre à jour le statut
        inventaire.setStatut(Inventaire.StatutInventaire.EN_COURS);
        inventaire.setDateDebut(LocalDateTime.now().toLocalDate());

        return inventaireRepository.save(inventaire);
    }

    @Transactional
    public AjustementInventaire creerAjustement(LigneInventaire ligne, UUID valideurId) {

        log.info("Création ajustement pour ligne: {}", ligne.getId());

        // Vérifier si double validation requise
        boolean doubleValidation = requiertDoubleValidation(ligne);

        // Calculer la valeur de l'ajustement
        BigDecimal valeurAjustement = ligne.getCoutUnitaire() != null && ligne.getEcart() != null
                ? ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ligne.getEcart()))
                : BigDecimal.ZERO;

        AjustementInventaire ajustement = AjustementInventaire.builder()
                .ligneInventaire(ligne)
                .quantiteAjustee(ligne.getEcart())
                .valeurAjustement(valeurAjustement)
                .valideurId(valideurId)
                .dateValidation(LocalDateTime.now())
                .motif("Ajustement suite inventaire - " + ligne.getCauseEcart())
                .justification("Inventaire " + ligne.getInventaire().getReference())
                .requiertDoubleValidation(doubleValidation)
                .build();

        AjustementInventaire ajustementSauvegarde = ajustementInventaireRepository.save(ajustement);

        // Si pas besoin de double validation, créer mouvement de stock immédiat
        if (!doubleValidation) {
            creerMouvementAjustement(ajustementSauvegarde, valideurId);
        }

        return ajustementSauvegarde;
    }

    /**
     * Créer un ajustement d'inventaire (version avec paramètres)
     */
    @Transactional
    public AjustementInventaire creerAjustement(UUID ligneId, Integer quantiteAjustee,
            String motif, String justification,
            UUID valideurId) {

        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
                .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));

        log.info("Création ajustement pour ligne: {}", ligne.getId());

        // Vérifier si double validation requise
        boolean doubleValidation = requiertDoubleValidation(ligne);

        // Calculer la valeur de l'ajustement
        BigDecimal valeurAjustement = ligne.getCoutUnitaire() != null
                ? ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(quantiteAjustee))
                : BigDecimal.ZERO;

        AjustementInventaire ajustement = AjustementInventaire.builder()
                .ligneInventaire(ligne)
                .quantiteAjustee(quantiteAjustee)
                .valeurAjustement(valeurAjustement)
                .valideurId(valideurId)
                .dateValidation(LocalDateTime.now())
                .motif(motif)
                .justification(justification)
                .requiertDoubleValidation(doubleValidation)
                .build();

        AjustementInventaire ajustementSauvegarde = ajustementInventaireRepository.save(ajustement);

        // Si pas besoin de double validation, créer mouvement de stock immédiat
        if (!doubleValidation) {
            creerMouvementAjustement(ajustementSauvegarde, valideurId);
        }

        return ajustementSauvegarde;
    }

    /**
     * Valider un ajustement (seconde validation si nécessaire)
     */
    @Transactional
    public AjustementInventaire validerAjustement(UUID ajustementId, UUID secondValideurId,
            String justification) {

        AjustementInventaire ajustement = ajustementInventaireRepository.findById(ajustementId)
                .orElseThrow(() -> new RuntimeException("Ajustement non trouvé"));

        if (!ajustement.getRequiertDoubleValidation()) {
            throw new RuntimeException("Cet ajustement ne requiert pas de double validation");
        }

        ajustement.setSecondValideurId(secondValideurId);
        ajustement.setDateSecondValidation(LocalDateTime.now());
        ajustement.setJustification(justification);

        // Créer mouvement de stock
        creerMouvementAjustement(ajustement, secondValideurId);

        return ajustementInventaireRepository.save(ajustement);
    }

    /**
     * Créer un mouvement de stock pour ajustement
     */
    @Transactional
    public StockMovement creerMouvementAjustement(AjustementInventaire ajustement,
            UUID utilisateurId) {

        LigneInventaire ligne = ajustement.getLigneInventaire();
        String typeMouvement = ajustement.getQuantiteAjustee() > 0 ? "AJUSTEMENT_POSITIF" : "AJUSTEMENT_NEGATIF";

        MovementType type = movementTypeRepository.findByCode(typeMouvement)
                .orElseThrow(() -> new RuntimeException("Type mouvement non trouvé"));

        String reference = "MVT-AJUST-" + LocalDate.now().getYear() +
                String.format("-%06d", sequenceService.getNextMovementSequence());

        StockMovement mouvement = StockMovement.builder()
                .reference(reference)
                .type(type)
                .article(ligne.getArticle())
                .depot(ligne.getDepot())
                .quantite(Math.abs(ajustement.getQuantiteAjustee()))
                .coutUnitaire(ligne.getCoutUnitaire())
                .inventaire(ligne.getInventaire())
                .dateMouvement(LocalDateTime.now())
                .dateComptable(LocalDate.now())
                .utilisateurId(utilisateurId)
                .motif("Ajustement inventaire: " + ajustement.getMotif())
                .statut(StockMovement.MovementStatus.VALIDE)
                .build();

        // Lier l'ajustement au mouvement
        ajustement.setMouvementStock(mouvement);
        ajustementInventaireRepository.save(ajustement);

        // Mettre à jour le stock
        if (ajustement.getQuantiteAjustee() > 0) {
            stockRepository.incrementerQuantiteTheorique(
                    ligne.getArticle().getId(),
                    ligne.getDepot().getId(),
                    ajustement.getQuantiteAjustee());
        } else {
            stockRepository.decrementerQuantiteTheorique(
                    ligne.getArticle().getId(),
                    ligne.getDepot().getId(),
                    Math.abs(ajustement.getQuantiteAjustee()));
        }

        return mouvementRepository.save(mouvement);
    }

    /**
     * Méthodes utilitaires
     */
    private List<Stock> getStocksPourInventaire(UUID depotId, UUID zoneId, UUID categorieId) {
        if (depotId != null) {
            return stockRepository.findByDepotId(depotId);
        }
        // Logique plus complexe pour filtres combinés
        return stockRepository.findAll();
    }

    private void determinerQuantiteFinale(LigneInventaire ligne) {
        if (ligne.getQuantiteComptee2() != null) {
            ligne.setQuantiteCompteeFinale(ligne.getQuantiteComptee2());
        } else if (ligne.getQuantiteComptee1() != null) {
            ligne.setQuantiteCompteeFinale(ligne.getQuantiteComptee1());
        }
    }

    private boolean doitEtreRecompte(LigneInventaire ligne) {
        if (ligne.getQuantiteCompteeFinale() == null || ligne.getQuantiteTheorique() == null) {
            return false;
        }

        int ecart = Math.abs(ligne.getQuantiteCompteeFinale() - ligne.getQuantiteTheorique());
        double pourcentageEcart = (double) ecart / ligne.getQuantiteTheorique() * 100;

        // Règles de recomptage
        return ecart > 10 || pourcentageEcart > 5 ||
                (ligne.getCoutUnitaire() != null &&
                        ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ecart))
                                .compareTo(new BigDecimal("1000")) > 0);
    }

    private boolean requiertDoubleValidation(LigneInventaire ligne) {
        if (ligne.getEcartValeur() == null) {
            return false;
        }
        // Double validation si écart > 5000€
        return ligne.getEcartValeur().abs().compareTo(new BigDecimal("5000")) > 0;
    }

    private void calculerStatistiquesInventaire(UUID inventaireId) {
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);

        BigDecimal valeurEcartTotal = BigDecimal.ZERO;
        BigDecimal valeurStockTheorique = BigDecimal.ZERO;
        BigDecimal quantiteTheoriqueTotal = BigDecimal.ZERO;
        BigDecimal quantiteEcartTotal = BigDecimal.ZERO;
        int lignesAvecEcart = 0;
        int lignesValidees = 0;

        for (LigneInventaire ligne : lignes) {
            if (ligne.getStatut() == LigneInventaire.StatutLigneInventaire.VALIDE
                    || ligne.getStatut() == LigneInventaire.StatutLigneInventaire.AJUSTE) {
                lignesValidees++;
            }

            if (ligne.getEcartValeur() != null) {
                valeurEcartTotal = valeurEcartTotal.add(ligne.getEcartValeur().abs());
                if (ligne.getEcart() != 0) {
                    lignesAvecEcart++;
                }
            }
            if (ligne.getCoutUnitaire() != null && ligne.getQuantiteTheorique() != null) {
                valeurStockTheorique = valeurStockTheorique.add(
                        ligne.getCoutUnitaire().multiply(BigDecimal.valueOf(ligne.getQuantiteTheorique())));
            }
            if (ligne.getQuantiteTheorique() != null) {
                quantiteTheoriqueTotal = quantiteTheoriqueTotal.add(
                        BigDecimal.valueOf(ligne.getQuantiteTheorique()));
            }
            if (ligne.getEcart() != null) {
                quantiteEcartTotal = quantiteEcartTotal.add(
                        BigDecimal.valueOf(Math.abs(ligne.getEcart())));
            }
        }

        // Calculer taux de précision
        BigDecimal tauxPrecision = BigDecimal.ZERO;
        if (quantiteTheoriqueTotal.compareTo(BigDecimal.ZERO) > 0) {
            tauxPrecision = BigDecimal.ONE.subtract(
                    quantiteEcartTotal.divide(quantiteTheoriqueTotal, 4, RoundingMode.HALF_UP))
                    .multiply(BigDecimal.valueOf(100));
        }

        Inventaire inventaire = inventaireRepository.findById(inventaireId).orElseThrow();
        inventaire.setValeurEcartTotal(valeurEcartTotal);
        inventaireRepository.save(inventaire);

        log.info("Statistiques inventaire {}: Écart={}, Précision={}%",
                inventaire.getReference(), valeurEcartTotal, tauxPrecision);
    }

    private void mettreAJourStocksPhysiques(UUID inventaireId) {
        List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);

        for (LigneInventaire ligne : lignes) {
            if (ligne.getQuantiteCompteeFinale() != null) {
                Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(
                        ligne.getArticle().getId(), ligne.getDepot().getId());

                if (stockOpt.isPresent()) {
                    Stock stock = stockOpt.get();
                    stock.setQuantitePhysique(ligne.getQuantiteCompteeFinale());
                    stock.setDateDernierInventaire(LocalDateTime.now());
                    stockRepository.save(stock);
                }
            }
        }
    }

    /**
     * Méthodes pour le controller
     */
    public Page<Inventaire> getInventairesFiltres(String reference,
            Inventaire.TypeInventaire type,
            Inventaire.StatutInventaire statut,
            String depotId,
            String responsableId,
            LocalDate dateDebut,
            LocalDate dateFin,
            Pageable pageable) {
        return inventaireRepository.findByFilters(
                reference, type, statut,
                depotId != null && !depotId.isEmpty() ? UUID.fromString(depotId) : null,
                responsableId != null && !responsableId.isEmpty() ? UUID.fromString(responsableId) : null,
                dateDebut, dateFin, pageable);
    }

    public Inventaire getInventaireById(UUID id) {
        return inventaireRepository.findById(id).orElse(null);
    }

    public Page<LigneInventaire> getLignesInventairePaginees(UUID inventaireId,
            LigneInventaire.StatutLigneInventaire statutLigne,
            Boolean avecEcart,
            Pageable pageable) {
        return ligneInventaireRepository.findByInventaireIdAndFilters(
                inventaireId, statutLigne, avecEcart, pageable);
    }

    public List<LigneInventaire> getLignesInventaire(UUID inventaireId) {
        return ligneInventaireRepository.findByInventaireId(inventaireId);
    }

    public LigneInventaire getProchaineLigneACompter(UUID inventaireId, UUID utilisateurId) {
        return ligneInventaireRepository.findFirstByInventaireIdAndStatutOrderByArticleCodeArticle(
                inventaireId, LigneInventaire.StatutLigneInventaire.A_COMPTER);
    }

    public LigneInventaire getLigneInventaire(UUID inventaireId, UUID articleId, UUID emplacementId) {
        return ligneInventaireRepository.findByInventaireIdAndArticleIdAndEmplacementId(
                inventaireId, articleId, emplacementId).orElse(null);
    }

    public Map<String, Object> getProgressionComptage(UUID inventaireId, UUID utilisateurId) {
        Map<String, Object> progression = new HashMap<>();

        long total = ligneInventaireRepository.countByInventaireId(inventaireId);
        long comptes = ligneInventaireRepository.countByInventaireIdAndStatut(
                inventaireId, LigneInventaire.StatutLigneInventaire.COMPTE);
        long comptesUtilisateur = ligneInventaireRepository.countByInventaireIdAndCompteur1Id(
                inventaireId, utilisateurId);

        progression.put("total", total);
        progression.put("comptes", comptes);
        progression.put("comptesUtilisateur", comptesUtilisateur);
        progression.put("pourcentage", total > 0 ? (comptes * 100 / total) : 0);

        return progression;
    }

    public Map<String, Object> getStatistiquesInventaire(UUID inventaireId) {
        Map<String, Object> stats = new HashMap<>();
        Inventaire inventaire = inventaireRepository.findById(inventaireId).orElse(null);

        if (inventaire == null) {
            return stats;
        }

        // Récupérer les statistiques calculées
        stats.put("valeurEcartTotal", inventaire.getValeurEcartTotal());
        stats.put("nombreArticlesComptes", inventaire.getNombreArticlesComptes());

        // Calculer d'autres statistiques si nécessaire
        long lignesTotal = ligneInventaireRepository.countByInventaireId(inventaireId);
        stats.put("lignesTotal", lignesTotal);

        return stats;
    }

    public Map<String, Object> getStatistiquesInventaires() {
        Map<String, Object> stats = new HashMap<>();

        long totalInventaires = inventaireRepository.count();
        long inventairesPlanifies = inventaireRepository.countByStatut(Inventaire.StatutInventaire.PLANIFIE);
        long inventairesEnCours = inventaireRepository.countByStatut(Inventaire.StatutInventaire.EN_COURS);
        long inventairesClotures = inventaireRepository.countByStatut(Inventaire.StatutInventaire.CLOTURE);

        // Calculer la précision moyenne sans utiliser tauxPrecision
        BigDecimal precisionMoyenne = calculerPrecisionMoyenne();

        stats.put("totalInventaires", totalInventaires);
        stats.put("inventairesPlanifies", inventairesPlanifies);
        stats.put("inventairesEnCours", inventairesEnCours);
        stats.put("inventairesClotures", inventairesClotures);
        stats.put("precisionMoyenne", precisionMoyenne);

        return stats;
    }

    public UUID getInventaireIdByLigneId(UUID ligneId) {
        LigneInventaire ligne = ligneInventaireRepository.findById(ligneId)
                .orElseThrow(() -> new RuntimeException("Ligne inventaire non trouvée"));
        return ligne.getInventaire().getId();
    }

    public List<AjustementInventaire> getAjustementsInventaire(UUID inventaireId) {
        return ajustementInventaireRepository.findByInventaireId(inventaireId);
    }

    public List<Inventaire> getInventairesEnCours() {
        return inventaireRepository.findByStatut(Inventaire.StatutInventaire.EN_COURS);
    }

    public List<Depot> getDepotsActifs() {
        return depotRepository.findByActifTrue();
    }

    public List<ZoneStockage> getZonesStockage() {
        return zoneStockageRepository.findAll();
    }

    public List<CategorieArticle> getCategoriesArticles() {
        return categorieArticleRepository.findAll();
    }

    /**
     * Générer un rapport d'inventaire
     */
    public String genererRapportInventaire(UUID inventaireId, String format) {
        // Implémentation simplifiée - retourne le nom du fichier généré
        Inventaire inventaire = getInventaireById(inventaireId);
        String nomFichier = "rapport_inventaire_" + inventaire.getReference() + "_"
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("PDF".equalsIgnoreCase(format)) {
            return nomFichier + ".pdf";
        } else if ("EXCEL".equalsIgnoreCase(format) || "CSV".equalsIgnoreCase(format)) {
            return nomFichier + ".xlsx";
        } else {
            return nomFichier + ".html";
        }
    }

    /**
     * Exporter les lignes d'inventaire
     */
    public String exporterLignesInventaire(UUID inventaireId, String format) {
        Inventaire inventaire = getInventaireById(inventaireId);
        String nomFichier = "export_lignes_" + inventaire.getReference() + "_"
                + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

        if ("CSV".equalsIgnoreCase(format)) {
            return nomFichier + ".csv";
        } else {
            return nomFichier + ".xlsx";
        }
    }

    /**
     * Scanner pour inventaire
     */
    public Map<String, Object> scannerPourInventaire(UUID inventaireId, String codeBarre,
            String typeScan, UUID utilisateurId) {
        Map<String, Object> resultat = new HashMap<>();

        // Recherche selon le type de scan
        switch (typeScan) {
            case "ARTICLE":
                Article article = articleRepository.findByCodeBarre(codeBarre).orElse(null);
                if (article != null) {
                    resultat.put("article", article);
                    resultat.put("ligne", ligneInventaireRepository
                            .findFirstByInventaireIdAndArticleId(inventaireId, article.getId()));
                }
                break;
            case "LOT":
                // Implémenter la recherche par lot
                break;
            case "EMPLACEMENT":
                // Implémenter la recherche par emplacement
                break;
        }

        resultat.put("scanValide", resultat.containsKey("article"));

        return resultat;
    }

    /**
     * Synchroniser les données hors ligne
     */
    public int synchroniserDonneesHorsLigne(UUID inventaireId,
            List<Map<String, Object>> donneesHorsLigne,
            UUID utilisateurId) {
        int nbSynchronises = 0;

        for (Map<String, Object> donnee : donneesHorsLigne) {
            try {
                String ligneId = (String) donnee.get("ligneId");
                Integer quantite = (Integer) donnee.get("quantite");
                Boolean estRecomptage = (Boolean) donnee.get("estRecomptage");
                String observations = (String) donnee.get("observations");
                String codeBarreScanner = (String) donnee.get("codeBarreScanner");

                enregistrerComptage(
                        UUID.fromString(ligneId),
                        quantite,
                        utilisateurId,
                        estRecomptage != null ? estRecomptage : false,
                        observations,
                        codeBarreScanner);

                nbSynchronises++;
            } catch (Exception e) {
                log.error("Erreur synchronisation donnée: {}", e.getMessage());
            }
        }

        return nbSynchronises;
    }

    public Map<String, Object> getStatistiquesGlobales(Integer mois, Integer annee) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("mois", mois);
        stats.put("annee", annee);
        
        if (mois != null && annee != null) {
            // Utiliser la nouvelle méthode du repository
            Long inventairesRealises = inventaireRepository.countByStatutAndDateCloture(
                    Inventaire.StatutInventaire.CLOTURE, mois, annee);
            stats.put("inventairesRealises", inventairesRealises != null ? inventairesRealises : 0);
            
            // Calculer la précision moyenne
            BigDecimal precisionMoyenneMois = calculerPrecisionMoyenneParMois(mois, annee);
            stats.put("precisionMoyenneMois", precisionMoyenneMois);
            
            // Calculer le nombre d'articles comptés
            Long articlesComptes = ligneInventaireRepository.countArticlesComptesByPeriode(mois, annee);
            stats.put("articlesComptes", articlesComptes != null ? articlesComptes : 0);
            
            // Calculer la valeur totale des écarts
            BigDecimal valeurEcartMois = ligneInventaireRepository.sumEcartValeurByPeriode(mois, annee);
            stats.put("valeurEcartMois", valeurEcartMois != null ? valeurEcartMois : BigDecimal.ZERO);
            
            // Ajouter les répartitions par statut
            stats.put("inventairesClotures", inventaireRepository.countByStatut(Inventaire.StatutInventaire.CLOTURE));
            stats.put("inventairesEnCours", inventaireRepository.countByStatut(Inventaire.StatutInventaire.EN_COURS));
            stats.put("inventairesPlanifies", inventaireRepository.countByStatut(Inventaire.StatutInventaire.PLANIFIE));
            stats.put("inventairesTermines", inventaireRepository.countByStatut(Inventaire.StatutInventaire.TERMINE));
            stats.put("inventairesAnnules", inventaireRepository.countByStatut(Inventaire.StatutInventaire.ANNULE));
        } else {
            stats.put("inventairesRealises", 0);
            stats.put("precisionMoyenneMois", BigDecimal.ZERO);
            stats.put("articlesComptes", 0);
            stats.put("valeurEcartMois", BigDecimal.ZERO);
            stats.put("inventairesClotures", 0);
            stats.put("inventairesEnCours", 0);
            stats.put("inventairesPlanifies", 0);
            stats.put("inventairesTermines", 0);
            stats.put("inventairesAnnules", 0);
        }
        
        return stats;
    }

/**
 * Récupérer le top 5 des écarts d'inventaire pour une période donnée
 */
public List<Map<String, Object>> getTopEcartsInventaire(Integer mois, Integer annee) {
    List<Map<String, Object>> topEcarts = new ArrayList<>();
    
    // Récupérer les lignes d'inventaire avec les plus grands écarts
    List<Object[]> resultats = ligneInventaireRepository.findTopEcartsByPeriode(mois, annee);
    
    for (Object[] resultat : resultats) {
        Map<String, Object> ecart = new HashMap<>();
        
        ecart.put("articleCode", resultat[0]); // code_article
        ecart.put("articleLibelle", resultat[1]); // libelle
        ecart.put("inventaireId", resultat[2]); // inventaire_id
        ecart.put("inventaireReference", resultat[3]); // reference
        ecart.put("ecart", resultat[4]); // ecart
        ecart.put("valeurEcart", resultat[5]); // ecart_valeur
        ecart.put("dateComptage", resultat[6]); // date_comptage
        ecart.put("statut", resultat[7]); // statut
        
        topEcarts.add(ecart);
    }
    
    return topEcarts;
}

    /**
 * Récupérer l'évolution mensuelle des inventaires pour une année donnée
 */
public Map<String, Object> getEvolutionInventaires(Integer annee) {
    Map<String, Object> evolution = new HashMap<>();
    
    if (annee == null) {
        annee = LocalDate.now().getYear();
    }
    
    // Initialiser les tableaux pour les 12 mois
    Integer[] inventairesParMois = new Integer[12];
    BigDecimal[] precisionParMois = new BigDecimal[12];
    
    // Initialiser avec des valeurs par défaut (0)
    for (int i = 0; i < 12; i++) {
        inventairesParMois[i] = 0;
        precisionParMois[i] = BigDecimal.ZERO;
    }
    
    // Récupérer les données d'inventaires clôturés par mois
    List<Object[]> resultatsInventaires = inventaireRepository.countInventairesCloturesParMois(annee);
    for (Object[] resultat : resultatsInventaires) {
        Integer mois = ((Number) resultat[0]).intValue() - 1; // -1 car tableau indexé à 0
        Long nombre = (Long) resultat[1];
        if (mois >= 0 && mois < 12) {
            inventairesParMois[mois] = nombre.intValue();
        }
    }
    
    // Calculer la précision moyenne par mois
    for (int mois = 0; mois < 12; mois++) {
        int moisIndex = mois + 1;
        BigDecimal precision = calculerPrecisionMoyenneParMois(moisIndex, annee);
        precisionParMois[mois] = precision;
    }
    
    // Préparer les labels des mois
    String[] moisLabels = new String[12];
    for (int i = 0; i < 12; i++) {
        moisLabels[i] = LocalDate.of(annee, i + 1, 1).getMonth().toString().substring(0, 3);
    }
    
    evolution.put("annee", annee);
    evolution.put("moisLabels", moisLabels);
    evolution.put("inventairesParMois", inventairesParMois);
    evolution.put("precisionParMois", precisionParMois);
    
    // Calculer les totaux et moyennes
    int totalInventaires = Arrays.stream(inventairesParMois).mapToInt(Integer::intValue).sum();
    BigDecimal precisionAnnuelle = Arrays.stream(precisionParMois)
            .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    
    long nbMoisAvecPrecision = Arrays.stream(precisionParMois)
            .filter(p -> p.compareTo(BigDecimal.ZERO) > 0)
            .count();
    
    if (nbMoisAvecPrecision > 0) {
        precisionAnnuelle = precisionAnnuelle.divide(BigDecimal.valueOf(nbMoisAvecPrecision), 2, RoundingMode.HALF_UP);
    }
    
    evolution.put("totalInventaires", totalInventaires);
    evolution.put("precisionAnnuelle", precisionAnnuelle);
    
    // Trouver le meilleur et le pire mois
    int maxInventaires = 0;
    int maxInventairesMois = 0;
    BigDecimal maxPrecision = BigDecimal.ZERO;
    int maxPrecisionMois = 0;
    
    for (int i = 0; i < 12; i++) {
        if (inventairesParMois[i] > maxInventaires) {
            maxInventaires = inventairesParMois[i];
            maxInventairesMois = i + 1;
        }
        if (precisionParMois[i].compareTo(maxPrecision) > 0) {
            maxPrecision = precisionParMois[i];
            maxPrecisionMois = i + 1;
        }
    }
    
    evolution.put("moisMaxInventaires", maxInventairesMois);
    evolution.put("maxInventaires", maxInventaires);
    evolution.put("moisMaxPrecision", maxPrecisionMois);
    evolution.put("maxPrecision", maxPrecision);
    
    return evolution;
}

    public List<Inventaire> getInventairesRecents() {
        return inventaireRepository.findTop5ByOrderByCreatedAtDesc();
    }

    private Utilisateur getUtilisateurById(UUID userId) {
        return utilisateurRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé: " + userId));
    }

    /**
     * Calculer la précision moyenne de tous les inventaires clôturés
     */
    private BigDecimal calculerPrecisionMoyenne() {
        List<Inventaire> inventairesClotures = inventaireRepository.findByStatut(Inventaire.StatutInventaire.CLOTURE);

        if (inventairesClotures.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sommePrecisions = BigDecimal.ZERO;
        int nbInventairesAvecPrecision = 0;

        for (Inventaire inventaire : inventairesClotures) {
            BigDecimal precision = calculerPrecisionInventaire(inventaire.getId());
            if (precision != null) {
                sommePrecisions = sommePrecisions.add(precision);
                nbInventairesAvecPrecision++;
            }
        }

        if (nbInventairesAvecPrecision == 0) {
            return BigDecimal.ZERO;
        }

        return sommePrecisions.divide(BigDecimal.valueOf(nbInventairesAvecPrecision), 2, RoundingMode.HALF_UP);
    }

    /**
     * Calculer la précision moyenne par mois/année
     */
    private BigDecimal calculerPrecisionMoyenneParMois(Integer mois, Integer annee) {
        if (mois == null || annee == null) {
            return BigDecimal.ZERO;
        }

        List<Inventaire> inventaires = inventaireRepository.findByStatutAndDateCloture(
                Inventaire.StatutInventaire.CLOTURE, mois, annee);

        if (inventaires.isEmpty()) {
            return BigDecimal.ZERO;
        }

        BigDecimal sommePrecisions = BigDecimal.ZERO;
        int nbInventairesAvecPrecision = 0;

        for (Inventaire inventaire : inventaires) {
            BigDecimal precision = calculerPrecisionInventaire(inventaire.getId());
            if (precision != null) {
                sommePrecisions = sommePrecisions.add(precision);
                nbInventairesAvecPrecision++;
            }
        }

        if (nbInventairesAvecPrecision == 0) {
            return BigDecimal.ZERO;
        }

        return sommePrecisions.divide(BigDecimal.valueOf(nbInventairesAvecPrecision), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal calculerPrecisionInventaire(UUID inventaireId) {
        try {
            List<LigneInventaire> lignes = ligneInventaireRepository.findByInventaireId(inventaireId);

            if (lignes == null || lignes.isEmpty()) {
                return BigDecimal.ZERO;
            }

            BigDecimal totalQuantiteTheorique = BigDecimal.ZERO;
            BigDecimal totalEcartAbsolu = BigDecimal.ZERO;

            for (LigneInventaire ligne : lignes) {
                if (ligne.getQuantiteTheorique() != null) {
                    totalQuantiteTheorique = totalQuantiteTheorique.add(
                            BigDecimal.valueOf(ligne.getQuantiteTheorique()));
                }
                if (ligne.getEcart() != null) {
                    totalEcartAbsolu = totalEcartAbsolu.add(
                            BigDecimal.valueOf(Math.abs(ligne.getEcart())));
                }
            }

            if (totalQuantiteTheorique.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal taux = BigDecimal.ONE.subtract(
                        totalEcartAbsolu.divide(totalQuantiteTheorique, 4, RoundingMode.HALF_UP))
                        .multiply(BigDecimal.valueOf(100));
                return taux.setScale(2, RoundingMode.HALF_UP);
            }

            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erreur calcul précision inventaire {}: {}", inventaireId, e.getMessage());
            return BigDecimal.ZERO;
        }
    }

    /**
 * Récupérer la répartition des inventaires par statut
 */
public Map<String, Object> getRepartitionParStatut() {
    Map<String, Object> repartition = new HashMap<>();
    
    long clotures = inventaireRepository.countByStatut(Inventaire.StatutInventaire.CLOTURE);
    long enCours = inventaireRepository.countByStatut(Inventaire.StatutInventaire.EN_COURS);
    long planifies = inventaireRepository.countByStatut(Inventaire.StatutInventaire.PLANIFIE);
    long annules = inventaireRepository.countByStatut(Inventaire.StatutInventaire.ANNULE);
    
    repartition.put("clotures", clotures);
    repartition.put("enCours", enCours);
    repartition.put("planifies", planifies);
    repartition.put("annules", annules);
    
    // Calculer les pourcentages
    long total = clotures + enCours + planifies + annules;
    if (total > 0) {
        repartition.put("pourcentageClotures", Math.round((clotures * 100.0) / total));
        repartition.put("pourcentageEnCours", Math.round((enCours * 100.0) / total));
        repartition.put("pourcentagePlanifies", Math.round((planifies * 100.0) / total));
        repartition.put("pourcentageAnnules", Math.round((annules * 100.0) / total));
    } else {
        repartition.put("pourcentageClotures", 0);
        repartition.put("pourcentageEnCours", 0);
        repartition.put("pourcentagePlanifies", 0);
        repartition.put("pourcentageAnnules", 0);
    }
    
    repartition.put("total", total);
    
    return repartition;
}
}