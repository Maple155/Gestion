package com.gestion.stock.repository;

import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.Serie;
import com.gestion.stock.repository.specification.SerieSpecifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SerieRepository extends JpaRepository<Serie, UUID>, JpaSpecificationExecutor<Serie> {

    Optional<Serie> findByNumeroSerie(String numeroSerie);

    List<Serie> findByArticleId(UUID articleId);

    List<Serie> findByLotId(UUID lotId);

    List<Serie> findByStatut(String statut);

    @Query("SELECT s FROM Serie s WHERE s.article.id = :articleId AND s.statut = 'EN_STOCK'")
    List<Serie> findSeriesDisponiblesByArticle(@Param("articleId") UUID articleId);

    boolean existsByNumeroSerie(String numeroSerie);

   default Page<Serie> search(String numeroSerie, UUID articleId, UUID lotId, 
                               String statut, Pageable pageable) {
        return findAll(SerieSpecifications.fromCriteria(numeroSerie, articleId, lotId, statut), pageable);
    }

    @Query("SELECT COUNT(s) > 0 FROM Serie s WHERE s.lot.id = :lotId")
    boolean existsByLotId(@Param("lotId") UUID lotId);
}
