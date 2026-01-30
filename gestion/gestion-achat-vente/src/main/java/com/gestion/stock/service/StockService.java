package com.gestion.stock.service;

import com.gestion.achat.entity.*;
import com.gestion.stock.entity.*;
import com.gestion.achat.repository.*;
import com.gestion.stock.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

        private final StockMovementRepository stockMovementRepository;
        private final LotRepository lotRepository;
        private final StockRepository stockRepository;
        private final ArticleRepository articleRepository;
        private final DepotRepository depotRepository;
        private final MovementTypeRepository movementTypeRepository;
        private final BonReceptionRepository bonReceptionRepository;
        private final SequenceGeneratorService sequenceService;

        @Transactional
        public StockMovement creerEntreeStockFromReception(UUID bonReceptionId, UUID utilisateurId) {
                log.info("Création entrée stock pour bon réception ID: {}", bonReceptionId);

                // 1. Récupérer le bon de réception
                BonReception bonReception = bonReceptionRepository.findById(bonReceptionId)
                                .orElseThrow(() -> new RuntimeException("Bon réception non trouvé"));

                // 2. Récupérer le bon de commande associé
                var bonCommande = bonReception.getBonCommande();
                var proforma = bonCommande.getProforma();
                var demande = proforma.getDemandeAchat();

                // 3. Vérifier que la réception est conforme
                if (!bonReception.isConforme()) {
                        throw new RuntimeException("Le bon de réception n'est pas conforme");
                }

                // 4. Trouver l'article par son ID (produit_id de la demande)
                // Note: Vous devez adapter cette partie selon votre modèle
                UUID articleId = demande.getProduitId(); // À vérifier selon votre modèle

                Article article = articleRepository.findById(articleId)
                                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

                // 5. Récupérer le dépôt par défaut
                Depot depot = depotRepository.findByCode("DEP-CENTRAL")
                                .orElse(depotRepository.findByActifTrue().stream().findFirst()
                                                .orElseThrow(() -> new RuntimeException("Aucun dépôt disponible")));

                // 6. Récupérer le coût unitaire
                BigDecimal coutUnitaire = proforma.getPrixUnitaireHt();
                Integer quantite = demande.getQuantiteDemandee();

                // 7. Récupérer le type de mouvement
                MovementType typeMouvement = movementTypeRepository.findByCode("RECEPTION_FOURNISSEUR")
                                .orElseThrow(() -> new RuntimeException("Type de mouvement non trouvé"));

                // 8. Générer la référence
                String reference = "MVT-" + LocalDate.now().getYear() +
                                String.format("-%06d", sequenceService.getNextMovementSequence());

                // 9. Créer le mouvement d'entrée
                StockMovement mouvement = StockMovement.builder()
                                .reference(reference)
                                .type(typeMouvement)
                                .article(article)
                                .depot(depot)
                                .quantite(quantite)
                                .coutUnitaire(coutUnitaire)
                                .bonReception(bonReception)
                                .bonCommande(bonCommande)
                                .dateMouvement(LocalDateTime.now())
                                .dateComptable(LocalDate.now())
                                .utilisateurId(utilisateurId)
                                .motif("Réception fournisseur - " + bonReception.getObservations())
                                .statut(StockMovement.MovementStatus.VALIDE)
                                .build();

                StockMovement mouvementSauvegarde = stockMovementRepository.save(mouvement);

                // 10. Mettre à jour le stock théorique
                mettreAJourStockTheorique(article.getId(), depot.getId(), quantite, true);

                // 11. Recalculer le CUMP
                recalculerCUMP(article.getId(), depot.getId(), coutUnitaire, quantite);

                // 12. Créer un lot si l'article est géré par lot
                if (article.isGestionParLot()) {
                        creerLotDepuisReception(article, depot, bonReception, coutUnitaire, quantite);
                }

                log.info("Entrée stock créée avec succès: {}", mouvementSauvegarde.getReference());
                return mouvementSauvegarde;
        }

        private void mettreAJourStockTheorique(UUID articleId, UUID depotId, Integer quantite, boolean isEntree) {
                Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);

                if (stockOpt.isPresent()) {
                        Stock stock = stockOpt.get();
                        if (isEntree) {
                                stock.setQuantiteTheorique(stock.getQuantiteTheorique() + quantite);
                                stock.setQuantitePhysique(stock.getQuantitePhysique() + quantite);
                        } else {
                                stock.setQuantiteTheorique(stock.getQuantiteTheorique() - quantite);
                                stock.setQuantitePhysique(stock.getQuantitePhysique() - quantite);
                        }
                        stock.setDateDernierMouvement(LocalDateTime.now());
                        stockRepository.save(stock);
                } else {
                        // Créer un nouveau stock si inexistant
                        Article article = articleRepository.findById(articleId)
                                        .orElseThrow(() -> new RuntimeException("Article non trouvé"));

                        Depot depot = depotRepository.findById(depotId)
                                        .orElseThrow(() -> new RuntimeException("Dépôt non trouvé"));

                        Stock stock = Stock.builder()
                                        .article(article)
                                        .depot(depot)
                                        .quantiteTheorique(isEntree ? quantite : -quantite)
                                        .quantitePhysique(isEntree ? quantite : -quantite)
                                        .quantiteReservee(0)
                                        .dateDernierMouvement(LocalDateTime.now())
                                        .build();

                        // Calculer la valeur initiale du stock
                        if (isEntree) {
                                // Trouver le coût moyen pour initialiser la valeur
                                BigDecimal coutMoyen = article.getCoutStandard() != null ? article.getCoutStandard()
                                                : BigDecimal.ZERO;
                                stock.setValeurStockCump(coutMoyen.multiply(BigDecimal.valueOf(quantite)));
                        }

                        stockRepository.save(stock);
                }
        }

        private void recalculerCUMP(UUID articleId, UUID depotId, BigDecimal coutEntree, Integer quantiteEntree) {
                Optional<Stock> stockOpt = stockRepository.findByArticleIdAndDepotId(articleId, depotId);

                if (stockOpt.isPresent()) {
                        Stock stock = stockOpt.get();

                        // Récupérer toutes les entrées pour cet article/dépôt
                        Long totalQuantiteEntrees = stockMovementRepository.sumEntreesByArticleAndDepot(articleId,
                                        depotId);
                        if (totalQuantiteEntrees == null || totalQuantiteEntrees == 0) {
                                return;
                        }

                        // Calculer la valeur totale des entrées
                        BigDecimal valeurTotaleEntrees = BigDecimal.ZERO;

                        // Cette requête devrait être optimisée dans un vrai système
                        var mouvementsEntrees = stockMovementRepository
                                        .findByArticleIdAndDepotIdOrderByDateMouvementDesc(articleId,
                                                        depotId);
                        for (var mvt : mouvementsEntrees) {
                                if (mvt.getType().getSens() == MovementType.SensMouvement.ENTREE) {
                                        valeurTotaleEntrees = valeurTotaleEntrees.add(
                                                        mvt.getCoutUnitaire().multiply(
                                                                        BigDecimal.valueOf(mvt.getQuantite())));
                                }
                        }

                        // Calculer le nouveau CUMP
                        BigDecimal nouveauCUMP = valeurTotaleEntrees.divide(
                                        BigDecimal.valueOf(totalQuantiteEntrees), 4, RoundingMode.HALF_UP);

                        // Mettre à jour la valeur du stock
                        BigDecimal nouvelleValeur = nouveauCUMP
                                        .multiply(BigDecimal.valueOf(stock.getQuantiteTheorique()));
                        stock.setValeurStockCump(nouvelleValeur);
                        stockRepository.save(stock);

                        log.info("CUMP recalculé pour article {}: {}", articleId, nouveauCUMP);
                }
        }

        private Lot creerLotDepuisReception(Article article, Depot depot, BonReception bonReception,
                        BigDecimal coutUnitaire, Integer quantite) {

                String numeroLot = "LOT-" + LocalDate.now().getYear() +
                                String.format("%02d", LocalDate.now().getMonthValue()) +
                                String.format("-%04d", sequenceService.getNextLotSequence());

                LocalDate datePeremption = null;
                if (article.getDureeVieJours() != null) {
                        datePeremption = LocalDate.now().plusDays(article.getDureeVieJours());
                }

                Lot lot = Lot.builder()
                                .numeroLot(numeroLot)
                                .article(article)
                                .bonReception(bonReception)
                                .quantiteInitiale(quantite)
                                .quantiteActuelle(quantite)
                                .dateFabrication(LocalDate.now().minusDays(2))
                                .dateReception(LocalDate.now())
                                .datePeremption(datePeremption)
                                .coutUnitaire(coutUnitaire)
                                .statut(Lot.LotStatus.DISPONIBLE)
                                .build();

                Lot lotSauvegarde = lotRepository.save(lot);
                log.info("Lot créé: {} pour article: {}", numeroLot, article.getCodeArticle());

                return lotSauvegarde;
        }

        public boolean verifierDisponibilite(UUID articleId, UUID depotId, Integer quantiteRequise) {
                return stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                                .map(stock -> stock.getQuantiteDisponible() >= quantiteRequise)
                                .orElse(false);
        }

        public Integer getStockDisponible(UUID articleId, UUID depotId) {
                return stockRepository.findByArticleIdAndDepotId(articleId, depotId)
                                .map(Stock::getQuantiteDisponible)
                                .orElse(0);
        }

        /**
         * Get derniers mouvements
         */
        public List<StockMovement> getDerniersMouvements(int limit) {
                return stockMovementRepository.findTop10ByOrderByDateMouvementDesc()
                                .stream()
                                .limit(limit)
                                .collect(Collectors.toList());
        }

        /**
         * Get statistiques des mouvements
         */
        public Map<String, Object> getStatistiquesMouvements(int jours) {
                Map<String, Object> stats = new HashMap<>();

                LocalDateTime dateDebut = LocalDateTime.now().minusDays(jours);
                List<StockMovement> mouvements = stockMovementRepository.findByDateMouvementBetween(
                                dateDebut, LocalDateTime.now());

                long totalEntrees = mouvements.stream()
                                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                                .count();

                long totalSorties = mouvements.stream()
                                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
                                .count();

                // Calcul des valeurs monétaires
                BigDecimal valeurEntrees = mouvements.stream()
                                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.ENTREE)
                                .map(m -> m.getCoutUnitaire().multiply(BigDecimal.valueOf(m.getQuantite())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                BigDecimal valeurSorties = mouvements.stream()
                                .filter(m -> m.getType().getSens() == MovementType.SensMouvement.SORTIE)
                                .map(m -> m.getCoutUnitaire().multiply(BigDecimal.valueOf(m.getQuantite())))
                                .reduce(BigDecimal.ZERO, BigDecimal::add);

                // Calculs nécessaires pour le template
                long soldeQuantite = totalEntrees - totalSorties;
                BigDecimal soldeValeur = valeurEntrees.subtract(valeurSorties);

                // Calcul moyenne journalière
                long moyenneJournaliere = (long) Math.ceil((double) mouvements.size() / jours);

                // Calcul pic journalier
                long picJournalier = calculerPicJournalier(mouvements, jours);

                // Remplir la Map avec TOUTES les clés nécessaires pour le template
                stats.put("totalEntrees", totalEntrees);
                stats.put("totalSorties", totalSorties);
                stats.put("valeurEntrees", valeurEntrees);
                stats.put("valeurSorties", valeurSorties);
                stats.put("soldeQuantite", soldeQuantite); // requis ligne 114
                stats.put("soldeValeur", soldeValeur); // requis ligne 116
                stats.put("moyenneJournaliere", moyenneJournaliere); // requis ligne 130
                stats.put("picJournalier", picJournalier); // requis ligne 133

                // Ajouter d'autres statistiques utiles
                stats.put("totalMouvements", mouvements.size());
                stats.put("periodeJours", jours);

                return stats;
        }

        // Méthode pour calculer le pic journalier
        private long calculerPicJournalier(List<StockMovement> mouvements, int jours) {
                if (mouvements.isEmpty()) {
                        return 0L;
                }

                Map<LocalDate, Long> mouvementsParJour = mouvements.stream()
                                .collect(Collectors.groupingBy(
                                                mvt -> mvt.getDateMouvement().toLocalDate(),
                                                Collectors.counting()));

                return mouvementsParJour.values().stream()
                                .max(Long::compare)
                                .orElse(0L);
        }

        /**
         * Get répartition des mouvements par type
         */
        public Map<String, Long> getRepartitionMouvementsParType(int jours) {
                LocalDateTime dateDebut = LocalDateTime.now().minusDays(jours);
                List<StockMovement> mouvements = stockMovementRepository.findByDateMouvementBetween(
                                dateDebut, LocalDateTime.now());

                return mouvements.stream()
                                .collect(Collectors.groupingBy(
                                                mvt -> mvt.getType().getLibelle(),
                                                Collectors.counting()));
        }

        /**
         * Get informations détaillées sur un article
         */
        public Map<String, Object> getInformationsArticle(UUID articleId) {
                Map<String, Object> infos = new HashMap<>();

                Article article = articleRepository.findById(articleId)
                                .orElseThrow(() -> new RuntimeException("Article non trouvé"));

                // Stocks par dépôt
                List<Stock> stocks = stockRepository.findByArticleId(articleId);

                // Derniers mouvements
                List<StockMovement> derniersMouvements = stockMovementRepository
                                .findByArticleIdOrderByDateMouvementDesc(articleId)
                                .stream()
                                .limit(5)
                                .collect(Collectors.toList());

                // Lots disponibles
                List<Lot> lots = lotRepository.findByArticleIdAndStatutOrderByDatePeremptionAsc(
                                articleId, Lot.LotStatus.DISPONIBLE);

                infos.put("article", article);
                infos.put("stocksParDepot", stocks);
                infos.put("derniersMouvements", derniersMouvements);
                infos.put("lotsDisponibles", lots);
                infos.put("totalStock", stocks.stream().mapToInt(Stock::getQuantiteTheorique).sum());

                return infos;
        }

        /**
         * Get stock par dépôt
         */
        public List<Map<String, Object>> getStockParDepot(UUID depotId) {
                List<Stock> stocks = stockRepository.findByDepotId(depotId);

                return stocks.stream()
                                .map(stock -> {
                                        Map<String, Object> stockInfo = new HashMap<>();
                                        stockInfo.put("articleCode", stock.getArticle().getCodeArticle());
                                        stockInfo.put("articleLibelle", stock.getArticle().getLibelle());
                                        stockInfo.put("quantiteTheorique", stock.getQuantiteTheorique());
                                        stockInfo.put("quantitePhysique", stock.getQuantitePhysique());
                                        stockInfo.put("quantiteDisponible", stock.getQuantiteDisponible());
                                        stockInfo.put("valeurStock", stock.getValeurStockCump());
                                        stockInfo.put("dateDernierMouvement", stock.getDateDernierMouvement());
                                        return stockInfo;
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Get historique des mouvements d'un article
         */
        public List<StockMovement> getHistoriqueMouvementsArticle(UUID articleId, int limit) {
                return stockMovementRepository.findByArticleIdOrderByDateMouvementDesc(articleId)
                                .stream()
                                .limit(limit)
                                .collect(Collectors.toList());
        }
}