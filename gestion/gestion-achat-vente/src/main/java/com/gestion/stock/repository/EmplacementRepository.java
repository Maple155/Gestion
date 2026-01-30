package com.gestion.stock.repository;

import com.gestion.stock.entity.Emplacement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
}