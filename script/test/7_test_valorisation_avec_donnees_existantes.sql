-- =====================================================
-- SCRIPT DE TEST VALORISATION UTILISANT LES DONNEES EXISTANTES
-- Ce script utilise les depots/zones/emplacements de d2.sql
-- =====================================================

-- =====================================================
-- 0. NETTOYER LES ANCIENNES DONNEES DE TEST
-- =====================================================
DELETE FROM mouvements_stock WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'VALO-%');
DELETE FROM lots WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'VALO-%');
DELETE FROM stocks WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'VALO-%');
DELETE FROM articles WHERE code_article LIKE 'VALO-%';

-- =====================================================
-- 1. CREER DES ARTICLES DE TEST VALORISATION
-- Utilise la categorie et unite existantes de d2.sql
-- =====================================================

-- Article CUMP (utilise categorie ELEC_COMP et unite UNITE de d2.sql)
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, stock_minimum, actif)
VALUES 
    ('a0000001-0001-0001-0001-000000000001', 'VALO-CUMP', 'Test Valorisation CUMP', 
     'Article pour tester le calcul CUMP', 
     'aaaaaaaa-2222-2222-2222-222222222222',  -- categorie ELEC_COMP
     'dddddddd-1111-1111-1111-111111111111',  -- unite UNITE
     'CUMP', true, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article FIFO
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, stock_minimum, actif)
VALUES 
    ('a0000002-0002-0002-0002-000000000002', 'VALO-FIFO', 'Test Valorisation FIFO', 
     'Article pour tester le calcul FIFO', 
     'aaaaaaaa-2222-2222-2222-222222222222',  -- categorie ELEC_COMP
     'dddddddd-1111-1111-1111-111111111111',  -- unite UNITE
     'FIFO', true, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article FEFO
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, duree_vie_jours, stock_minimum, actif)
VALUES 
    ('a0000003-0003-0003-0003-000000000003', 'VALO-FEFO', 'Test Valorisation FEFO', 
     'Article perissable pour tester FEFO', 
     'bbbbbbbb-2222-2222-2222-222222222222',  -- categorie ALIM_FRAIS (perissable)
     'dddddddd-1111-1111-1111-111111111111',  -- unite UNITE
     'FEFO', true, 30, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- =====================================================
-- 2. CREER LES LOTS DE TEST
-- Utilise l'emplacement A-01-01-01 du depot Paris (d2.sql)
-- =====================================================

-- === LOTS POUR ARTICLE CUMP ===
-- LOT 1 : 100 unites a 10 EUR
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000001-0001-0001-0001-000000000001', 'VALO-CUMP-001', 
     'a0000001-0001-0001-0001-000000000001', 100, 100,
     '2025-12-15', '2026-01-01', 10.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-1111-1111-111111111111')  -- emplacement A-01-01-01 Paris
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 100, cout_unitaire = 10.0000, statut = 'DISPONIBLE';

-- LOT 2 : 50 unites a 12 EUR
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000002-0002-0002-0002-000000000002', 'VALO-CUMP-002', 
     'a0000001-0001-0001-0001-000000000001', 50, 50,
     '2026-01-10', '2026-01-15', 12.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-1111-1111-111111111111')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 50, cout_unitaire = 12.0000, statut = 'DISPONIBLE';

-- === LOTS POUR ARTICLE FIFO ===
-- LOT 1 : 100 unites a 10 EUR (le plus ancien)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000003-0003-0003-0003-000000000003', 'VALO-FIFO-001', 
     'a0000002-0002-0002-0002-000000000002', 100, 100,
     '2025-12-20', '2026-01-01', 10.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-1111-1111-111111111111')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 100, cout_unitaire = 10.0000, statut = 'DISPONIBLE';

-- LOT 2 : 80 unites a 15 EUR
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000004-0004-0004-0004-000000000004', 'VALO-FIFO-002', 
     'a0000002-0002-0002-0002-000000000002', 80, 80,
     '2026-01-05', '2026-01-10', 15.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-2222-2222-222222222222')  -- emplacement A-01-01-02
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 80, cout_unitaire = 15.0000, statut = 'DISPONIBLE';

-- LOT 3 : 50 unites a 20 EUR (le plus recent)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000005-0005-0005-0005-000000000005', 'VALO-FIFO-003', 
     'a0000002-0002-0002-0002-000000000002', 50, 50,
     '2026-01-15', '2026-01-20', 20.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-3333-3333-333333333333')  -- emplacement A-01-02-01
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 50, cout_unitaire = 20.0000, statut = 'DISPONIBLE';

-- === LOTS POUR ARTICLE FEFO (dans le depot frais F-01) ===
-- LOT 1 : 60 unites, peremption 01/03/2026 (expire en premier)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000006-0006-0006-0006-000000000006', 'VALO-FEFO-001', 
     'a0000003-0003-0003-0003-000000000003', 60, 60,
     '2026-01-01', '2026-01-05', '2026-03-01', 8.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-4444-4444-444444444444')  -- emplacement F-01-01-01 (zone froide)
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 60, cout_unitaire = 8.0000, date_peremption = '2026-03-01', statut = 'DISPONIBLE';

-- LOT 2 : 40 unites, peremption 15/04/2026
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000007-0007-0007-0007-000000000007', 'VALO-FEFO-002', 
     'a0000003-0003-0003-0003-000000000003', 40, 40,
     '2026-01-05', '2026-01-10', '2026-04-15', 9.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-4444-4444-444444444444')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 40, cout_unitaire = 9.0000, date_peremption = '2026-04-15', statut = 'DISPONIBLE';

-- LOT 3 : 30 unites, peremption 01/06/2026 (expire en dernier)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('b0000008-0008-0008-0008-000000000008', 'VALO-FEFO-003', 
     'a0000003-0003-0003-0003-000000000003', 30, 30,
     '2026-01-10', '2026-01-15', '2026-06-01', 10.0000, 'DISPONIBLE', 
     'bbbbbbbb-bbbb-5555-5555-555555555555')  -- emplacement F-01-01-02
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 30, cout_unitaire = 10.0000, date_peremption = '2026-06-01', statut = 'DISPONIBLE';

-- =====================================================
-- 3. CREER LES STOCKS
-- =====================================================

-- Stock CUMP dans depot Paris principal (ffffffff-1111-1111-1111-111111111111)
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('c0000001-0001-0001-0001-000000000001', 
     'a0000001-0001-0001-0001-000000000001',  -- VALO-CUMP
     'ffffffff-1111-1111-1111-111111111111',  -- DPT_PARIS_1
     150, 150, 1600.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 150, quantite_theorique = 150, 
    valeur_stock_cump = 1600.00, date_dernier_mouvement = NOW();

-- Stock FIFO dans depot Paris principal
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('c0000002-0002-0002-0002-000000000002', 
     'a0000002-0002-0002-0002-000000000002',  -- VALO-FIFO
     'ffffffff-1111-1111-1111-111111111111',  -- DPT_PARIS_1
     230, 230, 3200.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 230, quantite_theorique = 230, 
    valeur_stock_cump = 3200.00, date_dernier_mouvement = NOW();

-- Stock FEFO dans depot frais Paris (ffffffff-2222-2222-2222-222222222222)
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('c0000003-0003-0003-0003-000000000003', 
     'a0000003-0003-0003-0003-000000000003',  -- VALO-FEFO
     'ffffffff-2222-2222-2222-222222222222',  -- DPT_PARIS_2 (produits frais)
     130, 130, 1140.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 130, quantite_theorique = 130, 
    valeur_stock_cump = 1140.00, date_dernier_mouvement = NOW();

-- =====================================================
-- 4. VERIFICATION
-- =====================================================

SELECT '=== ARTICLES DE TEST ===' as info;
SELECT code_article, libelle, methode_valorisation 
FROM articles WHERE code_article LIKE 'VALO-%';

SELECT '=== LOTS AVEC CHAINE COMPLETE ===' as info;
SELECT 
    l.numero_lot,
    a.code_article,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.date_reception,
    l.date_peremption,
    e.code as emplacement,
    z.code as zone,
    d.code as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'VALO-%'
ORDER BY a.code_article, l.date_reception;

SELECT '=== STOCKS ===' as info;
SELECT 
    a.code_article,
    d.code as depot,
    s.quantite_theorique,
    s.valeur_stock_cump,
    s.cout_unitaire_moyen
FROM stocks s
JOIN articles a ON s.article_id = a.id
JOIN depots d ON s.depot_id = d.id
WHERE a.code_article LIKE 'VALO-%';

SELECT '=== VALORISATION FIFO CALCULEE ===' as info;
SELECT 
    a.code_article,
    d.code as depot,
    SUM(l.quantite_actuelle) as qte_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valeur_fifo
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'VALO-%'
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
GROUP BY a.code_article, d.code;

-- =====================================================
-- RESULTATS ATTENDUS
-- =====================================================
/*
VALO-CUMP (Depot Paris Principal):
- 150 unites, Valeur CUMP = 1600 EUR, Cout moyen = 10.67 EUR

VALO-FIFO (Depot Paris Principal):
- 230 unites, Valeur FIFO = 3200 EUR
  - LOT-001: 100 x 10 = 1000 EUR
  - LOT-002: 80 x 15 = 1200 EUR
  - LOT-003: 50 x 20 = 1000 EUR

VALO-FEFO (Depot Produits Frais):
- 130 unites, Valeur = 1140 EUR
  - LOT-001: 60 x 8 = 480 EUR (expire 01/03)
  - LOT-002: 40 x 9 = 360 EUR (expire 15/04)
  - LOT-003: 30 x 10 = 300 EUR (expire 01/06)
*/
