-- 1. Nettoyage (Optionnel, à utiliser avec prudence)
-- DELETE FROM factures_achat;
-- DELETE FROM bons_reception;
-- DELETE FROM bons_commande;
-- DELETE FROM proformas;
-- DELETE FROM demandes_achat;
-- DELETE FROM fournisseurs;

-- 2. Initialisation des Fournisseurs (Nécessaire pour pouvoir faire des offres)
INSERT INTO fournisseurs (id, nom, email, actif) VALUES
(gen_random_uuid(), 'Fournisseur Alpha', 'alpha@test.com', TRUE),
(gen_random_uuid(), 'Fournisseur Beta', 'beta@test.com', TRUE),
(gen_random_uuid(), 'Fournisseur Gamma', 'gamma@test.com', TRUE);

-- 3. Initialisation de quelques Demandes d'Achat (DA) en attente
-- On simule des IDs produits venant du stock (Package Stock)
INSERT INTO demandes_achat (id, produit_id, quantite_demandee, motif, statut) VALUES
(gen_random_uuid(), gen_random_uuid(), 5, 'Besoin urgent de RAM 16Go', 'EN_ATTENTE'),
(gen_random_uuid(), gen_random_uuid(), 2, 'Remplacement chaises bureau', 'EN_ATTENTE');