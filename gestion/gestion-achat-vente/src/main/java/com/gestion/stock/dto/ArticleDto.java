package com.gestion.stock.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import jakarta.persistence.Column;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ArticleDto {
    private UUID id;
    private String codeArticle;
    private String codeBarre;
    private String libelle;
    private String description;
    private boolean gestionParLot;
    private boolean gestionParSerie;
    private Integer stockMinimum;
    private Integer stockMaximum;
    private Integer stockSecurite;
    private String methodeValorisation;
    private BigDecimal coutStandard;
    private BigDecimal prixVenteHt;
    private BigDecimal tvaPourcentage;
    private boolean actif;
    private boolean obsolete;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private UUID createdBy;
    private UUID updatedBy;
}