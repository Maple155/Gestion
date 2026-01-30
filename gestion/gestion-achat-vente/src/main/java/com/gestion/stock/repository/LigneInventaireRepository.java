package com.gestion.stock.repository;

import com.gestion.stock.entity.LigneInventaire;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
public interface LigneInventaireRepository extends JpaRepository<LigneInventaire, UUID> {
    
    List<LigneInventaire> findByInventaireId(UUID inventaireId);
    
    List<LigneInventaire> findByArticleId(UUID articleId);
    
    List<LigneInventaire> findByDepotId(UUID depotId);
    
    List<LigneInventaire> findByStatut(LigneInventaire.StatutLigneInventaire statut);
    
    @Query("SELECT li FROM LigneInventaire li WHERE li.inventaire.id = :inventaireId " +
           "AND li.ecart != 0")
    List<LigneInventaire> findLignesAvecEcart(@Param("inventaireId") UUID inventaireId);
    
    @Query("SELECT COUNT(li) FROM LigneInventaire li WHERE li.inventaire.id = :inventaireId " +
           "AND li.statut = 'VALIDE'")
    long countLignesValidees(@Param("inventaireId") UUID inventaireId);
    
    @Query("SELECT SUM(li.ecartValeur) FROM LigneInventaire li WHERE li.inventaire.id = :inventaireId")
    BigDecimal sumEcartValeurByInventaire(@Param("inventaireId") UUID inventaireId);
    
    @Query("SELECT li FROM LigneInventaire li WHERE li.inventaire.id = :inventaireId " +
           "AND ABS(li.ecartValeur) > :seuil")
    List<LigneInventaire> findLignesEcartSignificatif(@Param("inventaireId") UUID inventaireId,
                                                      @Param("seuil") BigDecimal seuil);
}