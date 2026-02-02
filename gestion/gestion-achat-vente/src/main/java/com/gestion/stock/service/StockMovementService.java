package com.gestion.stock.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestion.stock.entity.StockMovement;
import com.gestion.stock.entity.StockMovement.MovementStatus;
import com.gestion.stock.repository.StockMovementRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class StockMovementService {

    private final StockMovementRepository repository;

    public List<StockMovement> getAll(){
        return repository.findAll();
    }

    public StockMovement findById(UUID id) {
    return repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Mouvement introuvable"));
}


    @Transactional
    public StockMovement updateStatus(UUID id, MovementStatus newStatus) {
        StockMovement movement = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Mouvement introuvable"));

        // règles métier
        if (movement.getStatut() == MovementStatus.ANNULE) {
            throw new IllegalStateException("Un mouvement annulé ne peut plus être modifié");
        }

        // exemple : BROUILLON -> VALIDE seulement
        if (movement.getStatut() == MovementStatus.VALIDE
                && newStatus == MovementStatus.BROUILLON) {
            throw new IllegalStateException("Impossible de revenir en brouillon");
        }

        movement.setStatut(newStatus);

        return repository.save(movement);
    }
}
