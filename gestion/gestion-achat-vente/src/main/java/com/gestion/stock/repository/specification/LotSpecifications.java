package com.gestion.stock.repository.specification;

import com.gestion.stock.entity.Lot;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.*;
import java.time.LocalDate;
import java.util.UUID;

public class LotSpecifications {

    public static Specification<Lot> hasNumeroLot(String numeroLot) {
        return (root, query, criteriaBuilder) -> {
            if (numeroLot == null || numeroLot.trim().isEmpty()) {
                return criteriaBuilder.conjunction(); // Retourne toujours true
            }
            
            // CAST explicite du champ numeroLot en String
            Expression<String> numeroLotExpr = root.get("numeroLot");
            return criteriaBuilder.like(
                criteriaBuilder.lower(numeroLotExpr),
                "%" + numeroLot.toLowerCase() + "%"
            );
        };
    }
    
    public static Specification<Lot> hasArticleId(UUID articleId) {
        return (root, query, criteriaBuilder) -> {
            if (articleId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("article").get("id"), articleId);
        };
    }
    
    public static Specification<Lot> hasStatut(Lot.LotStatus statut) {
        return (root, query, criteriaBuilder) -> {
            if (statut == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("statut"), statut);
        };
    }
    
    public static Specification<Lot> hasDepotId(UUID depotId) {
        return (root, query, criteriaBuilder) -> {
            if (depotId == null) {
                return criteriaBuilder.conjunction();
            }
            
            // Joindre les tables nécessaires
            Join<Object, Object> emplacement = root.join("emplacement", JoinType.LEFT);
            Join<Object, Object> zone = emplacement.join("zone", JoinType.LEFT);
            Join<Object, Object> depot = zone.join("depot", JoinType.LEFT);
            
            return criteriaBuilder.equal(depot.get("id"), depotId);
        };
    }
    
    public static Specification<Lot> hasZoneId(UUID zoneId) {
        return (root, query, criteriaBuilder) -> {
            if (zoneId == null) {
                return criteriaBuilder.conjunction();
            }
            
            Join<Object, Object> emplacement = root.join("emplacement", JoinType.LEFT);
            Join<Object, Object> zone = emplacement.join("zone", JoinType.LEFT);
            
            return criteriaBuilder.equal(zone.get("id"), zoneId);
        };
    }
    
    public static Specification<Lot> hasEmplacementId(UUID emplacementId) {
        return (root, query, criteriaBuilder) -> {
            if (emplacementId == null) {
                return criteriaBuilder.conjunction();
            }
            return criteriaBuilder.equal(root.get("emplacement").get("id"), emplacementId);
        };
    }
    
    public static Specification<Lot> datePeremptionBetween(LocalDate from, LocalDate to) {
        return (root, query, criteriaBuilder) -> {
            if (from == null && to == null) {
                return criteriaBuilder.conjunction();
            }
            
            Predicate predicate = criteriaBuilder.conjunction();
            
            if (from != null) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.greaterThanOrEqualTo(root.get("datePeremption"), from)
                );
            }
            
            if (to != null) {
                predicate = criteriaBuilder.and(
                    predicate,
                    criteriaBuilder.lessThanOrEqualTo(root.get("datePeremption"), to)
                );
            }
            
            return predicate;
        };
    }
    
    public static Specification<Lot> isProchePeremption(Integer joursAlerte) {
        return (root, query, criteriaBuilder) -> {
            if (joursAlerte == null) {
                return criteriaBuilder.conjunction();
            }
            
            LocalDate dateLimite = LocalDate.now().plusDays(joursAlerte);
            return criteriaBuilder.and(
                criteriaBuilder.lessThanOrEqualTo(root.get("datePeremption"), dateLimite),
                criteriaBuilder.greaterThan(root.get("quantiteActuelle"), 0),
                criteriaBuilder.equal(root.get("statut"), Lot.LotStatus.DISPONIBLE)
            );
        };
    }
    
    // Méthode utilitaire pour combiner tous les critères
    public static Specification<Lot> fromCriteria(
            String numeroLot,
            UUID articleId,
            UUID depotId,
            UUID zoneId,
            UUID emplacementId,
            Lot.LotStatus statut,
            LocalDate datePeremptionFrom,
            LocalDate datePeremptionTo,
            Boolean prochePeremption) {
        
        Specification<Lot> spec = Specification.where(null);
        
        if (numeroLot != null && !numeroLot.trim().isEmpty()) {
            spec = spec.and(hasNumeroLot(numeroLot));
        }
        
        if (articleId != null) {
            spec = spec.and(hasArticleId(articleId));
        }
        
        if (depotId != null) {
            spec = spec.and(hasDepotId(depotId));
        }
        
        if (zoneId != null) {
            spec = spec.and(hasZoneId(zoneId));
        }
        
        if (emplacementId != null) {
            spec = spec.and(hasEmplacementId(emplacementId));
        }
        
        if (statut != null) {
            spec = spec.and(hasStatut(statut));
        }
        
        spec = spec.and(datePeremptionBetween(datePeremptionFrom, datePeremptionTo));
        
        if (Boolean.TRUE.equals(prochePeremption)) {
            spec = spec.and(isProchePeremption(30)); // 30 jours par défaut
        }
        
        return spec;
    }
}