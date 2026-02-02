package com.gestion.stock.controller.transferts;

import com.gestion.stock.dto.LigneTransfertDTO;
import com.gestion.stock.entity.*;
import com.gestion.stock.repository.*;
import com.gestion.stock.service.ArticleService;
import com.gestion.stock.service.TransfertService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/transferts")
@RequiredArgsConstructor
public class TransfertApiController {
    
    private final TransfertService transfertService;
    private final StockRepository stockRepository;
    private final LotRepository lotRepository;
    private final ArticleRepository articleRepository;
    private final TransfertRepository transfertRepository;
    private final LigneTransfertRepository ligneTransfertRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ArticleService articleService;

    /**
     * Vérifier disponibilité pour un transfert
     */
    @PostMapping("/verifier-disponibilite")
    public ResponseEntity<Map<String, Object>> verifierDisponibilite(
            @RequestBody Map<String, Object> request) {
        
        UUID depotSourceId = UUID.fromString((String) request.get("depotSourceId"));
        List<Map<String, Object>> lignes = (List<Map<String, Object>>) request.get("lignes");
        
        List<LigneTransfertDTO> lignesDTO = lignes.stream()
            .map(map -> LigneTransfertDTO.builder()
                .article(articleService.getArticleById(UUID.fromString((String) map.get("articleId"))))
                .quantiteDemandee(Integer.parseInt(map.get("quantiteDemandee").toString()))
                .build())
            .collect(Collectors.toList());
        
        Map<UUID, Integer> indisponibilites = 
            transfertService.verifierDisponibiliteTransfert(depotSourceId, lignesDTO);
        
        Map<String, Object> response = new HashMap<>();
        response.put("indisponibilites", indisponibilites);
        response.put("allAvailable", indisponibilites.isEmpty());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Obtenir les articles disponibles dans un dépôt
     */
    @GetMapping("/depot/{depotId}/articles-disponibles")
    public ResponseEntity<List<Map<String, Object>>> getArticlesDisponibles(
            @PathVariable UUID depotId) {
        
        List<Stock> stocks = stockRepository.findByDepotIdAndQuantiteDisponibleGreaterThan(depotId, 0);
        
        List<Map<String, Object>> articles = stocks.stream()
            .map(stock -> {
                Map<String, Object> articleInfo = new HashMap<>();
                articleInfo.put("id", stock.getArticle().getId());
                articleInfo.put("codeArticle", stock.getArticle().getCodeArticle());
                articleInfo.put("libelle", stock.getArticle().getLibelle());
                articleInfo.put("categorieId", stock.getArticle().getCategorie().getId());
                articleInfo.put("categorie", stock.getArticle().getCategorie().getLibelle());
                articleInfo.put("stockDisponible", stock.getQuantiteDisponible());
                articleInfo.put("stockMinimum", stock.getArticle().getStockMinimum());
                articleInfo.put("uniteMesure", stock.getArticle().getUniteMesure().getCode());
                articleInfo.put("coutUnitaire", stock.getCoutUnitaireMoyen());
                return articleInfo;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(articles);
    }
    
    /**
     * Obtenir les lots disponibles pour un article dans un dépôt
     */
    @GetMapping("/articles/{articleId}/depots/{depotId}/lots-disponibles")
    public ResponseEntity<List<Map<String, Object>>> getLotsDisponibles(
            @PathVariable UUID articleId, 
            @PathVariable UUID depotId) {
        
        List<Lot> lots = lotRepository.findByArticleIdAndDepotIdAndStatut(
            articleId, depotId, Lot.LotStatus.DISPONIBLE);
        
        List<Map<String, Object>> lotsInfo = lots.stream()
            .map(lot -> {
                Map<String, Object> info = new HashMap<>();
                info.put("id", lot.getId());
                info.put("numeroLot", lot.getNumeroLot());
                info.put("quantiteActuelle", lot.getQuantiteActuelle());
                info.put("datePeremption", lot.getDatePeremption());
                info.put("coutUnitaire", lot.getCoutUnitaire());
                info.put("statut", lot.getStatut());
                return info;
            })
            .collect(Collectors.toList());
        
        return ResponseEntity.ok(lotsInfo);
    }
    
    /**
     * Obtenir le détail d'un transfert
     */
    @GetMapping("/{transfertId}/details")
    public ResponseEntity<Map<String, Object>> getTransfertDetails(
            @PathVariable UUID transfertId) {
        
        Transfert transfert = transfertRepository.findById(transfertId)
            .orElseThrow(() -> new RuntimeException("Transfert non trouvé"));
        
        List<LigneTransfert> lignes = ligneTransfertRepository.findByTransfertId(transfertId);
        
        Map<String, Object> details = new HashMap<>();
        details.put("transfert", transfert);
        details.put("lignes", lignes);
        
        // Récupérer les mouvements associés
        List<StockMovement> mouvements = stockMovementRepository.findByTransfertId(transfertId);
        details.put("mouvements", mouvements);
        
        return ResponseEntity.ok(details);
    }
}