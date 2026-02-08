package com.gestion.achat.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gestion.achat.entity.BonCommande;
import com.gestion.achat.enums.StatutFinance;
import com.gestion.achat.repository.BonCommandeRepository;

import lombok.RequiredArgsConstructor;
import java.util.List;
@Service
@RequiredArgsConstructor
public class BonCommandeService {

    private final BonCommandeRepository bonCommandeRepository;

    @Transactional
    public BonCommande updateStatutFinance(UUID id, StatutFinance nouveauStatut) {
        BonCommande bc = bonCommandeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Bon de commande introuvable"));

        // règles métier possibles
        if (bc.getStatutFinance() == StatutFinance.REJETEE) {
            throw new IllegalStateException("Un BC rejeté ne peut plus être modifié");
        }

        bc.setStatutFinance(nouveauStatut);
        return bonCommandeRepository.save(bc);
    }

    public BonCommande findById(UUID id){
        return bonCommandeRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Bdc introuvable"));
    }

    public List<BonCommande> findAll(){
        return bonCommandeRepository.findAll();
    }
}
