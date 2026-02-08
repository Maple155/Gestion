package com.gestion.stock.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.gestion.achat.entity.BonReception;
import com.gestion.stock.entity.Article;
import com.gestion.stock.entity.Emplacement;
import com.gestion.stock.entity.Lot;
import com.gestion.stock.entity.Serie.SerieStatus;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SerieDTO {
    private UUID id;
    private String numeroSerie; 
    private Article article;
    private Lot lot;
    private SerieStatus statut;
    private BonReception bonReception;
    private LocalDate dateReception;
    private UUID commandeClientId;
    private LocalDate dateSortie;
    private Emplacement emplacement;
    private LocalDateTime createdAt;
}
