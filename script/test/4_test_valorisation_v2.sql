-- =====================================================
-- SCRIPT DE TEST POUR LA VALORISATION (FIFO/CUMP/FEFO)
-- Version corrigée compatible avec le schema 2_stock_Ranto.sql
-- =====================================================

-- =====================================================
-- 0. NETTOYER LES ANCIENNES DONNEES DE TEST
-- =====================================================
DELETE FROM mouvements_stock WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');
DELETE FROM lots WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');
DELETE FROM stocks WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');
DELETE FROM articles WHERE code_article LIKE 'TEST-%';
DELETE FROM emplacements WHERE code LIKE 'TEST-%';
DELETE FROM zones_stockage WHERE code LIKE 'TEST-%';
DELETE FROM depots WHERE code LIKE 'TEST-%';
DELETE FROM sites WHERE code LIKE 'TEST-%';
DELETE FROM categories_articles WHERE code LIKE 'TEST-%';
DELETE FROM unites_mesure WHERE code LIKE 'TEST-%';

-- =====================================================
-- 1. DONNEES DE BASE
-- =====================================================

-- Unite de mesure (colonnes: id, code, libelle, type)
INSERT INTO unites_mesure (id, code, libelle, type)
VALUES 
    ('11111111-1111-1111-1111-111111111111', 'TEST-UNIT', 'Unite Test', 'QUANTITE')
ON CONFLICT (code) DO NOTHING;

-- Categorie article (table: categories_articles avec S)
INSERT INTO categories_articles (id, code, libelle, description, methode_valorisation)
VALUES 
    ('22222222-2222-2222-2222-222222222222', 'TEST-CAT', 'Categorie Test', 'Categorie pour tests valorisation', 'CUMP')
ON CONFLICT (code) DO NOTHING;

-- Site (colonnes: id, code, nom, adresse, ville, pays, actif)
INSERT INTO sites (id, code, nom, adresse, ville, pays)
VALUES 
    ('33333333-3333-3333-3333-333333333333', 'TEST-SITE', 'Site Test', 'Adresse Test', 'Ville Test', 'Madagascar')
ON CONFLICT (code) DO NOTHING;

-- Depot (colonnes: id, site_id, code, nom, type, capacite_m3)
INSERT INTO depots (id, site_id, code, nom, type, capacite_m3)
VALUES 
    ('44444444-4444-4444-4444-444444444444', '33333333-3333-3333-3333-333333333333', 'TEST-DEP', 'Depot Test', 'GENERAL', 5000.00)
ON CONFLICT (code) DO NOTHING;

-- Zone de stockage (colonnes: id, depot_id, code, libelle, type, capacite_m3)
INSERT INTO zones_stockage (id, depot_id, code, libelle, type, capacite_m3)
VALUES 
    ('55555555-5555-5555-5555-555555555555', '44444444-4444-4444-4444-444444444444', 'TEST-ZONE', 'Zone Test', 'STOCKAGE', 1000.00)
ON CONFLICT (depot_id, code) DO NOTHING;

-- Emplacement (colonnes: id, zone_id, code, allee, travee, niveau, position, capacite_volume_m3)
INSERT INTO emplacements (id, zone_id, code, allee, travee, niveau, position, capacite_volume_m3)
VALUES 
    ('66666666-6666-6666-6666-666666666666', '55555555-5555-5555-5555-555555555555', 'TEST-EMP', 'T', '01', '01', '01', 50.00)
ON CONFLICT (zone_id, code) DO NOTHING;

-- =====================================================
-- 2. ARTICLES DE TEST
-- =====================================================

-- Article 1 : Methode CUMP
-- Stock: (100 x 10 EUR) + (50 x 12 EUR) = 1600 EUR pour 150 unites
-- CUMP = 1600 / 150 = 10.67 EUR
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, stock_minimum, actif)
VALUES 
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'TEST-CUMP', 'Article Test CUMP', 
     'Article pour tester le calcul CUMP', 
     '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     'CUMP', true, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article 2 : Methode FIFO
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, stock_minimum, actif)
VALUES 
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'TEST-FIFO', 'Article Test FIFO', 
     'Article pour tester le calcul FIFO', 
     '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     'FIFO', true, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article 3 : Methode FEFO (perissable)
INSERT INTO articles (id, code_article, libelle, description, categorie_id, unite_mesure_id, 
                      methode_valorisation, gestion_par_lot, duree_vie_jours, stock_minimum, actif)
VALUES 
    ('cccccccc-cccc-cccc-cccc-cccccccccccc', 'TEST-FEFO', 'Article Test FEFO', 
     'Article pour tester le calcul FEFO (perissable)', 
     '22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
     'FEFO', true, 30, 10, true)
ON CONFLICT (code_article) DO NOTHING;

-- =====================================================
-- 3. LOTS DE TEST
-- (colonnes: id, numero_lot, article_id, quantite_initiale, quantite_actuelle,
--  date_fabrication, date_reception, date_peremption, statut, cout_unitaire, emplacement_id)
-- =====================================================

-- === LOTS POUR ARTICLE CUMP ===
-- LOT 1 : 100 unites a 10 EUR (entree le 01/01/2026)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('d1111111-1111-1111-1111-111111111111', 'LOT-CUMP-001', 
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 100, 100,
     '2025-12-15', '2026-01-01', 10.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 100,
    cout_unitaire = 10.0000,
    statut = 'DISPONIBLE';

-- LOT 2 : 50 unites a 12 EUR (entree le 15/01/2026)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('d2222222-2222-2222-2222-222222222222', 'LOT-CUMP-002', 
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 50, 50,
     '2026-01-10', '2026-01-15', 12.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 50,
    cout_unitaire = 12.0000,
    statut = 'DISPONIBLE';

-- === LOTS POUR ARTICLE FIFO ===
-- LOT 1 : 100 unites a 10 EUR (entree le 01/01/2026) - LE PLUS ANCIEN
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('e1111111-1111-1111-1111-111111111111', 'LOT-FIFO-001', 
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 100, 100,
     '2025-12-20', '2026-01-01', 10.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 100,
    cout_unitaire = 10.0000,
    statut = 'DISPONIBLE';

-- LOT 2 : 80 unites a 15 EUR (entree le 10/01/2026)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('e2222222-2222-2222-2222-222222222222', 'LOT-FIFO-002', 
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 80, 80,
     '2026-01-05', '2026-01-10', 15.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 80,
    cout_unitaire = 15.0000,
    statut = 'DISPONIBLE';

-- LOT 3 : 50 unites a 20 EUR (entree le 20/01/2026) - LE PLUS RECENT
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id)
VALUES 
    ('e3333333-3333-3333-3333-333333333333', 'LOT-FIFO-003', 
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 50, 50,
     '2026-01-15', '2026-01-20', 20.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 50,
    cout_unitaire = 20.0000,
    statut = 'DISPONIBLE';

-- === LOTS POUR ARTICLE FEFO ===
-- LOT 1 : 60 unites, peremption 01/03/2026 (expire en premier)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('f1111111-1111-1111-1111-111111111111', 'LOT-FEFO-001', 
     'cccccccc-cccc-cccc-cccc-cccccccccccc', 60, 60,
     '2026-01-01', '2026-01-05', '2026-03-01', 8.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 60,
    cout_unitaire = 8.0000,
    date_peremption = '2026-03-01',
    statut = 'DISPONIBLE';

-- LOT 2 : 40 unites, peremption 15/04/2026
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('f2222222-2222-2222-2222-222222222222', 'LOT-FEFO-002', 
     'cccccccc-cccc-cccc-cccc-cccccccccccc', 40, 40,
     '2026-01-05', '2026-01-10', '2026-04-15', 9.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 40,
    cout_unitaire = 9.0000,
    date_peremption = '2026-04-15',
    statut = 'DISPONIBLE';

-- LOT 3 : 30 unites, peremption 01/06/2026 (expire en dernier)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id)
VALUES 
    ('f3333333-3333-3333-3333-333333333333', 'LOT-FEFO-003', 
     'cccccccc-cccc-cccc-cccc-cccccccccccc', 30, 30,
     '2026-01-10', '2026-01-15', '2026-06-01', 10.0000, 'DISPONIBLE', '66666666-6666-6666-6666-666666666666')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET
    quantite_actuelle = 30,
    cout_unitaire = 10.0000,
    date_peremption = '2026-06-01',
    statut = 'DISPONIBLE';

-- =====================================================
-- 4. STOCKS INITIAUX
-- (colonnes: id, article_id, depot_id, quantite_physique, quantite_theorique, 
--  quantite_reservee, valeur_stock_cump, date_dernier_mouvement)
-- =====================================================

-- Stock pour article CUMP : 150 unites (100 + 50)
-- Valeur attendue : (100 x 10) + (50 x 12) = 1600 EUR
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('11111111-2222-3333-4444-555555555551', 
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 
     '44444444-4444-4444-4444-444444444444',
     150, 150, 1600.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 150,
    quantite_theorique = 150,
    valeur_stock_cump = 1600.00,
    date_dernier_mouvement = NOW();

-- Stock pour article FIFO : 230 unites (100 + 80 + 50)
-- Valeur attendue : (100 x 10) + (80 x 15) + (50 x 20) = 1000 + 1200 + 1000 = 3200 EUR
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('11111111-2222-3333-4444-555555555552', 
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 
     '44444444-4444-4444-4444-444444444444',
     230, 230, 3200.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 230,
    quantite_theorique = 230,
    valeur_stock_cump = 3200.00,
    date_dernier_mouvement = NOW();

-- Stock pour article FEFO : 130 unites (60 + 40 + 30)
-- Valeur attendue : (60 x 8) + (40 x 9) + (30 x 10) = 480 + 360 + 300 = 1140 EUR
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    valeur_stock_cump, date_dernier_mouvement)
VALUES 
    ('11111111-2222-3333-4444-555555555553', 
     'cccccccc-cccc-cccc-cccc-cccccccccccc', 
     '44444444-4444-4444-4444-444444444444',
     130, 130, 1140.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET
    quantite_physique = 130,
    quantite_theorique = 130,
    valeur_stock_cump = 1140.00,
    date_dernier_mouvement = NOW();

-- =====================================================
-- 5. VERIFICATION DES DONNEES
-- =====================================================

-- Verifier la chaine complete : Lot -> Emplacement -> Zone -> Depot
SELECT '=== VERIFICATION CHAINE LOT -> DEPOT ===' as titre;

SELECT 
    l.numero_lot,
    a.code_article,
    a.methode_valorisation,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.statut,
    e.code as emplacement,
    z.code as zone,
    d.code as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%'
ORDER BY a.code_article, l.date_reception;

-- Verifier les stocks
SELECT '=== STOCKS ===' as titre;

SELECT 
    a.code_article,
    a.methode_valorisation,
    s.quantite_theorique,
    s.valeur_stock_cump,
    d.code as depot
FROM stocks s
JOIN articles a ON s.article_id = a.id
JOIN depots d ON s.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%';

-- Calcul valorisation FIFO directe
SELECT '=== VALORISATION FIFO DIRECTE ===' as titre;

SELECT 
    a.code_article,
    SUM(l.quantite_actuelle) as quantite_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valeur_fifo_totale
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%' 
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
  AND d.code = 'TEST-DEP'
GROUP BY a.code_article;

-- =====================================================
-- RESUME DES CALCULS ATTENDUS
-- =====================================================
/*
ARTICLE TEST-CUMP (methode CUMP):
- Stock total : 150 unites
- Valeur totale : 1600 EUR
- CUMP = 1600 / 150 = 10.67 EUR

ARTICLE TEST-FIFO (methode FIFO):
- LOT-FIFO-001 : 100 unites a 10 EUR (01/01/2026) - PREMIER A SORTIR
- LOT-FIFO-002 : 80 unites a 15 EUR (10/01/2026)
- LOT-FIFO-003 : 50 unites a 20 EUR (20/01/2026) - DERNIER A SORTIR
- Stock total : 230 unites
- Valeur totale FIFO : 3200 EUR

ARTICLE TEST-FEFO (methode FEFO - par date de peremption):
- LOT-FEFO-001 : 60 unites, expire 01/03/2026 - SORT EN PREMIER
- LOT-FEFO-002 : 40 unites, expire 15/04/2026
- LOT-FEFO-003 : 30 unites, expire 01/06/2026 - SORT EN DERNIER
- Stock total : 130 unites
- Valeur totale : 1140 EUR
*/
