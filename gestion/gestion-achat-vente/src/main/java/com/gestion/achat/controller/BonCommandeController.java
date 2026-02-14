package com.gestion.achat.controller;

import java.util.UUID;
import java.text.DecimalFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.service.BonCommandeService;
import org.springframework.stereotype.Controller;
import lombok.RequiredArgsConstructor;
import java.util.List;
import com.gestion.achat.enums.*;
@Controller
@RequestMapping("/achat/bons")
@RequiredArgsConstructor
public class BonCommandeController {

    private final BonCommandeService bonCommandeService;
     private final DecimalFormat decimalFormat = new DecimalFormat("#,##0.00");
    @GetMapping("/all")
    public String listBonsCommande(Model model) {
        List<BonCommande> bdcList = bonCommandeService.findAll();
        bdcList.forEach(bdc -> {
            if (bdc.getMontantTotalTtc() != null) {
                bdc.setMontantFormate(decimalFormat.format(bdc.getMontantTotalTtc()));
            } else {
                bdc.setMontantFormate("0.00");
            }
        });
        model.addAttribute("bdc", bdcList);
        return "home";
    }
    @PostMapping("/{id}/statut-finance")
    public String updateStatutFinance(
            @PathVariable UUID id,
            @RequestParam("statutFinance") StatutFinance request) {

        return "home";
    }
    @GetMapping("/{id}")
    public String detailBonCommande(@PathVariable UUID id, Model model) {
        BonCommande bdc = bonCommandeService.findById(id);
        if (bdc.getMontantTotalTtc() != null) {
            bdc.setMontantFormate(decimalFormat.format(bdc.getMontantTotalTtc()));
        } else {
            bdc.setMontantFormate("0.00");
        }
        model.addAttribute("bdc", bdc);
        return "achat/bon-commande-detail";
    }
}
