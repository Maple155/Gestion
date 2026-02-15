package com.gestion.vente.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionDefinition;

import com.gestion.stock.entity.Stock;
import com.gestion.stock.repository.DepotRepository;
import com.gestion.stock.repository.StockRepository;
import com.gestion.stock.service.LivraisonService;
import com.gestion.stock.service.ReservationService;
import com.gestion.stock.service.SequenceGeneratorService;
import com.gestion.achat.entity.DemandeAchat;
import com.gestion.achat.repository.DemandeAchatRepository;
import com.gestion.vente.dto.CreateAvoirRequest;
import com.gestion.vente.dto.CreateCommandeFromDevisRequest;
import com.gestion.vente.dto.CreateDevisRequest;
import com.gestion.vente.dto.CreateLivraisonRequest;
import com.gestion.vente.dto.CreatePaiementRequest;
import com.gestion.vente.dto.LigneVenteRequest;
import com.gestion.vente.dto.VenteTotals;
import com.gestion.vente.entity.*;
import com.gestion.vente.enums.*;
import com.gestion.vente.repository.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class VenteService {

    private final ClientRepository clientRepository;
    private final DevisVenteRepository devisRepository;
    private final CommandeClientRepository commandeRepository;
    private final LivraisonClientRepository livraisonRepository;
    private final FactureVenteRepository factureRepository;
    private final PaiementClientRepository paiementRepository;
    private final AvoirClientRepository avoirRepository;

    private final ReservationService reservationService;
    private final LivraisonService livraisonService;
    private final StockRepository stockRepository;
    private final DepotRepository depotRepository;
    private final SequenceGeneratorService sequenceService;
    private final DemandeAchatRepository demandeAchatRepository;
    private final BacklogStockVenteRepository backlogRepository;
    private final PlatformTransactionManager transactionManager;

    public DevisVente creerDevis(CreateDevisRequest request) {
        Client client = clientRepository.findById(request.getClientId())
            .orElseThrow(() -> new RuntimeException("Client introuvable"));

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new RuntimeException("Le devis doit contenir au moins une ligne");
        }

        DevisVente devis = new DevisVente();
        devis.setReference(genererReferenceDevis());
        devis.setClient(client);
        devis.setValiditeJours(request.getValiditeJours());
        devis.setRemiseGlobale(nz(request.getRemiseGlobale()));
        devis.setCreePar(request.getCreePar());
        devis.setNotes(request.getNotes());

        List<LigneDevisVente> lignes = new ArrayList<>();
        for (LigneVenteRequest ligneReq : request.getLignes()) {
            LigneDevisVente ligne = new LigneDevisVente();
            ligne.setDevis(devis);
            ligne.setArticleId(ligneReq.getArticleId());
            ligne.setQuantite(ligneReq.getQuantite());
            ligne.setPrixUnitaireHt(ligneReq.getPrixUnitaireHt());
            ligne.setRemisePourcentage(nz(ligneReq.getRemisePourcentage()));
            ligne.setTvaPourcentage(nz(ligneReq.getTvaPourcentage()));

            VenteTotals ligneTotals = calculerLigne(ligneReq);
            ligne.setTotalHt(ligneTotals.getTotalHt());
            ligne.setTotalTtc(ligneTotals.getTotalTtc());
            lignes.add(ligne);
        }

        VenteTotals totals = calculerTotauxGlobaux(request.getLignes(), devis.getRemiseGlobale());
        devis.setTotalHt(totals.getTotalHt());
        devis.setTotalTva(totals.getTotalTva());
        devis.setTotalTtc(totals.getTotalTtc());
        devis.setLignes(lignes);

        return devisRepository.save(devis);
    }

    public DevisVente modifierDevis(UUID devisId, CreateDevisRequest request) {
        DevisVente devis = devisRepository.findById(devisId)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (devis.getStatut() != StatutDevis.BROUILLON) {
            throw new RuntimeException("Seuls les devis en brouillon sont modifiables");
        }

        Client client = clientRepository.findById(request.getClientId())
            .orElseThrow(() -> new RuntimeException("Client introuvable"));

        if (request.getLignes() == null || request.getLignes().isEmpty()) {
            throw new RuntimeException("Le devis doit contenir au moins une ligne");
        }

        devis.setClient(client);
        if (request.getValiditeJours() != null) {
            devis.setValiditeJours(request.getValiditeJours());
        }
        devis.setRemiseGlobale(nz(request.getRemiseGlobale()));

        devis.getLignes().clear();
        List<LigneDevisVente> lignes = new ArrayList<>();
        for (LigneVenteRequest ligneReq : request.getLignes()) {
            LigneDevisVente ligne = new LigneDevisVente();
            ligne.setDevis(devis);
            ligne.setArticleId(ligneReq.getArticleId());
            ligne.setQuantite(ligneReq.getQuantite());
            ligne.setPrixUnitaireHt(ligneReq.getPrixUnitaireHt());
            ligne.setRemisePourcentage(nz(ligneReq.getRemisePourcentage()));
            ligne.setTvaPourcentage(nz(ligneReq.getTvaPourcentage()));

            VenteTotals ligneTotals = calculerLigne(ligneReq);
            ligne.setTotalHt(ligneTotals.getTotalHt());
            ligne.setTotalTtc(ligneTotals.getTotalTtc());
            lignes.add(ligne);
        }

        VenteTotals totals = calculerTotauxGlobaux(request.getLignes(), devis.getRemiseGlobale());
        devis.setTotalHt(totals.getTotalHt());
        devis.setTotalTva(totals.getTotalTva());
        devis.setTotalTtc(totals.getTotalTtc());
        devis.setLignes(lignes);

        return devisRepository.save(devis);
    }

    public DevisVente validerDevis(UUID devisId, UUID validePar) {
        DevisVente devis = devisRepository.findById(devisId)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (devis.getStatut() != StatutDevis.A_VALIDER) {
            throw new RuntimeException("Le devis doit être soumis avant validation");
        }
        if (devis.getStatut() == StatutDevis.ANNULE || devis.getStatut() == StatutDevis.EXPIRE) {
            throw new RuntimeException("Devis non validable");
        }

        devis.setStatut(StatutDevis.VALIDE);
        devis.setValidePar(validePar);
        devis.setDateValidation(LocalDateTime.now());
        return devisRepository.save(devis);
    }

    public DevisVente soumettreDevis(UUID devisId) {
        DevisVente devis = devisRepository.findById(devisId)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (devis.getStatut() != StatutDevis.BROUILLON) {
            throw new RuntimeException("Le devis est déjà soumis ou traité");
        }

        devis.setStatut(StatutDevis.A_VALIDER);
        return devisRepository.save(devis);
    }

    public DevisVente refuserDevis(UUID devisId, UUID validePar, String motif) {
        DevisVente devis = devisRepository.findById(devisId)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (devis.getStatut() != StatutDevis.A_VALIDER) {
            throw new RuntimeException("Seul un devis en attente peut être refusé");
        }

        devis.setStatut(StatutDevis.ANNULE);
        devis.setValidePar(validePar);
        devis.setDateValidation(LocalDateTime.now());
        if (motif != null && !motif.isBlank()) {
            String existing = devis.getNotes() != null ? devis.getNotes() + "\n" : "";
            devis.setNotes(existing + "Refus: " + motif.trim());
        }
        return devisRepository.save(devis);
    }

    public CommandeClient creerCommandeDepuisDevis(UUID devisId, CreateCommandeFromDevisRequest request) {
        DevisVente devis = devisRepository.findById(devisId)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        if (commandeRepository.existsByDevisId(devisId)) {
            throw new RuntimeException("Le devis a déjà été transformé en bon de commande");
        }

        if (devis.getStatut() != StatutDevis.VALIDE) {
            throw new RuntimeException("Le devis doit être validé avant transformation");
        }

        String modeReservation = request.getModeReservation() != null ? request.getModeReservation() : "IMMEDIATE";
        UUID depotId = request.getDepotLivraisonId();
        if (depotId == null && "IMMEDIATE".equalsIgnoreCase(modeReservation)) {
            try {
                depotId = resolveDepotIdForReservation(devis.getLignes());
            } catch (RuntimeException ex) {
                verifierStockOuCreerBacklogEtDemande(devis, null);
                throw ex;
            }
        }
        verifierStockOuCreerBacklogEtDemande(devis, depotId);

        if (depotId == null) {
            depotId = resolveDepotId(null);
        }

        CommandeClient commande = new CommandeClient();
        commande.setReference(genererReferenceCommande());
        commande.setDevis(devis);
        commande.setClient(devis.getClient());
        commande.setDepotLivraisonId(depotId);
        commande.setDateLivraisonPrevue(request.getDateLivraisonPrevue());
        commande.setModeReservation(modeReservation);
        commande.setCreePar(request.getCreePar());
        commande.setStatut(StatutCommandeClient.CONFIRMEE);

        List<LigneCommandeClient> lignesCommande = new ArrayList<>();
        for (LigneDevisVente ligneDevis : devis.getLignes()) {
            LigneCommandeClient ligne = new LigneCommandeClient();
            ligne.setCommande(commande);
            ligne.setArticleId(ligneDevis.getArticleId());
            ligne.setQuantite(ligneDevis.getQuantite());
            ligne.setPrixUnitaireHt(ligneDevis.getPrixUnitaireHt());
            ligne.setRemisePourcentage(ligneDevis.getRemisePourcentage());
            ligne.setTvaPourcentage(ligneDevis.getTvaPourcentage());
            ligne.setTotalHt(ligneDevis.getTotalHt());
            ligne.setTotalTtc(ligneDevis.getTotalTtc());
            ligne.setStatut(StatutLigneCommande.EN_ATTENTE);
            lignesCommande.add(ligne);
        }

        commande.setTotalHt(devis.getTotalHt());
        commande.setTotalTva(devis.getTotalTva());
        commande.setTotalTtc(devis.getTotalTtc());
        commande.setRemiseGlobale(devis.getRemiseGlobale());
        commande.setLignes(lignesCommande);

        CommandeClient saved = commandeRepository.save(commande);

        if ("IMMEDIATE".equalsIgnoreCase(saved.getModeReservation())) {
            for (LigneCommandeClient ligne : saved.getLignes()) {
                verifierStockDisponible(ligne.getArticleId(), depotId, ligne.getQuantite());
                var reservation = reservationService.reserverStock(
                    ligne.getArticleId(),
                    depotId,
                    ligne.getQuantite(),
                    saved.getId(),
                    ligne.getId(),
                    request.getCreePar()
                );
                ligne.setReservationStockId(reservation.getId());
                ligne.setStatut(StatutLigneCommande.RESERVEE);
                stockRepository.incrementerQuantiteReservee(ligne.getArticleId(), depotId, ligne.getQuantite());
            }
            saved = commandeRepository.save(saved);
        }

        return saved;
    }

    private void verifierStockOuCreerBacklogEtDemande(DevisVente devis, UUID depotId) {
        List<Shortage> shortages = new ArrayList<>();
        for (LigneDevisVente ligne : devis.getLignes()) {
            int disponible;
            if (depotId != null) {
                disponible = stockRepository.findByArticleIdAndDepotId(ligne.getArticleId(), depotId)
                    .map(Stock::getQuantiteDisponible)
                    .orElse(0);
            } else {
                disponible = stockRepository.findByArticleId(ligne.getArticleId()).stream()
                    .mapToInt(Stock::getQuantiteDisponible)
                    .sum();
            }
            if (disponible < ligne.getQuantite()) {
                int manquant = ligne.getQuantite() - disponible;
                shortages.add(new Shortage(ligne.getArticleId(), ligne.getQuantite(), disponible, manquant));
            }
        }

        if (shortages.isEmpty()) {
            return;
        }

        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.execute(status -> {
            for (Shortage shortage : shortages) {
                BacklogStockVente backlog = new BacklogStockVente();
                backlog.setDevisId(devis.getId());
                backlog.setArticleId(shortage.articleId);
                backlog.setQuantiteDemandee(shortage.quantiteDemandee);
                backlog.setQuantiteDisponible(shortage.quantiteDisponible);
                backlog.setQuantiteManquante(shortage.quantiteManquante);
                backlog.setNotes("Insuffisance stock pour devis " + devis.getReference());
                DemandeAchat demande = new DemandeAchat();
                demande.setProduitId(shortage.articleId);
                demande.setQuantiteDemandee(shortage.quantiteManquante);
                demande.setMotif("Demande auto: rupture pour devis " + devis.getReference());
                DemandeAchat savedDemande = demandeAchatRepository.save(demande);

                backlog.setDemandeAchatId(savedDemande.getId());
                backlogRepository.save(backlog);

            }
            return null;
        });

        throw new RuntimeException("Stock insuffisant: demandes d'achat créées");
    }

    private static class Shortage {
        private final UUID articleId;
        private final int quantiteDemandee;
        private final int quantiteDisponible;
        private final int quantiteManquante;

        private Shortage(UUID articleId, int quantiteDemandee, int quantiteDisponible, int quantiteManquante) {
            this.articleId = articleId;
            this.quantiteDemandee = quantiteDemandee;
            this.quantiteDisponible = quantiteDisponible;
            this.quantiteManquante = quantiteManquante;
        }
    }

    public LivraisonClient creerLivraison(UUID commandeId, CreateLivraisonRequest request) {
        CommandeClient commande = commandeRepository.findById(commandeId)
            .orElseThrow(() -> new RuntimeException("Commande introuvable"));

        log.info("Début livraison commande {} - lignes: {}", commande.getReference(), commande.getLignes().size());

        boolean hasMissingReservation = commande.getLignes().stream()
            .anyMatch(ligne -> ligne.getReservationStockId() == null);
        if (hasMissingReservation) {
            log.warn("Livraison refusée: réservations manquantes pour commande {}", commande.getReference());
            throw new RuntimeException("Livraison impossible: stock non réservé pour toutes les lignes");
        }

        LivraisonClient livraison = new LivraisonClient();
        livraison.setReference(genererReferenceLivraison());
        livraison.setCommande(commande);
        livraison.setTransporteur(request.getTransporteur());
        livraison.setNotes(request.getNotes());
        livraison.setDatePreparation(LocalDateTime.now());
        livraison.setStatut(StatutLivraison.EN_PREPARATION);

        List<LigneLivraisonClient> lignes = new ArrayList<>();
        for (LigneCommandeClient ligneCommande : commande.getLignes()) {
            LigneLivraisonClient ligne = new LigneLivraisonClient();
            ligne.setLivraison(livraison);
            ligne.setLigneCommande(ligneCommande);
            ligne.setArticleId(ligneCommande.getArticleId());
            ligne.setQuantiteLivree(ligneCommande.getQuantite());
            lignes.add(ligne);

            log.info("Préparation sortie stock - cmd: {} - ligne: {} - article: {} - quantite: {} - reservation: {}",
                commande.getReference(),
                ligneCommande.getId(),
                ligneCommande.getArticleId(),
                ligneCommande.getQuantite(),
                ligneCommande.getReservationStockId());

            livraisonService.creerSortieStock(ligneCommande.getReservationStockId(), request.getUtilisateurId(),
                "Livraison commande " + commande.getReference());

            ligneCommande.setStatut(StatutLigneCommande.LIVREE);
        }

        livraison.setLignes(lignes);
        livraison.setDateLivraison(LocalDateTime.now());
        livraison.setStatut(StatutLivraison.LIVREE);
        commande.setStatut(StatutCommandeClient.LIVREE);

        livraisonRepository.save(livraison);
        commandeRepository.save(commande);
        log.info("Livraison terminée: {} - commande: {}", livraison.getReference(), commande.getReference());
        return livraison;
    }

    public FactureVente genererFacture(UUID commandeId, UUID livraisonId) {
        CommandeClient commande = commandeRepository.findById(commandeId)
            .orElseThrow(() -> new RuntimeException("Commande introuvable"));

        LivraisonClient livraison = null;
        if (livraisonId != null) {
            livraison = livraisonRepository.findById(livraisonId)
                .orElseThrow(() -> new RuntimeException("Livraison introuvable"));
        }

        FactureVente facture = new FactureVente();
        facture.setReference(genererReferenceFacture());
        facture.setCommande(commande);
        facture.setLivraison(livraison);
        facture.setClient(commande.getClient());
        facture.setDateFacture(LocalDate.now());
        facture.setStatut(StatutFactureVente.EMISE);
        facture.setTotalHt(commande.getTotalHt());
        facture.setTotalTva(commande.getTotalTva());
        facture.setTotalTtc(commande.getTotalTtc());

        List<LigneFactureVente> lignesFacture = new ArrayList<>();
        for (LigneCommandeClient ligneCommande : commande.getLignes()) {
            LigneFactureVente ligne = new LigneFactureVente();
            ligne.setFacture(facture);
            ligne.setArticleId(ligneCommande.getArticleId());
            ligne.setQuantite(ligneCommande.getQuantite());
            ligne.setPrixUnitaireHt(ligneCommande.getPrixUnitaireHt());
            ligne.setRemisePourcentage(ligneCommande.getRemisePourcentage());
            ligne.setTvaPourcentage(ligneCommande.getTvaPourcentage());
            ligne.setTotalHt(ligneCommande.getTotalHt());
            ligne.setTotalTtc(ligneCommande.getTotalTtc());
            lignesFacture.add(ligne);
        }

        facture.setLignes(lignesFacture);
        FactureVente saved = factureRepository.save(facture);

        commande.setStatut(StatutCommandeClient.FACTUREE);
        commandeRepository.save(commande);

        return saved;
    }

    public PaiementClient enregistrerPaiement(UUID factureId, CreatePaiementRequest request) {
        FactureVente facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture introuvable"));

        PaiementClient paiement = new PaiementClient();
        paiement.setReference(genererReferencePaiement());
        paiement.setFacture(facture);
        paiement.setClient(facture.getClient());
        paiement.setModePaiement(request.getModePaiement());
        paiement.setMontant(request.getMontant());
        paiement.setNotes(request.getNotes());

        PaiementClient saved = paiementRepository.save(paiement);

        BigDecimal totalPaye = paiementRepository.sumMontantByFacture(facture.getId());
        if (totalPaye.compareTo(facture.getTotalTtc()) >= 0) {
            facture.setStatut(StatutFactureVente.PAYEE);
            factureRepository.save(facture);
        }

        return saved;
    }

    public AvoirClient creerAvoir(UUID factureId, CreateAvoirRequest request) {
        FactureVente facture = factureRepository.findById(factureId)
            .orElseThrow(() -> new RuntimeException("Facture introuvable"));

        AvoirClient avoir = new AvoirClient();
        avoir.setReference(genererReferenceAvoir());
        avoir.setFacture(facture);
        avoir.setClient(facture.getClient());
        avoir.setMontant(request.getMontant());
        avoir.setMotif(request.getMotif());

        return avoirRepository.save(avoir);
    }

    public CommandeClient annulerCommande(UUID commandeId, UUID validePar, String motif) {
        CommandeClient commande = commandeRepository.findById(commandeId)
            .orElseThrow(() -> new RuntimeException("Commande introuvable"));

        if (commande.getStatut() == StatutCommandeClient.LIVREE
            || commande.getStatut() == StatutCommandeClient.FACTUREE) {
            throw new RuntimeException("Impossible d'annuler une commande livrée ou facturée");
        }

        if (commande.getLignes() != null) {
            for (LigneCommandeClient ligne : commande.getLignes()) {
                UUID reservationId = ligne.getReservationStockId();
                if (reservationId == null) {
                    continue;
                }
                try {
                    var reservation = reservationService.getReservationById(reservationId);
                    if (reservation.getStatut() == com.gestion.stock.entity.ReservationStock.ReservationStatus.ACTIVE) {
                        reservationService.annulerReservation(reservationId);
                        if (reservation.getArticle() != null && reservation.getDepot() != null
                            && reservation.getQuantiteReservee() != null) {
                            stockRepository.decrementerQuantiteReservee(
                                reservation.getArticle().getId(),
                                reservation.getDepot().getId(),
                                reservation.getQuantiteReservee()
                            );
                        }
                    }
                } catch (RuntimeException ex) {
                    log.warn("Libération réservation échouée: {} - {}", reservationId, ex.getMessage());
                }
            }
        }

        commande.setStatut(StatutCommandeClient.ANNULEE);
        commande.setValidePar(validePar);
        commande.setDateValidation(LocalDateTime.now());
        if (motif != null && !motif.isBlank()) {
            String existing = commande.getNotes() != null ? commande.getNotes() + "\n" : "";
            commande.setNotes(existing + "Annulation: " + motif.trim());
        }

        return commandeRepository.save(commande);
    }

    public CommandeClient debloquerCommande(UUID commandeId, UUID validePar, String motif) {
        CommandeClient commande = commandeRepository.findById(commandeId)
            .orElseThrow(() -> new RuntimeException("Commande introuvable"));

        if (commande.getStatut() != StatutCommandeClient.EN_ATTENTE) {
            throw new RuntimeException("Seules les commandes en attente peuvent être débloquées");
        }

        commande.setStatut(StatutCommandeClient.CONFIRMEE);
        commande.setValidePar(validePar);
        commande.setDateValidation(LocalDateTime.now());
        if (motif != null && !motif.isBlank()) {
            String existing = commande.getNotes() != null ? commande.getNotes() + "\n" : "";
            commande.setNotes(existing + "Déblocage: " + motif.trim());
        }

        return commandeRepository.save(commande);
    }

    private UUID resolveDepotId(UUID depotLivraisonId) {
        if (depotLivraisonId != null) {
            return depotLivraisonId;
        }
        return depotRepository.findByActifTrue().stream()
            .findFirst()
            .orElseThrow(() -> new RuntimeException("Aucun dépôt actif"))
            .getId();
    }

    private UUID resolveDepotIdForReservation(List<LigneDevisVente> lignes) {
        if (lignes == null || lignes.isEmpty()) {
            throw new RuntimeException("Aucune ligne devis pour sélectionner un dépôt");
        }

        List<UUID> depotsCompatibles = null;
        for (LigneDevisVente ligne : lignes) {
            List<UUID> depotsPourLigne = stockRepository.findByArticleId(ligne.getArticleId()).stream()
                .filter(stock -> stock.getQuantiteDisponible() >= ligne.getQuantite())
                .map(stock -> stock.getDepot().getId())
                .toList();

            if (depotsCompatibles == null) {
                depotsCompatibles = new ArrayList<>(depotsPourLigne);
            } else {
                depotsCompatibles.retainAll(depotsPourLigne);
            }
        }

        if (depotsCompatibles == null || depotsCompatibles.isEmpty()) {
            throw new RuntimeException("Aucun dépôt ne dispose du stock suffisant pour cette commande");
        }

        return depotsCompatibles.get(0);
    }

    private void verifierStockDisponible(UUID articleId, UUID depotId, Integer quantite) {
        com.gestion.stock.entity.Stock stock = stockRepository.findByArticleIdAndDepotId(articleId, depotId)
            .orElseThrow(() -> new RuntimeException("Stock non trouvé pour cet article/dépôt"));
        if (stock.getQuantiteDisponible() < quantite) {
            throw new RuntimeException("Stock insuffisant pour cet article/dépôt");
        }
    }

    private VenteTotals calculerLigne(LigneVenteRequest ligne) {
        BigDecimal quantite = BigDecimal.valueOf(ligne.getQuantite());
        BigDecimal prix = ligne.getPrixUnitaireHt();
        BigDecimal remise = nz(ligne.getRemisePourcentage()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        BigDecimal tva = nz(ligne.getTvaPourcentage()).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);

        BigDecimal totalHt = quantite.multiply(prix).multiply(BigDecimal.ONE.subtract(remise));
        BigDecimal totalTva = totalHt.multiply(tva);
        BigDecimal totalTtc = totalHt.add(totalTva);

        return new VenteTotals(money(totalHt), money(totalTva), money(totalTtc));
    }

    private VenteTotals calculerTotauxGlobaux(List<LigneVenteRequest> lignes, BigDecimal remiseGlobale) {
        BigDecimal totalHt = BigDecimal.ZERO;
        BigDecimal totalTva = BigDecimal.ZERO;
        BigDecimal totalTtc = BigDecimal.ZERO;

        for (LigneVenteRequest ligne : lignes) {
            VenteTotals lt = calculerLigne(ligne);
            totalHt = totalHt.add(lt.getTotalHt());
            totalTva = totalTva.add(lt.getTotalTva());
            totalTtc = totalTtc.add(lt.getTotalTtc());
        }

        BigDecimal remise = nz(remiseGlobale).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP);
        if (remise.compareTo(BigDecimal.ZERO) > 0) {
            totalHt = totalHt.multiply(BigDecimal.ONE.subtract(remise));
            totalTva = totalTva.multiply(BigDecimal.ONE.subtract(remise));
            totalTtc = totalHt.add(totalTva);
        }

        return new VenteTotals(money(totalHt), money(totalTva), money(totalTtc));
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String genererReferenceDevis() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextDevisVenteSequence());
        return "DEV-" + annee + "-" + sequence;
    }

    private String genererReferenceCommande() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextCommandeClientSequence());
        return "CMD-" + annee + "-" + sequence;
    }

    private String genererReferenceLivraison() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextLivraisonClientSequence());
        return "BL-" + annee + "-" + sequence;
    }

    private String genererReferenceFacture() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextFactureVenteSequence());
        return "FAC-" + annee + "-" + sequence;
    }

    private String genererReferencePaiement() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextPaiementClientSequence());
        return "PAY-" + annee + "-" + sequence;
    }

    private String genererReferenceAvoir() {
        String annee = String.valueOf(LocalDate.now().getYear());
        String sequence = String.format("%06d", sequenceService.getNextAvoirClientSequence());
        return "AV-" + annee + "-" + sequence;
    }
}
