-- =====================================================
-- SCRIPT DE NETTOYAGE COMPLET DES DONNÉES
-- Efface toutes les données sans supprimer les tables
-- Ordre respectant les contraintes de clés étrangères
-- =====================================================

-- Désactiver temporairement les triggers pour éviter les conflits
SET session_replication_role = 'replica';

-- =====================================================
-- PARTIE 1 : TABLES DU MODULE VENTE (si elles existent)
-- =====================================================
TRUNCATE TABLE lignes_avoir CASCADE;
TRUNCATE TABLE avoirs CASCADE;
TRUNCATE TABLE paiements_client CASCADE;
TRUNCATE TABLE lignes_facture_client CASCADE;
TRUNCATE TABLE factures_client CASCADE;
TRUNCATE TABLE lignes_livraison_client CASCADE;
TRUNCATE TABLE livraisons_client CASCADE;
TRUNCATE TABLE lignes_commande_client CASCADE;
TRUNCATE TABLE commandes_client CASCADE;
TRUNCATE TABLE lignes_devis CASCADE;
TRUNCATE TABLE devis CASCADE;
TRUNCATE TABLE clients CASCADE;

-- =====================================================
-- PARTIE 2 : TABLES DU MODULE ACHAT (si elles existent)
-- =====================================================
TRUNCATE TABLE lignes_facture_fournisseur CASCADE;
TRUNCATE TABLE factures_fournisseur CASCADE;
TRUNCATE TABLE lignes_bon_reception CASCADE;
TRUNCATE TABLE bons_reception CASCADE;
TRUNCATE TABLE lignes_bon_commande CASCADE;
TRUNCATE TABLE bons_commande CASCADE;
TRUNCATE TABLE comparatifs_prix CASCADE;
TRUNCATE TABLE lignes_demande_achat CASCADE;
TRUNCATE TABLE demandes_achat CASCADE;
TRUNCATE TABLE fournisseurs CASCADE;

-- =====================================================
-- PARTIE 3 : TABLES DU MODULE STOCK
-- =====================================================

-- Historique et clôtures
TRUNCATE TABLE clotures_mensuelles CASCADE;
TRUNCATE TABLE historique_couts CASCADE;

-- Ajustements et inventaires
TRUNCATE TABLE ajustements_inventaire CASCADE;
TRUNCATE TABLE lignes_inventaire CASCADE;
TRUNCATE TABLE inventaires CASCADE;

-- Transferts
TRUNCATE TABLE lignes_transfert CASCADE;
TRUNCATE TABLE transferts CASCADE;

-- Réservations
TRUNCATE TABLE reservations_stock CASCADE;

-- Mouvements de stock
TRUNCATE TABLE mouvements_stock CASCADE;

-- Séries et lots
TRUNCATE TABLE series CASCADE;
TRUNCATE TABLE lots CASCADE;

-- Stock
TRUNCATE TABLE stocks CASCADE;

-- Articles et fournisseurs
TRUNCATE TABLE articles_fournisseurs CASCADE;
TRUNCATE TABLE articles CASCADE;

-- Emplacements, zones, dépôts
TRUNCATE TABLE emplacements CASCADE;
TRUNCATE TABLE zones_stockage CASCADE;
TRUNCATE TABLE depots CASCADE;
TRUNCATE TABLE sites CASCADE;

-- Catégories et unités
TRUNCATE TABLE categories_articles CASCADE;
TRUNCATE TABLE unites_mesure CASCADE;

-- Types de mouvement (garder les données de référence)
-- Ne pas truncate types_mouvement car c'est une table de référence

-- Utilisateurs (optionnel - décommenter si nécessaire)
-- TRUNCATE TABLE utilisateurs CASCADE;

-- =====================================================
-- PARTIE 4 : RÉINITIALISER LES SÉQUENCES
-- =====================================================
ALTER SEQUENCE IF EXISTS seq_mouvement_stock RESTART WITH 1;
ALTER SEQUENCE IF EXISTS seq_lot RESTART WITH 1;
ALTER SEQUENCE IF EXISTS seq_reservation_stock RESTART WITH 1;
ALTER SEQUENCE IF EXISTS seq_transfert RESTART WITH 1;
ALTER SEQUENCE IF EXISTS seq_inventaire RESTART WITH 1;

-- Réactiver les triggers
SET session_replication_role = 'origin';

-- =====================================================
-- PARTIE 5 : VÉRIFICATION
-- =====================================================
DO $$
DECLARE
    table_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO table_count FROM articles;
    RAISE NOTICE 'Articles restants: %', table_count;
    
    SELECT COUNT(*) INTO table_count FROM lots;
    RAISE NOTICE 'Lots restants: %', table_count;
    
    SELECT COUNT(*) INTO table_count FROM stocks;
    RAISE NOTICE 'Stocks restants: %', table_count;
    
    SELECT COUNT(*) INTO table_count FROM mouvements_stock;
    RAISE NOTICE 'Mouvements restants: %', table_count;
    
    RAISE NOTICE '✅ Nettoyage terminé avec succès!';
END $$;
