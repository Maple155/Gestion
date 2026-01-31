// ArticleApiController.java
package com.gestion.stock.controller.article;

import com.gestion.stock.dto.ArticleDto;
import com.gestion.stock.entity.Article;
import com.gestion.stock.service.ArticleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
@Slf4j
public class ArticleApiController {
    
    private final ArticleService articleService;
    
    /**
     * Recherche d'articles pour autocomplétion
     */
    @GetMapping("/search")
    public ResponseEntity<List<ArticleDto>> searchArticles(
            @RequestParam String q,
            @RequestParam(defaultValue = "10") int limit) {
        
        log.info("Recherche articles: {}", q);
        
        List<Article> articles = articleService.searchArticles(q, limit);
        
        List<ArticleDto> dtos = articles.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Récupérer un article par ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ArticleDto> getArticleById(@PathVariable UUID id) {
        try {
            Article article = articleService.getArticleById(id);
            return ResponseEntity.ok(convertToDto(article));
        } catch (Exception e) {
            log.error("Article non trouvé: {}", id, e);
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Récupérer les articles actifs
     */
    @GetMapping("/actifs")
    public ResponseEntity<List<ArticleDto>> getArticlesActifs() {
        List<Article> articles = articleService.getArticlesActifs();
        
        List<ArticleDto> dtos = articles.stream()
                .map(this::convertToDto)
                .limit(20) // Limiter pour les besoins d'autocomplétion
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(dtos);
    }
    
    /**
     * Convertir Article en ArticleDto
     */
    private ArticleDto convertToDto(Article article) {
        return ArticleDto.builder()
                .id(article.getId())
                .codeArticle(article.getCodeArticle())
                .codeBarre(article.getCodeBarre())
                .libelle(article.getLibelle())
                .description(article.getDescription())
                .gestionParLot(article.isGestionParLot())
                .gestionParSerie(article.isGestionParSerie())
                .stockMinimum(article.getStockMinimum())
                .stockMaximum(article.getStockMaximum())
                .stockSecurite(article.getStockSecurite())
                .methodeValorisation(article.getMethodeValorisation())
                .coutStandard(article.getCoutStandard())
                .prixVenteHt(article.getPrixVenteHt())
                .tvaPourcentage(article.getTvaPourcentage())
                .actif(article.isActif())
                .obsolete(article.isObsolete())
                .createdAt(article.getCreatedAt())
                .updatedAt(article.getUpdatedAt())
                .createdBy(article.getCreatedBy())
                .updatedBy(article.getUpdatedBy())
                .build();
    }
}