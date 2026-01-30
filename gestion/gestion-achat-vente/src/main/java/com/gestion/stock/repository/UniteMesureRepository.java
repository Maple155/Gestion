package com.gestion.stock.repository;

import com.gestion.stock.entity.UniteMesure;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UniteMesureRepository extends JpaRepository<UniteMesure, UUID> {
    
    Optional<UniteMesure> findByCode(String code);
    
    List<UniteMesure> findByType(String type);

    @Query("SELECT u FROM UniteMesure u ORDER BY u.code")
    List<UniteMesure> findAllUnites();
}