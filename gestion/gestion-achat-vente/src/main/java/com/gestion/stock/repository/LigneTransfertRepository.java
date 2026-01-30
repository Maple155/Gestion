package com.gestion.stock.repository;

import com.gestion.stock.entity.LigneTransfert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface LigneTransfertRepository extends JpaRepository<LigneTransfert, UUID> {
    
    List<LigneTransfert> findByTransfertId(UUID transfertId);
    
    List<LigneTransfert> findByArticleId(UUID articleId);
    
    List<LigneTransfert> findByLotId(UUID lotId);
    
    void deleteByTransfertId(UUID transfertId);
}