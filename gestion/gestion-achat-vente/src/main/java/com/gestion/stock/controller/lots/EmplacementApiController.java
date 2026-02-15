package com.gestion.stock.controller.lots;

import com.gestion.stock.entity.Emplacement;
import com.gestion.stock.service.EmplacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/emplacements")
@RequiredArgsConstructor
public class EmplacementApiController {

    private final EmplacementService emplacementService;

    @GetMapping("/disponibles")
    public List<Emplacement> getEmplacementsDisponibles(
            @RequestParam UUID articleId,
            @RequestParam Integer quantity) {
        
        return emplacementService.findEmplacementsDisponibles(articleId, quantity);
    }
}