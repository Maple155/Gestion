package com.gestion.stock.repository;

import com.gestion.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HistoriqueCoutRepository extends JpaRepository<HistoriqueCout, UUID> {
    
    List<HistoriqueCout> findByArticleIdAndDateEffetBetween(UUID articleId, LocalDate debut, LocalDate fin);
    
    Optional<HistoriqueCout> findByArticleIdAndDateEffet(UUID articleId, LocalDate dateEffet);
    
    @Query("SELECT h FROM HistoriqueCout h WHERE h.dateEffet = " +
           "(SELECT MAX(h2.dateEffet) FROM HistoriqueCout h2 WHERE h2.article.id = :articleId)")
    Optional<HistoriqueCout> findDernierCout(@Param("articleId") UUID articleId);
    
    List<HistoriqueCout> findByDateEffet(LocalDate date);

    List<HistoriqueCout> findByArticleIdAndDepotId(UUID articleId, UUID depotId);
    
    // Trouver par période
    List<HistoriqueCout> findByDateEffetBetween(LocalDate dateDebut, LocalDate dateFin);
    
    // Trouver par année et mois
    List<HistoriqueCout> findByAnneeAndMois(Integer annee, Integer mois);
    
    // Trouver par clôture mensuelle
    List<HistoriqueCout> findByClotureMensuelleId(UUID clotureMensuelleId);
    
    // Dernier historique pour un article/dépôt
    @Query("SELECT h FROM HistoriqueCout h WHERE h.article.id = :articleId AND h.depot.id = :depotId " +
           "ORDER BY h.dateEffet DESC LIMIT 1")
    Optional<HistoriqueCout> findDernierByArticleAndDepot(
            @Param("articleId") UUID articleId, 
            @Param("depotId") UUID depotId);
    
    // Coût unitaire à une date donnée
    @Query("SELECT h.coutUnitaireMoyen FROM HistoriqueCout h " +
           "WHERE h.article.id = :articleId AND h.depot.id = :depotId " +
           "AND h.dateEffet <= :dateEffet " +
           "ORDER BY h.dateEffet DESC LIMIT 1")
    Optional<BigDecimal> findCoutUnitaireAtDate(
            @Param("articleId") UUID articleId,
            @Param("depotId") UUID depotId,
            @Param("dateEffet") LocalDate dateEffet);
    
    // Évolution des coûts sur une période
    @Query("SELECT h FROM HistoriqueCout h " +
           "WHERE h.article.id = :articleId AND h.depot.id = :depotId " +
           "AND h.dateEffet BETWEEN :dateDebut AND :dateFin " +
           "ORDER BY h.dateEffet")
    List<HistoriqueCout> findEvolutionCouts(
            @Param("articleId") UUID articleId,
            @Param("depotId") UUID depotId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin);
    
    // Statistiques de valorisation par période
    @Query("SELECT " +
           "COUNT(h) as nbArticles, " +
           "SUM(h.valeurStock) as valeurTotale, " +
           "AVG(h.coutUnitaireMoyen) as coutMoyen, " +
           "MIN(h.dateEffet) as dateMin, " +
           "MAX(h.dateEffet) as dateMax " +
           "FROM HistoriqueCout h " +
           "WHERE h.annee = :annee AND h.mois = :mois")
    Object[] getStatistiquesPeriode(@Param("annee") Integer annee, @Param("mois") Integer mois);
    
    // Vérifier si une clôture existe pour une période
    boolean existsByAnneeAndMois(Integer annee, Integer mois);
    
    // Supprimer les historiques d'une période (en cas de rejet)
    @Transactional
    void deleteByAnneeAndMois(Integer annee, Integer mois);

    List<HistoriqueCout> findByArticleIdOrderByDateEffetDesc(UUID articleId);
    
    List<HistoriqueCout> findTop12ByArticleIdOrderByDateEffetDesc(UUID articleId);
    
    @Query("SELECT h FROM HistoriqueCout h WHERE h.article.id = :articleId " +
           "AND h.depot.id = :depotId ORDER BY h.dateEffet DESC")
    List<HistoriqueCout> findByArticleAndDepot(
            @Param("articleId") UUID articleId, 
            @Param("depotId") UUID depotId);
    
    @Query("SELECT h FROM HistoriqueCout h WHERE h.article.id = :articleId " +
           "AND h.depot.id = :depotId AND h.dateEffet <= :date " +
           "ORDER BY h.dateEffet DESC")
    Optional<HistoriqueCout> findDernierAvantDate(
            @Param("articleId") UUID articleId,
            @Param("depotId") UUID depotId,
            @Param("date") LocalDate date);
}