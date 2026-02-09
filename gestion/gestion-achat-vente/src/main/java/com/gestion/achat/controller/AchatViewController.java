package com.gestion.achat.controller;

import com.gestion.achat.entity.*;
import com.gestion.achat.repository.*;
import com.gestion.stock.repository.ArticleRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.UUID;

@Controller
@RequestMapping("/achats")
@RequiredArgsConstructor
public class AchatViewController {

    private final DemandeAchatRepository demandeRepo;
    private final BonCommandeRepository bcRepo;
    private final FournisseurRepository fournisseurRepo;
    private final ProformaRepository proformaRepo;
    private final BonReceptionRepository brRepo;
    private final FactureAchatRepository factureRepo;
    private final ArticleRepository articleRepo;
    private boolean isAuthenticated(HttpSession session, String... allowedRoles) {
        Object roleObj = session.getAttribute("userRole");
        if (session.getAttribute("userId") == null || roleObj == null) {
            System.out.println("DEBUG: Pas de session ou de rôle trouvé");
            return false;
        }
        
        String userRole = roleObj.toString();
        System.out.println("DEBUG: Rôle en session = [" + userRole + "]");
        
        for (String allowed : allowedRoles) {
            if (allowed.equals(userRole)) return true;
        }
        
        System.out.println("DEBUG: Rôle [" + userRole + "] non autorisé pour cette page.");
        return false;
    }
    @GetMapping("/demandes")
    public String pageDemandes(Model model, HttpSession session) {
        // Le DEMANDEUR peut voir ses propres DA, les autres voient tout
        if (!isAuthenticated(session, "ADMIN", "DEMANDEUR", "ACHETEUR", "RESPONSABLE_ACHATS", "APPRO_N1", "APPRO_N2", "APPRO_N3", "DAF")) {
            return "redirect:/login";
        }
        model.addAttribute("demandes", demandeRepo.findAll());
        model.addAttribute("activePage", "achat-demandes");
        return "achat/demandes"; 
    }

    @GetMapping("/demandes/{id}")
    public String voirDetailsDemande(@PathVariable UUID id, Model model, HttpSession session) {
        if (!isAuthenticated(session, "ADMIN", "DEMANDEUR", "ACHETEUR", "RESPONSABLE_ACHATS", "APPRO_N1", "APPRO_N2", "APPRO_N3", "DAF")) {
            return "redirect:/login";
        }
        DemandeAchat da = demandeRepo.findById(id).orElseThrow(() -> new RuntimeException("Demande introuvable"));
        
        model.addAttribute("da", da);
        model.addAttribute("proformas", proformaRepo.findByDemandeAchatId(id));
        
        // AJOUTER CECI : Pour alimenter le <select> du pop-up
        model.addAttribute("fournisseurs", fournisseurRepo.findAll()); 
        
        return "achat/details-demande"; 
    }
    @GetMapping("/bons-commande/liste")
    public String listeBonsCommande(Model model, HttpSession session) {
        // Un simple DEMANDEUR n'a pas accès à la liste globale des BC
        if (!isAuthenticated(session, "ADMIN", "ACHETEUR", "RESPONSABLE_ACHATS", "DAF", "DG", "COMPTABLE")) {
            return "redirect:/login";
        }
        model.addAttribute("bcs", bcRepo.findAllByOrderByDateEmissionDesc());
        return "achat/bc-liste";
    }

    @GetMapping("/reception/liste")
    public String listeReceptions(Model model, HttpSession session) {
        // Magasiniers et Acheteurs suivent les réceptions
        if (!isAuthenticated(session, "ADMIN", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "ACHETEUR")) {
            return "redirect:/login";
        }
        model.addAttribute("brs", brRepo.findAllByOrderByDateReceptionDesc());
        return "achat/br-liste";
    }
    @GetMapping("/factures/liste")
    public String listeFactures(Model model, HttpSession session) {
        if (!isAuthenticated(session, "ADMIN", "COMPTABLE", "DAF", "ACHETEUR")) {
            return "redirect:/login";
        }

        List<FactureAchat> factures = factureRepo.findAll();
        
        // Récupérer les BC qui n'ont PAS encore de facture
        // On filtre en Java pour simplifier, ou via une requête personnalisée
        List<BonCommande> bcsSansFacture = bcRepo.findAll().stream()
                .filter(bc -> !factureRepo.existsByBonCommandeId(bc.getId()))
                .toList();

        model.addAttribute("factures", factures);
        model.addAttribute("bcsDisponibles", bcsSansFacture); // Pour le pop-up
        
        java.math.BigDecimal totalPaye = factures.stream()
                .filter(f -> f.isEstPayee())
                .map(FactureAchat::getMontantTotalTtc)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);

        // On filtre les non-payées et on fait la somme
        java.math.BigDecimal totalReste = factures.stream()
                .filter(f -> !f.isEstPayee())
                .map(FactureAchat::getMontantTotalTtc)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        model.addAttribute("montantPaye", totalPaye);
        model.addAttribute("montantRestant", totalReste);

        return "achat/factures-liste";
    }

    @GetMapping("/demandes/{id}/comparer")
    public String comparerOffres(@PathVariable UUID id, Model model, HttpSession session) {
        // Rôle exclusif Acheteur ou Responsable Achats pour la négociation
        if (!isAuthenticated(session, "ADMIN", "ACHETEUR", "RESPONSABLE_ACHATS")) {
            return "redirect:/login";
        }
        model.addAttribute("demande", demandeRepo.findById(id).orElseThrow());
        model.addAttribute("proformas", proformaRepo.findByDemandeAchatId(id));
        return "achat/comparatif"; 
    }
    @GetMapping("/fournisseurs")
    public String listeFournisseurs(Model model, HttpSession session) {
        // Les acteurs du cycle achat et la finance ont accès au répertoire
        if (!isAuthenticated(session, "ADMIN", "ACHETEUR", "RESPONSABLE_ACHATS", "DAF", "COMPTABLE")) {
            return "redirect:/login";
        }
        
        // Récupération de la liste complète pour alimenter th:each="f : ${fournisseurs}"
        model.addAttribute("fournisseurs", fournisseurRepo.findAll());
        model.addAttribute("activePage", "achat-fournisseurs");
        
        return "achat/fournisseurs"; // Assure-toi que le nom du fichier HTML est correct
    }
    @GetMapping("/reception/nouveau")
    public String formulaireReception(Model model, HttpSession session) {
        // Seuls les magasiniers, gestionnaires de stock et acheteurs peuvent réceptionner
        if (!isAuthenticated(session, "ADMIN", "GESTIONNAIRE_STOCK", "RESPONSABLE_STOCK", "ACHETEUR")) {
            return "redirect:/login?error=access_denied";
        }

        // On récupère les BC qui sont validés/signés mais qui n'ont pas encore de Bon de Réception
        // Adapté selon tes noms de champs (ex: status ou check via jointure)
        List<BonCommande> bonsValides = bcRepo.findAll().stream()
                .filter(bc -> !brRepo.existsById(bc.getId())) // Évite les doubles réceptions
                .toList();

        model.addAttribute("bonsValides", bonsValides);
        model.addAttribute("activePage", "achat-reception");
        
        return "achat/reception"; // Assure-toi que le fichier .html s'appelle ainsi
    }
    @GetMapping("/demandes/nouveau")
    public String formulaireNouvelleDemande(Model model, HttpSession session) {
        // Le DEMANDEUR et les ACHETEURS peuvent créer des DA
        if (!isAuthenticated(session, "ADMIN", "DEMANDEUR", "ACHETEUR", "RESPONSABLE_ACHATS")) {
            return "redirect:/login?error=access_denied";
        }

        // On récupère les articles actifs pour le menu déroulant
        model.addAttribute("articles", articleRepo.findByActifTrue());
        model.addAttribute("activePage", "achat-demande-creation");
        
        return "achat/demande-form"; 
    }
}