-- ============================================================================
-- PARTIE 11 : VUES UTILES
-- ============================================================================

-- Vue : Stock disponible avec détails article
CREATE VIEW v_stock_disponible AS
SELECT 
    s.id,
    s.article_id,
    a.code_article,
    a.libelle AS article_libelle,
    a.code_barre,
    s.depot_id,
    d.code AS depot_code,
    d.nom AS depot_nom,
    s.quantite_theorique,
    s.quantite_reservee,
    s.quantite_disponible,
    s.cout_unitaire_moyen,
    s.valeur_stock_cump,
    a.stock_minimum,
    a.stock_securite,
    CASE 
        WHEN s.quantite_disponible <= 0 THEN 'RUPTURE'
        WHEN s.quantite_disponible <= a.stock_minimum THEN 'ALERTE'
        WHEN s.quantite_disponible <= a.stock_securite THEN 'ATTENTION'
        ELSE 'OK'
    END AS statut_stock
FROM stocks s
JOIN articles a ON s.article_id = a.id
JOIN depots d ON s.depot_id = d.id
WHERE a.actif = TRUE;

-- Vue : Lots avec dates de péremption
CREATE VIEW v_lots_peremption AS
SELECT 
    l.id,
    l.numero_lot,
    l.article_id,
    a.code_article,
    a.libelle,
    l.quantite_actuelle,
    l.date_peremption,
    l.dluo,
    l.statut,
    e.code AS emplacement,
    CASE 
        WHEN l.date_peremption < CURRENT_DATE THEN 'PERIME'
        WHEN l.date_peremption <= CURRENT_DATE + INTERVAL '7 days' THEN 'URGENT'
        WHEN l.date_peremption <= CURRENT_DATE + INTERVAL '30 days' THEN 'ATTENTION'
        ELSE 'OK'
    END AS alerte_peremption
FROM lots l
JOIN articles a ON l.article_id = a.id
LEFT JOIN emplacements e ON l.emplacement_id = e.id
WHERE l.quantite_actuelle > 0 
  AND l.statut = 'DISPONIBLE'
  AND l.date_peremption IS NOT NULL;

-- Vue : Mouvements avec détails
CREATE VIEW v_mouvements_detailles AS
SELECT 
    m.id,
    m.reference,
    m.date_mouvement,
    m.date_comptable,
    tm.libelle AS type_mouvement,
    tm.sens,
    a.code_article,
    a.libelle AS article_libelle,
    d.nom AS depot_nom,
    m.quantite,
    m.cout_unitaire,
    m.valeur_mouvement,
    l.numero_lot,
    m.motif,
    m.statut,
    m.created_at
FROM mouvements_stock m
JOIN types_mouvement tm ON m.type_mouvement_id = tm.id
JOIN articles a ON m.article_id = a.id
JOIN depots d ON m.depot_id = d.id
LEFT JOIN lots l ON m.lot_id = l.id;