package com.gestion.finance.controller;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.FactureAchatRepository;
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
    private final BonReceptionRepository bonReceptionRepository;
    private final StockRepository stockRepository;
    private final ArticleRepository articleRepository;
    private final LigneCommandeClientRepository ligneCommandeClientRepository;
    private final ValorisationService valorisationService;

    // --- DASHBOARD PRINCIPAL (DAF) ---
    @GetMapping("/daf")
    public String dashboardDaf(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        List<MismatchItem> mismatches = buildMismatchList();
        model.addAttribute("mismatchCount", mismatches.size());
        model.addAttribute("mismatches", mismatches);

        // Stock
        BigDecimal comptable = getValeurStockComptable();
        BigDecimal operationnelle = getValeurStockOperationnelle();
        model.addAttribute("valeurComptable", comptable);
        model.addAttribute("valeurOperationnelle", operationnelle);
        model.addAttribute("ecartStock", operationnelle.subtract(comptable));

        // Rentabilité
        MarginKpi margin = computeMarge();
        model.addAttribute("margeTotale", margin.margeTotale);
        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("tauxMarge", margin.tauxMarge);

        model.addAttribute("activePage", "finance-daf");
        model.addAttribute("dateJour", LocalDate.now());
        return "finance/daf-dashboard";
    }

    // --- FOURNISSEURS (ACHATS) ---
    @GetMapping("/fournisseurs")
    public String dashboardFournisseurs(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        List<FactureAchat> factures = factureAchatRepository.findAll();
        
        BigDecimal totalDu = factures.stream()
            .filter(f -> !f.isEstPayee())
            .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalPaye = factures.stream()
            .filter(FactureAchat::isEstPayee)
            .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("factures", factures);
        model.addAttribute("totalDu", totalDu);
        model.addAttribute("totalPaye", totalPaye);
        model.addAttribute("nombreFactures", factures.size());
        model.addAttribute("activePage", "finance-fournisseurs");

        return "finance/fournisseurs-dashboard";
    }

    // --- CLIENTS (VENTES) ---
    @GetMapping("/clients")
    public String dashboardClients(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        MarginKpi margin = computeMarge();
        model.addAttribute("caTotal", margin.chiffreAffaires);
        model.addAttribute("margeTotal", margin.margeTotale);
        model.addAttribute("activePage", "finance-clients");

        return "finance/clients-dashboard";
    }

    // --- TRESORERIE ---
    @GetMapping("/tresorerie")
    public String dashboardTresorerie(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        // On considère le CA comme l'argent "théorique" rentré (Encaissements)
        BigDecimal encaissements = computeMarge().chiffreAffaires;
        
        // Seules les factures marquées "estPayee" sont des décaissements réels
        BigDecimal decaissementsReels = factureAchatRepository.findAll().stream()
            .filter(FactureAchat::isEstPayee)
            .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Les factures NON payées sont des dettes à court terme
        BigDecimal dettesFournisseurs = factureAchatRepository.findAll().stream()
            .filter(f -> !f.isEstPayee())
            .map(f -> f.getMontantTotalTtc() != null ? f.getMontantTotalTtc() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("encaissements", encaissements);
        model.addAttribute("decaissements", decaissementsReels);
        model.addAttribute("dettesFournisseurs", dettesFournisseurs);
        model.addAttribute("soldeTheorique", encaissements.subtract(decaissementsReels));
        model.addAttribute("activePage", "finance-tresorerie");

        return "finance/tresorerie-dashboard";
    }

    // --- AUDIT & STOCK ---
    @GetMapping("/audit")
    public String audit(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");
        model.addAttribute("mismatches", buildMismatchList());
        model.addAttribute("activePage", "finance-audit");
        return "finance/audit-dashboard";
    }

    @GetMapping("/stock")
    public String dashboardStock(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "DAF", "FINANCE");

        BigDecimal comptable = getValeurStockComptable();
        BigDecimal operationnelle = getValeurStockOperationnelle();

        model.addAttribute("valeurComptable", comptable);
        model.addAttribute("valeurOperationnelle", operationnelle);
        model.addAttribute("ecartStock", operationnelle.subtract(comptable));
        model.addAttribute("activePage", "finance-stock");

        return "finance/stock-dashboard";
    }

    // --- LOGIQUE METIER PRIVÉE ---

    private List<MismatchItem> buildMismatchList() {
        List<MismatchItem> items = new ArrayList<>();
        List<FactureAchat> factures = factureAchatRepository.findAll();

        for (FactureAchat facture : factures) {
            BonCommande bc = facture.getBonCommande(); // Ne peut pas être nul selon ton entité
            String reason = null;

            BonReception br = bonReceptionRepository.findByBonCommandeId(bc.getId()).orElse(null);

            if (br == null) {
                reason = "Facture reçue sans Bon de Réception";
            } else if (!br.isConforme()) {
                reason = "Réception déclarée non-conforme";
            } else if (facture.getMontantTotalTtc().compareTo(bc.getMontantTotalTtc()) != 0) {
                reason = "Écart de prix: Facture (" + facture.getMontantTotalTtc() + ") vs BC (" + bc.getMontantTotalTtc() + ")";
            }

            if (reason != null) {
                items.add(new MismatchItem(
                    facture.getNumeroFactureFournisseur(),
                    bc.getReferenceBc(),
                    reason,
                    facture.getMontantTotalTtc(),
                    facture.isEstPayee()
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
            
            int qte = ligne.getQuantite() != null ? ligne.getQuantite() : 0;
            ca = ca.add(prixVente.multiply(BigDecimal.valueOf(qte)));
            marge = marge.add(prixVente.subtract(cout).multiply(BigDecimal.valueOf(qte)));
        }

        BigDecimal taux = (ca.compareTo(BigDecimal.ZERO) > 0) 
            ? marge.divide(ca, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)) 
            : BigDecimal.ZERO;

        return new MarginKpi(ca, marge, taux.setScale(2, RoundingMode.HALF_UP));
    }

    private void requireRole(HttpSession session, String... allowedRoles) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) throw new RuntimeException("Session expirée ou non autorisée");
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) return;
        }
        throw new RuntimeException("Accès interdit pour votre profil");
    }

    // --- DATA HOLDERS (Records Java 17+) ---
    private record MarginKpi(BigDecimal chiffreAffaires, BigDecimal margeTotale, BigDecimal tauxMarge) {}
    public record MismatchItem(String numeroFacture, String referenceBc, String motif, BigDecimal montant, boolean payee) {}
}