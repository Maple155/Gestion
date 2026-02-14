// DashboardController.java - Version avec clôture et historique
package com.gestion.stock.controller.dashboard;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.stock.service.*;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/main/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {

    private final ReportingService reportingService;
    private final ValorisationService valorisationService;
    private final StockService stockService;
    private final InventaireService inventaireService;
    private final LotService lotService;
    private final StockMovementRepository stockMovementRepository;
    private final ClotureService clotureService;
    private final ClotureMensuelleRepository clotureRepository;
    private final HistoriqueCoutRepository historiqueRepository;
    private final ArticleRepository articleRepository;
    private final DepotRepository depotRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final StockRepository stockRepository;

    /**
     * Dashboard principal - Vue d'ensemble (EXISTANT)
     */
    @GetMapping
    public String dashboard(Model model, HttpSession session) {
        try {
            if (session.getAttribute("userId") == null) {
                return "redirect:/login";
            }

            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            // 1. KPI globaux
            Map<String, Object> kpis = reportingService.getDashboardKPIs();
            model.addAttribute("kpis", kpis);

            // 2. Valorisation FIFO/FEFO/CUMP
            Map<String, Object> valorisation = valorisationService.getSyntheseValorisation();
            model.addAttribute("valorisation", valorisation);

            // 3. Évolution valeur stock (12 mois)
            List<Map<String, Object>> evolution = reportingService.getEvolutionValeurStock(12);
            model.addAttribute("evolution", evolution);

            // 4. Top 10 articles par valeur
            List<Map<String, Object>> topArticles = valorisationService.getTopArticlesParValeur(10);
            model.addAttribute("topArticles", topArticles);

            // 5. Alertes urgentes
            Map<String, Object> alertes = reportingService.getAlertes();
            model.addAttribute("alertes", alertes);

            // 6. Statistiques des lots
            Map<String, Object> statsLots = lotService.getStatistiquesLots(null);
            model.addAttribute("statsLots", statsLots);

            // 7. Inventaires en cours
            List<Inventaire> inventairesEnCours = inventaireService.getInventairesEnCours();
            model.addAttribute("inventairesEnCours", inventairesEnCours);

            // 8. Derniers mouvements
            List<StockMovement> derniersMouvements = stockService.getDerniersMouvements(10);
            model.addAttribute("derniersMouvements", derniersMouvements);

            List<Depot> depots = depotRepository.findAll();
            model.addAttribute("depots", depots);

            model.addAttribute("title", "Dashboard Stock");
            model.addAttribute("activePage", "stock-dashboard");
            model.addAttribute("dateJour", LocalDate.now());

            return "stock/dashboard/index";
        } catch (Exception e) {
            log.error("ERROR in dashboard method", e);

            // Ajouter des données par défaut pour éviter l'erreur
            model.addAttribute("kpis", new HashMap<String, Object>());
            model.addAttribute("valorisation", new HashMap<String, Object>());
            model.addAttribute("evolution", new ArrayList<Map<String, Object>>());
            model.addAttribute("topArticles", new ArrayList<Map<String, Object>>());
            model.addAttribute("alertes", new HashMap<String, Object>());
            model.addAttribute("inventairesEnCours", new ArrayList<Inventaire>());
            model.addAttribute("derniersMouvements", new ArrayList<StockMovement>());
            model.addAttribute("title", "Dashboard Stock");
            model.addAttribute("activePage", "stock-dashboard");
            model.addAttribute("dateJour", LocalDate.now());

            return "stock/dashboard/index";
        }
    }

    /**
     * Vue synthétique valorisation (EXISTANT)
     */
    @GetMapping("/valorisation")
    public String dashboardValorisation(Model model, HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

        // Synthèse des méthodes de valorisation
        Map<String, Object> valorisation = valorisationService.getSyntheseValorisation();

        BigDecimal coutSortiesFifo = valorisationService.getCoutMoyenSortiesFIFO();
        BigDecimal coutSortiesFefo = valorisationService.getCoutMoyenSortiesFEFO();
        BigDecimal differenceCout = valorisationService.getDifferenceCout();

        valorisation.put("coutSortiesFifo", coutSortiesFifo);
        valorisation.put("coutSortiesFefo", coutSortiesFefo);
        valorisation.put("differenceCout", differenceCout);

        model.addAttribute("valorisation", valorisation);

        // Détail par méthode
        model.addAttribute("detailFifo", valorisationService.getDetailValorisationParMethode("FIFO"));
        model.addAttribute("detailFefo", valorisationService.getDetailValorisationParMethode("FEFO"));
        model.addAttribute("detailCump", valorisationService.getDetailValorisationParMethode("CUMP"));

        // Évolution historique
        Map<String, Object> evolution = valorisationService.getEvolutionValorisation(6);
        model.addAttribute("evolution", evolution);

        // Récupérer les 3 derniers mois d'historique pour le graphique
        List<Map<String, Object>> historiqueGlobal = getHistoriqueGlobal(3);
        model.addAttribute("historiqueGlobal", historiqueGlobal);

        model.addAttribute("title", "Dashboard Valorisation");
        model.addAttribute("activePage", "stock-valorisation");
        model.addAttribute("utilisateurId", utilisateurId);

        return "stock/dashboard/valorisation";
    }

    /**
     * Vue synthétique mouvements (EXISTANT)
     */
    @GetMapping("/mouvements")
    public String dashboardMouvements(Model model,
            HttpSession session,
            @RequestParam(defaultValue = "50") int jours) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Statistiques des mouvements
        Map<String, Object> statsMouvements = stockService.getStatistiquesMouvements(jours);
        model.addAttribute("stats", statsMouvements);

        // Derniers mouvements détaillés
        List<StockMovement> derniersMouvements = stockService.getDerniersMouvements(50);
        model.addAttribute("mouvements", derniersMouvements);

        // Répartition par type
        Map<String, Long> repartitionType = stockService.getRepartitionMouvementsParType(jours);
        model.addAttribute("repartitionType", repartitionType);

        model.addAttribute("title", "Dashboard Mouvements");
        model.addAttribute("activePage", "stock-mouvements");
        model.addAttribute("jours", jours);

        return "stock/dashboard/mouvements";
    }

    /**
     * Vue synthétique pour un article spécifique (EXISTANT)
     */
    @GetMapping("/article/{articleId}")
    public String dashboardArticle(@PathVariable String articleId,
            Model model,
            HttpSession session) {

        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }

        // Informations article
        Map<String, Object> articleInfo = stockService.getInformationsArticle(UUID.fromString(articleId));
        model.addAttribute("article", articleInfo);

        // Stock par dépôt
        List<Map<String, Object>> stockParDepot = stockService.getStockParDepot(UUID.fromString(articleId));
        model.addAttribute("stockParDepot", stockParDepot);

        // Valorisation détaillée
        Map<String, Object> valorisation = valorisationService.getDetailValorisationArticle(UUID.fromString(articleId));
        model.addAttribute("valorisation", valorisation);

        // Historique des mouvements
        List<StockMovement> historique = stockService.getHistoriqueMouvementsArticle(UUID.fromString(articleId), 30);
        model.addAttribute("historique", historique);

        model.addAttribute("title", "Dashboard Article");
        model.addAttribute("activePage", "stock-dashboard");

        return "stock/dashboard/article";
    }

    /**
     * API pour données dashboard (AJAX) - EXISTANT
     */
    @GetMapping("/api/kpi")
    @ResponseBody
    public Map<String, Object> getKPIApi() {
        return reportingService.getDashboardKPIs();
    }

    @GetMapping("/api/valorisation")
    @ResponseBody
    public Map<String, Object> getValorisationApi() {
        return valorisationService.getSyntheseValorisation();
    }

    @GetMapping("/api/mouvements/recent")
    @ResponseBody
    public List<StockMovement> getMouvementsRecentsApi(
            @RequestParam(defaultValue = "10") int limit) {
        return stockService.getDerniersMouvements(limit);
    }

    @GetMapping("/api/valorisation/detail")
    @ResponseBody
    public List<Map<String, Object>> getValorisationDetailApi() {
        return valorisationService.getValorisationDetailForDashboard();
    }

    @GetMapping("/api/alertes")
    @ResponseBody
    public Map<String, Object> getAlertesApi() {
        return reportingService.getAlertes();
    }

    @GetMapping("/api/mouvements/journalier")
    @ResponseBody
    public Map<String, Object> getMouvementsJournalierApi(
            @RequestParam(defaultValue = "30") int jours) {

        Map<String, Object> response = new HashMap<>();

        // Récupérer les mouvements des X derniers jours
        LocalDateTime dateDebut = LocalDateTime.now().minusDays(jours);
        List<StockMovement> mouvements = stockMovementRepository.findByDateMouvementBetween(
                dateDebut, LocalDateTime.now());

        // Grouper par jour
        Map<LocalDate, Long> entreesParJour = new LinkedHashMap<>();
        Map<LocalDate, Long> sortiesParJour = new LinkedHashMap<>();

        // Initialiser tous les jours
        for (int i = jours - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            entreesParJour.put(date, 0L);
            sortiesParJour.put(date, 0L);
        }

        // Compter les mouvements par jour
        for (StockMovement mvt : mouvements) {
            LocalDate date = mvt.getDateMouvement().toLocalDate();
            if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE) {
                entreesParJour.put(date, entreesParJour.getOrDefault(date, 0L) + 1);
            } else {
                sortiesParJour.put(date, sortiesParJour.getOrDefault(date, 0L) + 1);
            }
        }

        // Préparer les labels (dates)
        List<String> labels = new ArrayList<>();
        List<Long> entrees = new ArrayList<>();
        List<Long> sorties = new ArrayList<>();

        for (int i = jours - 1; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            labels.add(date.format(DateTimeFormatter.ofPattern("dd/MM")));
            entrees.add(entreesParJour.get(date));
            sorties.add(sortiesParJour.get(date));
        }

        response.put("labels", labels);
        response.put("entrees", entrees);
        response.put("sorties", sorties);
        response.put("jours", jours);

        return response;
    }

    /**
     * NOUVELLES API POUR CLÔTURE ET HISTORIQUE
     */

    /**
     * API pour statistiques des clôtures
     */
    @GetMapping("/api/clotures/statistiques")
    @ResponseBody
    public Map<String, Object> getStatistiquesCloturesApi() {
        return clotureService.getStatistiquesClotures();
    }

    /**
     * API pour liste des clôtures récentes
     */
    @GetMapping("/api/clotures/liste")
    @ResponseBody
    public List<Map<String, Object>> getCloturesListeApi(
            @RequestParam(defaultValue = "10") int limit) {

        List<ClotureMensuelle> clotures = clotureRepository.findTopNByOrderByDateClotureDesc(limit);

        return clotures.stream().map(cloture -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cloture.getId());
            map.put("annee", cloture.getAnnee());
            map.put("mois", cloture.getMois());
            map.put("statut", cloture.getStatut().name());
            map.put("dateDebutPeriode", cloture.getDateDebutPeriode());
            map.put("dateFinPeriode", cloture.getDateFinPeriode());
            map.put("dateCloture", cloture.getDateCloture());
            map.put("valeurStockTotal", cloture.getValeurStockTotal());
            map.put("nombreMouvements", cloture.getNombreMouvements());
            map.put("valeurMouvementsEntree", cloture.getValeurMouvementsEntree());
            map.put("valeurMouvementsSortie", cloture.getValeurMouvementsSortie());
            map.put("clotureParNom",
                    cloture.getCloturePar() != null
                            ? cloture.getCloturePar().getNom() + " " + cloture.getCloturePar().getPrenom()
                            : "N/A");
            map.put("commentaires", cloture.getCommentaires());
            map.put("rapportGeneres", cloture.getRapportGeneres());

            return map;
        }).collect(Collectors.toList());
    }

    /**
     * API pour détails d'une clôture spécifique
     */
    @GetMapping("/api/clotures/{clotureId}/details")
    @ResponseBody
    public Map<String, Object> getClotureDetailsApi(@PathVariable UUID clotureId) {
        ClotureMensuelle cloture = clotureRepository.findById(clotureId)
                .orElseThrow(() -> new RuntimeException("Clôture non trouvée"));

        Map<String, Object> details = new HashMap<>();
        details.put("id", cloture.getId());
        details.put("annee", cloture.getAnnee());
        details.put("mois", cloture.getMois());
        details.put("statut", cloture.getStatut().name());
        details.put("dateDebutPeriode", cloture.getDateDebutPeriode());
        details.put("dateFinPeriode", cloture.getDateFinPeriode());
        details.put("dateCloture", cloture.getDateCloture());
        details.put("valeurStockTotal", cloture.getValeurStockTotal());
        details.put("nombreMouvements", cloture.getNombreMouvements());
        details.put("valeurMouvementsEntree", cloture.getValeurMouvementsEntree());
        details.put("valeurMouvementsSortie", cloture.getValeurMouvementsSortie());
        details.put("nombreArticles", cloture.getNombreArticles());
        details.put("nombreArticlesValorises", cloture.getNombreArticlesValorises());
        details.put("ecartValorisation", cloture.getEcartValorisation());
        details.put("tauxCouverture", cloture.getTauxCouverture());
        details.put("clotureParNom",
                cloture.getCloturePar() != null
                        ? cloture.getCloturePar().getNom() + " " + cloture.getCloturePar().getPrenom()
                        : "N/A");
        details.put("valideurNom",
                cloture.getValideur() != null ? cloture.getValideur().getNom() + " " + cloture.getValideur().getPrenom()
                        : null);
        details.put("dateValidation", cloture.getDateValidation());
        details.put("commentaires", cloture.getCommentaires());
        details.put("rapportGeneres", cloture.getRapportGeneres());

        return details;
    }

    /**
     * API pour prochaine période à clôturer
     */
    @GetMapping("/api/clotures/prochaine-periode")
    @ResponseBody
    public Map<String, Object> getProchainePeriodeACloturerApi() {
        Map<String, Object> response = new HashMap<>();

        try {
            Optional<ClotureMensuelle> prochaine = clotureService.getProchainePeriodeACloturer();

            if (prochaine.isPresent()) {
                response.put("mois", prochaine.get().getMois());
                response.put("annee", prochaine.get().getAnnee());
                response.put("statut", prochaine.get().getStatut().name());
            } else {
                // Si aucune période ouverte, suggérer le mois précédent
                LocalDate now = LocalDate.now();
                YearMonth previousMonth = YearMonth.from(now).minusMonths(1);
                response.put("mois", previousMonth.getMonthValue());
                response.put("annee", previousMonth.getYear());
                response.put("statut", "NON_EXISTANTE");
            }

            response.put("success", true);
        } catch (Exception e) {
            log.error("Erreur récupération prochaine période", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * API pour exécuter une clôture
     */
    @PostMapping("/api/clotures/executer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> executerClotureApi(
            @RequestParam Integer mois,
            @RequestParam Integer annee,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (session.getAttribute("userId") == null) {
                throw new RuntimeException("Utilisateur non authentifié");
            }

            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            log.info("Exécution clôture {}/{} par utilisateur {}", mois, annee, utilisateurId);

            // Vérifier si la période existe déjà
            Optional<ClotureMensuelle> existing = clotureRepository.findByAnneeAndMois(annee, mois);
            if (existing.isPresent() && !existing.get().isCloturable()) {
                throw new RuntimeException("La période " + existing.get().getPeriodeFormat() +
                        " est déjà en statut " + existing.get().getStatut());
            }

            // Initialiser ou récupérer la clôture
            ClotureMensuelle cloture = clotureService.initialiserCloture(annee, mois, utilisateurId);

            // Exécuter la clôture
            cloture = clotureService.executerCloture(cloture.getId(), utilisateurId);

            response.put("success", true);
            response.put("message", "Clôture exécutée avec succès pour " + cloture.getPeriodeFormat());
            response.put("cloture", Map.of(
                    "id", cloture.getId(),
                    "reference", "CLOT-" + cloture.getAnnee() + "-" + String.format("%02d", cloture.getMois()),
                    "periode", cloture.getPeriodeFormat(),
                    "valeurStockTotal", cloture.getValeurStockTotal(),
                    "nombreArticles", cloture.getNombreArticles()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de l'exécution de la clôture", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API pour historique global des coûts
     */
    @GetMapping("/api/historique/cout")
    @ResponseBody
    public List<Map<String, Object>> getHistoriqueCoutApi(
            @RequestParam(defaultValue = "6") int mois) {

        return getHistoriqueGlobal(mois);
    }

    /**
     * API pour historique détaillé d'un article
     */
    @GetMapping("/api/historique/cout/article/{articleId}")
    @ResponseBody
    public List<Map<String, Object>> getHistoriqueCoutArticleApi(
            @PathVariable UUID articleId,
            @RequestParam(required = false) UUID depotId,
            @RequestParam(defaultValue = "12") int mois) {

        List<HistoriqueCout> historiques = clotureService.getHistoriqueArticle(articleId, depotId, mois);

        return historiques.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("dateEffet", h.getDateEffet());
            map.put("annee", h.getAnnee());
            map.put("mois", h.getMois());
            map.put("coutUnitaireMoyen", h.getCoutUnitaireMoyen());
            map.put("quantiteStock", h.getQuantiteStock());
            map.put("valeurStock", h.getValeurStock());
            map.put("methodeValorisation", h.getMethodeValorisation());
            map.put("depotNom", h.getDepot() != null ? h.getDepot().getNom() : "Tous");
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * Méthode utilitaire pour historique global
     */
    private List<Map<String, Object>> getHistoriqueGlobal(int nbMois) {
        LocalDate dateFin = LocalDate.now();
        LocalDate dateDebut = dateFin.minusMonths(nbMois);

        // Récupérer les clôtures de la période
        List<ClotureMensuelle> clotures = clotureRepository.findByDateDebutPeriodeBetween(
                dateDebut, dateFin);

        // Si pas de clôtures, simuler des données
        if (clotures.isEmpty()) {
            return genererHistoriqueSimule(nbMois);
        }

        // Grouper par mois/année
        return clotures.stream()
                .sorted(Comparator.comparing(ClotureMensuelle::getDateDebutPeriode))
                .map(cloture -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("annee", cloture.getAnnee());
                    map.put("mois", cloture.getMois());
                    map.put("valeurTotale", cloture.getValeurStockTotal());
                    map.put("quantiteTotale", cloture.getNombreArticles());

                    // Calculer le coût moyen (simplifié)
                    BigDecimal coutMoyen = BigDecimal.ZERO;
                    if (cloture.getValeurStockTotal() != null && cloture.getNombreArticles() != null &&
                            cloture.getNombreArticles() > 0) {
                        coutMoyen = cloture.getValeurStockTotal()
                                .divide(BigDecimal.valueOf(cloture.getNombreArticles()), 2,
                                        java.math.RoundingMode.HALF_UP);
                    }
                    map.put("coutMoyen", coutMoyen);

                    return map;
                })
                .collect(Collectors.toList());
    }

    /**
     * Générer un historique simulé pour démonstration
     */
    private List<Map<String, Object>> genererHistoriqueSimule(int nbMois) {
        List<Map<String, Object>> historique = new ArrayList<>();
        LocalDate now = LocalDate.now();

        // Valeur de base (à adapter selon vos données réelles)
        BigDecimal valeurBase = new BigDecimal("1000000");

        for (int i = nbMois - 1; i >= 0; i--) {
            LocalDate date = now.minusMonths(i);

            // Variation aléatoire +/- 5%
            double variation = 1 + (Math.random() * 0.1 - 0.05);
            BigDecimal valeur = valeurBase.multiply(BigDecimal.valueOf(variation));

            // Quantité simulée
            int quantite = 500 + (int) (Math.random() * 100);

            Map<String, Object> moisData = new HashMap<>();
            moisData.put("annee", date.getYear());
            moisData.put("mois", date.getMonthValue());
            moisData.put("valeurTotale", valeur);
            moisData.put("quantiteTotale", quantite);
            moisData.put("coutMoyen", valeur.divide(BigDecimal.valueOf(quantite), 2, java.math.RoundingMode.HALF_UP));

            historique.add(moisData);
        }

        return historique;
    }

    /**
     * API pour valider une clôture
     */
    @PostMapping("/api/clotures/{clotureId}/valider")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validerClotureApi(
            @PathVariable UUID clotureId,
            @RequestParam String commentaires,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (session.getAttribute("userId") == null) {
                throw new RuntimeException("Utilisateur non authentifié");
            }

            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            ClotureMensuelle cloture = clotureService.validerCloture(clotureId, utilisateurId, commentaires);

            response.put("success", true);
            response.put("message", "Clôture validée avec succès");
            response.put("cloture", Map.of(
                    "id", cloture.getId(),
                    "statut", cloture.getStatut().name(),
                    "dateValidation", cloture.getDateValidation()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors de la validation de la clôture", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API pour rejeter une clôture
     */
    @PostMapping("/api/clotures/{clotureId}/rejeter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> rejeterClotureApi(
            @PathVariable UUID clotureId,
            @RequestParam String motif,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        try {
            if (session.getAttribute("userId") == null) {
                throw new RuntimeException("Utilisateur non authentifié");
            }

            UUID utilisateurId = UUID.fromString(session.getAttribute("userId").toString());

            ClotureMensuelle cloture = clotureService.rejeterCloture(clotureId, utilisateurId, motif);

            response.put("success", true);
            response.put("message", "Clôture rejetée");
            response.put("cloture", Map.of(
                    "id", cloture.getId(),
                    "statut", cloture.getStatut().name(),
                    "commentaires", cloture.getCommentaires()));

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erreur lors du rejet de la clôture", e);
            response.put("success", false);
            response.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * API pour obtenir le coût unitaire à une date donnée
     */
    @GetMapping("/api/historique/cout/date")
    @ResponseBody
    public Map<String, Object> getCoutUnitaireAtDateApi(
            @RequestParam UUID articleId,
            @RequestParam UUID depotId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        Map<String, Object> response = new HashMap<>();

        try {
            Optional<BigDecimal> coutOpt = clotureService.getCoutUnitaireAtDate(articleId, depotId, date);

            if (coutOpt.isPresent()) {
                response.put("success", true);
                response.put("coutUnitaire", coutOpt.get());
                response.put("date", date);
            } else {
                response.put("success", false);
                response.put("message", "Aucun coût historique trouvé pour cette date");
            }

        } catch (Exception e) {
            log.error("Erreur récupération coût historique", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * API pour liste des périodes de clôture avec filtres
     */
    @GetMapping("/api/clotures/filtrees")
    @ResponseBody
    public List<Map<String, Object>> getCloturesFiltreesApi(
            @RequestParam(required = false) Integer annee,
            @RequestParam(required = false) String statut) {

        ClotureMensuelle.StatutCloture statutEnum = null;
        if (statut != null && !statut.isEmpty()) {
            try {
                statutEnum = ClotureMensuelle.StatutCloture.valueOf(statut);
            } catch (IllegalArgumentException e) {
                // Statut invalide, on ignore le filtre
            }
        }

        List<ClotureMensuelle> clotures = clotureService.getClotures(annee, statutEnum);

        return clotures.stream().map(cloture -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", cloture.getId());
            map.put("periode", cloture.getPeriodeFormat());
            map.put("annee", cloture.getAnnee());
            map.put("mois", cloture.getMois());
            map.put("statut", cloture.getStatut().name());
            map.put("dateCloture", cloture.getDateCloture());
            map.put("valeurStockTotal", cloture.getValeurStockTotal());
            map.put("cloturePar",
                    cloture.getCloturePar() != null
                            ? cloture.getCloturePar().getNom() + " " + cloture.getCloturePar().getPrenom()
                            : null);
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * API pour rapport de clôture
     */
    @GetMapping("/api/clotures/{clotureId}/rapport")
    @ResponseBody
    public Map<String, Object> getRapportClotureApi(@PathVariable UUID clotureId) {
        Map<String, Object> response = new HashMap<>();

        try {
            ClotureMensuelle cloture = clotureRepository.findById(clotureId)
                    .orElseThrow(() -> new RuntimeException("Clôture non trouvée"));

            // Récupérer les historiques de coût pour cette clôture
            List<HistoriqueCout> historiques = historiqueRepository.findByClotureMensuelleId(clotureId);

            // Statistiques détaillées
            Map<String, Object> statistiques = new HashMap<>();

            // Par méthode de valorisation
            Map<String, BigDecimal> parMethode = historiques.stream()
                    .collect(Collectors.groupingBy(
                            HistoriqueCout::getMethodeValorisation,
                            Collectors.reducing(BigDecimal.ZERO,
                                    HistoriqueCout::getValeurStock,
                                    BigDecimal::add)));
            statistiques.put("parMethode", parMethode);

            // Par dépôt
            Map<String, BigDecimal> parDepot = historiques.stream()
                    .collect(Collectors.groupingBy(
                            h -> h.getDepot() != null ? h.getDepot().getNom() : "N/A",
                            Collectors.reducing(BigDecimal.ZERO,
                                    HistoriqueCout::getValeurStock,
                                    BigDecimal::add)));
            statistiques.put("parDepot", parDepot);

            // Top 10 articles par valeur
            List<Map<String, Object>> topArticles = historiques.stream()
                    .collect(Collectors.groupingBy(
                            h -> h.getArticle().getCodeArticle(),
                            Collectors.reducing(BigDecimal.ZERO,
                                    HistoriqueCout::getValeurStock,
                                    BigDecimal::add)))
                    .entrySet().stream()
                    .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                    .limit(10)
                    .map(entry -> {
                        Map<String, Object> article = new HashMap<>();
                        article.put("code", entry.getKey());
                        article.put("valeur", entry.getValue());
                        return article;
                    })
                    .collect(Collectors.toList());
            statistiques.put("topArticles", topArticles);

            response.put("success", true);
            response.put("cloture", Map.of(
                    "id", cloture.getId(),
                    "periode", cloture.getPeriodeFormat(),
                    "statut", cloture.getStatut().name(),
                    "valeurStockTotal", cloture.getValeurStockTotal(),
                    "nombreArticles", cloture.getNombreArticles()));
            response.put("statistiques", statistiques);
            response.put("historiquesCount", historiques.size());
            response.put("rapports",
                    cloture.getRapportGeneres() != null ? Arrays.asList(cloture.getRapportGeneres().split(";"))
                            : Collections.emptyList());

        } catch (Exception e) {
            log.error("Erreur génération rapport clôture", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }

    /**
     * API pour la liste des stocks avec pagination
     */
    @GetMapping("/api/stocks/liste")
    @ResponseBody
    public Map<String, Object> getStocksListApi(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) UUID depotId) {

        Map<String, Object> response = new HashMap<>();

        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("article.codeArticle").ascending());

            Page<Stock> stocksPage;
            if (depotId != null) {
                stocksPage = stockRepository.findByDepotId(depotId, pageable);
            } else {
                stocksPage = stockRepository.findAll(pageable);
            }

            List<Map<String, Object>> stocksData = stocksPage.getContent().stream()
                    .map(stock -> {
                        Map<String, Object> stockMap = new HashMap<>();
                        stockMap.put("id", stock.getId());
                        stockMap.put("articleCode", stock.getArticle().getCodeArticle());
                        stockMap.put("articleLibelle", stock.getArticle().getLibelle());
                        stockMap.put("depotId", stock.getDepot().getId());
                        stockMap.put("depotNom", stock.getDepot().getNom());
                        stockMap.put("depotCode", stock.getDepot().getCode());
                        stockMap.put("quantiteTheorique", stock.getQuantiteTheorique());
                        stockMap.put("quantiteReservee", stock.getQuantiteReservee());
                        stockMap.put("quantiteDisponible", stock.getQuantiteDisponible());
                        stockMap.put("valeurStockCump", stock.getValeurStockCump());
                        stockMap.put("coutUnitaireMoyen", stock.getCoutUnitaireMoyen());
                        stockMap.put("dateDernierMouvement", stock.getDateDernierMouvement());

                        // Calculer le statut
                        String statut = "NORMAL";
                        if (stock.getQuantiteDisponible() <= 0) {
                            statut = "RUPTURE";
                        } else if (stock.getArticle().getStockMinimum() != null &&
                                stock.getQuantiteDisponible() < stock.getArticle().getStockMinimum()) {
                            statut = "ALERTE";
                        } else if (stock.getArticle().getStockMaximum() != null &&
                                stock.getQuantiteTheorique() > stock.getArticle().getStockMaximum()) {
                            statut = "SURSTOCK";
                        }
                        stockMap.put("statut", statut);

                        return stockMap;
                    })
                    .collect(Collectors.toList());

            response.put("stocks", stocksData);
            response.put("currentPage", stocksPage.getNumber());
            response.put("totalPages", stocksPage.getTotalPages());
            response.put("totalItems", stocksPage.getTotalElements());
            response.put("pageSize", size);
            response.put("success", true);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des stocks", e);
            response.put("success", false);
            response.put("message", e.getMessage());
        }

        return response;
    }   

    @GetMapping("/valorisation/detail")
    public String redirectToValorisationDetail() {
        return "redirect:/stock/valorisation/detail";
    }
}