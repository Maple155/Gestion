package com.gestion.stock.repository;

import com.gestion.stock.entity.LigneTransfert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.UUID;

@Repository
public interface LigneTransfertRepository extends JpaRepository<LigneTransfert, UUID> {

    List<LigneTransfert> findByTransfertId(UUID transfertId);

    List<LigneTransfert> findByArticleId(UUID articleId);

    List<LigneTransfert> findByLotId(UUID lotId);

    void deleteByTransfertId(UUID transfertId);

    @Query("SELECT lt FROM LigneTransfert lt WHERE lt.transfert.id = :transfertId AND lt.article.id = :articleId")
    List<LigneTransfert> findByTransfertIdAndArticleId(@Param("transfertId") UUID transfertId,
            @Param("articleId") UUID articleId);

    @Query("SELECT SUM(lt.quantiteDemandee) FROM LigneTransfert lt " +
            "WHERE lt.article.id = :articleId AND lt.transfert.depotSource.id = :depotId " +
            "AND lt.transfert.statut IN ('VALIDE', 'EXPEDIE')")
    Integer getQuantiteEnTransfert(@Param("articleId") UUID articleId,
            @Param("depotId") UUID depotId);
}