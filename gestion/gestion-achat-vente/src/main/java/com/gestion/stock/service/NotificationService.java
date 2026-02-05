package com.gestion.stock.service;

import com.gestion.stock.entity.*;
import com.gestion.stock.repository.UtilisateurRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final LotService lotService;
    private final StockService stockService;
    private final UtilisateurRepository utilisateurRepository;

    /**
     * Types de notifications
     */
    public enum NotificationType {
        STOCK_CRITIQUE("Stock critique", "danger"),
        STOCK_OBSOLETE("Stock obsolète", "warning"),
        LOT_PEREMPTION_PROCH("Lot proche péremption", "warning"),
        LOT_PERIME("Lot périmé", "danger"),
        COMMANDE_EN_RETARD("Commande en retard", "warning"),
        INVENTAIRE_PLANIFIE("Inventaire planifié", "info"),
        AJUSTEMENT_A_VALIDER("Ajustement à valider", "warning"),
        TRANSFERT_A_EXPEDIER("Transfert à expédier", "info");
        
        private final String libelle;
        private final String niveau;
        
        NotificationType(String libelle, String niveau) {
            this.libelle = libelle;
            this.niveau = niveau;
        }
        
        public String getLibelle() { return libelle; }
        public String getNiveau() { return niveau; }
    }

    /**
     * Envoyer une notification
     */
    public void envoyerNotification(NotificationType type, String message, 
                                   List<UUID> destinataires, Map<String, Object> donnees) {
        log.info("Notification [{}]: {} à {} destinataires", 
                type.getLibelle(), message, destinataires.size());
        
        // Ici, on pourrait :
        // 1. Sauvegarder dans une table de notifications
        // 2. Envoyer par email
        // 3. Envoyer par notification push
        // 4. Intégrer avec un système de messagerie
        
        for (UUID utilisateurId : destinataires) {
            sauvegarderNotification(type, message, utilisateurId, donnees);
        }
    }

    /**
     * Vérifier et notifier les stocks critiques
     */
    @Transactional
    public void verifierStocksCritiques() {
        log.info("Vérification des stocks critiques");
        
        // Récupérer les stocks critiques (quantité < stock minimum)
        List<Map<String, Object>> stocksCritiques = stockService.getStocksCritiques();
        
        if (!stocksCritiques.isEmpty()) {
            // Trouver les responsables concernés
            List<UUID> responsables = trouverResponsablesStocks();
            
            // Préparer le message
            StringBuilder message = new StringBuilder();
            message.append("STOCKS CRITIQUES - ").append(stocksCritiques.size()).append(" articles concernés:\n");
            
            for (Map<String, Object> stock : stocksCritiques) {
                message.append("- ").append(stock.get("codeArticle"))
                       .append(" (").append(stock.get("libelle")).append("): ")
                       .append(stock.get("quantite")).append("/").append(stock.get("stockMinimum"))
                       .append(" au dépôt ").append(stock.get("depotNom")).append("\n");
            }
            
            envoyerNotification(NotificationType.STOCK_CRITIQUE, message.toString(), 
                              responsables, Map.of("stocks", stocksCritiques));
        }
    }

    /**
     * Vérifier et notifier les péremptions
     */
    @Transactional
    public void verifierPéremptions() {
        log.info("Vérification des péremptions");
        
        // Récupérer les lots proches de la péremption (7 jours)
        List<Lot> lotsProches = lotService.getLotsProchePeremption(7);
        
        if (!lotsProches.isEmpty()) {
            List<UUID> responsables = trouverResponsablesStocks();
            
            // Grouper par niveau d'urgence
            Map<String, List<Lot>> lotsParUrgence = new HashMap<>();
            lotsParUrgence.put("Déjà périmés", new ArrayList<>());
            lotsParUrgence.put("1-7 jours", new ArrayList<>());
            lotsParUrgence.put("8-30 jours", new ArrayList<>());
            
            for (Lot lot : lotsProches) {
                if (lot.getDatePeremption() != null) {
                    long joursRestants = ChronoUnit.DAYS.between(
                            LocalDate.now(), lot.getDatePeremption());
                    
                    if (joursRestants < 0) {
                        lotsParUrgence.get("Déjà périmés").add(lot);
                    } else if (joursRestants <= 7) {
                        lotsParUrgence.get("1-7 jours").add(lot);
                    } else if (joursRestants <= 30) {
                        lotsParUrgence.get("8-30 jours").add(lot);
                    }
                }
            }
            
            // Envoyer des notifications spécifiques
            for (Map.Entry<String, List<Lot>> entry : lotsParUrgence.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    String niveau = entry.getKey().equals("Déjà périmés") ? "danger" : "warning";
                    NotificationType type = entry.getKey().equals("Déjà périmés") ? 
                            NotificationType.LOT_PERIME : NotificationType.LOT_PEREMPTION_PROCH;
                    
                    StringBuilder message = new StringBuilder();
                    message.append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" lots\n");
                    
                    for (Lot lot : entry.getValue()) {
                        long joursRestants = lot.getDatePeremption() != null ? 
                                ChronoUnit.DAYS.between(LocalDate.now(), lot.getDatePeremption()) : 0;
                        message.append("- Lot ").append(lot.getNumeroLot())
                               .append(" (").append(lot.getArticle().getCodeArticle()).append("): ")
                               .append(lot.getQuantiteActuelle()).append(" unités, ")
                               .append(Math.abs(joursRestants)).append(" jour(s) ").append(joursRestants < 0 ? "dépassé(s)" : "restant(s)")
                               .append("\n");
                    }
                    
                    envoyerNotification(type, message.toString(), responsables, 
                            Map.of("lots", entry.getValue(), "urgence", entry.getKey()));
                }
            }
        }
    }

    /**
     * Vérifier les stocks obsolètes
     */
    @Transactional
    public void verifierStocksObsolètes() {
        log.info("Vérification des stocks obsolètes");
        
        // Récupérer les articles sans mouvement depuis plus de 6 mois
        List<Map<String, Object>> stocksObsolètes = stockService.getStocksObsoletes(180);
        
        if (!stocksObsolètes.isEmpty()) {
            List<UUID> responsables = trouverResponsablesStocks();
            
            BigDecimal valeurTotale = BigDecimal.ZERO;
            StringBuilder message = new StringBuilder();
            message.append("STOCKS OBSOLÈTES - ").append(stocksObsolètes.size()).append(" articles:\n");
            
            for (Map<String, Object> stock : stocksObsolètes) {
                message.append("- ").append(stock.get("codeArticle"))
                       .append(" (").append(stock.get("libelle")).append("): ")
                       .append(stock.get("quantite")).append(" unités depuis ")
                       .append(stock.get("dernierMouvement")).append("\n");
                
                if (stock.get("valeur") != null) {
                    valeurTotale = valeurTotale.add((BigDecimal) stock.get("valeur"));
                }
            }
            
            message.append("\nValeur totale: ").append(valeurTotale).append(" €");
            
            envoyerNotification(NotificationType.STOCK_OBSOLETE, message.toString(), 
                              responsables, Map.of("stocks", stocksObsolètes, "valeur", valeurTotale));
        }
    }

    /**
     * Trouver les responsables stocks
     */
    private List<UUID> trouverResponsablesStocks() {
        // Chercher les utilisateurs avec rôle GESTIONNAIRE_STOCK ou RESPONSABLE_STOCK
        return utilisateurRepository.findByRoleIn(
                Arrays.asList(Utilisateur.Role.GESTIONNAIRE_STOCK, 
                            Utilisateur.Role.RESPONSABLE_STOCK,
                            Utilisateur.Role.MANAGER))
                .stream()
                .map(Utilisateur::getId)
                .toList();
    }

    /**
     * Sauvegarder une notification dans la base de données
     */
    private void sauvegarderNotification(NotificationType type, String message, 
                                        UUID utilisateurId, Map<String, Object> donnees) {
        // Implémentation de la sauvegarde
        // Cela créerait une entrée dans une table notifications
        log.debug("Notification sauvegardée pour utilisateur {}: {} - {}", 
                utilisateurId, type.getLibelle(), message);
    }

    /**
     * Vérifications périodiques (à appeler via un scheduler)
     */
    @Transactional
    public void effectuerVerificationsPeriodiques() {
        log.info("Début des vérifications périodiques");
        
        try {
            verifierStocksCritiques();
            verifierPéremptions();
            verifierStocksObsolètes();
            
            log.info("Vérifications périodiques terminées");
        } catch (Exception e) {
            log.error("Erreur lors des vérifications périodiques", e);
        }
    }

    /**
     * Envoyer une notification personnalisée
     */
    public void notifierUtilisateur(UUID utilisateurId, String titre, String message, 
                                   String niveau, Map<String, Object> donnees) {
        log.info("Notification personnalisée à {}: {}", utilisateurId, titre);
        
        // Sauvegarder la notification
        sauvegarderNotificationPersonnalisee(utilisateurId, titre, message, niveau, donnees);
        
        // Optionnel: envoyer un email
        // envoyerEmailNotification(utilisateurId, titre, message);
    }

    private void sauvegarderNotificationPersonnalisee(UUID utilisateurId, String titre, 
                                                     String message, String niveau, 
                                                     Map<String, Object> donnees) {
        // Implémentation de la sauvegarde
        log.debug("Notification personnalisée sauvegardée pour {}: {}", utilisateurId, titre);
    }
}