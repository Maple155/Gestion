package com.gestion.stock.repository;

import com.gestion.stock.entity.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ArticleRepository extends JpaRepository<Article, UUID> {

        Optional<Article> findByCodeArticle(String codeArticle);

        Optional<Article> findByCodeBarre(String codeBarre);

        List<Article> findByCategorieId(UUID categorieId);

        List<Article> findByActifTrue();

        @Query("SELECT a FROM Article a WHERE LOWER(a.codeArticle) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(a.libelle) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(a.codeBarre) LIKE LOWER(CONCAT('%', :search, '%'))")
        List<Article> searchByCodeOrLibelleOrCodeBarre(@Param("search") String search);

        @Query("SELECT COUNT(a) FROM Article a WHERE a.actif = true")
        long countActifs();

        @Query("SELECT a FROM Article a WHERE a.gestionParLot = true AND a.actif = true")
        List<Article> findArticlesGestionParLot();

        @Query("SELECT a FROM Article a WHERE a.methodeValorisation = :methode")
        List<Article> findByMethodeValorisation(@Param("methode") String methode);

        @Query("SELECT a FROM Article a WHERE " +
                        "(:search IS NULL OR LOWER(a.codeArticle) LIKE :search " +
                        " OR LOWER(a.libelle) LIKE :search " +
                        " OR LOWER(a.codeBarre) LIKE :search) " +
                        "AND (:categorieId IS NULL OR a.categorie.id = :categorieId) " +
                        "AND (:actif IS NULL OR a.actif = :actif) " +
                        "AND (:gestionParLot IS NULL OR a.gestionParLot = :gestionParLot)")
        Page<Article> rechercherAvecFiltres(
                        @Param("search") String search,
                        @Param("categorieId") UUID categorieId,
                        @Param("actif") Boolean actif,
                        @Param("gestionParLot") Boolean gestionParLot,
                        Pageable pageable);

        boolean existsByCodeArticle(String codeArticle);

        long countByActifTrue();

        long countByGestionParLotTrue();

        long countByGestionParSerieTrue();

        @Query("SELECT a FROM Article a " +
                        "LEFT JOIN FETCH a.categorie " +
                        "WHERE a.gestionParLot = true " +
                        "AND a.actif = true")
        List<Article> findArticlesAvecGestionLot();

        @Query("SELECT a FROM Article a " +
                        "WHERE (LOWER(a.codeArticle) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(a.libelle) LIKE LOWER(CONCAT('%', :search, '%')) " +
                        "OR LOWER(a.codeBarre) LIKE LOWER(CONCAT('%', :search, '%'))) " +
                        "AND a.actif = true " +
                        "ORDER BY a.libelle")
        List<Article> searchActifs(@Param("search") String search);
}