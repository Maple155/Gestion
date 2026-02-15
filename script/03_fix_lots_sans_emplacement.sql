-- ============================================================================
-- Script de correction : Assigner un emplacement aux lots sans emplacement
-- Problème identifié : Les lots créés lors des réceptions n'avaient pas d'emplacement
-- Conséquence : Les requêtes FIFO/FEFO retournaient 0 (car elles filtrent par emplacement.zone.depot)
-- ============================================================================

-- 1. Vérifier combien de lots n'ont pas d'emplacement AVANT
SELECT 'Lots sans emplacement AVANT correction:' as info;
SELECT COUNT(*) as nb_lots_sans_emplacement 
FROM lots 
WHERE emplacement_id IS NULL;

-- 2. Lister les lots concernés
SELECT 'Détail des lots sans emplacement:' as info;
SELECT 
    l.id,
    l.numero_lot,
    a.code_article,
    a.libelle as designation,
    l.quantite_actuelle,
    l.cout_unitaire,
    l.statut
FROM lots l
JOIN articles a ON l.article_id = a.id
WHERE l.emplacement_id IS NULL;

-- 3. Trouver un emplacement par défaut dans le dépôt central
SELECT 'Emplacement par défaut disponible:' as info;
SELECT 
    e.id as emplacement_id,
    e.code as emplacement_code,
    z.code as zone_code,
    d.nom as depot_nom
FROM emplacements e
JOIN zones_stockage z ON e.zone_id = z.id
JOIN depots d ON z.depot_id = d.id
WHERE e.actif = true
AND d.code = 'DEP-CENTRAL'
LIMIT 1;

-- 4. CORRECTION : Assigner l'emplacement par défaut aux lots sans emplacement
UPDATE lots
SET emplacement_id = (
    SELECT e.id 
    FROM emplacements e
    JOIN zones_stockage z ON e.zone_id = z.id
    JOIN depots d ON z.depot_id = d.id
    WHERE e.actif = true
    AND d.code = 'DEP-CENTRAL'
    LIMIT 1
)
WHERE emplacement_id IS NULL;

-- 5. Vérification après correction
SELECT 'Lots sans emplacement APRÈS correction:' as info;
SELECT COUNT(*) as nb_lots_sans_emplacement 
FROM lots 
WHERE emplacement_id IS NULL;

-- 6. Vérification que les lots sont maintenant trouvables par FIFO
SELECT 'Vérification FIFO - Lots disponibles par dépôt:' as info;
SELECT 
    d.nom as depot,
    a.code_article,
    a.libelle as designation,
    COUNT(l.id) as nb_lots,
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
