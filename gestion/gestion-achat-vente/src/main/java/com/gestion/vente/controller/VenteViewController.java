package com.gestion.vente.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import com.gestion.vente.dto.CreateDevisRequest;
import com.gestion.vente.dto.CreateCommandeFromDevisRequest;
import com.gestion.vente.dto.CreateLivraisonRequest;
import com.gestion.vente.dto.CreatePaiementRequest;
import com.gestion.vente.dto.CreateAvoirRequest;
import com.gestion.vente.dto.LigneVenteRequest;
import com.gestion.vente.entity.*;
import com.gestion.vente.repository.*;
import com.gestion.vente.service.VenteService;
import com.gestion.vente.enums.ModePaiement;
import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.entity.Article;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ventes")
@RequiredArgsConstructor
public class VenteViewController {

    private static final BigDecimal PLAFOND_REMISE_COMMERCIAL = BigDecimal.valueOf(10);

    private final ClientRepository clientRepository;
    private final DevisVenteRepository devisRepository;
    private final CommandeClientRepository commandeRepository;
    private final LivraisonClientRepository livraisonRepository;
    private final FactureVenteRepository factureRepository;
    private final PaiementClientRepository paiementRepository;
    private final AvoirClientRepository avoirRepository;
    private final VenteService venteService;
    private final ArticleRepository articleRepository;

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

    @GetMapping("/clients")
    public String listClients(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("activePage", "vente-clients");
        return "vente/clients";
    }

    @GetMapping("/devis/liste")
    public String listDevis(Model model, HttpSession session) {
        model.addAttribute("devis", devisRepository.findAllByOrderByDateDevisDesc());
        Object flashError = session.getAttribute("flashError");
        if (flashError != null) {
            model.addAttribute("flashError", flashError.toString());
            session.removeAttribute("flashError");
        }
        model.addAttribute("activePage", "vente-devis");
        return "vente/devis-liste";
    }

    @GetMapping("/devis/nouveau")
    public String nouveauDevis(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("articles", articleRepository.findAll());
        model.addAttribute("activePage", "vente-devis");
        return "vente/devis-nouveau";
    }

    @PostMapping("/devis/nouveau")
    public String creerDevis(@RequestParam UUID clientId,
                             @RequestParam UUID articleId,
                             @RequestParam Integer quantite,
                             @RequestParam BigDecimal prixUnitaireHt,
                             @RequestParam(required = false) BigDecimal remisePourcentage,
                             @RequestParam(required = false) BigDecimal tvaPourcentage,
                             HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        String role = (String) session.getAttribute("userRole");
        UUID userId = (UUID) session.getAttribute("userId");
        BigDecimal remise = remisePourcentage != null ? remisePourcentage : BigDecimal.ZERO;
        CreateDevisRequest request = new CreateDevisRequest();
        request.setClientId(clientId);
        request.setCreePar(userId);
        LigneVenteRequest ligne = new LigneVenteRequest();
        ligne.setArticleId(articleId);
        ligne.setQuantite(quantite);
        BigDecimal prixFinal = prixUnitaireHt;
        BigDecimal tvaFinal = tvaPourcentage;
        if (prixFinal == null || tvaFinal == null) {
            Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article introuvable"));
            if (prixFinal == null) {
                prixFinal = article.getPrixVenteHt();
            }
            if (tvaFinal == null) {
                tvaFinal = article.getTvaPourcentage();
            }
        }
        ligne.setPrixUnitaireHt(prixFinal);
        if (remisePourcentage != null) {
            ligne.setRemisePourcentage(remisePourcentage);
        }
        if (tvaFinal != null) {
            ligne.setTvaPourcentage(tvaFinal);
        }
        request.setLignes(List.of(ligne));
        venteService.creerDevis(request);
        if ("COMMERCIAL".equals(role) && remise.compareTo(PLAFOND_REMISE_COMMERCIAL) > 0) {
            session.setAttribute("flashError", "Devis en attente de validation responsable (remise au-dessus du plafond)");
        }
        return "redirect:/ventes/devis/liste";
    }

    @PostMapping("/devis/{id}/valider")
    public String validerDevis(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        UUID userId = (UUID) session.getAttribute("userId");
        venteService.validerDevis(id, userId);
        return "redirect:/ventes/devis/liste";
    }

    @PostMapping("/devis/{id}/commande")
    public String transformerDevis(@PathVariable UUID id,
                                   @RequestParam(required = false) String modeReservation,
                                   HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        CreateCommandeFromDevisRequest request = new CreateCommandeFromDevisRequest();
        request.setModeReservation(modeReservation != null ? modeReservation : "IMMEDIATE");
        request.setCreePar((UUID) session.getAttribute("userId"));
        try {
            venteService.creerCommandeDepuisDevis(id, request);
            return "redirect:/ventes/commandes/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @GetMapping("/commandes/liste")
    public String listCommandes(Model model, HttpSession session) {
        model.addAttribute("commandes", commandeRepository.findAllByOrderByDateCommandeDesc());
        Object flashError = session.getAttribute("flashError");
        if (flashError != null) {
            model.addAttribute("flashError", flashError.toString());
            session.removeAttribute("flashError");
        }
        model.addAttribute("activePage", "vente-commandes");
        return "vente/commandes-liste";
    }

    @PostMapping("/commandes/{id}/livrer")
    public String livrerCommande(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "MAGASINIER_SORTIE");
        CreateLivraisonRequest request = new CreateLivraisonRequest();
        request.setUtilisateurId((UUID) session.getAttribute("userId"));
        try {
            venteService.creerLivraison(id, request);
            return "redirect:/ventes/livraisons/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/commandes/liste";
        }
    }

    @PostMapping("/commandes/{id}/facturer")
    public String facturerCommande(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        venteService.genererFacture(id, null);
        return "redirect:/ventes/factures/liste";
    }

    @GetMapping("/livraisons/liste")
    public String listLivraisons(Model model) {
        model.addAttribute("livraisons", livraisonRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "vente-livraisons");
        return "vente/livraisons-liste";
    }

    @GetMapping("/factures/liste")
    public String listFactures(Model model) {
        List<FactureVente> factures = factureRepository.findAllByOrderByDateFactureDesc();
        BigDecimal montantPaye = factures.stream()
            .filter(f -> f.getStatut() == com.gestion.vente.enums.StatutFactureVente.PAYEE)
            .map(FactureVente::getTotalTtc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montantRestant = factures.stream()
            .filter(f -> f.getStatut() != com.gestion.vente.enums.StatutFactureVente.PAYEE)
            .map(FactureVente::getTotalTtc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("factures", factures);
        model.addAttribute("montantPaye", montantPaye);
        model.addAttribute("montantRestant", montantRestant);
        model.addAttribute("activePage", "vente-factures");
        return "vente/factures-liste";
    }

    @PostMapping("/factures/{id}/payer")
    public String payerFacture(@PathVariable UUID id,
                               @RequestParam java.math.BigDecimal montant,
                               @RequestParam(defaultValue = "VIREMENT") ModePaiement modePaiement,
                               @RequestParam(required = false) String notes,
                               HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        CreatePaiementRequest request = new CreatePaiementRequest();
        request.setMontant(montant);
        request.setModePaiement(modePaiement);
        request.setNotes(notes);
        venteService.enregistrerPaiement(id, request);
        return "redirect:/ventes/factures/liste";
    }

    @PostMapping("/factures/{id}/avoir")
    public String creerAvoir(@PathVariable UUID id,
                             @RequestParam java.math.BigDecimal montant,
                             @RequestParam String motif,
                             HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        CreateAvoirRequest request = new CreateAvoirRequest();
        request.setMontant(montant);
        request.setMotif(motif);
        venteService.creerAvoir(id, request);
        return "redirect:/ventes/avoirs/liste";
    }

    @GetMapping("/paiements/liste")
    public String listPaiements(Model model) {
        model.addAttribute("paiements", paiementRepository.findAllByOrderByDatePaiementDesc());
        model.addAttribute("activePage", "vente-paiements");
        return "vente/paiements-liste";
    }

    @GetMapping("/avoirs/liste")
    public String listAvoirs(Model model) {
        model.addAttribute("avoirs", avoirRepository.findAllByOrderByDateAvoirDesc());
        model.addAttribute("activePage", "vente-avoirs");
        return "vente/avoirs-liste";
    }
}
