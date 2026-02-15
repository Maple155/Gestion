-- =============================================================================
-- MODULE VENTE - SCHÉMA PRINCIPAL
-- =============================================================================

-- 1. Clients
CREATE TABLE clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    telephone VARCHAR(50),
    adresse TEXT,
    ville VARCHAR(100),
    pays VARCHAR(100),
    actif BOOLEAN DEFAULT TRUE,

    plafond_remise DECIMAL(5, 2) DEFAULT 0, -- % max accordé
    plafond_credit DECIMAL(15, 2) DEFAULT 0, -- encours max
    conditions_paiement VARCHAR(100), -- NET30, NET60, etc.

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Devis (proforma)
CREATE TABLE devis_vente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- DEV-YYYY-000001
    client_id UUID NOT NULL REFERENCES clients(id),
    date_devis TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    validite_jours INTEGER DEFAULT 15,

    statut VARCHAR(50) DEFAULT 'BROUILLON', -- BROUILLON, VALIDE, EXPIRE, ANNULE

    total_ht DECIMAL(15, 2) DEFAULT 0,
    total_tva DECIMAL(15, 2) DEFAULT 0,
    total_ttc DECIMAL(15, 2) DEFAULT 0,
    remise_globale DECIMAL(5, 2) DEFAULT 0,

    cree_par UUID,
    valide_par UUID,
    date_validation TIMESTAMP,

    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lignes_devis_vente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    devis_id UUID NOT NULL REFERENCES devis_vente(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id),

    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire_ht DECIMAL(15, 2) NOT NULL,
    remise_pourcentage DECIMAL(5, 2) DEFAULT 0,
    tva_pourcentage DECIMAL(5, 2) DEFAULT 20.0,

    total_ht DECIMAL(15, 2) NOT NULL,
    total_ttc DECIMAL(15, 2) NOT NULL
);

-- 3. Commandes clients
CREATE TABLE commandes_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- CMD-YYYY-000001
    devis_id UUID REFERENCES devis_vente(id),
    client_id UUID NOT NULL REFERENCES clients(id),
    depot_livraison_id UUID REFERENCES depots(id),

    date_commande TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_livraison_prevue DATE,

    statut VARCHAR(50) DEFAULT 'EN_ATTENTE',
    -- EN_ATTENTE, CONFIRMEE, EN_PREPARATION, LIVREE, FACTUREE, ANNULEE

    total_ht DECIMAL(15, 2) DEFAULT 0,
    total_tva DECIMAL(15, 2) DEFAULT 0,
    total_ttc DECIMAL(15, 2) DEFAULT 0,
    remise_globale DECIMAL(5, 2) DEFAULT 0,

    mode_reservation VARCHAR(20) DEFAULT 'IMMEDIATE', -- IMMEDIATE, DIFFEREE

    cree_par UUID,
    valide_par UUID,
    date_validation TIMESTAMP,

    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lignes_commandes_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    commande_id UUID NOT NULL REFERENCES commandes_clients(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id),

    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire_ht DECIMAL(15, 2) NOT NULL,
    remise_pourcentage DECIMAL(5, 2) DEFAULT 0,
    tva_pourcentage DECIMAL(5, 2) DEFAULT 20.0,

    total_ht DECIMAL(15, 2) NOT NULL,
    total_ttc DECIMAL(15, 2) NOT NULL,

    reservation_stock_id UUID,
    statut VARCHAR(50) DEFAULT 'EN_ATTENTE' -- EN_ATTENTE, RESERVEE, LIVREE, ANNULEE
);

-- 3.b Backlog stock (insuffisance)
CREATE TABLE backlog_stock_vente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    devis_id UUID REFERENCES devis_vente(id),
    demande_achat_id UUID REFERENCES demandes_achat(id),
    article_id UUID NOT NULL REFERENCES articles(id),
    quantite_demandee INTEGER NOT NULL,
    quantite_disponible INTEGER NOT NULL,
    quantite_manquante INTEGER NOT NULL,
    statut VARCHAR(50) DEFAULT 'EN_ATTENTE',
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


-- 4. Livraisons
CREATE TABLE livraisons_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- BL-YYYY-000001
    commande_id UUID NOT NULL REFERENCES commandes_clients(id),

    date_preparation TIMESTAMP,
    date_livraison TIMESTAMP,

    statut VARCHAR(50) DEFAULT 'EN_PREPARATION',
    -- EN_PREPARATION, PRETE, LIVREE, ANNULEE

    transporteur VARCHAR(100),
    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lignes_livraisons_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    livraison_id UUID NOT NULL REFERENCES livraisons_clients(id) ON DELETE CASCADE,
    ligne_commande_id UUID NOT NULL REFERENCES lignes_commandes_clients(id),
    article_id UUID NOT NULL REFERENCES articles(id),

    quantite_livree INTEGER NOT NULL CHECK (quantite_livree > 0)
);

-- 5. Facturation
CREATE TABLE factures_vente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- FAC-YYYY-000001
    commande_id UUID NOT NULL REFERENCES commandes_clients(id),
    livraison_id UUID REFERENCES livraisons_clients(id),
    client_id UUID NOT NULL REFERENCES clients(id),

    date_facture DATE DEFAULT CURRENT_DATE,
    statut VARCHAR(50) DEFAULT 'EMISE', -- EMISE, PAYEE, ANNULEE

    total_ht DECIMAL(15, 2) NOT NULL,
    total_tva DECIMAL(15, 2) NOT NULL,
    total_ttc DECIMAL(15, 2) NOT NULL,

    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE lignes_factures_vente (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    facture_id UUID NOT NULL REFERENCES factures_vente(id) ON DELETE CASCADE,
    article_id UUID NOT NULL REFERENCES articles(id),

    quantite INTEGER NOT NULL CHECK (quantite > 0),
    prix_unitaire_ht DECIMAL(15, 2) NOT NULL,
    remise_pourcentage DECIMAL(5, 2) DEFAULT 0,
    tva_pourcentage DECIMAL(5, 2) DEFAULT 20.0,

    total_ht DECIMAL(15, 2) NOT NULL,
    total_ttc DECIMAL(15, 2) NOT NULL
);

-- 6. Paiements
CREATE TABLE paiements_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- PAY-YYYY-000001
    facture_id UUID NOT NULL REFERENCES factures_vente(id),
    client_id UUID NOT NULL REFERENCES clients(id),

    date_paiement DATE DEFAULT CURRENT_DATE,
    mode_paiement VARCHAR(30) DEFAULT 'VIREMENT',
    montant DECIMAL(15, 2) NOT NULL,

    statut VARCHAR(50) DEFAULT 'ENREGISTRE', -- ENREGISTRE, ANNULE

    notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Avoirs
CREATE TABLE avoirs_clients (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference VARCHAR(100) UNIQUE NOT NULL, -- AV-YYYY-000001
    facture_id UUID NOT NULL REFERENCES factures_vente(id),
    client_id UUID NOT NULL REFERENCES clients(id),

    date_avoir DATE DEFAULT CURRENT_DATE,
    montant DECIMAL(15, 2) NOT NULL,
    motif TEXT,

    statut VARCHAR(50) DEFAULT 'EMIS', -- EMIS, ANNULE

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. Séquences
CREATE SEQUENCE IF NOT EXISTS seq_devis_vente START 1;
CREATE SEQUENCE IF NOT EXISTS seq_commande_client START 1;
CREATE SEQUENCE IF NOT EXISTS seq_livraison_client START 1;
CREATE SEQUENCE IF NOT EXISTS seq_facture_vente START 1;
CREATE SEQUENCE IF NOT EXISTS seq_paiement_client START 1;
CREATE SEQUENCE IF NOT EXISTS seq_avoir_client START 1;

-- Index
CREATE INDEX idx_devis_client ON devis_vente(client_id);
CREATE INDEX idx_devis_statut ON devis_vente(statut);
CREATE INDEX idx_cmd_client ON commandes_clients(client_id);
CREATE INDEX idx_cmd_statut ON commandes_clients(statut);
CREATE INDEX idx_liv_cmd ON livraisons_clients(commande_id);
CREATE INDEX idx_fac_client ON factures_vente(client_id);
CREATE INDEX idx_pay_fac ON paiements_clients(facture_id);
CREATE INDEX idx_avo_fac ON avoirs_clients(facture_id);
