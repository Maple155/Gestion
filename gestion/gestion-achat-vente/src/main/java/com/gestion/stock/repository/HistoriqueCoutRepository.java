package com.gestion.stock.repository;

import com.gestion.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoriqueCoutRepository extends JpaRepository<HistoriqueCout, UUID> {
    
    List<HistoriqueCout> findByArticleIdAndDateEffetBetween(UUID articleId, LocalDate debut, LocalDate fin);
    
    Optional<HistoriqueCout> findByArticleIdAndDateEffet(UUID articleId, LocalDate dateEffet);
    
    @Query("SELECT h FROM HistoriqueCout h WHERE h.dateEffet = " +
           "(SELECT MAX(h2.dateEffet) FROM HistoriqueCout h2 WHERE h2.articleId = :articleId)")
    Optional<HistoriqueCout> findDernierCout(@Param("articleId") UUID articleId);
    
    List<HistoriqueCout> findByDateEffet(LocalDate date);
}