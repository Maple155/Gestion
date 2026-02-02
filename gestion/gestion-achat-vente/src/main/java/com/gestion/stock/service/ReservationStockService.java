package com.gestion.stock.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.entity.ReservationStock.ReservationStatus;
import com.gestion.stock.repository.ReservationStockRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ReservationStockService {

    private final ReservationStockRepository repository;

    @Transactional
    public ReservationStock updateStatus(UUID id, ReservationStatus newStatus) {
        ReservationStock res = repository.findById(id)
                .orElseThrow(() -> new RuntimeException("Réservation introuvable"));

        if (res.getStatut() != ReservationStatus.ACTIVE) {
            throw new IllegalStateException("Réservation déjà finalisée");
        }

        res.setStatut(newStatus);
        return repository.save(res);
    }
}
