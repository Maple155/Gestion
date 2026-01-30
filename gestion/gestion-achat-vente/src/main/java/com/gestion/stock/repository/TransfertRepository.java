package com.gestion.stock.repository;

import com.gestion.stock.entity.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransfertRepository extends JpaRepository<Transfert, UUID> {
    
    Transfert findByReference(String reference);
    
    List<Transfert> findByDepotSourceId(UUID depotSourceId);
    
    List<Transfert> findByDepotDestinationId(UUID depotDestinationId);
    
    List<Transfert> findByStatut(Transfert.TransfertStatut statut);
    
    List<Transfert> findByDemandeurId(UUID demandeurId);
    
    List<Transfert> findByDateDemandeBetween(LocalDate debut, LocalDate fin);
    
    @Query("SELECT t FROM Transfert t WHERE t.depotSource.id = :depotId OR t.depotDestination.id = :depotId")
    List<Transfert> findByDepotId(@Param("depotId") UUID depotId);
    
    @Query("SELECT COUNT(t) FROM Transfert t WHERE t.statut = :statut")
    long countByStatut(@Param("statut") Transfert.TransfertStatut statut);
    
    @Query("SELECT COUNT(t) FROM Transfert t WHERE t.statut = 'RECEPTIONNE' " +
           "AND EXTRACT(MONTH FROM t.dateReceptionReelle) = :mois " +
           "AND EXTRACT(YEAR FROM t.dateReceptionReelle) = :annee")
    long countReceptionnesByMois(@Param("mois") int mois, @Param("annee") int annee);
}