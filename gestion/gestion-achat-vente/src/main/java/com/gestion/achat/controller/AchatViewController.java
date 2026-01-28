package com.gestion.achat.controller;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.DemandeAchat;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.entity.Proforma;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.DemandeAchatRepository;
import com.gestion.achat.repository.FactureAchatRepository;
import com.gestion.achat.repository.FournisseurRepository;
import com.gestion.achat.repository.ProformaRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
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
    // Affiche le template : src/main/resources/templates/achat/demandes.html
    @GetMapping("/demandes")
    public String pageDemandes(Model model) {
        model.addAttribute("demandes", demandeRepo.findAll());
        return "achat/demandes"; 
    }

    // Affiche le template : src/main/resources/templates/achat/bon-commande.html
    @GetMapping("/bons-commande/{id}")
    public String pageBonCommande(@PathVariable UUID id, Model model) {
        model.addAttribute("bc", bcRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Bon de commande introuvable")));
        return "achat/bon-commande";
    }

    // Affiche le template : src/main/resources/templates/achat/reception.html
    @GetMapping("/reception/nouveau")
    public String pageReception(Model model) {
        // On peut passer ici la liste des BC validés pour les réceptionner
        model.addAttribute("bonsValides", bcRepo.findAll()); 
        return "achat/reception";
    }
    @GetMapping("/fournisseurs")
    public String listeFournisseurs(Model model) {
        model.addAttribute("fournisseurs", fournisseurRepo.findAll());
        return "achat/fournisseurs"; 
    }
    @GetMapping("/demandes/{id}/comparer")
    public String comparerOffres(@PathVariable UUID id, Model model) {
        model.addAttribute("demande", demandeRepo.findById(id).orElseThrow());
        model.addAttribute("proformas", proformaRepo.findByDemandeAchatId(id));
        return "achat/comparatif"; 
    }
    @GetMapping("/demandes/{id}")
    public String voirDetailsDemande(@PathVariable UUID id, Model model) {
        DemandeAchat da = demandeRepo.findById(id)
            .orElseThrow(() -> new RuntimeException("Demande introuvable"));
        
        // Récupération des proformas liés à cette demande spécifique
        List<Proforma> proformas = proformaRepo.findByDemandeAchatId(id);
        
        model.addAttribute("da", da);
        model.addAttribute("proformas", proformas);
        return "achat/details-demande"; 
    }
    @GetMapping("/bons-commande/liste")
    public String listeBonsCommande(Model model) {
        List<BonCommande> bcs = bcRepo.findAllByOrderByDateEmissionDesc();
        
        // On extrait les noms de fournisseurs uniques pour le filtre
        List<String> fournisseurs = bcs.stream()
                .map(bc -> bc.getProforma().getFournisseur().getNom())
                .distinct()
                .sorted()
                .toList();

        model.addAttribute("bcs", bcs);
        model.addAttribute("fournisseurs", fournisseurs); // Nouvelle liste simplifiée
        return "achat/bc-liste";
    }

    // 2. Liste des Bons de Réception (BR)
    @GetMapping("/reception/liste")
    public String listeReceptions(Model model) {
        List<BonReception> brs = brRepo.findAllByOrderByDateReceptionDesc();
        model.addAttribute("brs", brs);
        return "achat/br-liste";
    }

    // 3. Liste des Factures d'Achat
    @GetMapping("/factures/liste")
    public String listeFactures(Model model) {
        List<FactureAchat> factures = factureRepo.findAll();
        
        // Calcul des sommes avec BigDecimal
        BigDecimal montantPaye = factures.stream()
                .filter(FactureAchat::isEstPayee)
                .map(FactureAchat::getMontantTotalTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
                
        BigDecimal montantRestant = factures.stream()
                .filter(f -> !f.isEstPayee())
                .map(FactureAchat::getMontantTotalTtc)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("factures", factures);
        model.addAttribute("montantPaye", montantPaye);
        model.addAttribute("montantRestant", montantRestant);
        
        return "achat/factures-liste";
    }
}