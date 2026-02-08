-- Nettoyage
TRUNCATE TABLE utilisateurs RESTART IDENTITY CASCADE;

INSERT INTO utilisateurs (id, username, email, password, nom, prenom, role, service, poste, actif)
VALUES 
-- ADMIN
(gen_random_uuid(), 'admin_erp', 'admin@erp.com', 'admin123', 'SYSTÈME', 'Admin', 'ADMIN', 'IT', 'Administrateur SI', TRUE),

-- DEMANDEUR
(gen_random_uuid(), 'jean_demandeur', 'j.dupont@erp.com', 'pass123', 'DUPONT', 'Jean', 'DEMANDEUR', 'Maintenance', 'Chef d''équipe', TRUE),

-- ACHETEUR
(gen_random_uuid(), 'anne_acheteur', 'a.legrand@erp.com', 'pass123', 'LEGRAND', 'Anne', 'ACHETEUR', 'Achats', 'Acheteur Senior', TRUE),

-- RESPONSABLE ACHATS
(gen_random_uuid(), 'marc_resp_achats', 'm.boss@erp.com', 'pass123', 'BOSS', 'Marc', 'RESPONSABLE_ACHATS', 'Achats', 'Responsable Achats', TRUE),

-- VALIDEURS (Corrigés pour correspondre à l'Enum Java APPROBATEUR_...)
(gen_random_uuid(), 'paul_appro1', 'p.appro1@erp.com', 'pass123', 'VALIDE', 'Paul', 'APPROBATEUR_N1', 'Direction', 'Manager N1', TRUE),
(gen_random_uuid(), 'julie_appro2', 'j.appro2@erp.com', 'pass123', 'VALIDE', 'Julie', 'APPROBATEUR_N2', 'Direction', 'Manager N2', TRUE),
(gen_random_uuid(), 'luc_appro3', 'l.appro3@erp.com', 'pass123', 'VALIDE', 'Luc', 'APPROBATEUR_N3', 'Direction', 'Manager N3', TRUE),

-- DAF
(gen_random_uuid(), 'robert_daf', 'r.argent@erp.com', 'pass123', 'ARGENT', 'Robert', 'DAF', 'Finance', 'Directeur Financier', TRUE),

-- DG
(gen_random_uuid(), 'dir_general', 'dg@erp.com', 'pass123', 'LEGRAND', 'Paul', 'DG', 'Direction Generale', 'Directeur Général', TRUE),

-- COMPTABLE
(gen_random_uuid(), 'comptable_achat', 'c.compta@erp.com', 'pass123', 'CHIFFRE', 'Alice', 'COMPTABLE', 'Comptabilité', 'Comptable Fournisseur', TRUE),

-- MAGASIN
(gen_random_uuid(), 'pierre_magasin', 'p.martin@erp.com', 'pass123', 'MARTIN', 'Pierre', 'GESTIONNAIRE_STOCK', 'Logistique', 'Magasinier', TRUE);