-- ============================================================================
-- DONNÉES DE TEST POUR HISTORIQUE DES COÛTS ET CLÔTURES MENSUELLES
-- ============================================================================

-- 1. Table des clôtures mensuelles
INSERT INTO clotures_mensuelles (
    id, annee, mois, 
    date_debut_periode, date_fin_periode, date_cloture,
    cloture_par_id, statut,
    nombre_articles, nombre_articles_valorises, valeur_stock_total,
    nombre_mouvements, valeur_mouvements_entree, valeur_mouvements_sortie,
    ecart_valorisation, taux_couverture
) VALUES
-- Clôture Janvier 2024
('cccccccc-1111-1111-1111-111111111111', 2024, 1, 
    '2024-01-01', '2024-01-31', '2024-02-05 18:30:00',
    '55555555-5555-5555-5555-555555555555', 'VALIDEE',
    8, 8, 20666.50,
    12, 18500.00, 3500.00,
    150.25, 98.5),

-- Clôture Décembre 2023
('cccccccc-2222-2222-2222-222222222222', 2023, 12,
    '2023-12-01', '2023-12-31', '2024-01-05 17:45:00',
    '55555555-5555-5555-5555-555555555555', 'VALIDEE',
    7, 7, 18500.75,
    15, 12000.00, 4500.00,
    200.50, 97.8),

-- Clôture Novembre 2023
('cccccccc-3333-3333-3333-333333333333', 2023, 11,
    '2023-11-01', '2023-11-30', '2023-12-04 19:00:00',
    '55555555-5555-5555-5555-555555555555', 'VALIDEE',
    6, 6, 16500.25,
    10, 8500.00, 3000.00,
    125.75, 99.0),

-- Clôture Octobre 2023 (en cours)
('cccccccc-4444-4444-4444-444444444444', 2023, 10,
    '2023-10-01', '2023-10-31', '2023-11-03 16:20:00',
    '55555555-5555-5555-5555-555555555555', 'CLOTUREE',
    6, 6, 15500.00,
    8, 7000.00, 2500.00,
    100.00, 98.0),

-- Clôture Février 2024 (ouverte)
('cccccccc-5555-5555-5555-555555555555', 2024, 2,
    '2024-02-01', '2024-02-29', CURRENT_TIMESTAMP,
    '55555555-5555-5555-5555-555555555555', 'OUVERTE',
    NULL, NULL, NULL,
    NULL, NULL, NULL,
    NULL, NULL);

-- ============================================================================
-- DONNÉES DE TEST POUR HISTORIQUE DES COÛTS ET CLÔTURES MENSUELLES (CORRIGÉ)
-- ============================================================================

-- 2. Table historique des coûts (exemples pour plusieurs articles et périodes) - VERSION CORRIGÉE
INSERT INTO historique_couts (
    id, article_id, depot_id,
    date_effet, cout_unitaire_moyen, quantite_stock, valeur_stock,
    methode_valorisation, cloture_mensuelle_id, created_by
) VALUES
-- Données pour Janvier 2024
-- Processeurs Intel i7 - Paris
('aaaaaaaa-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111',
    '2024-01-31', 250.00, 25, 6250.00,
    'FIFO', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Processeurs Intel i7 - Lyon
('aaaaaaaa-2222-2222-2222-222222222222', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-3333-3333-3333-333333333333',
    '2024-01-31', 250.00, 15, 3750.00,
    'FIFO', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Carte mère ASUS - Paris
('aaaaaaaa-3333-3333-3333-333333333333', 'c2222222-2222-2222-2222-222222222222', 'ffffffff-1111-1111-1111-111111111111',
    '2024-01-31', 180.00, 30, 5400.00,
    'FIFO', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- PC Portable Dell - Paris
('aaaaaaaa-4444-4444-4444-444444444444', 'c3333333-3333-3333-3333-333333333333', 'ffffffff-1111-1111-1111-111111111111',
    '2024-01-31', 1200.00, 8, 9600.00,
    'CUMP', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Lait UHT - Paris frais
('aaaaaaaa-5555-5555-5555-555555555555', 'c4444444-4444-4444-4444-444444444444', 'ffffffff-2222-2222-2222-222222222222',
    '2024-01-31', 0.85, 150, 127.50,
    'FIFO', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Yaourts - Paris frais
('aaaaaaaa-6666-6666-6666-666666666666', 'c5555555-5555-5555-5555-555555555555', 'ffffffff-2222-2222-2222-222222222222',
    '2024-01-31', 0.35, 300, 105.00,
    'FIFO', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Stylos - Lyon
('aaaaaaaa-7777-7777-7777-777777777777', 'c7777777-7777-7777-7777-777777777777', 'ffffffff-3333-3333-3333-333333333333',
    '2024-01-31', 0.15, 600, 90.00,
    'CUMP', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Cahiers - Lyon
('aaaaaaaa-8888-8888-8888-888888888888', 'c8888888-8888-8888-8888-888888888888', 'ffffffff-3333-3333-3333-333333333333',
    '2024-01-31', 1.20, 120, 144.00,
    'CUMP', 'cccccccc-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555'),

-- Données pour Décembre 2023 (comparaison)
-- Processeurs Intel i7 - Paris (avant réception)
('bbbbbbbb-1111-1111-1111-111111111111', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111',
    '2023-12-31', 245.00, 10, 2450.00,
    'FIFO', 'cccccccc-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555'),

-- Processeurs Intel i7 - Lyon (avant réception)
('bbbbbbbb-2222-2222-2222-222222222222', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-3333-3333-3333-333333333333',
    '2023-12-31', 245.00, 10, 2450.00,
    'FIFO', 'cccccccc-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555'),

-- PC Portable Dell - Paris (avant réception)
('bbbbbbbb-3333-3333-3333-333333333333', 'c3333333-3333-3333-3333-333333333333', 'ffffffff-1111-1111-1111-111111111111',
    '2023-12-31', 1150.00, 3, 3450.00,
    'CUMP', 'cccccccc-2222-2222-2222-222222222222', '55555555-5555-5555-5555-555555555555'),

-- Données pour Novembre 2023
-- Processeurs Intel i7 - Paris
('bbbbbbbb-4444-4444-4444-444444444444', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111',
    '2023-11-30', 240.00, 8, 1920.00,
    'FIFO', 'cccccccc-3333-3333-3333-333333333333', '55555555-5555-5555-5555-555555555555'),

-- Données pour Octobre 2023
-- Processeurs Intel i7 - Paris
('bbbbbbbb-5555-5555-5555-555555555555', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111',
    '2023-10-31', 235.00, 5, 1175.00,
    'FIFO', 'cccccccc-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555'),

-- Lait UHT - Paris frais
('bbbbbbbb-6666-6666-6666-666666666666', 'c4444444-4444-4444-4444-444444444444', 'ffffffff-2222-2222-2222-222222222222',
    '2023-10-31', 0.83, 100, 83.00,
    'FIFO', 'cccccccc-4444-4444-4444-444444444444', '55555555-5555-5555-5555-555555555555'),

-- Données d'évolution des coûts avec lien vers mouvements (exemple)
('bbbbbbbb-7777-7777-7777-777777777777', 'c1111111-1111-1111-1111-111111111111', 'ffffffff-1111-1111-1111-111111111111',
    '2024-01-15', 245.00, 10, 2450.00,
    'FIFO', NULL, '55555555-5555-5555-5555-555555555555');

-- 3. Mise à jour des clôtures mensuelles avec les validateurs
UPDATE clotures_mensuelles 
SET valideur_id = '22222222-2222-2222-2222-222222222222', 
    date_validation = date_cloture + INTERVAL '1 day',
    commentaires = 'Clôture validée après vérification des écarts',
    rapport_generes = 'rapport_valorisation_202401.pdf, rapport_stock_202401.xlsx'
WHERE statut = 'VALIDEE';

-- 4. Exemple de données pour un rejet de clôture
INSERT INTO clotures_mensuelles (
    id, annee, mois, 
    date_debut_periode, date_fin_periode, date_cloture,
    cloture_par_id, statut, valideur_id, date_validation,
    commentaires
) VALUES
('cccccccc-6666-6666-6666-666666666666', 2023, 9,
    '2023-09-01', '2023-09-30', '2023-10-02 15:00:00',
    '55555555-5555-5555-5555-555555555555', 'REJETEE',
    '22222222-2222-2222-2222-222222222222', '2023-10-03 10:30:00',
    'Clôture rejetée : écarts trop importants sur les produits frais. Ré-inventaire nécessaire.');