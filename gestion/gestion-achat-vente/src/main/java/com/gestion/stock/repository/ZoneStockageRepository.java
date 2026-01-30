package com.gestion.stock.repository;

import com.gestion.stock.entity.ZoneStockage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface ZoneStockageRepository extends JpaRepository<ZoneStockage, UUID> {
    
    List<ZoneStockage> findByDepotId(UUID depotId);
    
    List<ZoneStockage> findByDepotIdAndType(UUID depotId, String type);
}