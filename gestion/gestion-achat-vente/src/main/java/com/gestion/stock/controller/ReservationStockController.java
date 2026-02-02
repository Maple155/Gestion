package com.gestion.stock.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.stock.dto.UpdateReservationStatusRequest;
import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.service.ReservationStockService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/api/reservations")
@RequiredArgsConstructor
public class ReservationStockController {

    private final ReservationStockService service;

    @PutMapping("/{id}/statut")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam("statut") ReservationStock.ReservationStatus statut) {
        ReservationStock updated = service.updateStatus(id, request.getStatut());
        return "home";
    }
}
