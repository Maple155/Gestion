package com.gestion.stock.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SequenceGeneratorService {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public synchronized Long getNextMovementSequence() {
        return getNextSequence("seq_mouvement_stock");
    }
    
    @Transactional
    public synchronized Long getNextLotSequence() {
        return getNextSequence("seq_lot");
    }
    
    @Transactional
    public synchronized Long getNextReservationSequence() {
        return getNextSequence("seq_reservation_stock");
    }
    
    @Transactional
    public synchronized Long getNextTransfertSequence() {
        return getNextSequence("seq_transfert");
    }
    
    @Transactional
    public synchronized Long getNextInventaireSequence() {
        return getNextSequence("seq_inventaire");
    }

    @Transactional
    public synchronized Long getNextDevisVenteSequence() {
        return getNextSequence("seq_devis_vente");
    }

    @Transactional
    public synchronized Long getNextCommandeClientSequence() {
        return getNextSequence("seq_commande_client");
    }

    @Transactional
    public synchronized Long getNextLivraisonClientSequence() {
        return getNextSequence("seq_livraison_client");
    }

    @Transactional
    public synchronized Long getNextFactureVenteSequence() {
        return getNextSequence("seq_facture_vente");
    }

    @Transactional
    public synchronized Long getNextPaiementClientSequence() {
        return getNextSequence("seq_paiement_client");
    }

    @Transactional
    public synchronized Long getNextAvoirClientSequence() {
        return getNextSequence("seq_avoir_client");
    }
    
    private Long getNextSequence(String sequenceName) {
        try {
            Query query = entityManager.createNativeQuery(
                "SELECT nextval(:sequenceName)"
            );
            query.setParameter("sequenceName", sequenceName);
            return ((Number) query.getSingleResult()).longValue();
        } catch (Exception e) {
            // Si la séquence n'existe pas, la créer
            try {
                Query createQuery = entityManager.createNativeQuery(
                    "CREATE SEQUENCE " + sequenceName + " START 1"
                );
                createQuery.executeUpdate();
                
                // Retry getting the sequence
                Query query = entityManager.createNativeQuery(
                    "SELECT nextval(:sequenceName)"
                );
                query.setParameter("sequenceName", sequenceName);
                return ((Number) query.getSingleResult()).longValue();
            } catch (Exception ex) {
                // Fallback: utiliser timestamp
                return System.currentTimeMillis() % 1000000;
            }
        }
    }
    
    @Transactional
    public void initSequences() {
        String[] sequences = {
            "seq_mouvement_stock",
            "seq_lot", 
            "seq_reservation_stock",
            "seq_transfert",
            "seq_inventaire",
            "seq_devis_vente",
            "seq_commande_client",
            "seq_livraison_client",
            "seq_facture_vente",
            "seq_paiement_client",
            "seq_avoir_client"
        };
        
        for (String seq : sequences) {
            try {
                Query query = entityManager.createNativeQuery(
                    "CREATE SEQUENCE IF NOT EXISTS " + seq + " START 1"
                );
                query.executeUpdate();
            } catch (Exception e) {
                // Ignore si la séquence existe déjà
            }
        }
    }
}