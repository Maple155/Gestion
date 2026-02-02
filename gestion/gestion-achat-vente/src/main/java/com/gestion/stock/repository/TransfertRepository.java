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

        @Query("SELECT t FROM Transfert t WHERE " +
                        "(:statut IS NULL OR t.statut = :statutEnum) AND " +
                        "(:depotSourceId IS NULL OR t.depotSource.id = :depotSourceId) AND " +
                        "(:depotDestinationId IS NULL OR t.depotDestination.id = :depotDestinationId) AND " +
                        "(:dateDebut IS NULL OR CAST(t.dateDemande AS date) >= :dateDebut) AND " +
                        "(:dateFin IS NULL OR CAST(t.dateDemande AS date) <= :dateFin) " +
                        "ORDER BY t.dateDemande DESC")
        List<Transfert> findWithFilters(
                        @Param("statutEnum") Transfert.TransfertStatut statutEnum,
                        @Param("depotSourceId") UUID depotSourceId,
                        @Param("depotDestinationId") UUID depotDestinationId,
                        @Param("dateDebut") LocalDate dateDebut,
                        @Param("dateFin") LocalDate dateFin);

        @Query("SELECT t FROM Transfert t WHERE " +
                        "MONTH(t.dateDemande) = :mois AND YEAR(t.dateDemande) = :annee " +
                        "ORDER BY t.dateDemande DESC")
        List<Transfert> findByMonth(@Param("mois") Integer mois, @Param("annee") Integer annee);

        @Query("SELECT t.depotSource, COUNT(t) as nombreTransferts " +
                        "FROM Transfert t WHERE " +
                        "MONTH(t.dateDemande) = :mois AND YEAR(t.dateDemande) = :annee " +
                        "GROUP BY t.depotSource " +
                        "ORDER BY COUNT(t) DESC")
        List<Object[]> findTopDepotsSources(@Param("mois") Integer mois, @Param("annee") Integer annee);

        @Query(value = "SELECT nextval('seq_transfert')", nativeQuery = true)
        Long getNextSequenceValue();

        List<Transfert> findByDepotSourceIdAndDateDemandeBetween(UUID depotId, LocalDate dateDebut, LocalDate dateFin);

        List<Transfert> findByDepotDestinationIdAndDateReceptionReelleBetween(UUID depotId, LocalDate dateDebut,
                        LocalDate dateFin);

        List<Transfert> findByStatutAndDateExpeditionBefore(Transfert.TransfertStatut statut, LocalDate date);
}