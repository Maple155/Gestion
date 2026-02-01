package com.gestion.stock.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.stock.dto.UpdateReservationStatusRequest;
import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.service.ReservationStockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationStockController {

    private final ReservationStockService service;

    @PutMapping("/{id}/statut")
    public ResponseEntity<ReservationStock> updateStatus(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateReservationStatusRequest request) {

        return ResponseEntity.ok(service.updateStatus(id, request.getStatut()));
    }
}
