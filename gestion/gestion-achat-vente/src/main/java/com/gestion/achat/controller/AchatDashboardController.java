package com.gestion.achat.controller;

import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.entity.BonReception;
import com.gestion.achat.entity.DemandeAchat;
import com.gestion.achat.entity.FactureAchat;
import com.gestion.achat.repository.BonCommandeRepository;
import com.gestion.achat.repository.BonReceptionRepository;
import com.gestion.achat.repository.DemandeAchatRepository;
import com.gestion.achat.repository.FactureAchatRepository;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/achats/dashboard")
@RequiredArgsConstructor
public class AchatDashboardController {

    private final BonCommandeRepository bcRepo;
    private final FactureAchatRepository factureRepo;
    private final DemandeAchatRepository daRepo;
    private final BonReceptionRepository brRepo;

    @GetMapping
    public String afficherDashboard(Model model, HttpSession session) {
        // Sécurité : Vérifier si l'utilisateur a le droit d'être ici
        String role = (String) session.getAttribute("userRole");
        if (role == null || role.equals("DEMANDEUR")) { // Le demandeur n'a pas accès au dashboard global
            return "redirect:/achats/demandes";
        }

        // --- 1. Calcul du Total des Dépenses (BC Validés) ---
        List<BonCommande> tousLesBC = bcRepo.findAll();
        double totalDepenses = 0;
        int bcEnAttente = 0;

        for (BonCommande bc : tousLesBC) {
            if ("VALIDEE".equals(bc.getStatutFinance().name())) {
                totalDepenses += bc.getMontantTotalTtc().doubleValue();
            } else if ("EN_ATTENTE_VALIDATION".equals(bc.getStatutFinance().name())) {
                bcEnAttente++;
            }
        }

        // --- 2. Calcul du Taux de Conformité ---
        List<BonReception> toutesLesReceptions = brRepo.findAll();
        int totalReceptions = toutesLesReceptions.size();
        int conformes = 0;
        
        for (BonReception br : toutesLesReceptions) {
            if (br.isConforme()) {
                conformes++;
            }
        }
        int tauxConformite = totalReceptions > 0 ? (conformes * 100 / totalReceptions) : 0;

        // --- 3. DA sans Proformas (Statut EN_ATTENTE) ---
        List<DemandeAchat> toutesLesDA = daRepo.findAll();
        int daEnAttente = 0;
        for (DemandeAchat da : toutesLesDA) {
            if ("EN_ATTENTE".equals(da.getStatut().name())) {
                daEnAttente++;
            }
        }

        // --- 4. Factures Impayées (Top 5 pour le tableau) ---
        List<FactureAchat> toutesLesFactures = factureRepo.findAll();
        List<FactureAchat> facturesImpayees = toutesLesFactures.stream()
                .filter(f -> !f.isEstPayee())
                .limit(5)
                .toList();

        // --- Envoi des données à la vue ---
        model.addAttribute("totalDepenses", totalDepenses);
        model.addAttribute("bcEnAttente", bcEnAttente);
        model.addAttribute("tauxConformite", tauxConformite);
        model.addAttribute("daSansProforma", daEnAttente);
        model.addAttribute("facturesImpayees", facturesImpayees);

        return "achat/dashboard";
    }
}