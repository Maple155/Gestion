package com.gestion.achat.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.gestion.achat.entity.BonReception;

@Repository
public interface BonReceptionRepository extends JpaRepository<BonReception, UUID> {
    List<BonReception> findAllByOrderByDateReceptionDesc();

    List<BonReception> findTop10ByOrderByDateReceptionDesc();
    
    List<BonReception> findAllByBonCommandeId(UUID bonCommandeId);

    List<BonReception> findByConforme(boolean conforme);

    @Query("SELECT br FROM BonReception br WHERE br.dateReception >= :dateDebut AND br.dateReception <= :dateFin")
    List<BonReception> findByDateReceptionBetween(@Param("dateDebut") java.time.LocalDateTime dateDebut,
            @Param("dateFin") java.time.LocalDateTime dateFin);

    @Query("SELECT COUNT(br) FROM BonReception br WHERE br.bonCommande.id = :bonCommandeId")
    long countByBonCommandeId(@Param("bonCommandeId") UUID bonCommandeId);

    Optional<BonReception> findFirstByBonCommandeIdOrderByDateReceptionDesc(UUID bonCommandeId);

    @Query("SELECT br FROM BonReception br " +
            "JOIN FETCH br.bonCommande bc " +
            "JOIN FETCH bc.proforma.fournisseur " +
            "WHERE br.dateReception >= :dateLimite " +
            "ORDER BY br.dateReception DESC")
    List<BonReception> findBonsReceptionRecents(@Param("dateLimite") LocalDateTime dateLimite);

    default List<BonReception> findBonsReceptionRecents() {
        return findBonsReceptionRecents(LocalDateTime.now().minusDays(30));
    }

    // Ajouter ces méthodes manquantes
    @Query("SELECT br FROM BonReception br ORDER BY br.dateReception DESC")
    List<BonReception> findTopNByOrderByDateReceptionDesc(@Param("limit") int limit);

    @Query("SELECT br FROM BonReception br " +
            "JOIN br.bonCommande bc " +
            "WHERE (:numeroBonCommande IS NULL OR bc.referenceBc LIKE %:numeroBonCommande%) " +
            "AND (:dateFrom IS NULL OR br.dateReception >= :dateFrom) " +
            "AND (:dateTo IS NULL OR br.dateReception <= :dateTo) " +
            "AND (:conforme IS NULL OR br.conforme = :conforme) " +
            "ORDER BY br.dateReception DESC")
    List<BonReception> search(
            @Param("numeroBonCommande") String numeroBonCommande,
            @Param("dateFrom") LocalDateTime dateFrom,
            @Param("dateTo") LocalDateTime dateTo,
            @Param("conforme") Boolean conforme);

    Optional<BonReception> findByBonCommandeId(UUID bonCommandeId);

    boolean existsByBonCommandeId(UUID id);
}
