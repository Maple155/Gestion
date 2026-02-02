package com.gestion.stock.repository;

import com.gestion.stock.entity.MovementType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MovementTypeRepository extends JpaRepository<MovementType, UUID> {

    Optional<MovementType> findByCode(String code);

    Optional<MovementType> findByLibelle(String libelle);

    List<MovementType> findBySens(MovementType.SensMouvement sens);

    // Add a convenience method that accepts String
    default List<MovementType> findBySensString(String sens) {
        if (sens == null) {
            return findAll();
        }
        try {
            MovementType.SensMouvement sensEnum = MovementType.SensMouvement.valueOf(sens.toUpperCase());
            return findBySens(sensEnum);
        } catch (IllegalArgumentException e) {
            return List.of(); // Or throw a custom exception
        }
    }

    @Query("SELECT t FROM MovementType t ORDER BY t.libelle")
    List<MovementType> findAllOrderedByLibelle();

    @Query("SELECT t FROM MovementType t WHERE t.sens = :sens AND t.impactValorisation = :impact")
    List<MovementType> findBySensAndImpactValorisation(@Param("sens") String sens,
            @Param("impact") boolean impact);
}