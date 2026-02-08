package com.gestion.achat.service;

import com.gestion.achat.entity.*;
import com.gestion.achat.repository.*;
import com.gestion.stock.dto.LotDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.service.*;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BonReceptionService {

    private final BonReceptionRepository bonReceptionRepository;
    private final BonCommandeRepository bonCommandeRepository;
    private final LotService lotService;
    private final MouvementService mouvementStockService;

    /**
     * Trouver un bon de réception par ID
     */
    public Optional<BonReception> findById(UUID id) {
        return bonReceptionRepository.findById(id);
    }

    // Remplacer findRecent par :
    public List<BonReception> findRecent(int limit) {
        return bonReceptionRepository.findAll(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "dateReception"))).getContent();
    }

    /**
     * Trouver les bons de réception par bon de commande
     */
    public List<BonReception> findByBonCommandeId(UUID bonCommandeId) {
        return bonReceptionRepository.findByBonCommandeId(bonCommandeId)
                .map(Collections::singletonList)
                .orElse(Collections.emptyList());
    }

    /**
     * Créer un bon de réception
     */
    @Transactional
    public BonReception createBonReception(UUID bonCommandeId, boolean conforme, String observations) {
        BonCommande bonCommande = bonCommandeRepository.findById(bonCommandeId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de commande non trouvé"));

        BonReception bonReception = new BonReception();
        bonReception.setBonCommande(bonCommande);
        bonReception.setConforme(conforme);
        bonReception.setObservations(observations);
        // dateReception est auto-générée par @CreationTimestamp

        return bonReceptionRepository.save(bonReception);
    }

    /**
     * Réceptionner des articles et créer les lots correspondants
     */
    // Modifier recevoirArticles pour utiliser LotDTO au lieu de LotDTO.CreateLotDTO
    @Transactional
    public BonReception recevoirArticles(UUID bonCommandeId, List<ReceptionArticleDTO> articles,
            UUID utilisateurId, UUID depotId) {
        BonReception bonReception = createBonReception(bonCommandeId, true, "Réception standard");

        for (ReceptionArticleDTO articleReception : articles) {
            // Utiliser LotDTO directement
            LotDTO lotDTO = new LotDTO();
            lotDTO.setBonReceptionId(bonReception.getId());
            lotDTO.setArticleId(articleReception.getArticleId());
            lotDTO.setQuantiteInitiale(articleReception.getQuantite());
            lotDTO.setQuantiteActuelle(articleReception.getQuantite());
            lotDTO.setCoutUnitaire(articleReception.getCoutUnitaire());
            lotDTO.setDateReception(LocalDate.now());
            lotDTO.setDateFabrication(articleReception.getDateFabrication());
            lotDTO.setDatePeremption(articleReception.getDatePeremption());
            lotDTO.setEmplacementId(articleReception.getEmplacementId());

            // Créer le lot
            Lot lot = lotService.createLot(lotDTO);

            mouvementStockService.createReceptionFournisseur(
                    articleReception.getArticleId(),
                    depotId,
                    articleReception.getQuantite(),
                    articleReception.getCoutUnitaire(),
                    lot.getId(),
                    bonReception.getId(),
                    bonCommandeId,
                    utilisateurId,
                    "Réception fournisseur");
        }
        return bonReception;
    }

    /**
     * Marquer un bon de réception comme non conforme
     */
    @Transactional
    public BonReception marquerNonConforme(UUID bonReceptionId, String motif) {
        BonReception bonReception = bonReceptionRepository.findById(bonReceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de réception non trouvé"));

        bonReception.setConforme(false);
        bonReception.setObservations("NON CONFORME: " + motif + ". " +
                (bonReception.getObservations() != null ? bonReception.getObservations() : ""));

        return bonReceptionRepository.save(bonReception);
    }

    /**
     * Annuler un bon de réception
     */
    @Transactional
    public void annulerBonReception(UUID bonReceptionId, String motif) {
        BonReception bonReception = bonReceptionRepository.findById(bonReceptionId)
                .orElseThrow(() -> new IllegalArgumentException("Bon de réception non trouvé"));

        // Vérifier si des mouvements sont associés
        if (mouvementStockService.existeMouvementsPourBonReception(bonReceptionId)) {
            throw new IllegalArgumentException("Impossible d'annuler: des mouvements de stock sont associés");
        }

        bonReceptionRepository.delete(bonReception);
    }

    /**
     * Rechercher des bons de réception
     */
    public List<BonReception> search(String numeroBonCommande, LocalDateTime dateFrom,
            LocalDateTime dateTo, Boolean conforme) {
        return bonReceptionRepository.search(numeroBonCommande, dateFrom, dateTo, conforme);
    }

    // DTO pour la réception d'articles
    public static class ReceptionArticleDTO {
        private UUID articleId;
        private Integer quantite;
        private BigDecimal coutUnitaire;
        private LocalDate dateFabrication;
        private LocalDate datePeremption;
        private UUID emplacementId;

        // Getters et setters
        public UUID getArticleId() {
            return articleId;
        }

        public void setArticleId(UUID articleId) {
            this.articleId = articleId;
        }

        public Integer getQuantite() {
            return quantite;
        }

        public void setQuantite(Integer quantite) {
            this.quantite = quantite;
        }

        public BigDecimal getCoutUnitaire() {
            return coutUnitaire;
        }

        public void setCoutUnitaire(BigDecimal coutUnitaire) {
            this.coutUnitaire = coutUnitaire;
        }

        public LocalDate getDateFabrication() {
            return dateFabrication;
        }

        public void setDateFabrication(LocalDate dateFabrication) {
            this.dateFabrication = dateFabrication;
        }

        public LocalDate getDatePeremption() {
            return datePeremption;
        }

        public void setDatePeremption(LocalDate datePeremption) {
            this.datePeremption = datePeremption;
        }

        public UUID getEmplacementId() {
            return emplacementId;
        }

        public void setEmplacementId(UUID emplacementId) {
            this.emplacementId = emplacementId;
        }
    }
}