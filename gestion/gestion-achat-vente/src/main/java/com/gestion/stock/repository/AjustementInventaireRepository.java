package com.gestion.stock.repository;

import com.gestion.stock.entity.AjustementInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AjustementInventaireRepository extends JpaRepository<AjustementInventaire, UUID> {

       List<AjustementInventaire> findByLigneInventaireId(UUID ligneInventaireId);

       List<AjustementInventaire> findByValideurId(UUID valideurId);

       @Query("SELECT a FROM AjustementInventaire a WHERE a.dateValidation >= :debut " +
                     "AND a.dateValidation <= :fin")
       List<AjustementInventaire> findByDateValidationBetween(@Param("debut") LocalDateTime debut,
                     @Param("fin") LocalDateTime fin);

       @Query("SELECT SUM(a.valeurAjustement) FROM AjustementInventaire a " +
                     "WHERE a.dateValidation >= :debut AND a.dateValidation <= :fin")
       BigDecimal sumValeurAjustements(@Param("debut") LocalDateTime debut,
                     @Param("fin") LocalDateTime fin);

       List<AjustementInventaire> findByLigneInventaireInventaireId(UUID inventaireId);

       default List<AjustementInventaire> findByInventaireId(UUID inventaireId) {
              return findByLigneInventaireInventaireId(inventaireId);
       }

       @Query("SELECT a FROM AjustementInventaire a " +
                     "WHERE a.ligneInventaire.inventaire.id = :inventaireId " +
                     "ORDER BY a.dateValidation DESC")
       List<AjustementInventaire> findByInventaireIdOrderByDate(@Param("inventaireId") UUID inventaireId);

       List<AjustementInventaire> findByRequiertDoubleValidationTrueAndDateSecondValidationIsNull();
}