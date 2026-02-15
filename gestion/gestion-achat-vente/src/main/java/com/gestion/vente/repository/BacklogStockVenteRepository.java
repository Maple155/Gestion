package com.gestion.vente.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gestion.vente.entity.BacklogStockVente;

public interface BacklogStockVenteRepository extends JpaRepository<BacklogStockVente, UUID> {
	List<BacklogStockVente> findByArticleIdAndStatut(UUID articleId, String statut);
	List<BacklogStockVente> findByDemandeAchatIdAndStatut(UUID demandeAchatId, String statut);
}
