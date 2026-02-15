-- =====================================================
-- SCRIPT DE NETTOYAGE DES DONNEES DE TEST VALORISATION
-- Version corrigee compatible avec le schema 2_stock_Ranto.sql
-- =====================================================

-- Supprimer dans l'ordre des dependances

-- 1. Mouvements de stock
DELETE FROM mouvements_stock 
WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');

-- 2. Lots
DELETE FROM lots 
WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');

-- 3. Stocks
DELETE FROM stocks 
WHERE article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');

-- 4. Articles
DELETE FROM articles WHERE code_article LIKE 'TEST-%';

-- 5. Emplacements
DELETE FROM emplacements WHERE code LIKE 'TEST-%';

-- 6. Zones de stockage
DELETE FROM zones_stockage WHERE code LIKE 'TEST-%';

-- 7. Depots
DELETE FROM depots WHERE code LIKE 'TEST-%';

-- 8. Sites
DELETE FROM sites WHERE code LIKE 'TEST-%';

-- 9. Categories (table avec S)
DELETE FROM categories_articles WHERE code LIKE 'TEST-%';

-- 10. Unites de mesure
DELETE FROM unites_mesure WHERE code LIKE 'TEST-%';

-- Verification
SELECT 'Nettoyage termine. Verification:' as message;

SELECT 'Articles restants TEST-*:' as check_type, COUNT(*) as count 
FROM articles WHERE code_article LIKE 'TEST-%';

SELECT 'Lots restants TEST-*:' as check_type, COUNT(*) as count 
FROM lots WHERE numero_lot LIKE 'LOT-%-00%' 
AND article_id IN (SELECT id FROM articles WHERE code_article LIKE 'TEST-%');
