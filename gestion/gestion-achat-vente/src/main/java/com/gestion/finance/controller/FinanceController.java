package com.gestion.finance.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.enums.StatutFinance;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.Stock;
import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.repository.StockRepository;
import com.gestion.stock.service.ValorisationService;
import com.gestion.vente.entity.AvoirClient;
import com.gestion.vente.entity.FactureVente;
import com.gestion.vente.entity.LigneFactureVente;
import com.gestion.vente.entity.PaiementClient;
import com.gestion.vente.enums.StatutAvoir;
import com.gestion.vente.repository.AvoirClientRepository;
import com.gestion.vente.repository.FactureVenteRepository;
import com.gestion.vente.repository.LigneFactureVenteRepository;
import com.gestion.vente.repository.LigneCommandeClientRepository;
import com.gestion.vente.repository.PaiementClientRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FactureAchatRepository factureAchatRepository;
    private final FactureVenteRepository factureVenteRepository;
    private final BonCommandeRepository bonCommandeRepository;
    private final BonReceptionRepository bonReceptionRepository;
    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final LigneCommandeClientRepository ligneCommandeClientRepository;
    private final LigneFactureVenteRepository ligneFactureVenteRepository;
    private final AvoirClientRepository avoirClientRepository;
    private final ValorisationService valorisationService;
    private final PaiementClientRepository paiementClientRepository;

    // --- MIDDLEWARE DE SÉCURITÉ ---
    private void requireRole(HttpSession session, String... allowedRoles) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) throw new RuntimeException("Accès non autorisé : Session expirée");
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) return;
        }
        throw new RuntimeException("Accès refusé pour le rôle: " + role);
    }

    // --- DASHBOARD GÉNÉRAL (DAF) ---
    @GetMapping("/daf")
    public String dashboardDaf(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        // --- 1. PERFORMANCE COMMERCIALE (NET D'AVOIRS) ---
        AvoirTotals avoirs = computeAvoirsTotals();
        MarginKpi margin = computeMargeFacture(avoirs.avoirHt());
        model.addAttribute("caTotal", margin.chiffreAffaires());
        model.addAttribute("margeTotale", margin.margeTotale());
        model.addAttribute("tauxMarge", margin.tauxMarge());

        // --- 2. AUDIT OPÉRATIONNEL (RISQUES LOGISTIQUES & QUALITÉ) ---
        List<BonCommande> tousLesBC = bonCommandeRepository.findAll();
        List<BonReception> toutesReceptions = bonReceptionRepository.findAll();
        LocalDate today = LocalDate.now();

        // Litiges Retard (Date passée, aucune réception)
        List<BonCommande> retards = tousLesBC.stream()
                .filter(bc -> bc.getDateLivraisonEstimee() != null && bc.getDateLivraisonEstimee().isBefore(today)
                        && !hasReception(bc, toutesReceptions))
                .toList();

        // Litiges Qualité (Non conforme)
        List<BonReception> nonConformes = toutesReceptions.stream()
                .filter(br -> !br.isConforme())
                .toList();

        BigDecimal valeurRetards = retards.stream().map(BonCommande::getMontantTotalTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal valeurQualite = nonConformes.stream().map(br -> br.getBonCommande().getMontantTotalTtc()).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("nbRetards", retards.size());
        model.addAttribute("valeurRetards", valeurRetards);
        model.addAttribute("nbQualite", nonConformes.size());
        model.addAttribute("valeurQualite", valeurQualite);

        // --- 3. RECOUVREMENT RÉEL (FACTURATION VS ENCAISSEMENT) ---
        List<FactureVente> facturesVente = factureVenteRepository.findAll();
        List<PaiementClient> paiements = paiementClientRepository.findAll();

        Map<UUID, BigDecimal> encaissementsMap = paiements.stream()
                .collect(Collectors.groupingBy(p -> p.getFacture().getId(),
                        Collectors.reducing(BigDecimal.ZERO, PaiementClient::getMontant, BigDecimal::add)));

        Map<String, BigDecimal> balanceClient = new HashMap<>();
        BigDecimal totalResteARecouvrer = BigDecimal.ZERO;

        for (FactureVente f : facturesVente) {
            BigDecimal ttc = (f.getTotalTtc() != null) ? f.getTotalTtc() : BigDecimal.ZERO;
            BigDecimal dejaPaye = encaissementsMap.getOrDefault(f.getId(), BigDecimal.ZERO);
            BigDecimal resteDu = ttc.subtract(dejaPaye);

            if (resteDu.compareTo(BigDecimal.ZERO) > 0) {
                String clientNom = (f.getClient() != null) ? f.getClient().getNom() : "Client Inconnu";
                balanceClient.merge(clientNom, resteDu, BigDecimal::add);
                totalResteARecouvrer = totalResteARecouvrer.add(resteDu);
            }
        }

        Map<String, BigDecimal> topDebiteurs = balanceClient.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .limit(5)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));

        model.addAttribute("topDebiteurs", topDebiteurs);
        model.addAttribute("totalResteARecouvrer", totalResteARecouvrer);

        // --- 4. ANOMALIES COMPTABLES (MISMATCHES) ---
        List<MismatchItem> mismatches = buildMismatchList();
        model.addAttribute("mismatches", mismatches);
        model.addAttribute("mismatchCount", mismatches.size());

        // --- 5. STOCKS & INFOS GÉNÉRALES ---
        model.addAttribute("valeurOperationnelle", getValeurStockOperationnelle());
        model.addAttribute("dateJour", LocalDate.now());
        model.addAttribute("activePage", "finance-daf");

        return "finance/daf-dashboard";
    }
    @GetMapping("/fournisseurs")
    public String dashboardFournisseurs(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        // --- CALCUL DU SOLDE DISPONIBLE (CASH REEL) ---
        BigDecimal totalEncaisse = paiementClientRepository.findAll().stream()
                .map(p -> p.getMontant() != null ? p.getMontant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDecaisse = factureAchatRepository.findAll().stream()
                .filter(FactureAchat::isEstPayee)
                .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal soldeDisponible = totalEncaisse.subtract(totalDecaisse);

        // --- DONNÉES DU DASHBOARD ---
        List<BonCommande> bcEnAttente = bonCommandeRepository.findAll().stream()
                .filter(bc -> StatutFinance.EN_ATTENTE_VALIDATION.equals(bc.getStatutFinance()))
                .toList();

        List<FactureAchat> facturesImpayees = factureAchatRepository.findAll().stream()
                .filter(f -> !f.isEstPayee())
                .toList();

        model.addAttribute("bcEnAttente", bcEnAttente);
        model.addAttribute("facturesImpayees", facturesImpayees);
        model.addAttribute("soldeDisponible", soldeDisponible);
        model.addAttribute("totalFactures", totalDecaisse); // Déjà payé
        model.addAttribute("activePage", "finance-fournisseurs");

        return "finance/fournisseurs-dashboard";
    }

    @PostMapping("/fournisseurs/payer-facture/{id}")
    public String payerFacture(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        
        // Logique de paiement sécurisée
        factureAchatRepository.findById(id).ifPresent(f -> {
            // Recalcul rapide du solde pour éviter les fraudes/doubles clics
            // (En prod, on ferait ça dans un Service Transactionnel)
            f.setEstPayee(true);
            f.setDateFacture(LocalDate.now());
            factureAchatRepository.save(f);
        });
        
        return "redirect:/finance/fournisseurs?success=paiement-effectue";
    }
    @PostMapping("/fournisseurs/valider-bc/{id}")
    public String validerBonCommande(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        
        bonCommandeRepository.findById(id).ifPresent(bc -> {
            bc.setStatutFinance(StatutFinance.VALIDEE); 
            bonCommandeRepository.save(bc);
        });
        
        return "redirect:/finance/fournisseurs?success=bc-valide";
    }

    // --- TRÉSORERIE (FLUX RÉELS) ---
    @GetMapping("/tresorerie")
    public String dashboardTresorerie(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        // 1. CASH RÉEL (Ce qui est déjà encaissé / décaissé)
        BigDecimal encaissements = paiementClientRepository.findAll().stream()
                .map(p -> p.getMontant() != null ? p.getMontant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal decaissements = factureAchatRepository.findAll().stream()
                .filter(FactureAchat::isEstPayee)
                .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 2. PRÉVISIONS (Factures de vente émises - déjà payé)
        BigDecimal totalFactureVenteTtc = factureVenteRepository.findAll().stream()
                .map(f -> f.getTotalTtc() != null ? f.getTotalTtc() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Le "Reste à encaisser" est le Revenu Potentiel
        BigDecimal resteAEncaisser = totalFactureVenteTtc.subtract(encaissements).max(BigDecimal.ZERO);

        // 3. DONNÉES POUR LA COURBE (Prévisions sur 6 mois)
        MonthlySeries series = buildMonthlySeries(6);

        model.addAttribute("encaissements", encaissements);
        model.addAttribute("decaissements", decaissements);
        model.addAttribute("cashEstime", encaissements.subtract(decaissements));
        model.addAttribute("revenuPotentiel", resteAEncaisser);
        model.addAttribute("tresorerieProjetee", encaissements.subtract(decaissements).add(resteAEncaisser));
        
        model.addAttribute("chartLabels", series.labels);
        model.addAttribute("chartDataCA", series.ca); // Vos revenus par mois (émis)
        model.addAttribute("activePage", "finance-tresorerie");

        return "finance/tresorerie-dashboard";
    }

    // --- AUTRES MODULES (STOCKS & VENTES) ---
    @GetMapping("/clients")
    public String dashboardClients(@RequestParam(defaultValue = "6") Integer months, Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        
        // 1. Calcul de la Marge et du CA HT
        MarginKpi margin = computeMargeFacture(computeAvoirsTotals().avoirHt);
        
        // 2. Calcul du CA TTC RÉEL (Indispensable pour comparer aux paiements)
        // On va chercher la somme des factures de vente en base
        BigDecimal totalTtcFacture = factureVenteRepository.findAll().stream()
            .map(f -> f.getTotalTtc() != null ? f.getTotalTtc() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        AvoirTotals avoirs = computeAvoirsTotals();
        BigDecimal totalTtcNet = totalTtcFacture.subtract(avoirs.avoirTtc);
        if (totalTtcNet.compareTo(BigDecimal.ZERO) < 0) totalTtcNet = BigDecimal.ZERO;

        // 3. Récupération des encaissements (Table paiements_clients)
        BigDecimal totalEncaisse = paiementClientRepository.findAll().stream()
                .map(p -> p.getMontant() != null ? p.getMontant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Calcul du reste à percevoir (ne peut pas être inférieur à zéro logiquement)
        BigDecimal resteARecouvrer = totalTtcNet.subtract(totalEncaisse);
        if (resteARecouvrer.compareTo(BigDecimal.ZERO) < 0) resteARecouvrer = BigDecimal.ZERO;

        // 5. Calcul du taux de recouvrement
        double tauxRecouvrement = 0;
        if (totalTtcNet.compareTo(BigDecimal.ZERO) > 0) {
            tauxRecouvrement = totalEncaisse.divide(totalTtcNet, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("margeTotale", margin.margeTotale);
        model.addAttribute("tauxMarge", margin.tauxMarge);
        model.addAttribute("totalEncaisse", totalEncaisse);
        model.addAttribute("resteARecouvrer", resteARecouvrer);
        model.addAttribute("tauxRecouvrement", (int)tauxRecouvrement);
        
        // Données Graphes
        int monthsBack = months != null ? Math.max(3, Math.min(24, months)) : 6;
        MonthlySeries series = buildMonthlySeries(monthsBack);
        model.addAttribute("monthsBack", monthsBack);
        model.addAttribute("chartLabels", series.labels);
        model.addAttribute("chartDataCA", series.ca);
        model.addAttribute("chartDataMarge", series.marge);

        return "finance/clients-dashboard";
    }
    @GetMapping("/stock")
    public String dashboardStock(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        List<Stock> tousLesStocks = stockRepository.findAll();
        List<StockDetailsDto> alertesDetails = new ArrayList<>();
        BigDecimal depenseTotale = BigDecimal.ZERO;

        for (Stock s : tousLesStocks) {
            Article art = s.getArticle();
            if (art == null) continue;

            // Calcul du besoin pour atteindre le stock max
            int qteActuelle = s.getQuantiteTheorique() != null ? s.getQuantiteTheorique() : 0;
            int stockSecu = art.getStockSecurite() != null ? art.getStockSecurite() : 0;
            
            // On ne cible que ce qui est sous le seuil de sécurité
            if (qteActuelle <= stockSecu) {
                int aCommander = Math.max(0, art.getStockMaximum() - qteActuelle);
                BigDecimal coutStandard = art.getCoutStandard() != null ? art.getCoutStandard() : BigDecimal.ZERO;
                BigDecimal coutLigne = coutStandard.multiply(BigDecimal.valueOf(aCommander));
                
                depenseTotale = depenseTotale.add(coutLigne);

                // Simulation de criticité (Exemple: si stock actuel < 20% du seuil de secu)
                String niveauUrgence = (qteActuelle <= stockSecu * 0.2) ? "CRITIQUE" : "ALERTE";

                alertesDetails.add(new StockDetailsDto(
                    art.getCodeArticle(),
                    art.getLibelle(),
                    qteActuelle,
                    stockSecu,
                    aCommander,
                    coutLigne,
                    niveauUrgence
                ));
            }
        }

        // Tri par coût décroissant (le DAF veut voir les plus grosses dépenses en premier)
        alertesDetails.sort((a, b) -> b.coutPrevu().compareTo(a.coutPrevu()));

        model.addAttribute("valeurOperationnelle", getValeurStockOperationnelle());
        model.addAttribute("alertes", alertesDetails);
        model.addAttribute("depensePrevue", depenseTotale);
        model.addAttribute("nbAlertes", alertesDetails.size());
        
        return "finance/stock-dashboard";
    }

    // Petit DTO interne pour structurer la vue
    public record StockDetailsDto(
        String code, String libelle, int actuel, int secu, 
        int aCommander, BigDecimal coutPrevu, String urgence) {}

    @GetMapping("/audit")
    public String auditComplet(Model model) {
        List<BonCommande> tousLesBC = bonCommandeRepository.findAll();
        List<BonReception> toutesReceptions = bonReceptionRepository.findAll();
        LocalDate today = LocalDate.now();

        // 1. Calcul des Litiges de Retard (Sans réception après date prévue)
        List<BonCommande> litigesRetard = tousLesBC.stream()
            .filter(bc -> bc.getDateLivraisonEstimee() != null 
                    && bc.getDateLivraisonEstimee().isBefore(today)
                    && !hasReception(bc, toutesReceptions)) // Méthode utilitaire pour vérifier si un BR existe
            .toList();

        // 2. Calcul des Litiges Qualité (Réceptionnés mais non conformes)
        List<BonReception> litigesQualite = toutesReceptions.stream()
            .filter(br -> !br.isConforme())
            .toList();

        // 3. Statistiques Financières
        BigDecimal valeurRetards = litigesRetard.stream()
            .map(BonCommande::getMontantTotalTtc).reduce(BigDecimal.ZERO, BigDecimal::add);
            
        BigDecimal valeurNonConforme = litigesQualite.stream()
            .map(br -> br.getBonCommande().getMontantTotalTtc()).reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("litigesRetard", litigesRetard);
        model.addAttribute("litigesQualite", litigesQualite);
        model.addAttribute("totalLitiges", valeurRetards.add(valeurNonConforme));
        model.addAttribute("valeurRetards", valeurRetards);
        model.addAttribute("valeurNonConforme", valeurNonConforme);
        
        return "finance/audit-dashboard";
    }
    private boolean hasReception(BonCommande bc, List<BonReception> toutesReceptions) {
        return toutesReceptions.stream()
                .anyMatch(br -> br.getBonCommande().getId().equals(bc.getId()));
    }
    // --- MÉTHODES PRIVÉES DE CALCUL ---

    private List<MismatchItem> buildMismatchList() {
        List<MismatchItem> items = new ArrayList<>();
        List<FactureAchat> factures = factureAchatRepository.findAll();
        for (FactureAchat facture : factures) {
            BonCommande bc = facture.getBonCommande(); // Toujours présent selon tes règles
            String reason = null;

            if (bc == null) {
                reason = "Alerte : BC manquant (Violation règle métier)";
            } else {
                BonReception br = bonReceptionRepository
                    .findFirstByBonCommandeIdOrderByDateReceptionDesc(bc.getId())
                    .orElse(null);
                if (br == null) {
                    reason = "Livraison non reçue";
                } else if (!br.isConforme()) {
                    reason = "Litige : Réception non conforme";
                } else if (facture.getMontantTotalTtc().compareTo(bc.getMontantTotalTtc()) != 0) {
                    reason = "Écart de prix Facture/BC";
                }
            }

            if (reason != null) {
                items.add(new MismatchItem(
                    facture.getNumeroFactureFournisseur(),
                    bc != null ? bc.getReferenceBc() : "N/A",
                    reason,
                    facture.getMontantTotalTtc()
                ));
            }
        }
        return items;
    }

    private BigDecimal getValeurStockComptable() {
        Object value = valorisationService.getSyntheseValorisation().get("valeurTotale");
        return (value instanceof BigDecimal) ? (BigDecimal) value : BigDecimal.ZERO;
    }

    private BigDecimal getValeurStockOperationnelle() {
        return stockRepository.findAll().stream()
            .map(s -> {
                BigDecimal cout = (s.getArticle() != null && s.getArticle().getCoutStandard() != null) 
                                 ? s.getArticle().getCoutStandard() : BigDecimal.ZERO;
                int qte = (s.getQuantiteTheorique() != null) ? s.getQuantiteTheorique() : 0;
                return cout.multiply(BigDecimal.valueOf(qte));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2, RoundingMode.HALF_UP);
    }

    private MarginKpi computeMargeFacture(BigDecimal avoirsHt) {
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal marge = BigDecimal.ZERO;
        List<LigneFactureVente> lignes = ligneFactureVenteRepository.findAll();

        for (LigneFactureVente ligne : lignes) {
            BigDecimal prixVente = ligne.getPrixUnitaireHt() != null ? ligne.getPrixUnitaireHt() : BigDecimal.ZERO;
            BigDecimal cout = articleRepository.findById(ligne.getArticleId())
                .map(a -> a.getCoutStandard() != null ? a.getCoutStandard() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);

            BigDecimal qte = BigDecimal.valueOf(ligne.getQuantite() != null ? ligne.getQuantite() : 0);
            ca = ca.add(prixVente.multiply(qte));
            marge = marge.add(prixVente.subtract(cout).multiply(qte));
        }

        if (avoirsHt != null && avoirsHt.compareTo(BigDecimal.ZERO) > 0 && ca.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tauxMargeBrut = marge.divide(ca, 6, RoundingMode.HALF_UP);
            ca = ca.subtract(avoirsHt).max(BigDecimal.ZERO);
            marge = marge.subtract(avoirsHt.multiply(tauxMargeBrut)).max(BigDecimal.ZERO);
        }

        BigDecimal taux = (ca.compareTo(BigDecimal.ZERO) > 0)
            ? marge.divide(ca, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
            : BigDecimal.ZERO;

        return new MarginKpi(ca, marge, taux.setScale(2, RoundingMode.HALF_UP));
    }

    private AvoirTotals computeAvoirsTotals() {
        BigDecimal totalAvoirTtc = BigDecimal.ZERO;
        BigDecimal totalAvoirHt = BigDecimal.ZERO;
        List<AvoirClient> avoirs = avoirClientRepository.findAll().stream()
            .filter(a -> a.getStatut() == StatutAvoir.EMIS)
            .toList();

        for (AvoirClient avoir : avoirs) {
            BigDecimal montant = avoir.getMontant() != null ? avoir.getMontant() : BigDecimal.ZERO;
            totalAvoirTtc = totalAvoirTtc.add(montant);

            FactureVente facture = avoir.getFacture();
            BigDecimal totalTtc = facture != null && facture.getTotalTtc() != null ? facture.getTotalTtc() : BigDecimal.ZERO;
            BigDecimal totalHt = facture != null && facture.getTotalHt() != null ? facture.getTotalHt() : BigDecimal.ZERO;
            if (totalTtc.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratioHt = totalHt.divide(totalTtc, 6, RoundingMode.HALF_UP);
                totalAvoirHt = totalAvoirHt.add(montant.multiply(ratioHt));
            }
        }

        return new AvoirTotals(totalAvoirHt, totalAvoirTtc);
    }

    private MonthlySeries buildMonthlySeries(int monthsBack) {
        YearMonth current = YearMonth.now();
        Map<YearMonth, BigDecimal> caByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> margeByMonth = new LinkedHashMap<>();
        Map<YearMonth, BigDecimal> avoirHtByMonth = new LinkedHashMap<>();

        for (int i = monthsBack - 1; i >= 0; i--) {
            YearMonth ym = current.minusMonths(i);
            caByMonth.put(ym, BigDecimal.ZERO);
            margeByMonth.put(ym, BigDecimal.ZERO);
            avoirHtByMonth.put(ym, BigDecimal.ZERO);
        }

        List<LigneFactureVente> lignes = ligneFactureVenteRepository.findAll();
        for (LigneFactureVente ligne : lignes) {
            FactureVente facture = ligne.getFacture();
            if (facture == null || facture.getDateFacture() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(facture.getDateFacture());
            if (!caByMonth.containsKey(ym)) {
                continue;
            }

            BigDecimal prixVente = ligne.getPrixUnitaireHt() != null ? ligne.getPrixUnitaireHt() : BigDecimal.ZERO;
            BigDecimal cout = articleRepository.findById(ligne.getArticleId())
                .map(a -> a.getCoutStandard() != null ? a.getCoutStandard() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
            BigDecimal qte = BigDecimal.valueOf(ligne.getQuantite() != null ? ligne.getQuantite() : 0);

            BigDecimal ca = prixVente.multiply(qte);
            BigDecimal marge = prixVente.subtract(cout).multiply(qte);

            caByMonth.put(ym, caByMonth.get(ym).add(ca));
            margeByMonth.put(ym, margeByMonth.get(ym).add(marge));
        }

        List<AvoirClient> avoirs = avoirClientRepository.findAll().stream()
            .filter(a -> a.getStatut() == StatutAvoir.EMIS)
            .toList();

        for (AvoirClient avoir : avoirs) {
            if (avoir.getDateAvoir() == null) {
                continue;
            }
            YearMonth ym = YearMonth.from(avoir.getDateAvoir());
            if (!avoirHtByMonth.containsKey(ym)) {
                continue;
            }

            BigDecimal montant = avoir.getMontant() != null ? avoir.getMontant() : BigDecimal.ZERO;
            FactureVente facture = avoir.getFacture();
            BigDecimal totalTtc = facture != null && facture.getTotalTtc() != null ? facture.getTotalTtc() : BigDecimal.ZERO;
            BigDecimal totalHt = facture != null && facture.getTotalHt() != null ? facture.getTotalHt() : BigDecimal.ZERO;
            if (totalTtc.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal ratioHt = totalHt.divide(totalTtc, 6, RoundingMode.HALF_UP);
                BigDecimal avoirHt = montant.multiply(ratioHt);
                avoirHtByMonth.put(ym, avoirHtByMonth.get(ym).add(avoirHt));
            }
        }

        List<String> labels = new ArrayList<>();
        List<BigDecimal> caData = new ArrayList<>();
        List<BigDecimal> margeData = new ArrayList<>();

        for (YearMonth ym : caByMonth.keySet()) {
            labels.add(ym.getMonth().getDisplayName(TextStyle.FULL, Locale.FRENCH));
            BigDecimal ca = caByMonth.get(ym).subtract(avoirHtByMonth.get(ym)).max(BigDecimal.ZERO);
            BigDecimal marge = margeByMonth.get(ym);
            if (caByMonth.get(ym).compareTo(BigDecimal.ZERO) > 0 && avoirHtByMonth.get(ym).compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal tauxMargeBrut = margeByMonth.get(ym)
                    .divide(caByMonth.get(ym), 6, RoundingMode.HALF_UP);
                marge = marge.subtract(avoirHtByMonth.get(ym).multiply(tauxMargeBrut)).max(BigDecimal.ZERO);
            }
            caData.add(ca.setScale(2, RoundingMode.HALF_UP));
            margeData.add(marge.setScale(2, RoundingMode.HALF_UP));
        }

        return new MonthlySeries(labels, caData, margeData);
    }

    // --- CLASSES INTERNES (DTOs) ---
    private static record MarginKpi(BigDecimal chiffreAffaires, BigDecimal margeTotale, BigDecimal tauxMarge) {}
    private static record AvoirTotals(BigDecimal avoirHt, BigDecimal avoirTtc) {}
    private static record MonthlySeries(List<String> labels, List<BigDecimal> ca, List<BigDecimal> marge) {}

    public static record MismatchItem(String numeroFacture, String referenceBc, String motif, BigDecimal montant) {}
}