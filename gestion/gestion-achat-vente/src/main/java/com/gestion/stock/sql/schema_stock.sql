
-- Table des utilisateurs (ajouter dans PARTIE 1 ou créer une section séparée)
CREATE TABLE utilisateurs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(100) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    
    -- Rôles et permissions
    role VARCHAR(50) DEFAULT 'UTILISATEUR', -- ADMIN, MANAGER, RESPONSABLE_STOCK, COMPTABLE, etc.
    actif BOOLEAN DEFAULT TRUE,
    
    -- Informations professionnelles
    telephone VARCHAR(20),
    poste VARCHAR(100),
    service VARCHAR(100),
    
    -- -- Dépôt affecté (pour les responsables stock)
    -- depot_id UUID REFERENCES depots(id),
    
    -- -- Sécurité
    -- dernier_login TIMESTAMP,
    -- login_tentatives INTEGER DEFAULT 0,
    -- compte_verrouille BOOLEAN DEFAULT FALSE,
    -- date_verrouillage TIMESTAMP,
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    -- created_by UUID REFERENCES utilisateurs(id),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    -- updated_by UUID REFERENCES utilisateurs(id)
);

-- 1.1 Table des Catégories d'articles
CREATE TABLE categories_articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    description TEXT,
    categorie_parent_id UUID REFERENCES categories_articles(id),
    necessite_tracabilite_lot BOOLEAN DEFAULT FALSE, -- Pour produits périssables/sensibles
    methode_valorisation VARCHAR(20) DEFAULT 'CUMP', -- FIFO, CUMP
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1.2 Table des Unités de mesure
CREATE TABLE unites_mesure (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(20) UNIQUE NOT NULL, -- KG, L, UNITE, CARTON, etc.
    libelle VARCHAR(100) NOT NULL,
    type VARCHAR(50) NOT NULL, -- POIDS, VOLUME, QUANTITE
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1.3 Table des Sites (multi-sites)
CREATE TABLE sites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(255) NOT NULL,
    adresse TEXT,
    ville VARCHAR(100),
    pays VARCHAR(100),
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1.4 Table des Dépôts/Entrepôts
CREATE TABLE depots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    site_id UUID NOT NULL REFERENCES sites(id),
    code VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(255) NOT NULL,
    type VARCHAR(50) DEFAULT 'GENERAL', -- GENERAL, QUARANTAINE, PRODUITS_FINIS, etc.
    adresse TEXT,
    capacite_m3 DECIMAL(12, 2),
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 1.5 Table des Zones dans les dépôts
CREATE TABLE zones_stockage (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    depot_id UUID NOT NULL REFERENCES depots(id),
    code VARCHAR(50) NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    type VARCHAR(50), -- RECEPTION, STOCKAGE, EXPEDITION, QUARANTAINE
    capacite_m3 DECIMAL(12, 2),
    temperature_min DECIMAL(5, 2), -- Pour zones réfrigérées
    temperature_max DECIMAL(5, 2),
    UNIQUE(depot_id, code)
);

-- 1.6 Table des Emplacements précis
CREATE TABLE emplacements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_id UUID NOT NULL REFERENCES zones_stockage(id),
    code VARCHAR(50) NOT NULL, -- Ex: A-01-02-03 (Allée-Travée-Niveau-Position)
    allee VARCHAR(10),
    travee VARCHAR(10),
    niveau VARCHAR(10),
    position VARCHAR(10),
    capacite_poids_kg DECIMAL(10, 2),
    capacite_volume_m3 DECIMAL(10, 2),
    actif BOOLEAN DEFAULT TRUE,
    UNIQUE(zone_id, code)
);

-- ============================================================================
-- PARTIE 2 : ARTICLES/PRODUITS
-- ============================================================================

-- 2.1 Table principale des Articles
CREATE TABLE articles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_article VARCHAR(100) UNIQUE NOT NULL,
    code_barre VARCHAR(100) UNIQUE, -- EAN, Code 39, etc.
    libelle VARCHAR(255) NOT NULL,
    description TEXT,
    categorie_id UUID NOT NULL REFERENCES categories_articles(id),
    unite_mesure_id UUID NOT NULL REFERENCES unites_mesure(id),
    
    -- Gestion des lots
    gestion_par_lot BOOLEAN DEFAULT FALSE,
    gestion_par_serie BOOLEAN DEFAULT FALSE, -- Numéros de série uniques
    duree_vie_jours INTEGER, -- Pour calcul DLC/DLUO
    
    -- Caractéristiques physiques
    poids_kg DECIMAL(10, 3),
    volume_m3 DECIMAL(10, 3),
    
    -- Stocks et seuils
    stock_minimum INTEGER DEFAULT 0,
    stock_maximum INTEGER,
    stock_securite INTEGER DEFAULT 0,
    
    -- Valorisation
    methode_valorisation VARCHAR(20) DEFAULT 'CUMP', -- FIFO, CUMP (hérite de catégorie)
    cout_standard DECIMAL(15, 4), -- Coût de référence
    
    -- Prix de vente
    prix_vente_ht DECIMAL(15, 2),
    tva_pourcentage DECIMAL(5, 2) DEFAULT 20.0,
    
    -- Statut
    actif BOOLEAN DEFAULT TRUE,
    obsolete BOOLEAN DEFAULT FALSE,
    
    -- Audit
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by UUID, -- Référence vers table utilisateurs
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID
);

-- 2.2 Table des Fournisseurs par Article (prix d'achat)
CREATE TABLE articles_fournisseurs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES articles(id),
    fournisseur_id UUID NOT NULL REFERENCES fournisseurs(id),
    reference_fournisseur VARCHAR(100), -- Leur code article
    prix_achat_ht DECIMAL(15, 4) NOT NULL,
    delai_livraison_jours INTEGER,
    quantite_minimum_commande INTEGER DEFAULT 1,
    fournisseur_principal BOOLEAN DEFAULT FALSE,
    actif BOOLEAN DEFAULT TRUE,
    date_debut_validite DATE,
    date_fin_validite DATE,
    UNIQUE(article_id, fournisseur_id)
);

-- ============================================================================
-- PARTIE 3 : STOCKS EN TEMPS RÉEL
-- ============================================================================

-- 3.1 Table du Stock théorique par dépôt et article
CREATE TABLE stocks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES articles(id),
    depot_id UUID NOT NULL REFERENCES depots(id),
    
    -- Quantités
    quantite_physique INTEGER DEFAULT 0, -- Stock réel après inventaire
    quantite_theorique INTEGER DEFAULT 0, -- Stock calculé par mouvements
    quantite_reservee INTEGER DEFAULT 0, -- Réservé pour commandes clients
    quantite_disponible INTEGER GENERATED ALWAYS AS (quantite_theorique - quantite_reservee) STORED,
    
    -- Valorisation
    valeur_stock_cump DECIMAL(15, 2) DEFAULT 0, -- Coût moyen pondéré
    cout_unitaire_moyen DECIMAL(15, 4) GENERATED ALWAYS AS (
        CASE WHEN quantite_theorique > 0 
        THEN valeur_stock_cump / quantite_theorique 
        ELSE 0 END
    ) STORED,
    
    -- Dates
    date_dernier_mouvement TIMESTAMP,
    date_dernier_inventaire TIMESTAMP,
    
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(article_id, depot_id),
    CHECK (quantite_physique >= 0),
    CHECK (quantite_theorique >= 0),
    CHECK (quantite_reservee >= 0)
);

-- Index pour performances
CREATE INDEX idx_stocks_article ON stocks(article_id);
CREATE INDEX idx_stocks_depot ON stocks(depot_id);
CREATE INDEX idx_stocks_disponible ON stocks(quantite_disponible) WHERE quantite_disponible > 0;

-- ============================================================================
-- PARTIE 4 : GESTION DES LOTS ET SÉRIES
-- ============================================================================

-- 4.1 Table des Lots
CREATE TABLE lots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_lot VARCHAR(100) NOT NULL,
    article_id UUID NOT NULL REFERENCES articles(id),
    bon_reception_id UUID REFERENCES bons_reception(id), -- Lien avec réception
    
    -- Quantités
    quantite_initiale INTEGER NOT NULL,
    quantite_actuelle INTEGER NOT NULL,
    
    -- Dates et traçabilité
    date_fabrication DATE,
    date_reception DATE NOT NULL,
    date_peremption DATE, -- DLC
    dluo DATE, -- Date Limite d'Utilisation Optimale
    
    -- Qualité et conformité
    statut VARCHAR(50) DEFAULT 'DISPONIBLE', -- DISPONIBLE, QUARANTAINE, BLOQUE, PERIME, EPUISE
    certificat_conformite VARCHAR(500), -- URL du certificat
    
    -- Coût
    cout_unitaire DECIMAL(15, 4) NOT NULL,
    
    -- Localisation
    emplacement_id UUID REFERENCES emplacements(id),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    UNIQUE(numero_lot, article_id),
    CHECK (quantite_actuelle >= 0),
    CHECK (quantite_actuelle <= quantite_initiale)
);

-- 4.2 Table des Numéros de série (pour articles sérialisés)
CREATE TABLE series (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    numero_serie VARCHAR(100) UNIQUE NOT NULL,
    article_id UUID NOT NULL REFERENCES articles(id),
    lot_id UUID REFERENCES lots(id),
    
    statut VARCHAR(50) DEFAULT 'EN_STOCK', -- EN_STOCK, VENDU, RETOUR, SAV, REBUT
    
    bon_reception_id UUID REFERENCES bons_reception(id),
    date_reception TIMESTAMP,
    
    -- Si vendu
    commande_client_id UUID, -- Référence vers module vente
    date_sortie TIMESTAMP,
    
    emplacement_id UUID REFERENCES emplacements(id),
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- PARTIE 5 : MOUVEMENTS DE STOCK
-- ============================================================================

-- 5.1 Table des Types de mouvements
CREATE TABLE types_mouvement (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    libelle VARCHAR(255) NOT NULL,
    sens VARCHAR(10) NOT NULL CHECK (sens IN ('ENTREE', 'SORTIE')),
    impact_valorisation BOOLEAN DEFAULT TRUE,
    description TEXT
);

-- Création des séquences pour la génération des références
CREATE SEQUENCE IF NOT EXISTS seq_mouvement_stock START 1;
CREATE SEQUENCE IF NOT EXISTS seq_lot START 1;
CREATE SEQUENCE IF NOT EXISTS seq_reservation_stock START 1;
CREATE SEQUENCE IF NOT EXISTS seq_transfert START 1;
CREATE SEQUENCE IF NOT EXISTS seq_inventaire START 1;

-- Insertion des types de mouvement initiaux
INSERT INTO types_mouvement (id, code, libelle, sens, impact_valorisation) VALUES
(gen_random_uuid(), 'RECEPTION_FOURNISSEUR', 'Reception fournisseur', 'ENTREE', TRUE),
(gen_random_uuid(), 'RETOUR_CLIENT', 'Retour client', 'ENTREE', TRUE),
(gen_random_uuid(), 'AJUSTEMENT_POSITIF', 'Ajustement inventaire positif', 'ENTREE', TRUE),
(gen_random_uuid(), 'TRANSFERT_ENTRANT', 'Transfert entrant', 'ENTREE', TRUE),
(gen_random_uuid(), 'PRODUCTION', 'Entree production', 'ENTREE', TRUE),
(gen_random_uuid(), 'LIVRAISON_CLIENT', 'Livraison client', 'SORTIE', TRUE),
(gen_random_uuid(), 'CONSOMMATION_INTERNE', 'Consommation interne', 'SORTIE', TRUE),
(gen_random_uuid(), 'REBUT', 'Mise au rebut', 'SORTIE', TRUE),
(gen_random_uuid(), 'AJUSTEMENT_NEGATIF', 'Ajustement inventaire negatif', 'SORTIE', TRUE),
(gen_random_uuid(), 'TRANSFERT_SORTANT', 'Transfert sortant', 'SORTIE', TRUE),
(gen_random_uuid(), 'PERTE', 'Perte/Vol', 'SORTIE', TRUE)
ON CONFLICT (code) DO NOTHING;

-- 5.2 Table des Mouvements de stock (journal des mouvements)
CREATE TABLE mouvements_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- MVT-2026-000001 (auto-généré)
    
    -- Identification
    type_mouvement_id UUID NOT NULL REFERENCES types_mouvement(id),
    article_id UUID NOT NULL REFERENCES articles(id),
    depot_id UUID NOT NULL REFERENCES depots(id),
    emplacement_id UUID REFERENCES emplacements(id),
    
    -- Quantités et valorisation
    quantite INTEGER NOT NULL CHECK (quantite > 0),
    cout_unitaire DECIMAL(15, 4) NOT NULL,
    valeur_mouvement DECIMAL(15, 2) GENERATED ALWAYS AS (quantite * cout_unitaire) STORED,
    
    -- Traçabilité lot/série
    lot_id UUID REFERENCES lots(id),
    numero_serie_id UUID REFERENCES series(id),
    
    -- Documents source
    bon_commande_id UUID REFERENCES bons_commande(id),
    bon_reception_id UUID REFERENCES bons_reception(id),
    commande_client_id UUID, -- Référence module vente
    bon_livraison_id UUID, -- Référence module vente
    inventaire_id UUID, -- Référence vers table inventaires
    transfert_id UUID, -- Référence vers table transferts
    
    -- Dates et utilisateur
    date_mouvement TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_comptable DATE NOT NULL DEFAULT CURRENT_DATE, -- Pour clôture mensuelle
    utilisateur_id UUID NOT NULL, -- Qui a fait le mouvement
    
    -- Informations complémentaires
    motif TEXT,
    notes TEXT,
    
    -- Validation et contrôle
    statut VARCHAR(50) DEFAULT 'VALIDE', -- BROUILLON, VALIDE, ANNULE
    valide_par UUID, -- Responsable qui a validé
    date_validation TIMESTAMP,
    
    -- Audit (immuable une fois validé)
    modifiable BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index pour performances et recherches
CREATE INDEX idx_mvt_article ON mouvements_stock(article_id);
CREATE INDEX idx_mvt_depot ON mouvements_stock(depot_id);
CREATE INDEX idx_mvt_date ON mouvements_stock(date_mouvement);
CREATE INDEX idx_mvt_date_comptable ON mouvements_stock(date_comptable);
CREATE INDEX idx_mvt_type ON mouvements_stock(type_mouvement_id);
CREATE INDEX idx_mvt_bc ON mouvements_stock(bon_commande_id);
CREATE INDEX idx_mvt_br ON mouvements_stock(bon_reception_id);

-- ============================================================================
-- PARTIE 6 : RÉSERVATIONS DE STOCK
-- ============================================================================

-- 6.1 Table des Réservations (pour commandes clients)
CREATE TABLE reservations_stock (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL,
    
    article_id UUID NOT NULL REFERENCES articles(id),
    depot_id UUID NOT NULL REFERENCES depots(id),
    
    quantite_reservee INTEGER NOT NULL CHECK (quantite_reservee > 0),
    quantite_prelevee INTEGER DEFAULT 0 CHECK (quantite_prelevee >= 0),
    quantite_restante INTEGER GENERATED ALWAYS AS (quantite_reservee - quantite_prelevee) STORED,
    
    -- Allocation FIFO/FEFO
    lot_id UUID REFERENCES lots(id), -- Si lot spécifique alloué
    
    -- Document source
    commande_client_id UUID NOT NULL, -- Référence module vente
    ligne_commande_id UUID, -- Ligne spécifique de la commande
    
    -- Dates
    date_reservation TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    date_expiration TIMESTAMP, -- Réservation temporaire
    date_livraison_prevue DATE,
    
    -- Statut
    statut VARCHAR(50) DEFAULT 'ACTIVE', -- ACTIVE, PRELEVEE, ANNULEE, EXPIREE
    
    utilisateur_id UUID NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index
CREATE INDEX idx_reservation_article ON reservations_stock(article_id);
CREATE INDEX idx_reservation_depot ON reservations_stock(depot_id);
CREATE INDEX idx_reservation_commande ON reservations_stock(commande_client_id);
CREATE INDEX idx_reservation_statut ON reservations_stock(statut);

-- ============================================================================
-- PARTIE 7 : TRANSFERTS ENTRE DÉPÔTS
-- ============================================================================

-- 7.1 Table des Transferts
CREATE TABLE transferts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- TRF-2026-0001
    
    depot_source_id UUID NOT NULL REFERENCES depots(id),
    depot_destination_id UUID NOT NULL REFERENCES depots(id),
    
    date_demande TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_expedition DATE,
    date_reception_prevue DATE,
    date_reception_reelle TIMESTAMP,
    
    statut VARCHAR(50) DEFAULT 'BROUILLON', 
    -- BROUILLON, EN_ATTENTE_VALIDATION, VALIDE, EXPEDIE, RECEPTIONNE, ANNULE
    
    motif TEXT,
    
    -- Validation
    demandeur_id UUID NOT NULL,
    valideur_id UUID,
    date_validation TIMESTAMP,
    
    -- Réception
    receptionnaire_id UUID,
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CHECK (depot_source_id != depot_destination_id)
);

-- 7.2 Lignes de transfert
CREATE TABLE lignes_transfert (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transfert_id UUID NOT NULL REFERENCES transferts(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id),
    
    quantite_demandee INTEGER NOT NULL CHECK (quantite_demandee > 0),
    quantite_expedie INTEGER DEFAULT 0,
    quantite_recue INTEGER DEFAULT 0,
    
    lot_id UUID REFERENCES lots(id),
    
    emplacement_source_id UUID REFERENCES emplacements(id),
    emplacement_destination_id UUID REFERENCES emplacements(id),
    
    notes TEXT
);

-- ============================================================================
-- PARTIE 8 : INVENTAIRES
-- ============================================================================

-- 8.1 Table des Campagnes d'inventaire
CREATE TABLE inventaires (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- INV-2026-0001
    
    type VARCHAR(50) NOT NULL, -- ANNUEL, TOURNANT, PARTIEL, CONTROLE
    depot_id UUID REFERENCES depots(id), -- NULL = tous les dépôts
    
    date_debut DATE NOT NULL,
    date_fin DATE,
    date_cloture TIMESTAMP, -- Quand l'inventaire est finalisé
    
    statut VARCHAR(50) DEFAULT 'PLANIFIE', 
    -- PLANIFIE, EN_COURS, TERMINE, VALIDE, CLOTURE
    
    -- Périmètre
    zone_id UUID REFERENCES zones_stockage(id), -- Optionnel : zone spécifique
    categorie_id UUID REFERENCES categories_articles(id), -- Optionnel : catégorie
    
    responsable_id UUID NOT NULL,
    
    -- Résultats globaux
    nombre_articles_comptes INTEGER DEFAULT 0,
    valeur_ecart_total DECIMAL(15, 2) DEFAULT 0,
    
    observations TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8.2 Lignes d'inventaire (comptages)
CREATE TABLE lignes_inventaire (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventaire_id UUID NOT NULL REFERENCES inventaires(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id),
    depot_id UUID NOT NULL REFERENCES depots(id),
    emplacement_id UUID REFERENCES emplacements(id),
    
    lot_id UUID REFERENCES lots(id),
    
    -- Quantités
    quantite_theorique INTEGER NOT NULL, -- Stock système avant inventaire
    quantite_comptee_1 INTEGER, -- Premier comptage
    quantite_comptee_2 INTEGER, -- Second comptage (si écart)
    quantite_comptee_finale INTEGER, -- Comptage retenu
    
    ecart INTEGER GENERATED ALWAYS AS (quantite_comptee_finale - quantite_theorique) STORED,
    ecart_valeur DECIMAL(15, 2), -- Valorisation de l'écart
    
    -- Traçabilité comptage
    compteur_1_id UUID, -- Utilisateur premier comptage
    date_comptage_1 TIMESTAMP,
    compteur_2_id UUID, -- Second compteur
    date_comptage_2 TIMESTAMP,
    
    -- Validation
    statut VARCHAR(50) DEFAULT 'A_COMPTER', 
    -- A_COMPTER, COMPTE, ECART_A_RECOMPTER, VALIDE, AJUSTE
    
    observations TEXT,
    cause_ecart TEXT, -- Perte, vol, erreur saisie, etc.
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8.3 Ajustements de stock suite à inventaire
CREATE TABLE ajustements_inventaire (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    ligne_inventaire_id UUID NOT NULL REFERENCES lignes_inventaire(id),
    mouvement_stock_id UUID REFERENCES mouvements_stock(id), -- Mouvement créé
    
    quantite_ajustee INTEGER NOT NULL, -- Positif ou négatif
    valeur_ajustement DECIMAL(15, 2) NOT NULL,
    
    valideur_id UUID NOT NULL, -- Chef magasin ou supérieur
    date_validation TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    motif TEXT NOT NULL,
    justification TEXT
);

-- ============================================================================
-- PARTIE 9 : VALORISATION ET CLÔTURE
-- ============================================================================

-- 9.1 Historique des coûts (pour FIFO et suivi)
CREATE TABLE historique_couts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    article_id UUID NOT NULL REFERENCES articles(id),
    depot_id UUID REFERENCES depots(id),
    
    date_effet DATE NOT NULL,
    cout_unitaire_moyen DECIMAL(15, 4) NOT NULL,
    quantite_stock INTEGER NOT NULL,
    valeur_stock DECIMAL(15, 2) NOT NULL,
    
    methode_valorisation VARCHAR(20) NOT NULL,
    
    mouvement_stock_id UUID REFERENCES mouvements_stock(id), -- Mouvement ayant causé le changement
    
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9.2 Clôtures mensuelles (gel des coûts)
CREATE TABLE clotures_mensuelles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    annee INTEGER NOT NULL,
    mois INTEGER NOT NULL CHECK (mois BETWEEN 1 AND 12),
    
    date_cloture TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cloture_par UUID NOT NULL,
    
    statut VARCHAR(50) DEFAULT 'OUVERTE', -- OUVERTE, CLOTUREE, VALIDEE
    
    -- Statistiques globales
    nombre_articles INTEGER,
    valeur_stock_total DECIMAL(15, 2),
    nombre_mouvements INTEGER,
    
    valideur_id UUID, -- DAF ou contrôleur
    date_validation TIMESTAMP,
    
    commentaires TEXT,
    
    UNIQUE(annee, mois)
);

CREATE INDEX idx_utilisateurs_role ON utilisateurs(role);
CREATE INDEX idx_utilisateurs_actif ON utilisateurs(actif) WHERE actif = TRUE;
-- CREATE INDEX idx_utilisateurs_depot ON utilisateurs(depot_id);

-- ============================================================================
-- PARTIE 10 : TRIGGERS ET CONTRAINTES
-- ============================================================================

-- Remplacer la fonction update_stock_after_movement par cette version corrigée
CREATE OR REPLACE FUNCTION update_stock_after_movement()
RETURNS TRIGGER AS $$
DECLARE
    v_sens VARCHAR(10);
    v_nouvelle_quantite INTEGER;
    v_existing_theorique INTEGER;
    v_existing_physique INTEGER;
BEGIN
    -- Récupérer le sens du mouvement
    SELECT sens INTO v_sens 
    FROM types_mouvement 
    WHERE id = NEW.type_mouvement_id;
    
    -- Calculer la nouvelle quantité
    IF v_sens = 'ENTREE' THEN
        v_nouvelle_quantite := NEW.quantite;
    ELSE
        v_nouvelle_quantite := -NEW.quantite;
    END IF;
    
    -- Récupérer les quantités existantes si elles existent
    SELECT quantite_theorique, quantite_physique 
    INTO v_existing_theorique, v_existing_physique
    FROM stocks 
    WHERE article_id = NEW.article_id 
      AND depot_id = NEW.depot_id;
    
    -- Si pas de stock existant et c'est une sortie, initialiser à 0
    IF NOT FOUND AND v_sens = 'SORTIE' THEN
        v_existing_theorique := 0;
        v_existing_physique := 0;
    END IF;
    
    -- Calculer les nouvelles valeurs
    IF FOUND OR (NOT FOUND AND v_sens = 'SORTIE') THEN
        -- Stock existe déjà OU c'est une sortie sans stock
        UPDATE stocks 
        SET 
            quantite_theorique = GREATEST(v_existing_theorique + v_nouvelle_quantite, 0),
            quantite_physique = GREATEST(v_existing_physique + v_nouvelle_quantite, 0),
            date_dernier_mouvement = NEW.date_mouvement,
            updated_at = CURRENT_TIMESTAMP
        WHERE article_id = NEW.article_id 
          AND depot_id = NEW.depot_id;
    ELSE
        -- Pas de stock existant et c'est une entrée
        INSERT INTO stocks (
            article_id, 
            depot_id, 
            quantite_theorique, 
            quantite_physique, 
            date_dernier_mouvement
        )
        VALUES (
            NEW.article_id, 
            NEW.depot_id, 
            GREATEST(v_nouvelle_quantite, 0), 
            GREATEST(v_nouvelle_quantite, 0), 
            NEW.date_mouvement
        );
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_stock
AFTER INSERT ON mouvements_stock
FOR EACH ROW
WHEN (NEW.statut = 'VALIDE')
EXECUTE FUNCTION update_stock_after_movement();

-- Trigger pour mettre à jour la quantité du lot
CREATE OR REPLACE FUNCTION update_lot_quantity()
RETURNS TRIGGER AS $$
DECLARE
    v_sens VARCHAR(10);
BEGIN
    IF NEW.lot_id IS NOT NULL THEN
        SELECT sens INTO v_sens 
        FROM types_mouvement 
        WHERE id = NEW.type_mouvement_id;
        
        IF v_sens = 'SORTIE' THEN
            UPDATE lots 
            SET quantite_actuelle = quantite_actuelle - NEW.quantite
            WHERE id = NEW.lot_id;
        END IF;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_lot
AFTER INSERT ON mouvements_stock
FOR EACH ROW
WHEN (NEW.statut = 'VALIDE' AND NEW.lot_id IS NOT NULL)
EXECUTE FUNCTION update_lot_quantity();

-- ============================================================================
-- PARTIE 12 : INDEXES ADDITIONNELS POUR PERFORMANCES
-- ============================================================================

CREATE INDEX idx_articles_categorie ON articles(categorie_id);
CREATE INDEX idx_articles_code ON articles(code_article);
CREATE INDEX idx_articles_actif ON articles(actif) WHERE actif = TRUE;

CREATE INDEX idx_lots_article ON lots(article_id);
CREATE INDEX idx_lots_statut ON lots(statut);
CREATE INDEX idx_lots_peremption ON lots(date_peremption) WHERE date_peremption IS NOT NULL;

CREATE INDEX idx_series_article ON series(article_id);
CREATE INDEX idx_series_statut ON series(statut);

CREATE INDEX idx_transferts_statut ON transferts(statut);
CREATE INDEX idx_transferts_depot_source ON transferts(depot_source_id);
CREATE INDEX idx_transferts_depot_dest ON transferts(depot_destination_id);

CREATE INDEX idx_inventaires_statut ON inventaires(statut);
CREATE INDEX idx_inventaires_depot ON inventaires(depot_id);

-- ============================================================================
-- FIN DU SCHÉMA STOCK
-- ============================================================================



--- erreur 

-- Vérifiez le type exact de la colonne id dans mouvements_stock
-- SELECT 
--     column_name, 
--     data_type, 
--     udt_name,
--     character_maximum_length,
--     is_nullable
-- FROM information_schema.columns 
-- WHERE table_name = 'mouvements_stock' 
-- AND column_name = 'id';

-- -- Vérifiez aussi les colonnes UUID dans les tables liées
-- SELECT 
--     table_name, 
--     column_name, 
--     data_type
-- FROM information_schema.columns 
-- WHERE table_name IN ('mouvements_stock', 'types_mouvement', 'articles', 'depots', 'utilisateurs')
-- AND column_name LIKE '%id%'
-- ORDER BY table_name, column_name;

-- ALTER TABLE mouvements_stock 
-- ALTER COLUMN utilisateur_id TYPE UUID USING utilisateur_id::uuid;

-- ALTER TABLE mouvements_stock 
-- ALTER COLUMN commande_client_id TYPE UUID USING NULLIF(commande_client_id, '')::uuid;

-- ALTER TABLE mouvements_stock 
-- ALTER COLUMN inventaire_id TYPE UUID USING NULLIF(inventaire_id, '')::uuid;

-- Remplacer les '' par NULL puis caster vers uuid
-- ALTER TABLE articles
--   ALTER COLUMN created_by TYPE uuid
--   USING NULLIF(created_by, '')::uuid;

-- ALTER TABLE articles
--   ALTER COLUMN updated_by TYPE uuid
--   USING NULLIF(updated_by, '')::uuid;