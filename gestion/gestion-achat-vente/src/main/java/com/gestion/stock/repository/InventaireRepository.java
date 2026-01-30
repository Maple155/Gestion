package com.gestion.stock.repository;

import com.gestion.stock.entity.Inventaire;
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
    
    @Query("SELECT AVG(i.tauxPrecision) FROM Inventaire i WHERE i.statut = 'CLOTURE'")
    Double getTauxPrecisionMoyen();

    long countByStatut(Inventaire.StatutInventaire statut);
    
    @Query("SELECT AVG(i.tauxPrecision) FROM Inventaire i WHERE i.tauxPrecision IS NOT NULL")
    BigDecimal calculateAveragePrecision();

    List<Inventaire> findTop1ByOrderByDateFinDesc();
}