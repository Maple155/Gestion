package com.gestion.stock.repository;

import com.gestion.stock.entity.Emplacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmplacementRepository extends JpaRepository<Emplacement, UUID> {

    List<Emplacement> findByZoneId(UUID zoneId);

    List<Emplacement> findByActifTrue();

    List<Emplacement> findByZoneDepotId(UUID depotId);

    @Query("SELECT e FROM Emplacement e WHERE e.actif = true ORDER BY e.code")
    List<Emplacement> findEmplacementsDisponibles();

    @Query("SELECT e FROM Emplacement e WHERE e.zone.id = :zoneId AND e.code = :code")
    boolean existsByZoneIdAndCode(@Param("zoneId") UUID zoneId, @Param("code") String code);

    @Query("SELECT e FROM Emplacement e WHERE e.actif = true " +
            "AND NOT EXISTS (SELECT 1 FROM Lot l WHERE l.emplacement.id = e.id AND l.quantiteActuelle > 0)")
    List<Emplacement> findEmplacementsVides();
}