-- ============================================================================
-- DONNÉES DE TEST POUR LE MODULE ACHATS (CORRIGÉ)
-- ============================================================================

-- 1. Table des fournisseurs
INSERT INTO fournisseurs (id, nom, email, telephone, adresse, actif) VALUES
('a1111111-1111-1111-1111-111111111111', 'Fournisseur Electronique SARL', 'contact@fourelec.fr', '+33 1 40 00 00 01', '10 Rue de la Technologie, 75015 Paris', TRUE),
('a2222222-2222-2222-2222-222222222222', 'Dell France', 'ventes@dell.fr', '+33 1 40 00 00 02', '25 Avenue des Nations, 92000 Nanterre', TRUE),
('a3333333-3333-3333-3333-333333333333', 'Laitières du Nord', 'commandes@laitieres-nord.fr', '+33 3 20 00 00 01', 'Zone Industrielle, 59000 Lille', TRUE),
('a4444444-4444-4444-4444-444444444444', 'Papeterie Centrale', 'contact@papeterie-centrale.fr', '+33 1 40 00 00 03', '5 Rue du Papier, 75002 Paris', TRUE),
('a5555555-5555-5555-5555-555555555555', 'ASUS France', 'france@asus.com', '+33 1 40 00 00 04', '15 Rue des Processeurs, 75013 Paris', TRUE);

-- 2. Table des demandes d'achat
INSERT INTO demandes_achat (id, produit_id, quantite_demandee, motif, statut, date_demande) VALUES
('b1111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 50, 'Réapprovisionnement stock processeurs pour Q1 2024', 'TERMINEE', '2024-01-05 09:00:00'),
('b2222222-2222-2222-2222-222222222222', 'c3333333-3333-3333-3333-333333333333', 10, 'Commande PC portables pour équipe commerciale', 'EN_COURS', '2024-01-10 14:30:00'),
('b3333333-3333-3333-3333-333333333333', 'c4444444-4444-4444-4444-444444444444', 200, 'Réapprovisionnement produits laitiers', 'EN_ATTENTE', '2024-01-12 11:15:00'),
('b4444444-4444-4444-4444-444444444444', 'c7777777-7777-7777-7777-777777777777', 1000, 'Commande fournitures bureau pour année 2024', 'TERMINEE', '2024-01-02 10:00:00');

-- 3. Table des proformas
INSERT INTO proformas (id, demande_achat_id, fournisseur_id, prix_unitaire_ht, tva_pourcentage, delai_livraison_jours, date_reception, est_selectionne) VALUES
-- Proforma pour processeurs (demande terminée)
('d1111111-1111-1111-1111-111111111111', 'b1111111-1111-1111-1111-111111111111', 'a1111111-1111-1111-1111-111111111111', 245.00, 20.0, 7, '2024-01-06 16:45:00', TRUE),
('d2222222-2222-2222-2222-222222222222', 'b1111111-1111-1111-1111-111111111111', 'a5555555-5555-5555-5555-555555555555', 255.00, 20.0, 10, '2024-01-06 17:30:00', FALSE),
-- Proforma pour PC portables (en cours)
('d3333333-3333-3333-3333-333333333333', 'b2222222-2222-2222-2222-222222222222', 'a2222222-2222-2222-2222-222222222222', 1180.00, 20.0, 14, '2024-01-11 10:20:00', TRUE),
-- Proforma pour fournitures (terminée)
('d4444444-4444-4444-4444-444444444444', 'b4444444-4444-4444-4444-444444444444', 'a4444444-4444-4444-4444-444444444444', 0.145, 20.0, 3, '2024-01-03 09:15:00', TRUE);

-- 4. Table des bons de commande
INSERT INTO bons_commande (id, proforma_id, reference_bc, statut_finance, montant_total_ttc, date_emission, date_livraison_estimee) VALUES
-- BC pour processeurs (validé)
('e1111111-1111-1111-1111-111111111111', 'd1111111-1111-1111-1111-111111111111', 'BC-2024-0001', 'VALIDEE', 14700.00, '2024-01-07 11:00:00', '2024-01-14'),
-- BC pour fournitures (validé)
('e2222222-2222-2222-2222-222222222222', 'd4444444-4444-4444-4444-444444444444', 'BC-2024-0002', 'VALIDEE', 174.00, '2024-01-04 15:30:00', '2024-01-07'),
-- BC pour PC portables (en attente validation finance)
('e3333333-3333-3333-3333-333333333333', 'd3333333-3333-3333-3333-333333333333', 'BC-2024-0003', 'EN_ATTENTE_VALIDATION', 14160.00, '2024-01-12 14:00:00', '2024-01-26');

-- 5. Table des bons de réception
INSERT INTO bons_reception (id, bon_commande_id, date_reception, est_conforme, observations) VALUES
-- BR pour processeurs (livré et conforme)
('f1111111-1111-1111-1111-111111111111', 'e1111111-1111-1111-1111-111111111111', '2024-01-15 08:30:00', TRUE, 'Livraison complète et conforme'),
-- BR pour fournitures (livré avec observation)
('f2222222-2222-2222-2222-222222222222', 'e2222222-2222-2222-2222-222222222222', '2024-01-08 10:15:00', TRUE, '2 cartons légèrement endommagés mais produits intacts');

-- 6. Table des factures d'achat
INSERT INTO factures_achat (id, bon_commande_id, numero_facture_fournisseur, montant_total_ttc, est_payee, date_facture) VALUES
-- Facture pour processeurs (payée)
('f8111111-1111-1111-1111-111111111111', 'e1111111-1111-1111-1111-111111111111', 'FAC-2024-001-ELEC', 14700.00, TRUE, '2024-01-16'),
-- Facture pour fournitures (non payée)
('f8222222-2222-2222-2222-222222222222', 'e2222222-2222-2222-2222-222222222222', 'FAC-2024-002-PAP', 174.00, FALSE, '2024-01-09');
