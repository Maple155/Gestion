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

    // Ajoutez cette méthode manquante
    List<MovementType> findBySens(String sens);

    @Query("SELECT t FROM MovementType t ORDER BY t.libelle")
    List<MovementType> findAllOrderedByLibelle();

    @Query("SELECT t FROM MovementType t WHERE t.sens = :sens AND t.impactValorisation = :impact")
    List<MovementType> findBySensAndImpactValorisation(@Param("sens") String sens,
            @Param("impact") boolean impact);
}