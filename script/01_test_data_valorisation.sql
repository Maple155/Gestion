-- =====================================================
-- DONNÉES DE TEST COMPLÈTES POUR VALORISATION FIFO/CUMP/FEFO
-- Ce script crée une chaîne complète : 
-- Lot → Emplacement → Zone → Dépôt (pour que FIFO fonctionne!)
-- =====================================================

-- =====================================================
-- 1. DONNÉES DE BASE
-- =====================================================

-- 1.1 Utilisateurs
INSERT INTO utilisateurs (id, username, email, password, nom, prenom, role, telephone, poste, service) VALUES
('11111111-1111-1111-1111-111111111111', 'admin', 'admin@test.com', 'admin', 'Admin', 'System', 'ADMIN', '0341234567', 'Administrateur', 'IT'),
('22222222-2222-2222-2222-222222222222', 'stock', 'stock@test.com', 'stock', 'Rakoto', 'Jean', 'RESPONSABLE_STOCK', '0341234568', 'Responsable Stock', 'Logistique')
ON CONFLICT (username) DO NOTHING;

-- 1.2 Unités de mesure
INSERT INTO unites_mesure (id, code, libelle, type) VALUES
('aaaaaaaa-0001-0001-0001-000000000001', 'UNITE', 'Unite', 'QUANTITE'),
('aaaaaaaa-0001-0001-0001-000000000002', 'KG', 'Kilogramme', 'POIDS'),
('aaaaaaaa-0001-0001-0001-000000000003', 'L', 'Litre', 'VOLUME'),
('aaaaaaaa-0001-0001-0001-000000000004', 'CARTON', 'Carton', 'QUANTITE'),
('aaaaaaaa-0001-0001-0001-000000000005', 'BOITE', 'Boite', 'QUANTITE')
ON CONFLICT (code) DO NOTHING;

-- 1.3 Catégories d'articles
INSERT INTO categories_articles (id, code, libelle, description, methode_valorisation, actif) VALUES
('bbbbbbbb-0001-0001-0001-000000000001', 'ELEC', 'Electronique', 'Composants electroniques', 'FIFO', true),
('bbbbbbbb-0001-0001-0001-000000000002', 'ALIM', 'Alimentaire', 'Produits alimentaires', 'FEFO', true),
('bbbbbbbb-0001-0001-0001-000000000003', 'FOUR', 'Fournitures', 'Fournitures de bureau', 'CUMP', true),
('bbbbbbbb-0001-0001-0001-000000000004', 'PIECE', 'Pieces detachees', 'Pieces mecaniques', 'CUMP', true)
ON CONFLICT (code) DO NOTHING;

-- 1.4 Sites
INSERT INTO sites (id, code, nom, adresse, ville, pays, actif) VALUES
('cccccccc-0001-0001-0001-000000000001', 'SITE-TANA', 'Site Antananarivo', 'Analakely', 'Antananarivo', 'Madagascar', true),
('cccccccc-0001-0001-0001-000000000002', 'SITE-TANA2', 'Site Ankorondrano', 'Ankorondrano', 'Antananarivo', 'Madagascar', true)
ON CONFLICT (code) DO NOTHING;

-- 1.5 Dépôts
INSERT INTO depots (id, site_id, code, nom, type, capacite_m3, actif) VALUES
('dddddddd-0001-0001-0001-000000000001', 'cccccccc-0001-0001-0001-000000000001', 'DEP-CENTRAL', 'Depot Central', 'GENERAL', 5000.00, true),
('dddddddd-0001-0001-0001-000000000002', 'cccccccc-0001-0001-0001-000000000001', 'DEP-FRAIS', 'Depot Produits Frais', 'GENERAL', 500.00, true),
('dddddddd-0001-0001-0001-000000000003', 'cccccccc-0001-0001-0001-000000000002', 'DEP-SECOND', 'Depot Secondaire', 'GENERAL', 2000.00, true)
ON CONFLICT (code) DO NOTHING;

-- 1.6 Zones de stockage (liées aux dépôts)
INSERT INTO zones_stockage (id, depot_id, code, libelle, type, capacite_m3, temperature_min, temperature_max) VALUES
-- Zones du dépôt central
('eeeeeeee-0001-0001-0001-000000000001', 'dddddddd-0001-0001-0001-000000000001', 'ZONE-A', 'Zone A - Reception', 'RECEPTION', 500.00, NULL, NULL),
('eeeeeeee-0001-0001-0001-000000000002', 'dddddddd-0001-0001-0001-000000000001', 'ZONE-B', 'Zone B - Stockage', 'STOCKAGE', 3000.00, 15.00, 25.00),
('eeeeeeee-0001-0001-0001-000000000003', 'dddddddd-0001-0001-0001-000000000001', 'ZONE-C', 'Zone C - Expedition', 'EXPEDITION', 500.00, NULL, NULL),
-- Zones du dépôt frais
('eeeeeeee-0001-0001-0001-000000000004', 'dddddddd-0001-0001-0001-000000000002', 'ZONE-FROID', 'Zone Refrigeree', 'STOCKAGE', 400.00, 2.00, 8.00),
-- Zones du dépôt secondaire
('eeeeeeee-0001-0001-0001-000000000005', 'dddddddd-0001-0001-0001-000000000003', 'ZONE-SEC', 'Zone Stockage Secondaire', 'STOCKAGE', 1500.00, 10.00, 30.00)
ON CONFLICT (depot_id, code) DO NOTHING;

-- 1.7 Emplacements (liés aux zones)
INSERT INTO emplacements (id, zone_id, code, allee, travee, niveau, position, capacite_volume_m3, actif) VALUES
-- Emplacements Zone B (Stockage principal du dépôt central)
('ffffffff-0001-0001-0001-000000000001', 'eeeeeeee-0001-0001-0001-000000000002', 'B-01-01-01', 'B', '01', '01', '01', 5.00, true),
('ffffffff-0001-0001-0001-000000000002', 'eeeeeeee-0001-0001-0001-000000000002', 'B-01-01-02', 'B', '01', '01', '02', 5.00, true),
('ffffffff-0001-0001-0001-000000000003', 'eeeeeeee-0001-0001-0001-000000000002', 'B-01-02-01', 'B', '01', '02', '01', 5.00, true),
('ffffffff-0001-0001-0001-000000000004', 'eeeeeeee-0001-0001-0001-000000000002', 'B-02-01-01', 'B', '02', '01', '01', 5.00, true),
-- Emplacements Zone Froid
('ffffffff-0001-0001-0001-000000000005', 'eeeeeeee-0001-0001-0001-000000000004', 'F-01-01-01', 'F', '01', '01', '01', 2.00, true),
('ffffffff-0001-0001-0001-000000000006', 'eeeeeeee-0001-0001-0001-000000000004', 'F-01-01-02', 'F', '01', '01', '02', 2.00, true),
-- Emplacements Zone Secondaire
('ffffffff-0001-0001-0001-000000000007', 'eeeeeeee-0001-0001-0001-000000000005', 'S-01-01-01', 'S', '01', '01', '01', 10.00, true)
ON CONFLICT (zone_id, code) DO NOTHING;

-- =====================================================
-- 2. ARTICLES DE TEST
-- =====================================================

-- Article FIFO : Composant électronique
INSERT INTO articles (id, code_article, code_barre, libelle, description, categorie_id, unite_mesure_id, 
                      gestion_par_lot, methode_valorisation, stock_minimum, cout_standard, prix_vente_ht, actif) VALUES
('10000000-0001-0001-0001-000000000001', 'ART-FIFO-001', '1234567890123', 'Condensateur 100uF', 
 'Condensateur electrolytique 100uF 25V', 
 'bbbbbbbb-0001-0001-0001-000000000001', 'aaaaaaaa-0001-0001-0001-000000000001',
 true, 'FIFO', 50, 15.00, 25.00, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article CUMP : Fourniture de bureau
INSERT INTO articles (id, code_article, code_barre, libelle, description, categorie_id, unite_mesure_id, 
                      gestion_par_lot, methode_valorisation, stock_minimum, cout_standard, prix_vente_ht, actif) VALUES
('10000000-0001-0001-0001-000000000002', 'ART-CUMP-001', '1234567890124', 'Stylo Bille Bleu', 
 'Stylo a bille encre bleue', 
 'bbbbbbbb-0001-0001-0001-000000000003', 'aaaaaaaa-0001-0001-0001-000000000001',
 true, 'CUMP', 100, 2.00, 5.00, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article FEFO : Produit périssable
INSERT INTO articles (id, code_article, code_barre, libelle, description, categorie_id, unite_mesure_id, 
                      gestion_par_lot, duree_vie_jours, methode_valorisation, stock_minimum, cout_standard, prix_vente_ht, actif) VALUES
('10000000-0001-0001-0001-000000000003', 'ART-FEFO-001', '1234567890125', 'Yaourt Nature', 
 'Yaourt nature pot 125g', 
 'bbbbbbbb-0001-0001-0001-000000000002', 'aaaaaaaa-0001-0001-0001-000000000001',
 true, 30, 'FEFO', 200, 1.50, 3.00, true)
ON CONFLICT (code_article) DO NOTHING;

-- Article supplémentaire FIFO
INSERT INTO articles (id, code_article, code_barre, libelle, description, categorie_id, unite_mesure_id, 
                      gestion_par_lot, methode_valorisation, stock_minimum, cout_standard, prix_vente_ht, actif) VALUES
('10000000-0001-0001-0001-000000000004', 'ART-FIFO-002', '1234567890126', 'Resistance 10K Ohm', 
 'Resistance 10K Ohm 1/4W', 
 'bbbbbbbb-0001-0001-0001-000000000001', 'aaaaaaaa-0001-0001-0001-000000000001',
 true, 'FIFO', 100, 0.50, 1.00, true)
ON CONFLICT (code_article) DO NOTHING;

-- =====================================================
-- 3. LOTS DE TEST (CHAÎNE COMPLÈTE : Lot → Emplacement → Zone → Dépôt)
-- =====================================================

-- === LOTS POUR ARTICLE FIFO (ART-FIFO-001) ===
-- Dépôt: DEP-CENTRAL (dddddddd-0001-0001-0001-000000000001)
-- Emplacement: B-01-01-01 qui est dans Zone-B qui est dans DEP-CENTRAL

-- LOT 1 : 100 unités à 10Ar (le plus ancien - sera utilisé en premier en FIFO)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000001', 'LOT-FIFO-001-A', 
 '10000000-0001-0001-0001-000000000001', 100, 100,
 '2025-11-01', '2025-11-15', 10.0000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000001')  -- Emplacement B-01-01-01
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- LOT 2 : 80 unités à 12Ar (intermédiaire)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000002', 'LOT-FIFO-001-B', 
 '10000000-0001-0001-0001-000000000001', 80, 80,
 '2025-12-01', '2025-12-15', 12.0000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000002')  -- Emplacement B-01-01-02
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- LOT 3 : 50 unités à 15Ar (le plus récent)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000003', 'LOT-FIFO-001-C', 
 '10000000-0001-0001-0001-000000000001', 50, 50,
 '2026-01-01', '2026-01-15', 15.0000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000003')  -- Emplacement B-01-02-01
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- === LOTS POUR ARTICLE CUMP (ART-CUMP-001) ===
-- LOT 1 : 200 unités à 2Ar
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000004', 'LOT-CUMP-001-A', 
 '10000000-0001-0001-0001-000000000002', 200, 200,
 '2025-10-01', '2025-10-15', 2.0000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000001')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- LOT 2 : 100 unités à 2.50Ar
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000005', 'LOT-CUMP-001-B', 
 '10000000-0001-0001-0001-000000000002', 100, 100,
 '2025-12-01', '2025-12-20', 2.5000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000002')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- === LOTS POUR ARTICLE FEFO (ART-FEFO-001) - avec dates de péremption ===
-- LOT 1 : 150 unités, péremption proche (sera utilisé en premier en FEFO)
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000006', 'LOT-FEFO-001-A', 
 '10000000-0001-0001-0001-000000000003', 150, 150,
 '2026-01-01', '2026-01-05', '2026-02-28', 1.5000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000005')  -- Emplacement zone froide
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    date_peremption = EXCLUDED.date_peremption,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- LOT 2 : 100 unités, péremption plus tardive
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000007', 'LOT-FEFO-001-B', 
 '10000000-0001-0001-0001-000000000003', 100, 100,
 '2026-01-15', '2026-01-20', '2026-03-31', 1.6000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000006')  -- Emplacement zone froide
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    date_peremption = EXCLUDED.date_peremption,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- LOT 3 : 80 unités, péremption la plus tardive
INSERT INTO lots (id, numero_lot, article_id, quantite_initiale, quantite_actuelle, 
                  date_fabrication, date_reception, date_peremption, cout_unitaire, statut, emplacement_id) VALUES
('20000000-0001-0001-0001-000000000008', 'LOT-FEFO-001-C', 
 '10000000-0001-0001-0001-000000000003', 80, 80,
 '2026-02-01', '2026-02-05', '2026-04-30', 1.7000, 'DISPONIBLE', 
 'ffffffff-0001-0001-0001-000000000005')
ON CONFLICT (numero_lot, article_id) DO UPDATE SET 
    quantite_actuelle = EXCLUDED.quantite_actuelle,
    cout_unitaire = EXCLUDED.cout_unitaire,
    date_peremption = EXCLUDED.date_peremption,
    statut = EXCLUDED.statut,
    emplacement_id = EXCLUDED.emplacement_id;

-- =====================================================
-- 4. STOCKS (un enregistrement par article/dépôt)
-- =====================================================

-- Stock FIFO article dans dépôt central
-- Total: 100 + 80 + 50 = 230 unités
-- Valeur CUMP: (100*10 + 80*12 + 50*15) / 230 = (1000+960+750)/230 = 2710/230 = 11.78Ar
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    quantite_reservee, valeur_stock_cump, date_dernier_mouvement) VALUES
('30000000-0001-0001-0001-000000000001', 
 '10000000-0001-0001-0001-000000000001',  -- ART-FIFO-001
 'dddddddd-0001-0001-0001-000000000001',  -- DEP-CENTRAL
 230, 230, 0, 2710.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET 
    quantite_physique = EXCLUDED.quantite_physique,
    quantite_theorique = EXCLUDED.quantite_theorique,
    valeur_stock_cump = EXCLUDED.valeur_stock_cump,
    date_dernier_mouvement = EXCLUDED.date_dernier_mouvement;

-- Stock CUMP article dans dépôt central
-- Total: 200 + 100 = 300 unités
-- Valeur CUMP: (200*2 + 100*2.5) / 300 = (400+250)/300 = 650/300 = 2.17Ar
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    quantite_reservee, valeur_stock_cump, date_dernier_mouvement) VALUES
('30000000-0001-0001-0001-000000000002', 
 '10000000-0001-0001-0001-000000000002',  -- ART-CUMP-001
 'dddddddd-0001-0001-0001-000000000001',  -- DEP-CENTRAL
 300, 300, 0, 650.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET 
    quantite_physique = EXCLUDED.quantite_physique,
    quantite_theorique = EXCLUDED.quantite_theorique,
    valeur_stock_cump = EXCLUDED.valeur_stock_cump,
    date_dernier_mouvement = EXCLUDED.date_dernier_mouvement;

-- Stock FEFO article dans dépôt frais
-- Total: 150 + 100 + 80 = 330 unités
-- Valeur: (150*1.5 + 100*1.6 + 80*1.7) = 225+160+136 = 521Ar
INSERT INTO stocks (id, article_id, depot_id, quantite_physique, quantite_theorique, 
                    quantite_reservee, valeur_stock_cump, date_dernier_mouvement) VALUES
('30000000-0001-0001-0001-000000000003', 
 '10000000-0001-0001-0001-000000000003',  -- ART-FEFO-001
 'dddddddd-0001-0001-0001-000000000002',  -- DEP-FRAIS
 330, 330, 0, 521.00, NOW())
ON CONFLICT (article_id, depot_id) DO UPDATE SET 
    quantite_physique = EXCLUDED.quantite_physique,
    quantite_theorique = EXCLUDED.quantite_theorique,
    valeur_stock_cump = EXCLUDED.valeur_stock_cump,
    date_dernier_mouvement = EXCLUDED.date_dernier_mouvement;

-- =====================================================
-- 5. VÉRIFICATION DES DONNÉES
-- =====================================================

-- Vérifier la chaîne complète pour FIFO
DO $$
DECLARE
    lot_count INTEGER;
    chain_ok INTEGER;
BEGIN
    -- Compter les lots avec chaîne complète
    SELECT COUNT(*) INTO chain_ok
    FROM lots l
    JOIN emplacements e ON l.emplacement_id = e.id
    JOIN zones_stockage z ON e.zone_id = z.id
    JOIN depots d ON z.depot_id = d.id
    WHERE l.statut = 'DISPONIBLE' AND l.quantite_actuelle > 0;
    
    SELECT COUNT(*) INTO lot_count FROM lots WHERE statut = 'DISPONIBLE' AND quantite_actuelle > 0;
    
    IF chain_ok = lot_count THEN
        RAISE NOTICE '✅ Tous les % lots ont une chaîne complète (Lot → Emplacement → Zone → Dépôt)', lot_count;
    ELSE
        RAISE WARNING '⚠️ % lots sur % n ont pas de chaîne complète!', lot_count - chain_ok, lot_count;
    END IF;
END $$;

-- Afficher un résumé
SELECT 
    'RÉSUMÉ DES DONNÉES DE TEST' as info,
    (SELECT COUNT(*) FROM articles) as nb_articles,
    (SELECT COUNT(*) FROM lots WHERE statut = 'DISPONIBLE') as nb_lots_disponibles,
    (SELECT COUNT(*) FROM stocks) as nb_stocks,
    (SELECT COUNT(*) FROM depots) as nb_depots,
    (SELECT COUNT(*) FROM emplacements WHERE actif = true) as nb_emplacements;
