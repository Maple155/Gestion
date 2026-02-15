-- =====================================================
-- SCRIPT DE DIAGNOSTIC VALORISATION FIFO
-- Exécutez après 01_test_data_valorisation.sql
-- =====================================================

-- 1. Vérifier les articles de test
SELECT '=== ARTICLES ===' as section;
SELECT id, code_article, libelle, methode_valorisation 
FROM articles 
WHERE code_article LIKE 'ART-%'
ORDER BY code_article;

-- 2. Vérifier la chaîne complète Lot → Emplacement → Zone → Dépôt
SELECT '=== CHAÎNE COMPLÈTE LOTS ===' as section;
SELECT 
    l.numero_lot,
    a.code_article,
    a.methode_valorisation,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.statut,
    l.date_reception,
    l.date_peremption,
    e.code as emplacement,
    z.code as zone,
    d.code as depot,
    CASE 
        WHEN e.id IS NULL THEN '❌ PAS D EMPLACEMENT'
        WHEN z.id IS NULL THEN '❌ PAS DE ZONE'
        WHEN d.id IS NULL THEN '❌ PAS DE DEPOT'
        ELSE '✅ OK'
    END as statut_chaine
FROM lots l
JOIN articles a ON l.article_id = a.id
LEFT JOIN emplacements e ON l.emplacement_id = e.id
LEFT JOIN zones_stockage z ON e.zone_id = z.id
LEFT JOIN depots d ON z.depot_id = d.id
WHERE l.statut = 'DISPONIBLE' AND l.quantite_actuelle > 0
ORDER BY a.code_article, l.date_reception;

-- 3. Vérifier les stocks
SELECT '=== STOCKS ===' as section;
SELECT 
    s.id,
    a.code_article,
    d.code as depot,
    s.quantite_theorique,
    s.quantite_disponible,
    s.valeur_stock_cump,
    s.cout_unitaire_moyen
FROM stocks s
JOIN articles a ON s.article_id = a.id
JOIN depots d ON s.depot_id = d.id
ORDER BY a.code_article;

-- 4. SIMULATION REQUÊTE FIFO (exactement comme le code Java)
SELECT '=== SIMULATION REQUÊTE FIFO ===' as section;
SELECT 
    l.id,
    l.numero_lot,
    a.code_article,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.date_reception,
    d.code as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article = 'ART-FIFO-001'
  AND d.id = 'dddddddd-0001-0001-0001-000000000001'  -- DEP-CENTRAL
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
ORDER BY l.date_reception ASC;

-- 5. CALCUL VALORISATION FIFO MANUEL
SELECT '=== CALCUL FIFO POUR ART-FIFO-001 ===' as section;
WITH lots_fifo AS (
    SELECT 
        l.numero_lot,
        l.quantite_actuelle,
        l.cout_unitaire,
        l.date_reception,
        l.quantite_actuelle * l.cout_unitaire as valeur_lot
    FROM lots l
    JOIN articles a ON l.article_id = a.id
    JOIN emplacements e ON l.emplacement_id = e.id
    JOIN zones_stockage z ON e.zone_id = z.id
    JOIN depots d ON z.depot_id = d.id
    WHERE a.code_article = 'ART-FIFO-001'
      AND d.id = 'dddddddd-0001-0001-0001-000000000001'
      AND l.statut = 'DISPONIBLE'
      AND l.quantite_actuelle > 0
    ORDER BY l.date_reception ASC
)
SELECT 
    numero_lot,
    quantite_actuelle as qte,
    cout_unitaire as prix_unit,
    valeur_lot,
    SUM(valeur_lot) OVER (ORDER BY date_reception) as valeur_cumul
FROM lots_fifo;

-- 6. TOTAL VALORISATION FIFO
SELECT '=== TOTAL VALORISATION FIFO ART-FIFO-001 ===' as section;
SELECT 
    a.code_article,
    SUM(l.quantite_actuelle) as quantite_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valorisation_fifo,
    AVG(l.cout_unitaire) as cout_moyen_simple
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article = 'ART-FIFO-001'
  AND d.id = 'dddddddd-0001-0001-0001-000000000001'
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
GROUP BY a.code_article;

-- 7. SIMULATION REQUÊTE FEFO (tri par date péremption)
SELECT '=== SIMULATION REQUÊTE FEFO ===' as section;
SELECT 
    l.id,
    l.numero_lot,
    a.code_article,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.date_peremption,
    d.code as depot
FROM lots l
JOIN articles a ON l.article_id = a.id
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE a.code_article = 'ART-FEFO-001'
  AND d.id = 'dddddddd-0001-0001-0001-000000000002'  -- DEP-FRAIS
  AND l.statut = 'DISPONIBLE'
  AND l.quantite_actuelle > 0
ORDER BY l.date_peremption ASC NULLS LAST;

-- 8. Résumé final
SELECT '=== RÉSUMÉ VALORISATION ===' as section;
SELECT 
    a.code_article,
    a.methode_valorisation,
    d.code as depot,
    COUNT(l.id) as nb_lots,
    SUM(l.quantite_actuelle) as qte_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valeur_totale,
    CASE 
        WHEN COUNT(l.id) > 0 THEN '✅ FIFO OK'
        ELSE '❌ FIFO RETOURNERA 0'
    END as statut_fifo
FROM articles a
LEFT JOIN lots l ON a.id = l.article_id AND l.statut = 'DISPONIBLE' AND l.quantite_actuelle > 0
LEFT JOIN emplacements e ON l.emplacement_id = e.id
LEFT JOIN zones_stockage z ON e.zone_id = z.id
LEFT JOIN depots d ON z.depot_id = d.id
LEFT JOIN stocks s ON a.id = s.article_id AND d.id = s.depot_id
WHERE a.code_article LIKE 'ART-%'
GROUP BY a.code_article, a.methode_valorisation, d.code
ORDER BY a.code_article;
