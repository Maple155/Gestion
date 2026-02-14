package com.gestion.stock.repository;

import com.gestion.stock.entity.Depot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DepotRepository extends JpaRepository<Depot, UUID> {
    
    Optional<Depot> findByCode(String code);
    
    List<Depot> findBySiteId(UUID siteId);
    
    List<Depot> findByActifTrue();
    
    List<Depot> findByType(String type);

    default List<Depot> findDepotsActifs() {
        return findByActifTrue();
    }

    // Trouver les dépôts actifs avec leurs sites
    @Query("SELECT d FROM Depot d JOIN FETCH d.site WHERE d.actif = true ORDER BY d.nom")
    List<Depot> findDepotsActifsAvecSite();
    
    // Compter les dépôts actifs
    Long countByActifTrue();
    
    // Vérifier si un code existe déjà
    boolean existsByCode(String code);

    List<Depot> findAllByActifTrue();
    
    Optional<Depot> findByActifTrueAndId(UUID id);
}