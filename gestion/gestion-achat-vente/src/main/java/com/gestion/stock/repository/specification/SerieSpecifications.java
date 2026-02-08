package com.gestion.stock.repository.specification;

import com.gestion.stock.entity.Serie;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;
import java.util.UUID;

public class SerieSpecifications {
    
    public static Specification<Serie> hasNumeroSerie(String numeroSerie) {
        return (root, query, criteriaBuilder) -> {
            if (numeroSerie == null || numeroSerie.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            
            // Convertir bytea en text pour PostgreSQL
            Expression<String> numeroSerieExpr = criteriaBuilder.function(
                "convert_from",
                String.class,
                root.get("numeroSerie"),
                criteriaBuilder.literal("UTF8")
            );
            
            return criteriaBuilder.like(
                criteriaBuilder.lower(numeroSerieExpr),
                "%" + numeroSerie.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<Serie> hasArticleId(UUID articleId) {
        return (root, query, criteriaBuilder) -> {
            if (articleId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("article").get("id"), articleId);
        };
    }
    
    public static Specification<Serie> hasLotId(UUID lotId) {
        return (root, query, criteriaBuilder) -> {
            if (lotId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("lot").get("id"), lotId);
        };
    }
    
    public static Specification<Serie> hasStatut(String statut) {
        return (root, query, criteriaBuilder) -> {
            if (statut == null || statut.trim().isEmpty()) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("statut"), statut);
        };
    }
    
    public static Specification<Serie> fromCriteria(
            String numeroSerie,
            UUID articleId,
            UUID lotId,
            String statut) {
        
        Specification<Serie> spec = Specification.where(null);
        
        if (numeroSerie != null && !numeroSerie.trim().isEmpty()) {
            spec = spec.and(hasNumeroSerie(numeroSerie));
        }
        
        if (articleId != null) {
            spec = spec.and(hasArticleId(articleId));
        }
        
        if (lotId != null) {
            spec = spec.and(hasLotId(lotId));
        }
        
        if (statut != null && !statut.trim().isEmpty()) {
            spec = spec.and(hasStatut(statut));
        }
        
        return spec;
    }
}