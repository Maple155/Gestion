package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClotureService {

    private final ClotureMensuelleRepository clotureRepository;
    private final HistoriqueCoutRepository historiqueRepository;
    private final StockRepository stockRepository;
    private final StockMovementRepository mouvementRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    @Lazy
    private final ValorisationService valorisationService;
    private final ReportingService reportingService;
    private final UtilisateurRepository utilisateurRepository;

    private static final DateTimeFormatter FORMATTER_MOIS = DateTimeFormatter.ofPattern("MM/yyyy");

    /**
     * Initialiser une nouvelle période de clôture
     */
    @Transactional
    public ClotureMensuelle initialiserCloture(Integer annee, Integer mois, UUID utilisateurId) {
        log.info("Initialisation clôture {}/{} par utilisateur {}", mois, annee, utilisateurId);

        // Vérifier si la période existe déjà
        Optional<ClotureMensuelle> existing = clotureRepository.findByAnneeAndMois(annee, mois);
        if (existing.isPresent()) {
            ClotureMensuelle cloture = existing.get();
            if (!cloture.isCloturable()) {
                throw new RuntimeException("La période " + cloture.getPeriodeFormat() +
                        " est déjà en statut " + cloture.getStatut());
            }
            return cloture;
        }

        // Calculer les dates de la période
        YearMonth yearMonth = YearMonth.of(annee, mois);
        LocalDate dateDebut = yearMonth.atDay(1);
        LocalDate dateFin = yearMonth.atEndOfMonth();

        // Créer la clôture
        Utilisateur utilisateur = utilisateurRepository.findById(utilisateurId)
                .orElseThrow(() -> new RuntimeException("Utilisateur non trouvé"));

        ClotureMensuelle cloture = ClotureMensuelle.builder()
                .annee(annee)
                .mois(mois)
                .dateDebutPeriode(dateDebut)
                .dateFinPeriode(dateFin)
                .dateCloture(LocalDateTime.now())
                .cloturePar(utilisateur)
                .statut(ClotureMensuelle.StatutCloture.OUVERTE)
                .commentaires("Initialisation automatique")
                .build();

        ClotureMensuelle saved = clotureRepository.save(cloture);
        log.info("Période clôture initialisée: {}", saved.getPeriodeFormat());

        return saved;
    }

    /**
     * Exécuter la clôture mensuelle complète
     */
    @Transactional
    public ClotureMensuelle executerCloture(UUID clotureId, UUID utilisateurId) {
        log.info("Exécution clôture ID: {} par utilisateur: {}", clotureId, utilisateurId);

        ClotureMensuelle cloture = clotureRepository.findById(clotureId)
                .orElseThrow(() -> new RuntimeException("Clôture non trouvée"));

        if (!cloture.isCloturable()) {
            throw new RuntimeException("La clôture n'est pas clôturable. Statut: " + cloture.getStatut());
        }

        // 1. Mettre à jour le statut
        cloture.setStatut(ClotureMensuelle.StatutCloture.EN_COURS);
        cloture = clotureRepository.save(cloture);

        try {
            // 2. Récupérer tous les stocks actifs
            List<Stock> stocks = stockRepository.findAll();
            log.info("Nombre de stocks à clôturer: {}", stocks.size());

            // 3. Grouper par article et dépôt pour traitement optimisé
            Map<UUID, Map<UUID, Stock>> stocksByArticle = stocks.stream()
                    .collect(Collectors.groupingBy(
                            s -> s.getArticle().getId(),
                            Collectors.toMap(
                                    s -> s.getDepot().getId(), // Changed from Stock::getDepotId
                                    s -> s)));

            int totalHistoriques = 0;
            BigDecimal valeurTotaleStock = BigDecimal.ZERO;

            // 4. Pour chaque article, calculer et sauvegarder l'historique
            for (Map.Entry<UUID, Map<UUID, Stock>> entry : stocksByArticle.entrySet()) {
                UUID articleId = entry.getKey();
                Article article = articleRepository.findById(articleId)
                        .orElseThrow(() -> new RuntimeException("Article non trouvé: " + articleId));

                for (Stock stock : entry.getValue().values()) {
                    try {
                        // Calculer la valorisation selon la méthode
                        BigDecimal valorisation = calculerValorisationPourCloture(
                                article, stock, cloture.getDateFinPeriode());

                        // Créer l'historique
                        HistoriqueCout historique = HistoriqueCout.builder()
                                .article(article)
                                .depot(stock.getDepot())
                                .dateEffet(cloture.getDateFinPeriode())
                                .coutUnitaireMoyen(stock.getCoutUnitaireMoyen())
                                .quantiteStock(stock.getQuantiteTheorique())
                                .valeurStock(valorisation)
                                .methodeValorisation(article.getMethodeValorisation())
                                .clotureMensuelle(cloture)
                                .createdBy(utilisateurId)
                                .build();

                        historiqueRepository.save(historique);
                        totalHistoriques++;

                        valeurTotaleStock = valeurTotaleStock.add(valorisation);

                    } catch (Exception e) {
                        log.error("Erreur lors de la clôture pour article {} dépôt {}: {}",
                                article.getCodeArticle(), stock.getDepot().getCode(), e.getMessage());
                        // Continuer avec les autres stocks
                    }
                }
            }

            // 5. Calculer les statistiques des mouvements de la période
            Map<String, Object> statsMouvements = calculerStatistiquesMouvementsPeriode(
                    cloture.getDateDebutPeriode(), cloture.getDateFinPeriode());

            // 6. Mettre à jour la clôture avec les statistiques
            cloture.setNombreArticles(totalHistoriques);
            cloture.setNombreArticlesValorises(totalHistoriques);
            cloture.setValeurStockTotal(valeurTotaleStock);
            cloture.setNombreMouvements((Integer) statsMouvements.getOrDefault("totalMouvements", 0));
            cloture.setValeurMouvementsEntree(
                    (BigDecimal) statsMouvements.getOrDefault("valeurEntrees", BigDecimal.ZERO));
            cloture.setValeurMouvementsSortie(
                    (BigDecimal) statsMouvements.getOrDefault("valeurSorties", BigDecimal.ZERO));

            // Calculer l'écart de valorisation
            BigDecimal ecart = calculerEcartValorisation(cloture.getDateFinPeriode(), valeurTotaleStock);
            cloture.setEcartValorisation(ecart);

            // Taux de couverture
            if (valeurTotaleStock.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tauxCouverture = BigDecimal.valueOf(totalHistoriques * 100.0 / stocks.size())
                        .setScale(2, RoundingMode.HALF_UP);
                cloture.setTauxCouverture(tauxCouverture);
            }

            // 7. Finaliser la clôture
            cloture.setStatut(ClotureMensuelle.StatutCloture.CLOTUREE);
            cloture.setDateCloture(LocalDateTime.now());
            cloture.setCommentaires("Clôture exécutée avec succès. " + totalHistoriques + " articles valorisés.");

            final ClotureMensuelle clotureFinalisee = clotureRepository.save(cloture);

            log.info("Clôture {} terminée avec succès. Valeur totale: {} €",
                    cloture.getPeriodeFormat(), valeurTotaleStock);

            // 8. Générer les rapports (asynchrone)
            CompletableFuture.runAsync(() -> genererRapportsCloture(clotureFinalisee, utilisateurId));

            return cloture;

        } catch (Exception e) {
            // En cas d'erreur, marquer la clôture comme rejetée
            cloture.setStatut(ClotureMensuelle.StatutCloture.REJETEE);
            cloture.setCommentaires("Erreur lors de la clôture: " + e.getMessage());
            clotureRepository.save(cloture);

            log.error("Erreur fatale lors de la clôture {}: {}", cloture.getPeriodeFormat(), e.getMessage(), e);
            throw new RuntimeException("Erreur lors de la clôture: " + e.getMessage(), e);
        }
    }

    /**
     * Calculer la valorisation pour clôture
     */
    private BigDecimal calculerValorisationPourCloture(Article article, Stock stock, LocalDate dateCloture) {
        String methode = article.getMethodeValorisation();

        switch (methode) {
            case "FIFO":
                return valorisationService.calculerValorisationFIFO(article.getId(), stock.getDepot().getId());

            case "FEFO":
                return valorisationService.calculerValorisationFEFO(article.getId(), stock.getDepot().getId());

            case "CUMP":
            default:
                // Pour CUMP, utiliser la valeur stock existante ou calculer
                if (stock.getValeurStockCump() != null && stock.getValeurStockCump().compareTo(BigDecimal.ZERO) > 0) {
                    return stock.getValeurStockCump();
                } else {
                    return valorisationService.calculerCUMP(article.getId(), stock.getDepot().getId())
                            .multiply(BigDecimal.valueOf(stock.getQuantiteTheorique()));
                }
        }
    }

    /**
     * Calculer les statistiques des mouvements pour la période
     */
    private Map<String, Object> calculerStatistiquesMouvementsPeriode(LocalDate dateDebut, LocalDate dateFin) {
        Map<String, Object> stats = new HashMap<>();

        LocalDateTime debut = dateDebut.atStartOfDay();
        LocalDateTime fin = dateFin.atTime(23, 59, 59);

        List<StockMovement> mouvements = mouvementRepository.findByDateMouvementBetween(debut, fin);

        long totalEntrees = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                .count();

        long totalSorties = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
                .count();

        BigDecimal valeurEntrees = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                .map(m -> m.getCoutUnitaire().multiply(BigDecimal.valueOf(m.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal valeurSorties = mouvements.stream()
                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
                .map(m -> m.getCoutUnitaire().multiply(BigDecimal.valueOf(m.getQuantite())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        stats.put("totalMouvements", mouvements.size());
        stats.put("totalEntrees", totalEntrees);
        stats.put("totalSorties", totalSorties);
        stats.put("valeurEntrees", valeurEntrees);
        stats.put("valeurSorties", valeurSorties);

        return stats;
    }

    /**
     * Calculer l'écart de valorisation par rapport à la période précédente
     */
    private BigDecimal calculerEcartValorisation(LocalDate dateCloture, BigDecimal valeurActuelle) {
        try {
            // Trouver la clôture précédente validée
            Optional<ClotureMensuelle> previousCloture = clotureRepository.findDerniereClotureValidee();

            if (previousCloture.isPresent()) {
                ClotureMensuelle previous = previousCloture.get();

                // Calculer la valeur totale de la clôture précédente
                List<HistoriqueCout> historiques = historiqueRepository.findByClotureMensuelleId(previous.getId());
                BigDecimal valeurPrecedente = historiques.stream()
                        .map(HistoriqueCout::getValeurStock)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                if (valeurPrecedente.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal ecart = valeurActuelle.subtract(valeurPrecedente);
                    BigDecimal pourcentageEcart = ecart.divide(valeurPrecedente, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100));

                    log.info("Écart de valorisation: {} € ({}%) entre {} et {}",
                            ecart, pourcentageEcart, previous.getPeriodeFormat(),
                            dateCloture.format(FORMATTER_MOIS));

                    return ecart;
                }
            }
        } catch (Exception e) {
            log.warn("Impossible de calculer l'écart de valorisation: {}", e.getMessage());
        }

        return BigDecimal.ZERO;
    }

    /**
     * Valider une clôture (par la direction)
     */
    @Transactional
    public ClotureMensuelle validerCloture(UUID clotureId, UUID valideurId, String commentaires) {
        ClotureMensuelle cloture = clotureRepository.findById(clotureId)
                .orElseThrow(() -> new RuntimeException("Clôture non trouvée"));

        if (cloture.getStatut() != ClotureMensuelle.StatutCloture.CLOTUREE) {
            throw new RuntimeException("Seules les clôtures terminées peuvent être validées");
        }

        Utilisateur valideur = utilisateurRepository.findById(valideurId)
                .orElseThrow(() -> new RuntimeException("Validateur non trouvé"));

        cloture.setValideur(valideur);
        cloture.setDateValidation(LocalDateTime.now());
        cloture.setStatut(ClotureMensuelle.StatutCloture.VALIDEE);
        cloture.setCommentaires(commentaires != null ? commentaires : "Validée par " + valideur.getNom());

        return clotureRepository.save(cloture);
    }

    /**
     * Rejeter une clôture (doit être reprise)
     */
    @Transactional
    public ClotureMensuelle rejeterCloture(UUID clotureId, UUID utilisateurId, String motif) {
        ClotureMensuelle cloture = clotureRepository.findById(clotureId)
                .orElseThrow(() -> new RuntimeException("Clôture non trouvée"));

        if (cloture.isValidee()) {
            throw new RuntimeException("Impossible de rejeter une clôture déjà validée");
        }

        // Supprimer les historiques associés
        historiqueRepository.deleteByAnneeAndMois(cloture.getAnnee(), cloture.getMois());

        cloture.setStatut(ClotureMensuelle.StatutCloture.REJETEE);
        cloture.setCommentaires("Rejetée: " + motif);

        return clotureRepository.save(cloture);
    }

    /**
     * Générer les rapports de clôture (asynchrone)
     */
    private void genererRapportsCloture(ClotureMensuelle cloture, UUID utilisateurId) {
        try {
            log.info("Génération des rapports pour la clôture {}", cloture.getPeriodeFormat());

            List<String> rapports = new ArrayList<>();

            // 1. Rapport de valorisation
            String rapportValorisation = genererRapportValorisation(cloture);
            rapports.add(rapportValorisation);

            // 2. Rapport des écarts
            String rapportEcart = genererRapportEcart(cloture);
            rapports.add(rapportEcart);

            // 3. Rapport des mouvements
            String rapportMouvements = genererRapportMouvements(cloture);
            rapports.add(rapportMouvements);

            // Sauvegarder les chemins des rapports
            cloture.setRapportGeneres(String.join(";", rapports));
            clotureRepository.save(cloture);

            log.info("Rapports générés avec succès pour {}", cloture.getPeriodeFormat());

        } catch (Exception e) {
            log.error("Erreur lors de la génération des rapports: {}", e.getMessage(), e);
        }
    }

    /**
     * Générer le rapport de valorisation
     */
    private String genererRapportValorisation(ClotureMensuelle cloture) {
        // Implémentation simplifiée - à compléter avec une librairie de reporting
        return "/rapports/valorisation_" + cloture.getAnnee() + "_" + cloture.getMois() + ".pdf";
    }

    /**
     * Générer le rapport des écarts
     */
    private String genererRapportEcart(ClotureMensuelle cloture) {
        return "/rapports/ecarts_" + cloture.getAnnee() + "_" + cloture.getMois() + ".pdf";
    }

    /**
     * Générer le rapport des mouvements
     */
    private String genererRapportMouvements(ClotureMensuelle cloture) {
        return "/rapports/mouvements_" + cloture.getAnnee() + "_" + cloture.getMois() + ".pdf";
    }

    /**
     * Obtenir l'historique des coûts d'un article
     */
    public List<HistoriqueCout> getHistoriqueArticle(UUID articleId, UUID depotId, Integer nbMois) {
        LocalDate dateFin = LocalDate.now();
        LocalDate dateDebut = dateFin.minusMonths(nbMois);

        return historiqueRepository.findEvolutionCouts(articleId, depotId, dateDebut, dateFin);
    }

    /**
     * Obtenir le coût unitaire à une date donnée
     */
    public Optional<BigDecimal> getCoutUnitaireAtDate(UUID articleId, UUID depotId, LocalDate date) {
        return historiqueRepository.findCoutUnitaireAtDate(articleId, depotId, date);
    }

    /**
     * Liste des clôtures avec filtres
     */
    public List<ClotureMensuelle> getClotures(Integer annee, ClotureMensuelle.StatutCloture statut) {
        return clotureRepository.rechercherAvecFiltres(annee, statut);
    }

    /**
     * Obtenir la prochaine période à clôturer
     */
    public Optional<ClotureMensuelle> getProchainePeriodeACloturer() {
        return clotureRepository.findProchainePeriodeACloturer();
    }

    /**
     * Statistiques des clôtures
     */
    public Map<String, Object> getStatistiquesClotures() {
        Map<String, Object> stats = new HashMap<>();

        // Nombre par statut
        List<Object[]> countByStatut = clotureRepository.countByStatut();
        Map<String, Long> parStatut = countByStatut.stream()
                .collect(Collectors.toMap(
                        row -> ((String) row[0]),
                        row -> ((Long) row[1])));

        stats.put("parStatut", parStatut);

        // Dernière clôture validée
        clotureRepository.findDerniereClotureValidee().ifPresent(cloture -> {
            stats.put("derniereCloture", cloture);
        });

        // Périodes ouvertes
        List<ClotureMensuelle> periodesOuvertes = clotureRepository.findPeriodesOuvertes();
        stats.put("periodesOuvertes", periodesOuvertes.size());
        stats.put("listePeriodesOuvertes", periodesOuvertes);

        return stats;
    }
}