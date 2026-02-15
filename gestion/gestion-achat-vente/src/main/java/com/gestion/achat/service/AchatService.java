package com.gestion.achat.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.gestion.achat.entity.*;
import com.gestion.achat.enums.*;
import com.gestion.achat.repository.*;
import com.gestion.vente.entity.BacklogStockVente;
import com.gestion.vente.repository.BacklogStockVenteRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional
public class AchatService {

    private final ProformaRepository proformaRepo;
    private final BonCommandeRepository bcRepo;
    private final BonReceptionRepository brRepo;
    private final FactureAchatRepository factureRepo;
    private final BacklogStockVenteRepository backlogRepository;

    // Étape 1 : Sélectionner le moins cher
    public Proforma selectionnerMeilleureOffre(UUID demandeAchatId) {
        Proforma moinsCher = proformaRepo.findFirstByDemandeAchatIdOrderByPrixUnitaireHtAsc(demandeAchatId)
                .orElseThrow(() -> new RuntimeException("Aucun proforma trouvé"));
        
        moinsCher.setEstSelectionne(true);
        moinsCher.getDemandeAchat().setStatut(StatutDemande.EN_COURS);
        return proformaRepo.save(moinsCher);
    }

    // Étape 2 : Créer le Bon de Commande
    public BonCommande genererBonCommande(UUID proformaId) {
        Proforma p = proformaRepo.findById(proformaId).orElseThrow();
        
        BonCommande bc = new BonCommande();
        bc.setProforma(p);
        bc.setReferenceBc("BC-" + System.currentTimeMillis()); // Exemple simple de ref
        bc.setMontantTotalTtc(p.getPrixUnitaireHt().multiply(BigDecimal.valueOf(1.2)));
        bc.setStatutFinance(StatutFinance.EN_ATTENTE_VALIDATION);
        
        return bcRepo.save(bc);
    }

    // Étape 3 : Validation Finance
    public void validerAchat(UUID bcId) {
        BonCommande bc = bcRepo.findById(bcId).orElseThrow();
        bc.setStatutFinance(StatutFinance.VALIDEE);
        bcRepo.save(bc);
    }

    // Étape 4 : Réception et Mise à jour du Stock
    public BonReception enregistrerReception(UUID bcId, boolean conforme, String obs) {
        BonCommande bc = bcRepo.findById(bcId).orElseThrow();
        
        if (!bc.getStatutFinance().equals(StatutFinance.VALIDEE))
            throw new RuntimeException("BC non validé par la finance");

        BonReception br = new BonReception();
        br.setBonCommande(bc);
        br.setConforme(true);
        br.setObservations(obs);

        if (conforme) {
            // Ici : Appel interne à ton service Stock
            // stockService.augmenterStock(bc.getProforma().getDemandeAchat().getProduitId(), 
            //                             bc.getProforma().getDemandeAchat().getQuantiteDemandee());
            bc.getProforma().getDemandeAchat().setStatut(StatutDemande.TERMINEE);

            UUID demandeAchatId = bc.getProforma().getDemandeAchat().getId();
            List<BacklogStockVente> backlogs = backlogRepository.findByDemandeAchatIdAndStatut(demandeAchatId, "EN_ATTENTE");
            for (BacklogStockVente backlog : backlogs) {
                backlog.setStatut("RESOLU");
                String existing = backlog.getNotes() != null ? backlog.getNotes() + "\n" : "";
                backlog.setNotes(existing + "Réception achat: " + bc.getReferenceBc());
            }
            backlogRepository.saveAll(backlogs);
        }
        
        return brRepo.save(br);
    }

    // Étape 5 : Enregistrement Facture
    public FactureAchat enregistrerFacture(UUID bcId, String numFacture, LocalDate dateFact) {
        BonCommande bc = bcRepo.findById(bcId).orElseThrow();
        
        FactureAchat facture = new FactureAchat();
        facture.setBonCommande(bc);
        facture.setNumeroFactureFournisseur(numFacture);
        facture.setMontantTotalTtc(bc.getMontantTotalTtc());
        facture.setDateFacture(dateFact);
        
        return factureRepo.save(facture);
    }
}