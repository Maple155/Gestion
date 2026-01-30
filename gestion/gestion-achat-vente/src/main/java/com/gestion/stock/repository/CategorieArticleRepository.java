package com.gestion.stock.repository;

import com.gestion.stock.entity.CategorieArticle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategorieArticleRepository extends JpaRepository<CategorieArticle, UUID> {
    
    Optional<CategorieArticle> findByCode(String code);
    
    List<CategorieArticle> findByCategorieParentId(UUID parentId);
    
    List<CategorieArticle> findByActifTrue();

    @Query("SELECT c FROM CategorieArticle c WHERE c.actif = true ORDER BY c.libelle")
    List<CategorieArticle> findActiveCategories();
}