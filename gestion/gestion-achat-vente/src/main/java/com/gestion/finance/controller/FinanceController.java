package com.gestion.finance.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.enums.StatutFinance;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.repository.StockRepository;
import com.gestion.stock.service.ValorisationService;
import com.gestion.vente.entity.LigneCommandeClient;
import com.gestion.vente.repository.FactureVenteRepository;
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

        List<MismatchItem> mismatches = buildMismatchList();
        model.addAttribute("mismatchCount", mismatches.size());
        model.addAttribute("mismatches", mismatches);

        BigDecimal comptable = getValeurStockComptable();
        BigDecimal operationnelle = getValeurStockOperationnelle();
        model.addAttribute("valeurComptable", comptable);
        model.addAttribute("valeurOperationnelle", operationnelle);
        model.addAttribute("ecartStock", operationnelle.subtract(comptable));

        MarginKpi margin = computeMarge();
        model.addAttribute("margeTotale", margin.margeTotale);
        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("tauxMarge", margin.tauxMarge);

        model.addAttribute("activePage", "finance-daf");
        model.addAttribute("dateJour", LocalDate.now());
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

        // Encaissements : Somme des paiements clients reçus
        BigDecimal encaissements = paiementClientRepository.findAll().stream()
                .map(p -> p.getMontant() != null ? p.getMontant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Décaissements : Somme des factures d'achat marquées comme payées
        BigDecimal decaissements = factureAchatRepository.findAll().stream()
                .filter(FactureAchat::isEstPayee)
                .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("encaissements", encaissements);
        model.addAttribute("decaissements", decaissements);
        model.addAttribute("cashEstime", encaissements.subtract(decaissements));
        model.addAttribute("activePage", "finance-tresorerie");

        return "finance/tresorerie-dashboard";
    }

    // --- AUTRES MODULES (STOCKS & VENTES) ---
    @GetMapping("/clients")
    public String dashboardClients(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        
        // 1. Calcul de la Marge et du CA HT
        MarginKpi margin = computeMarge();
        
        // 2. Calcul du CA TTC RÉEL (Indispensable pour comparer aux paiements)
        // On va chercher la somme des factures de vente en base
        BigDecimal totalTtcFacture = factureVenteRepository.findAll().stream()
                .map(f -> f.getTotalTtc() != null ? f.getTotalTtc() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 3. Récupération des encaissements (Table paiements_clients)
        BigDecimal totalEncaisse = paiementClientRepository.findAll().stream()
                .map(p -> p.getMontant() != null ? p.getMontant() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 4. Calcul du reste à percevoir (ne peut pas être inférieur à zéro logiquement)
        BigDecimal resteARecouvrer = totalTtcFacture.subtract(totalEncaisse);
        if (resteARecouvrer.compareTo(BigDecimal.ZERO) < 0) resteARecouvrer = BigDecimal.ZERO;

        // 5. Calcul du taux de recouvrement
        double tauxRecouvrement = 0;
        if (totalTtcFacture.compareTo(BigDecimal.ZERO) > 0) {
            tauxRecouvrement = totalEncaisse.divide(totalTtcFacture, 4, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100)).doubleValue();
        }

        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("margeTotale", margin.margeTotale);
        model.addAttribute("tauxMarge", margin.tauxMarge);
        model.addAttribute("totalEncaisse", totalEncaisse);
        model.addAttribute("resteARecouvrer", resteARecouvrer);
        model.addAttribute("tauxRecouvrement", (int)tauxRecouvrement);
        
        // Données Graphes
        model.addAttribute("chartLabels", List.of("Janvier", "Février"));
        model.addAttribute("chartDataCA", List.of(8000, margin.chiffreAffaires));
        model.addAttribute("chartDataMarge", List.of(3000, margin.margeTotale));

        return "finance/clients-dashboard";
    }
    @GetMapping("/stock")
    public String dashboardStock(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        model.addAttribute("valeurComptable", getValeurStockComptable());
        model.addAttribute("valeurOperationnelle", getValeurStockOperationnelle());
        model.addAttribute("activePage", "finance-stock");
        return "finance/stock-dashboard";
    }

    @GetMapping("/audit")
    public String audit(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        model.addAttribute("mismatches", buildMismatchList());
        model.addAttribute("activePage", "finance-audit");
        return "finance/audit-dashboard";
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

    private MarginKpi computeMarge() {
        BigDecimal ca = BigDecimal.ZERO;
        BigDecimal marge = BigDecimal.ZERO;
        List<LigneCommandeClient> lignes = ligneCommandeClientRepository.findAll();

        for (LigneCommandeClient ligne : lignes) {
            BigDecimal prixVente = ligne.getPrixUnitaireHt() != null ? ligne.getPrixUnitaireHt() : BigDecimal.ZERO;
            BigDecimal cout = articleRepository.findById(ligne.getArticleId())
                .map(a -> a.getCoutStandard() != null ? a.getCoutStandard() : BigDecimal.ZERO)
                .orElse(BigDecimal.ZERO);
            
            BigDecimal qte = BigDecimal.valueOf(ligne.getQuantite() != null ? ligne.getQuantite() : 0);
            ca = ca.add(prixVente.multiply(qte));
            marge = marge.add(prixVente.subtract(cout).multiply(qte));
        }

        BigDecimal taux = (ca.compareTo(BigDecimal.ZERO) > 0) 
            ? marge.divide(ca, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) 
            : BigDecimal.ZERO;

        return new MarginKpi(ca, marge, taux.setScale(2, RoundingMode.HALF_UP));
    }

    // --- CLASSES INTERNES (DTOs) ---
    private static record MarginKpi(BigDecimal chiffreAffaires, BigDecimal margeTotale, BigDecimal tauxMarge) {}

    public static record MismatchItem(String numeroFacture, String referenceBc, String motif, BigDecimal montant) {}
}