-- ============================================================================
-- Script de correction URGENT : Assigner un emplacement aux lots sans emplacement
-- EXÉCUTER CE SCRIPT IMMÉDIATEMENT après lecture
-- ============================================================================

-- 1. Diagnostic : Combien de lots n'ont pas d'emplacement ?
SELECT 'DIAGNOSTIC - Lots sans emplacement:' as etape;
SELECT COUNT(*) as nb_lots_sans_emplacement FROM lots WHERE emplacement_id IS NULL;

-- 2. Trouver TOUS les emplacements actifs disponibles
SELECT 'Emplacements actifs disponibles:' as etape;
SELECT 
    e.id as emplacement_id,
    e.code as emplacement_code,
    z.code as zone_code,
    z.id as zone_id,
    d.nom as depot_nom,
    d.id as depot_id,
    d.code as depot_code
FROM emplacements e
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE e.actif = true
LIMIT 5;

-- 3. CORRECTION : Utiliser le PREMIER emplacement actif trouvé (peu importe le dépôt)
UPDATE lots
SET emplacement_id = (
    SELECT e.id 
    FROM emplacements e
    JOIN zones_stockage z ON e.zone_id = z.id
    JOIN depots d ON z.depot_id = d.id
    WHERE e.actif = true
    LIMIT 1
)
WHERE emplacement_id IS NULL;

-- 4. Vérification après correction
SELECT 'APRÈS CORRECTION - Lots sans emplacement:' as etape;
SELECT COUNT(*) as nb_lots_sans_emplacement FROM lots WHERE emplacement_id IS NULL;

-- 5. Vérifier que les lots ont maintenant un emplacement avec la chaîne complète
SELECT 'Vérification chaîne Lot -> Emplacement -> Zone -> Dépôt:' as etape;
SELECT 
    l.numero_lot,
    l.quantite_actuelle,
    l.cout_unitaire,
    e.code as emplacement,
    z.code as zone,
    d.nom as depot
FROM lots l
LEFT JOIN emplacements e ON l.emplacement_id = e.id
LEFT JOIN zones_stockage z ON e.zone_id = z.id
LEFT JOIN depots d ON z.depot_id = d.id
WHERE l.quantite_actuelle > 0
LIMIT 10;

-- 6. Calcul FIFO manuel pour vérification
SELECT 'Calcul FIFO manuel par article:' as etape;
SELECT 
    d.nom as depot,
    a.code_article,
    a.libelle,
    SUM(l.quantite_actuelle) as quantite_totale,
    SUM(l.quantite_actuelle * l.cout_unitaire) as valeur_fifo
FROM lots l
JOIN emplacements e ON l.emplacement_id = e.id
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
JOIN articles a ON l.article_id = a.id
WHERE l.quantite_actuelle > 0
AND l.statut = 'DISPONIBLE'
GROUP BY d.nom, a.code_article, a.libelle
ORDER BY d.nom, a.code_article;
