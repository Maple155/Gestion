-- 1. Récupérer l'ID d'une demande d'achat existante (ou en créer une nouvelle)
INSERT INTO demandes_achat (id, produit_id, quantite_demandee, motif, statut) 
VALUES ('f47ac10b-58cc-4372-a567-0e02b2c3d479', gen_random_uuid(), 10, 'Achat de 10 Switchs Cisco pour le nouveau bureau', 'EN_COURS');

-- 2. Insertion de 3 offres (Proformas) pour cette même DA
-- Offre 1 : La moins chère mais délai long
INSERT INTO proformas (id, demande_achat_id, fournisseur_id, prix_unitaire_ht, tva_pourcentage, delai_livraison_jours, date_reception, est_selectionne)
VALUES (
    gen_random_uuid(), 
    'f47ac10b-58cc-4372-a567-0e02b2c3d479', 
    (SELECT id FROM fournisseurs WHERE nom='Fournisseur Alpha' LIMIT 1), 
    450.00, 20.0, 15, CURRENT_TIMESTAMP, FALSE
);

-- Offre 2 : Prix moyen, livraison très rapide
INSERT INTO proformas (id, demande_achat_id, fournisseur_id, prix_unitaire_ht, tva_pourcentage, delai_livraison_jours, date_reception, est_selectionne)
VALUES (
    gen_random_uuid(), 
    'f47ac10b-58cc-4372-a567-0e02b2c3d479', 
    (SELECT id FROM fournisseurs WHERE nom='Fournisseur Beta' LIMIT 1), 
    490.00, 20.0, 2, CURRENT_TIMESTAMP, FALSE
);

-- Offre 3 : La plus chère
INSERT INTO proformas (id, demande_achat_id, fournisseur_id, prix_unitaire_ht, tva_pourcentage, delai_livraison_jours, date_reception, est_selectionne)
VALUES (
    gen_random_uuid(), 
    'f47ac10b-58cc-4372-a567-0e02b2c3d479', 
    (SELECT id FROM fournisseurs WHERE nom='Fournisseur Gamma' LIMIT 1), 
    550.00, 20.0, 5, CURRENT_TIMESTAMP, FALSE
);