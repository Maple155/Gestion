package com.gestion.stock.job;

import com.gestion.stock.entity.Transfert;
import com.gestion.stock.repository.TransfertRepository;
import com.gestion.stock.service.TransfertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TransfertJob {
    
    private final TransfertRepository transfertRepository;
    private final TransfertService transfertService;
    
    /**
     * Vérifier les transferts expédiés non réceptionnés depuis longtemps
     * Exécuté tous les jours à 8h
     */
    @Scheduled(cron = "0 0 8 * * *") // Tous les jours à 8h
    public void checkTransfertsEnRetard() {
        log.info("Vérification des transferts en retard");
        
        // Transferts expédiés depuis plus de 7 jours non réceptionnés
        LocalDateTime dateLimite = LocalDateTime.now().minusDays(7);
        
        List<Transfert> transfertsEnRetard = transfertRepository
            .findByStatutAndDateExpeditionBefore(
                Transfert.TransfertStatut.EXPEDIE, 
                dateLimite.toLocalDate());
        
        for (Transfert transfert : transfertsEnRetard) {
            log.warn("Transfert {} expédié le {} non réceptionné", 
                    transfert.getReference(), transfert.getDateExpedition());
            
            // Envoyer notification au responsable
            // notificationService.sendTransfertRetardNotification(transfert);
        }
    }
    
    /**
     * Notification des transferts à expédier aujourd'hui
     * Exécuté tous les jours à 9h
     */
    @Scheduled(cron = "0 0 9 * * *") // Tous les jours à 9h
    public void notifyTransfertsAExpedier() {
        log.info("Notification des transferts à expédier");
        
        List<Transfert> transfertsAExpedier = transfertRepository
            .findByStatut(Transfert.TransfertStatut.VALIDE);
        
        for (Transfert transfert : transfertsAExpedier) {
            // Vérifier si la date de réception prévue est proche
            if (transfert.getDateReceptionPrevue() != null &&
                transfert.getDateReceptionPrevue().isBefore(LocalDateTime.now().plusDays(2).toLocalDate())) {
                
                log.info("Transfert {} doit être expédié rapidement", transfert.getReference());
                // notificationService.sendTransfertExpeditionReminder(transfert);
            }
        }
    }
}