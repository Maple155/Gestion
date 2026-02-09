package com.gestion.finance.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.stock.entity.Stock;
import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.repository.StockRepository;
import com.gestion.stock.service.ValorisationService;
import com.gestion.vente.entity.LigneCommandeClient;
import com.gestion.vente.repository.LigneCommandeClientRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/finance")
@RequiredArgsConstructor
public class FinanceController {

    private final FactureAchatRepository factureAchatRepository;
    private final BonCommandeRepository bonCommandeRepository;
    private final BonReceptionRepository bonReceptionRepository;
    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final LigneCommandeClientRepository ligneCommandeClientRepository;
    private final ValorisationService valorisationService;

    @GetMapping("/daf")
    public String dashboardDaf(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF");

        List<MismatchItem> mismatches = buildMismatchList();
        model.addAttribute("mismatchCount", mismatches.size());
        model.addAttribute("mismatches", mismatches);

        BigDecimal comptable = getValeurStockComptable();
        BigDecimal operationnelle = getValeurStockOperationnelle();
        BigDecimal ecart = operationnelle.subtract(comptable);
        model.addAttribute("valeurComptable", comptable);
        model.addAttribute("valeurOperationnelle", operationnelle);
        model.addAttribute("ecartStock", ecart);

        MarginKpi margin = computeMarge();
        model.addAttribute("margeTotale", margin.margeTotale);
        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("tauxMarge", margin.tauxMarge);

        model.addAttribute("activePage", "finance-daf");
        model.addAttribute("dateJour", LocalDate.now());
        return "finance/daf-dashboard";
    }

    private void requireRole(HttpSession session, String... allowedRoles) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) {
            throw new RuntimeException("Rôle utilisateur manquant");
        }
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) {
                return;
            }
        }
        throw new RuntimeException("Accès refusé pour le rôle: " + role);
    }

    private List<MismatchItem> buildMismatchList() {
        List<MismatchItem> items = new ArrayList<>();
        List<FactureAchat> factures = factureAchatRepository.findAll();
        for (FactureAchat facture : factures) {
            BonCommande bc = facture.getBonCommande();
            String reason = null;
            if (bc == null) {
                reason = "BC manquant";
            } else {
                BonReception br = bonReceptionRepository.findByBonCommandeId(bc.getId()).orElse(null);
                if (br == null) {
                    reason = "BR manquant";
                } else if (!br.isConforme()) {
                    reason = "Réception non conforme";
                } else if (facture.getMontantTotalTtc() != null && bc.getMontantTotalTtc() != null
                        && facture.getMontantTotalTtc().compareTo(bc.getMontantTotalTtc()) != 0) {
                    reason = "Montant facture != BC";
                }
            }

            if (reason != null) {
                items.add(new MismatchItem(
                    facture.getNumeroFactureFournisseur(),
                    bc != null ? bc.getReferenceBc() : "-",
                    reason,
                    facture.getMontantTotalTtc()
                ));
            }
        }
        return items;
    }

    private BigDecimal getValeurStockComptable() {
        Object value = valorisationService.getSyntheseValorisation().get("valeurTotale");
        if (value instanceof BigDecimal) {
            return (BigDecimal) value;
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal getValeurStockOperationnelle() {
        BigDecimal total = BigDecimal.ZERO;
        List<Stock> stocks = stockRepository.findAll();
        for (Stock stock : stocks) {
            BigDecimal cout = stock.getArticle() != null ? stock.getArticle().getCoutStandard() : BigDecimal.ZERO;
            if (cout == null) {
                cout = BigDecimal.ZERO;
            }
            int quantite = stock.getQuantiteTheorique() != null ? stock.getQuantiteTheorique() : 0;
            total = total.add(cout.multiply(BigDecimal.valueOf(quantite)));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
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
            int quantite = ligne.getQuantite() != null ? ligne.getQuantite() : 0;
            BigDecimal ligneCa = prixVente.multiply(BigDecimal.valueOf(quantite));
            BigDecimal ligneMarge = prixVente.subtract(cout).multiply(BigDecimal.valueOf(quantite));
            ca = ca.add(ligneCa);
            marge = marge.add(ligneMarge);
        }

        BigDecimal taux = BigDecimal.ZERO;
        if (ca.compareTo(BigDecimal.ZERO) > 0) {
            taux = marge.divide(ca, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
        }
        return new MarginKpi(ca, marge, taux.setScale(2, RoundingMode.HALF_UP));
    }

    private static class MarginKpi {
        private final BigDecimal chiffreAffaires;
        private final BigDecimal margeTotale;
        private final BigDecimal tauxMarge;

        private MarginKpi(BigDecimal chiffreAffaires, BigDecimal margeTotale, BigDecimal tauxMarge) {
            this.chiffreAffaires = chiffreAffaires;
            this.margeTotale = margeTotale;
            this.tauxMarge = tauxMarge;
        }
    }

    private static class MismatchItem {
        private final String numeroFacture;
        private final String referenceBc;
        private final String motif;
        private final BigDecimal montant;

        private MismatchItem(String numeroFacture, String referenceBc, String motif, BigDecimal montant) {
            this.numeroFacture = numeroFacture;
            this.referenceBc = referenceBc;
            this.motif = motif;
            this.montant = montant;
        }

        public String getNumeroFacture() {
            return numeroFacture;
        }

        public String getReferenceBc() {
            return referenceBc;
        }

        public String getMotif() {
            return motif;
        }

        public BigDecimal getMontant() {
            return montant;
        }
    }
}
