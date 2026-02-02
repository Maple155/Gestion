package com.gestion.stock.repository;

import com.gestion.stock.entity.Inventaire;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface InventaireRepository extends JpaRepository<Inventaire, UUID> {

    Inventaire findByReference(String reference);

    List<Inventaire> findByDepotId(UUID depotId);

    List<Inventaire> findByStatut(Inventaire.StatutInventaire statut);

    List<Inventaire> findByResponsableId(UUID responsableId);

    List<Inventaire> findByDateDebutBetween(LocalDate debut, LocalDate fin);

    List<Inventaire> findByType(Inventaire.TypeInventaire type);

    @Query("SELECT i FROM Inventaire i WHERE i.statut IN ('EN_COURS', 'PLANIFIE')")
    List<Inventaire> findInventairesActifs();

    @Query("SELECT COUNT(i) FROM Inventaire i WHERE i.statut = 'CLOTURE' " +
            "AND EXTRACT(YEAR FROM i.dateCloture) = :annee")
    long countCloturesParAnnee(@Param("annee") int annee);

    // @Query("SELECT AVG(i.tauxPrecision) FROM Inventaire i WHERE i.statut =
    // 'CLOTURE'")
    // Double getTauxPrecisionMoyen();

    long countByStatut(Inventaire.StatutInventaire statut);

    // @Query("SELECT AVG(i.tauxPrecision) FROM Inventaire i WHERE i.tauxPrecision
    // IS NOT NULL")
    // BigDecimal calculateAveragePrecision();

    List<Inventaire> findTop1ByOrderByDateFinDesc();

    List<Inventaire> findTop5ByOrderByCreatedAtDesc();

    // @Query("SELECT COUNT(i) FROM Inventaire i WHERE EXTRACT(MONTH FROM
    // i.dateCloture) = :mois " +
    // "AND EXTRACT(YEAR FROM i.dateCloture) = :annee")
    // long countByMoisAndAnnee(@Param("mois") Integer mois, @Param("annee") Integer
    // annee);

    // @Query("SELECT AVG(i.tauxPrecision) FROM Inventaire i WHERE i.statut =
    // 'CLOTURE' " +
    // "AND EXTRACT(MONTH FROM i.dateCloture) = :mois " +
    // "AND EXTRACT(YEAR FROM i.dateCloture) = :annee")
    // BigDecimal calculateAveragePrecisionByMois(@Param("mois") Integer mois,
    // @Param("annee") Integer annee);

    @Query("SELECT i FROM Inventaire i WHERE " +
            "(:reference IS NULL OR i.reference LIKE %:reference%) AND " +
            "(:type IS NULL OR i.type = :type) AND " +
            "(:statut IS NULL OR i.statut = :statut) AND " +
            "(:depotId IS NULL OR i.depot.id = :depotId) AND " +
            "(:responsableId IS NULL OR i.responsable.id = :responsableId) AND " +
            "(:dateDebut IS NULL OR i.dateDebut >= :dateDebut) AND " +
            "(:dateFin IS NULL OR i.dateFin <= :dateFin) " +
            "ORDER BY i.dateDebut DESC")
    Page<Inventaire> findByFilters(
            @Param("reference") String reference,
            @Param("type") Inventaire.TypeInventaire type,
            // CORRECTION : Changer le type de String à Inventaire.StatutInventaire
            @Param("statut") Inventaire.StatutInventaire statut,
            @Param("depotId") UUID depotId,
            @Param("responsableId") UUID responsableId,
            @Param("dateDebut") LocalDate dateDebut,
            @Param("dateFin") LocalDate dateFin,
            Pageable pageable);

    @Query("SELECT i FROM Inventaire i WHERE i.statut = :statut " +
            "AND FUNCTION('MONTH', i.dateCloture) = :mois " +
            "AND FUNCTION('YEAR', i.dateCloture) = :annee")
    List<Inventaire> findByStatutAndDateCloture(@Param("statut") Inventaire.StatutInventaire statut,
            @Param("mois") Integer mois,
            @Param("annee") Integer annee);

    @Query("SELECT COUNT(i) FROM Inventaire i WHERE i.statut = :statut " +
            "AND FUNCTION('MONTH', i.dateCloture) = :mois " +
            "AND FUNCTION('YEAR', i.dateCloture) = :annee")
    Long countByStatutAndDateCloture(@Param("statut") Inventaire.StatutInventaire statut,
            @Param("mois") Integer mois,
            @Param("annee") Integer annee);

}