package com.gestion.stock.controller;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.gestion.stock.dto.UpdateReservationStatusRequest;
import com.gestion.stock.entity.ReservationStock;
import com.gestion.stock.service.ReservationStockService;
import org.springframework.ui.Model;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
@Controller
@RequestMapping("/stock/reservations")
@RequiredArgsConstructor
public class ReservationStockController {

    private final ReservationStockService service;

    @PostMapping("/{id}/statut")
    public String updateStatus(
            @PathVariable UUID id,
            @RequestParam("statut") ReservationStock.ReservationStatus statut) {
        ReservationStock updated = service.updateStatus(id, statut);
        return "home";
    }

    @GetMapping("{id}")
    public String detailReservation(@PathVariable UUID id, Model model) {
        ReservationStock mvt = service.findById(id);
        model.addAttribute("res", mvt);
        return "stock/reservations/reservations-detail";
    }
}
