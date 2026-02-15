-- Données de test basiques pour le module vente
INSERT INTO clients (id, code, nom, email, telephone, adresse, ville, pays, plafond_remise, plafond_credit)
VALUES
    (gen_random_uuid(), 'CL-0001', 'Client Alpha', 'alpha@client.com', '0320000001', 'Lot I', 'Antananarivo', 'MG', 5, 1000000),
    (gen_random_uuid(), 'CL-0002', 'Client Beta', 'beta@client.com', '0320000002', 'Lot II', 'Antsirabe', 'MG', 10, 2000000)
ON CONFLICT (code) DO NOTHING;

INSERT INTO utilisateurs (
    id, username, email, password, nom, prenom, role, actif, telephone, poste, service, created_at, updated_at
) VALUES
    (gen_random_uuid(), 'com1', 'com1@erp.com', '1234', 'Rakoto', 'Jean', 'COMMERCIAL', true, '0321111111', 'Commercial', 'Ventes', now(), now()),
    (gen_random_uuid(), 'rv1', 'rv1@erp.com', '1234', 'Ranaivo', 'Paul', 'RESPONSABLE_VENTES', true, '0322222222', 'Resp. Ventes', 'Ventes', now(), now()),
    (gen_random_uuid(), 'mag1', 'mag1@erp.com', '1234', 'Razafy', 'Lova', 'MAGASINIER_SORTIE', true, '0323333333', 'Magasinier', 'Logistique', now(), now()),
    (gen_random_uuid(), 'cc1', 'cc1@erp.com', '1234', 'Randria', 'Mina', 'COMPTABLE_CLIENT', true, '0324444444', 'Comptable Client', 'Finance', now(), now())
ON CONFLICT (username) DO NOTHING;

-- Données de test complètes (vente + référentiels stock minimaux)
-- Identifiants fixes pour garantir les liens

-- Référentiels stock minimum
INSERT INTO unites_mesure (id, code, libelle, type)
VALUES ('11111111-1111-1111-1111-111111111111', 'UN', 'Unité', 'QUANTITE')
ON CONFLICT (code) DO NOTHING;

INSERT INTO categories_articles (id, code, libelle)
VALUES ('22222222-2222-2222-2222-222222222222', 'CAT-TEST', 'Catégorie Test')
ON CONFLICT (code) DO NOTHING;

INSERT INTO sites (id, code, nom, ville, pays)
VALUES ('33333333-3333-3333-3333-333333333333', 'SITE-1', 'Site Central', 'Antananarivo', 'MG')
ON CONFLICT (code) DO NOTHING;

INSERT INTO depots (id, site_id, code, nom, type, adresse)
VALUES ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'DEP-TEST', 'Dépôt Test', 'GENERAL', 'Zone Test')
ON CONFLICT (code) DO NOTHING;

INSERT INTO articles (
    id, code_article, libelle, categorie_id, unite_mesure_id, prix_vente_ht, tva_pourcentage, actif
) VALUES (
    '55555555-5555-5555-5555-555555555555', 'ART-TEST-1', 'Article Test 1',
    '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
    10000, 20.0, true
)
ON CONFLICT (code_article) DO NOTHING;

-- Clients dédiés aux documents vente
INSERT INTO clients (id, code, nom, email, telephone, adresse, ville, pays, plafond_remise, plafond_credit)
VALUES
    ('66666666-6666-6666-6666-666666666666', 'CL-TEST-1', 'Client Test 1', 'test1@client.com', '0325555555', 'Lot III', 'Antananarivo', 'MG', 5, 1000000)
ON CONFLICT (code) DO NOTHING;

-- Devis + lignes
INSERT INTO devis_vente (
    id, reference, client_id, statut, total_ht, total_tva, total_ttc, remise_globale, notes
) VALUES (
    '77777777-7777-7777-7777-777777777777', 'DEV-2026-000010',
    '66666666-6666-6666-6666-666666666666', 'VALIDE', 10000, 2000, 12000, 0, 'Devis test'
)
ON CONFLICT (reference) DO NOTHING;

INSERT INTO lignes_devis_vente (
    id, devis_id, article_id, quantite, prix_unitaire_ht, remise_pourcentage, tva_pourcentage, total_ht, total_ttc
) VALUES (
    '88888888-8888-8888-8888-888888888888', '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555', 1, 10000, 0, 20.0, 10000, 12000
)
ON CONFLICT (id) DO NOTHING;

-- Commande + lignes
INSERT INTO commandes_clients (
    id, reference, devis_id, client_id, depot_livraison_id, statut, total_ht, total_tva, total_ttc, remise_globale
) VALUES (
    '99999999-9999-9999-9999-999999999999', 'CMD-2026-000010',
    '77777777-7777-7777-7777-777777777777', '66666666-6666-6666-6666-666666666666',
    '44444444-4444-4444-4444-444444444444', 'CONFIRMEE', 10000, 2000, 12000, 0
)
ON CONFLICT (reference) DO NOTHING;

INSERT INTO lignes_commandes_clients (
    id, commande_id, article_id, quantite, prix_unitaire_ht, remise_pourcentage, tva_pourcentage, total_ht, total_ttc, statut
) VALUES (
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '99999999-9999-9999-9999-999999999999',
    '55555555-5555-5555-5555-555555555555', 1, 10000, 0, 20.0, 10000, 12000, 'RESERVEE'
)
ON CONFLICT (id) DO NOTHING;

-- Livraison + lignes
INSERT INTO livraisons_clients (
    id, reference, commande_id, date_preparation, date_livraison, statut, transporteur, notes
) VALUES (
    'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'BL-2026-000010',
    '99999999-9999-9999-9999-999999999999', now(), now(), 'LIVREE', 'Transporteur X', 'Livraison test'
)
ON CONFLICT (reference) DO NOTHING;

INSERT INTO lignes_livraisons_clients (
    id, livraison_id, ligne_commande_id, article_id, quantite_livree
) VALUES (
    'cccccccc-cccc-cccc-cccc-cccccccccccc', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', '55555555-5555-5555-5555-555555555555', 1
)
ON CONFLICT (id) DO NOTHING;

-- Facture + lignes
INSERT INTO factures_vente (
    id, reference, commande_id, livraison_id, client_id, statut, total_ht, total_tva, total_ttc, notes
) VALUES (
    'dddddddd-dddd-dddd-dddd-dddddddddddd', 'FAC-2026-000010',
    '99999999-9999-9999-9999-999999999999', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
    '66666666-6666-6666-6666-666666666666', 'EMISE', 10000, 2000, 12000, 'Facture test'
)
ON CONFLICT (reference) DO NOTHING;

INSERT INTO lignes_factures_vente (
    id, facture_id, article_id, quantite, prix_unitaire_ht, remise_pourcentage, tva_pourcentage, total_ht, total_ttc
) VALUES (
    'eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'dddddddd-dddd-dddd-dddd-dddddddddddd',
    '55555555-5555-5555-5555-555555555555', 1, 10000, 0, 20.0, 10000, 12000
)
ON CONFLICT (id) DO NOTHING;

-- Paiement + avoir
INSERT INTO paiements_clients (
    id, reference, facture_id, client_id, date_paiement, mode_paiement, montant, statut, notes
) VALUES (
    'ffffffff-ffff-ffff-ffff-ffffffffffff', 'PAY-2026-000010',
    'dddddddd-dddd-dddd-dddd-dddddddddddd', '66666666-6666-6666-6666-666666666666',
    current_date, 'VIREMENT', 12000, 'ENREGISTRE', 'Paiement test'
)
ON CONFLICT (reference) DO NOTHING;

INSERT INTO avoirs_clients (
    id, reference, facture_id, client_id, date_avoir, montant, motif, statut
) VALUES (
    '12121212-1212-1212-1212-121212121212', 'AV-2026-000010',
    'dddddddd-dddd-dddd-dddd-dddddddddddd', '66666666-6666-6666-6666-666666666666',
    current_date, 1000, 'Remise commerciale', 'EMIS'
)
ON CONFLICT (reference) DO NOTHING;
