package com.gestion.achat.enums;

public enum StatutDemande {
    EN_ATTENTE,   // La demande vient d'être créée, pas encore de proformas
    EN_COURS,     // Des proformas ont été reçus et sont en cours d'étude
    TERMINEE,     // La commande a été passée avec succès
    ANNULEE       // La demande a été annulée (besoin disparu ou budget refusé)
}