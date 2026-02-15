-- =====================================================
-- SCRIPT DE DIAGNOSTIC VALORISATION FIFO/CUMP/FEFO
-- Version corrigee compatible avec le schema 2_stock_Ranto.sql
-- Executez ce script pour identifier pourquoi les valeurs sont a 0
-- =====================================================

-- =====================================================
-- 1. VERIFIER LES ARTICLES DE TEST
-- =====================================================
SELECT '=== 1. ARTICLES DE TEST ===' as diagnostic;
SELECT id, code_article, libelle, methode_valorisation, gestion_par_lot, actif
FROM articles 
WHERE code_article LIKE 'TEST-%'
ORDER BY code_article;

-- =====================================================
-- 2. VERIFIER LA HIERARCHIE COMPLETE
-- =====================================================
SELECT '=== 2. HIERARCHIE DEPOT -> ZONE -> EMPLACEMENT ===' as diagnostic;

SELECT 
    d.id as depot_id,
    d.code as depot_code,
    d.nom as depot_nom,
    z.id as zone_id,
    z.code as zone_code,
    e.id as emplacement_id,
    e.code as emplacement_code
FROM depots d
LEFT JOIN zones_stockage z ON z.depot_id = d.id
LEFT JOIN emplacements e ON e.zone_id = z.id
WHERE d.code LIKE 'TEST-%' OR d.code LIKE 'DPT_%'
ORDER BY d.code, z.code, e.code;

-- =====================================================
-- 3. VERIFIER LES LOTS AVEC CHAINE COMPLETE
-- =====================================================
SELECT '=== 3. LOTS AVEC CHAINE COMPLETE (LOT -> EMPLACEMENT -> ZONE -> DEPOT) ===' as diagnostic;

SELECT 
    l.id as lot_id,
    l.numero_lot,
    a.code_article,
    a.methode_valorisation,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.statut,
    l.emplacement_id,
    CASE WHEN e.id IS NULL THEN '!! EMPLACEMENT MANQUANT !!' ELSE e.code END as emplacement,
    CASE WHEN z.id IS NULL THEN '!! ZONE MANQUANTE !!' ELSE z.code END as zone,
    CASE WHEN d.id IS NULL THEN '!! DEPOT MANQUANT !!' ELSE d.code END as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
LEFT JOIN emplacements e ON l.emplacement_id = e.id
LEFT JOIN zones_stockage z ON e.zone_id = z.id
LEFT JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%'
ORDER BY a.code_article, l.date_reception;

-- =====================================================
-- 4. PROBLEME PRINCIPAL : LOTS SANS EMPLACEMENT
-- =====================================================
SELECT '=== 4. LOTS SANS EMPLACEMENT (CAUSE DU PROBLEME!) ===' as diagnostic;

SELECT 
    l.id,
    l.numero_lot,
    a.code_article,
    l.quantite_actuelle,
    l.emplacement_id,
    CASE 
        WHEN l.emplacement_id IS NULL THEN 'PAS D EMPLACEMENT - FIFO = 0!'
        ELSE 'OK'
    END as probleme
FROM lots l
JOIN articles a ON l.article_id = a.id
WHERE l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
  AND (l.emplacement_id IS NULL OR l.emplacement_id NOT IN (SELECT id FROM emplacements))
ORDER BY a.code_article;

-- =====================================================
-- 5. VERIFIER LES STOCKS
-- =====================================================
SELECT '=== 5. STOCKS ===' as diagnostic;

SELECT 
    s.id,
    a.code_article,
    a.methode_valorisation,
    d.code as depot_code,
    s.quantite_theorique,
    s.quantite_disponible,
    s.valeur_stock_cump,
    s.cout_unitaire_moyen
FROM stocks s
JOIN articles a ON s.article_id = a.id
JOIN depots d ON s.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%';

-- =====================================================
-- 6. SIMULATION REQUETE FIFO (CE QUE LE CODE JAVA FAIT)
-- =====================================================
SELECT '=== 6. SIMULATION REQUETE FIFO POUR ARTICLE TEST-FIFO ===' as diagnostic;

-- Cette requete simule exactement ce que fait LotRepository.findByArticleIdAndDepotOrderByDateReceptionAsc
SELECT 
    l.id,
    l.numero_lot,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.date_reception,
    e.code as emplacement,
    z.code as zone,
    d.code as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article = 'TEST-FIFO'
  AND d.id = '44444444-4444-4444-4444-444444444444'
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
ORDER BY l.date_reception ASC;

-- =====================================================
-- 7. CALCUL VALORISATION FIFO ATTENDU
-- =====================================================
SELECT '=== 7. CALCUL VALORISATION FIFO ATTENDU ===' as diagnostic;

SELECT 
    a.code_article,
    a.methode_valorisation,
    COUNT(l.id) as nb_lots,
    SUM(l.quantite_actuelle) as quantite_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valeur_fifo_totale,
    CASE 
        WHEN SUM(l.quantite_actuelle) > 0 
        THEN ROUND(SUM(l.quantite_actuelle * l.cout_unitaire) / SUM(l.quantite_actuelle), 2)
        ELSE 0 
    END as cout_moyen
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article LIKE 'TEST-%'
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
GROUP BY a.code_article, a.methode_valorisation
ORDER BY a.code_article;

-- =====================================================
-- 8. COMPARAISON AVEC TOUS LES LOTS (SANS FILTRE DEPOT)
-- =====================================================
SELECT '=== 8. TOUS LES LOTS DISPONIBLES (SANS FILTRE DEPOT) ===' as diagnostic;

SELECT 
    a.code_article,
    COUNT(l.id) as nb_lots,
    SUM(l.quantite_actuelle) as quantite_totale
FROM lots l
JOIN articles a ON l.article_id = a.id
WHERE a.code_article LIKE 'TEST-%'
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
GROUP BY a.code_article;

-- =====================================================
-- 9. VERIFIER SI LES IDs D'EMPLACEMENT EXISTENT
-- =====================================================
SELECT '=== 9. VERIFICATION EXISTENCE EMPLACEMENTS ===' as diagnostic;

SELECT 
    l.numero_lot,
    l.emplacement_id,
    CASE 
        WHEN e.id IS NOT NULL THEN 'EXISTE'
        ELSE 'N EXISTE PAS'
    END as emplacement_existe
FROM lots l
JOIN articles a ON l.article_id = a.id
LEFT JOIN emplacements e ON l.emplacement_id = e.id
WHERE a.code_article LIKE 'TEST-%';

-- =====================================================
-- 10. LISTE DES DEPOTS DISPONIBLES
-- =====================================================
SELECT '=== 10. LISTE DES DEPOTS ===' as diagnostic;

SELECT id, code, nom, type FROM depots ORDER BY code;
