// ClotureMensuelleRepository.java
package com.gestion.stock.repository;

import com.gestion.stock.entity.ClotureMensuelle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ClotureMensuelleRepository extends JpaRepository<ClotureMensuelle, UUID> {

       Optional<ClotureMensuelle> findByAnneeAndMois(Integer annee, Integer mois);

       List<ClotureMensuelle> findByAnnee(Integer annee);

       List<ClotureMensuelle> findByStatut(ClotureMensuelle.StatutCloture statut);

       // Dernière clôture validée
       @Query("SELECT c FROM ClotureMensuelle c WHERE c.statut = 'VALIDEE' " +
                     "ORDER BY c.annee DESC, c.mois DESC LIMIT 1")
       Optional<ClotureMensuelle> findDerniereClotureValidee();

       // Périodes ouvertes (non clôturées)
       @Query("SELECT c FROM ClotureMensuelle c WHERE c.statut IN ('OUVERTE', 'REJETEE') " +
                     "ORDER BY c.annee, c.mois")
       List<ClotureMensuelle> findPeriodesOuvertes();

       // Vérifier les périodes manquantes
       @Query(value = "SELECT generate_series AS mois " +
                     "FROM generate_series(1, 12) " +
                     "WHERE generate_series NOT IN ( " +
                     "    SELECT mois FROM clotures_mensuelles WHERE annee = :annee AND statut = 'VALIDEE'" +
                     ") ORDER BY 1", nativeQuery = true)
       List<Integer> findMoisManquantsPourAnnee(@Param("annee") Integer annee);

       // Calculer le nombre de clôtures par statut
       @Query("SELECT c.statut, COUNT(c) FROM ClotureMensuelle c GROUP BY c.statut")
       List<Object[]> countByStatut();

       // Prochaine période à clôturer (la plus ancienne ouverte)
       @Query("SELECT c FROM ClotureMensuelle c WHERE c.statut IN ('OUVERTE', 'REJETEE') " +
                     "ORDER BY c.annee ASC, c.mois ASC LIMIT 1")
       Optional<ClotureMensuelle> findProchainePeriodeACloturer();

       // Historique des clôtures avec filtres
       @Query("SELECT c FROM ClotureMensuelle c WHERE " +
                     "(:annee IS NULL OR c.annee = :annee) AND " +
                     "(:statut IS NULL OR c.statut = :statut) " +
                     "ORDER BY c.annee DESC, c.mois DESC")
       List<ClotureMensuelle> rechercherAvecFiltres(
                     @Param("annee") Integer annee,
                     @Param("statut") ClotureMensuelle.StatutCloture statut);

       @Query("SELECT c FROM ClotureMensuelle c ORDER BY c.dateCloture DESC")
       List<ClotureMensuelle> findTopNByOrderByDateClotureDesc(@Param("limit") int limit);

       @Query("SELECT c FROM ClotureMensuelle c WHERE c.dateDebutPeriode BETWEEN :debut AND :fin ORDER BY c.dateDebutPeriode")
       List<ClotureMensuelle> findByDateDebutPeriodeBetween(@Param("debut") LocalDate debut,
                     @Param("fin") LocalDate fin);

}