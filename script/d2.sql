-- ============================================================================
-- DONNÉES DE TEST POUR LE MODULE STOCK (VERSION FINALE CORRIGÉE)
-- ============================================================================
-- Note: Si problèmes d'encodage, exécutez: SET client_encoding = 'UTF8';

-- 1. Table des utilisateurs
INSERT INTO utilisateurs (id, username, email, password, nom, prenom, role, telephone, poste, service) VALUES
('11111111-1111-1111-1111-111111111111', 'admin', 'admin@entreprise.com', 'admin', 'Dupont', 'Jean', 'ADMIN', '+33 1 23 45 67 89', 'Administrateur systeme', 'IT'),
('22222222-2222-2222-2222-222222222222', 'manager', 'manager@entreprise.com', 'manager', 'Martin', 'Sophie', 'MANAGER', '+33 1 23 45 67 90', 'Responsable logistique', 'Logistique'),
('33333333-3333-3333-3333-333333333333', 'stock1', 'stock1@entreprise.com', 'stock1', 'Dubois', 'Pierre', 'RESPONSABLE_STOCK', '+33 1 23 45 67 91', 'Responsable stock', 'Logistique'),
('44444444-4444-4444-4444-444444444444', 'stock2', 'stock2@entreprise.com', 'stock2', 'Leroy', 'Marie', 'RESPONSABLE_STOCK', '+33 1 23 45 67 92', 'Magasinier', 'Logistique'),
('55555555-5555-5555-5555-555555555555', 'compta', 'compta@entreprise.com', 'compta', 'Moreau', 'Thomas', 'COMPTABLE', '+33 1 23 45 67 93', 'Comptable', 'Finance');

-- 2. Table des categories d'articles
INSERT INTO categories_articles (id, code, libelle, description, methode_valorisation) VALUES
-- Electronique
('aaaaaaaa-1111-1111-1111-111111111111', 'ELEC', 'Electronique', 'Composants et equipements electroniques', 'FIFO'),
('aaaaaaaa-2222-2222-2222-222222222222', 'ELEC_COMP', 'Composants', 'Composants electroniques divers', 'FIFO'),
('aaaaaaaa-3333-3333-3333-333333333333', 'ELEC_EQ', 'Equipements', 'Equipements electroniques complets', 'CUMP'),
-- Alimentaire (avec tracabilite)
('bbbbbbbb-1111-1111-1111-111111111111', 'ALIM', 'Alimentaire', 'Produits alimentaires', 'FIFO'),
('bbbbbbbb-2222-2222-2222-222222222222', 'ALIM_FRAIS', 'Produits frais', 'Produits necessitant refrigeration', 'FIFO'),
('bbbbbbbb-3333-3333-3333-333333333333', 'ALIM_SEC', 'Produits secs', 'Produits non perissables', 'CUMP'),
-- Fournitures
('cccccccc-1111-1111-1111-111111111111', 'FOURN', 'Fournitures', 'Fournitures de bureau', 'CUMP');

-- Mise a jour des categories parentes
UPDATE categories_articles SET categorie_parent_id = 'aaaaaaaa-1111-1111-1111-111111111111' WHERE id IN ('aaaaaaaa-2222-2222-2222-222222222222', 'aaaaaaaa-3333-3333-3333-333333333333');
UPDATE categories_articles SET categorie_parent_id = 'bbbbbbbb-1111-1111-1111-111111111111' WHERE id IN ('bbbbbbbb-2222-2222-2222-222222222222', 'bbbbbbbb-3333-3333-3333-333333333333');

-- 3. Table des unites de mesure
INSERT INTO unites_mesure (id, code, libelle, type) VALUES
('dddddddd-1111-1111-1111-111111111111', 'UNITE', 'Unite', 'QUANTITE'),
('dddddddd-2222-2222-2222-222222222222', 'KG', 'Kilogramme', 'POIDS'),
('dddddddd-3333-3333-3333-333333333333', 'L', 'Litre', 'VOLUME'),
('dddddddd-4444-4444-4444-444444444444', 'CARTON', 'Carton', 'QUANTITE'),
('dddddddd-5555-5555-5555-555555555555', 'BOITE', 'Boite', 'QUANTITE');

-- 4. Table des sites
INSERT INTO sites (id, code, nom, adresse, ville, pays) VALUES
('eeeeeeee-1111-1111-1111-111111111111', 'PARIS', 'Siege Paris', '123 Avenue des Champs-Elysees', 'Paris', 'France'),
('eeeeeeee-2222-2222-2222-222222222222', 'LYON', 'Entrepot Lyon', '45 Rue de la Republique', 'Lyon', 'France'),
('eeeeeeee-3333-3333-3333-333333333333', 'MARSEILLE', 'Site Marseille', '78 Vieux Port', 'Marseille', 'France');

-- 5. Table des depots
INSERT INTO depots (id, site_id, code, nom, type, capacite_m3) VALUES
('ffffffff-1111-1111-1111-111111111111', 'eeeeeeee-1111-1111-1111-111111111111', 'DPT_PARIS_1', 'Depot principal Paris', 'GENERAL', 5000.00),
('ffffffff-2222-2222-2222-222222222222', 'eeeeeeee-1111-1111-1111-111111111111', 'DPT_PARIS_2', 'Depot produits frais', 'GENERAL', 200.00),
('ffffffff-3333-3333-3333-333333333333', 'eeeeeeee-2222-2222-2222-222222222222', 'DPT_LYON_1', 'Depot regional Lyon', 'GENERAL', 3000.00),
('ffffffff-4444-4444-4444-444444444444', 'eeeeeeee-3333-3333-3333-333333333333', 'DPT_MRS_1', 'Depot Marseille', 'GENERAL', 1500.00),
('ffffffff-5555-5555-5555-555555555555', 'eeeeeeee-1111-1111-1111-111111111111', 'DPT_QUAR', 'Zone quarantaine Paris', 'QUARANTAINE', 100.00);

-- 6. Table des zones de stockage
INSERT INTO zones_stockage (id, depot_id, code, libelle, type, capacite_m3, temperature_min, temperature_max) VALUES
-- Depot principal Paris
('aaaaaaaa-aaaa-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111', 'ZONE_A', 'Zone A - Reception', 'RECEPTION', 200.00, NULL, NULL),
('aaaaaaaa-aaaa-2222-2222-222222222222', 'ffffffff-1111-1111-1111-111111111111', 'ZONE_B', 'Zone B - Stockage general', 'STOCKAGE', 4000.00, 15.00, 25.00),
('aaaaaaaa-aaaa-3333-3333-333333333333', 'ffffffff-1111-1111-1111-111111111111', 'ZONE_C', 'Zone C - Expedition', 'EXPEDITION', 300.00, NULL, NULL),
-- Depot produits frais
('aaaaaaaa-aaaa-4444-4444-444444444444', 'ffffffff-2222-2222-2222-222222222222', 'ZONE_FROID', 'Chambre froide', 'STOCKAGE', 150.00, 2.00, 8.00),
-- Depot Lyon
('aaaaaaaa-aaaa-5555-5555-555555555555', 'ffffffff-3333-3333-3333-333333333333', 'ZONE_LYON_1', 'Stockage Lyon', 'STOCKAGE', 2500.00, 10.00, 30.00);

-- 7. Table des emplacements
INSERT INTO emplacements (id, zone_id, code, allee, travee, niveau, position, capacite_volume_m3) VALUES
-- Zone stockage general Paris
('bbbbbbbb-bbbb-1111-1111-111111111111', 'aaaaaaaa-aaaa-2222-2222-222222222222', 'A-01-01-01', 'A', '01', '01', '01', 2.00),
('bbbbbbbb-bbbb-2222-2222-222222222222', 'aaaaaaaa-aaaa-2222-2222-222222222222', 'A-01-01-02', 'A', '01', '01', '02', 2.00),
('bbbbbbbb-bbbb-3333-3333-333333333333', 'aaaaaaaa-aaaa-2222-2222-222222222222', 'A-01-02-01', 'A', '01', '02', '01', 2.00),
-- Zone froide
('bbbbbbbb-bbbb-4444-4444-444444444444', 'aaaaaaaa-aaaa-4444-4444-444444444444', 'F-01-01-01', 'F', '01', '01', '01', 5.00),
('bbbbbbbb-bbbb-5555-5555-555555555555', 'aaaaaaaa-aaaa-4444-4444-444444444444', 'F-01-01-02', 'F', '01', '01', '02', 5.00),
-- Zone Lyon
('bbbbbbbb-bbbb-6666-6666-666666666666', 'aaaaaaaa-aaaa-5555-5555-555555555555', 'LY-01-01-01', 'LY', '01', '01', '01', 3.00);

-- 8. Table des articles
INSERT INTO articles (id, code_article, code_barre, libelle, description, categorie_id, unite_mesure_id, gestion_par_lot, poids_kg, volume_m3, stock_minimum, stock_securite, cout_standard, prix_vente_ht) VALUES
-- Electronique
('c1111111-1111-1111-1111-111111111111', 'PROD001', '1234567890123', 'Processeur Intel i7', 'Processeur Intel Core i7 12eme generation', 'aaaaaaaa-2222-2222-2222-222222222222', 'dddddddd-1111-1111-1111-111111111111', FALSE, 0.050, 0.001, 10, 5, 250.0000, 350.00),
('c2222222-2222-2222-2222-222222222222', 'PROD002', '1234567890124', 'Carte mere ASUS', 'Carte mere gaming ASUS Z690', 'aaaaaaaa-2222-2222-2222-222222222222', 'dddddddd-1111-1111-1111-111111111111', FALSE, 1.200, 0.010, 15, 8, 180.0000, 250.00),
('c3333333-3333-3333-3333-333333333333', 'PROD003', '1234567890125', 'PC Portable Dell', 'PC Portable Dell XPS 15', 'aaaaaaaa-3333-3333-3333-333333333333', 'dddddddd-1111-1111-1111-111111111111', TRUE, 2.000, 0.020, 5, 2, 1200.0000, 1500.00),
-- Alimentaire (avec gestion par lot)
('c4444444-4444-4444-4444-444444444444', 'PROD004', '1234567890126', 'Lait UHT', 'Lait demi-ecreme UHT 1L', 'bbbbbbbb-2222-2222-2222-222222222222', 'dddddddd-3333-3333-3333-333333333333', TRUE, 1.030, 0.001, 100, 20, 0.8500, 1.20),
('c5555555-5555-5555-5555-555555555555', 'PROD005', '1234567890127', 'Yaourt nature', 'Yaourt nature 125g', 'bbbbbbbb-2222-2222-2222-222222222222', 'dddddddd-1111-1111-1111-111111111111', TRUE, 0.150, 0.0002, 200, 50, 0.3500, 0.50),
('c6666666-6666-6666-6666-666666666666', 'PROD006', '1234567890128', 'Riz basmati', 'Riz basmati 1kg', 'bbbbbbbb-3333-3333-3333-333333333333', 'dddddddd-2222-2222-2222-222222222222', FALSE, 1.000, 0.002, 50, 10, 2.5000, 3.50),
-- Fournitures
('c7777777-7777-7777-7777-777777777777', 'PROD007', '1234567890129', 'Stylo bleu', 'Stylo a bille bleu', 'cccccccc-1111-1111-1111-111111111111', 'dddddddd-1111-1111-1111-111111111111', FALSE, 0.010, 0.0001, 500, 100, 0.1500, 0.30),
('c8888888-8888-8888-8888-888888888888', 'PROD008', '1234567890130', 'Cahier A4', 'Cahier A4 96 pages', 'cccccccc-1111-1111-1111-111111111111', 'dddddddd-1111-1111-1111-111111111111', FALSE, 0.200, 0.003, 100, 25, 1.2000, 2.00);

-- 9. Table des stocks initiaux
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, valeur_stock_cump, date_dernier_mouvement) VALUES
-- Paris - Depot principal
('d0111111-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111', 25, 25, 6250.00, '2024-01-15 10:30:00'),
('d0222222-2222-2222-2222-222222222222', 'c2222222-2222-2222-2222-222222222222', 'ffffffff-1111-1111-1111-111111111111', 30, 30, 5400.00, '2024-01-16 14:20:00'),
('d0333333-3333-3333-3333-333333333333', 'c3333333-3333-3333-3333-333333333333', 'ffffffff-1111-1111-1111-111111111111', 8, 8, 9600.00, '2024-01-10 09:15:00'),
-- Paris - Depot frais
('d0444444-4444-4444-4444-444444444444', 'c4444444-4444-4444-4444-444444444444', 'ffffffff-2222-2222-2222-222222222222', 150, 150, 127.50, '2024-01-17 08:45:00'),
('d0555555-5555-5555-5555-555555555555', 'c5555555-5555-5555-5555-555555555555', 'ffffffff-2222-2222-2222-222222222222', 300, 300, 105.00, '2024-01-17 08:45:00'),
-- Lyon
('d0666666-6666-6666-6666-666666666666', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-3333-3333-3333-333333333333', 15, 15, 3750.00, '2024-01-14 11:30:00'),
('d0777777-7777-7777-7777-777777777777', 'c7777777-7777-7777-7777-777777777777', 'ffffffff-3333-3333-3333-333333333333', 600, 600, 90.00, '2024-01-13 16:20:00'),
('d0888888-8888-8888-8888-888888888888', 'c8888888-8888-8888-8888-888888888888', 'ffffffff-3333-3333-3333-333333333333', 120, 120, 144.00, '2024-01-12 10:10:00');

-- 10. Table des lots (pour produits avec gestion par lot)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, date_fabrication, date_reception, date_peremption, statut, cout_unitaire, emplacement_id) VALUES
-- Lait UHT
('d1111111-1111-1111-1111-111111111111', 'LOT-LAIT-2401-001', 'c4444444-4444-4444-4444-444444444444', 100, 100, '2024-01-01', '2024-01-10', '2024-04-01', 'DISPONIBLE', 0.8500, 'bbbbbbbb-bbbb-4444-4444-444444444444'),
('d1222222-2222-2222-2222-222222222222', 'LOT-LAIT-2401-002', 'c4444444-4444-4444-4444-444444444444', 50, 50, '2024-01-05', '2024-01-12', '2024-04-05', 'DISPONIBLE', 0.8600, 'bbbbbbbb-bbbb-4444-4444-444444444444'),
-- Yaourts
('d1333333-3333-3333-3333-333333333333', 'LOT-YAOURT-2401-001', 'c5555555-5555-5555-5555-555555555555', 200, 200, '2024-01-02', '2024-01-11', '2024-02-02', 'DISPONIBLE', 0.3500, 'bbbbbbbb-bbbb-5555-5555-555555555555'),
('d1444444-4444-4444-4444-444444444444', 'LOT-YAOURT-2401-002', 'c5555555-5555-5555-5555-555555555555', 100, 100, '2024-01-08', '2024-01-15', '2024-02-08', 'DISPONIBLE', 0.3550, 'bbbbbbbb-bbbb-5555-5555-555555555555'),
-- PC Portable (numeros de serie)
('d1555555-5555-5555-5555-555555555555', 'LOT-PC-2312-001', 'c3333333-3333-3333-3333-333333333333', 8, 8, '2023-12-15', '2024-01-05', NULL, 'DISPONIBLE', 1200.0000, 'bbbbbbbb-bbbb-1111-1111-111111111111');

-- 11. Table des series (pour PC portables)
INSERT INTO series (id, numero_serie, article_id, lot_id, statut, date_reception, emplacement_id) VALUES
('d2111111-1111-1111-1111-111111111111', 'SN-DELLXPS-2401001', 'c3333333-3333-3333-3333-333333333333', 'd1555555-5555-5555-5555-555555555555', 'EN_STOCK', '2024-01-05 14:30:00', 'bbbbbbbb-bbbb-1111-1111-111111111111'),
('d2222222-2222-2222-2222-222222222222', 'SN-DELLXPS-2401002', 'c3333333-3333-3333-3333-333333333333', 'd1555555-5555-5555-5555-555555555555', 'EN_STOCK', '2024-01-05 14:30:00', 'bbbbbbbb-bbbb-2222-2222-222222222222'),
('d2333333-3333-3333-3333-333333333333', 'SN-DELLXPS-2401003', 'c3333333-3333-3333-3333-333333333333', 'd1555555-5555-5555-5555-555555555555', 'EN_STOCK', '2024-01-05 14:30:00', 'bbbbbbbb-bbbb-3333-3333-333333333333');

-- 12. Quelques mouvements de stock (avec cast UUID)
INSERT INTO mouvements_stock (id, reference, type_mouvement_id, article_id, depot_id, quantite, cout_unitaire, date_mouvement, utilisateur_id) 
SELECT 
    gen_random_uuid(),
    'MVT-' || EXTRACT(YEAR FROM CURRENT_DATE) || '-000001',
    id,
    'c1111111-1111-1111-1111-111111111111'::uuid,
    'ffffffff-1111-1111-1111-111111111111'::uuid,
    25,
    250.00,
    '2024-01-15 10:30:00'::timestamp,
    '33333333-3333-3333-3333-333333333333'::uuid
FROM types_mouvement WHERE code = 'RECEPTION_FOURNISSEUR'
UNION ALL
SELECT 
    gen_random_uuid(),
    'MVT-' || EXTRACT(YEAR FROM CURRENT_DATE) || '-000002',
    id,
    'c4444444-4444-4444-4444-444444444444'::uuid,
    'ffffffff-2222-2222-2222-222222222222'::uuid,
    150,
    0.85,
    '2024-01-17 08:45:00'::timestamp,
    '33333333-3333-3333-3333-333333333333'::uuid
FROM types_mouvement WHERE code = 'RECEPTION_FOURNISSEUR';

-- 13. Reservations de stock (commande_client_id doit etre UUID)
INSERT INTO reservations_stock (id, reference, article_id, depot_id, quantite_reservee, commande_client_id, date_reservation, utilisateur_id, statut) VALUES
('d3111111-1111-1111-1111-111111111111', 'RES-2024-0001', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111', 5, 'e0000001-0001-0001-0001-000000000001', '2024-01-18 09:00:00', '33333333-3333-3333-3333-333333333333', 'ACTIVE'),
('d3222222-2222-2222-2222-222222222222', 'RES-2024-0002', 'c3333333-3333-3333-3333-333333333333', 'ffffffff-1111-1111-1111-111111111111', 2, 'e0000002-0002-0002-0002-000000000002', '2024-01-18 10:30:00', '33333333-3333-3333-3333-333333333333', 'ACTIVE');
