package com.gestion.achat.enums;

public enum StatutFinance {
    EN_ATTENTE_VALIDATION, // Le BC est créé mais attend le feu vert financier
    VALIDEE,               // Budget accordé, on peut envoyer le BC au fournisseur
    REJETEE                // Budget refusé par la direction financière
}