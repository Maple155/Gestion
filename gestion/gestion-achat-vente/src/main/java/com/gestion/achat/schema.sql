
-- 1. Table des Fournisseurs
CREATE TABLE fournisseurs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    nom VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    telephone VARCHAR(50),
    adresse TEXT,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table des Demandes d'Achat (DA)
-- Note : produit_id fait référence à la table produit (dans le package stock)
CREATE TABLE demandes_achat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    produit_id UUID NOT NULL, 
    quantite_demandee INTEGER NOT NULL CHECK (quantite_demandee > 0),
    motif TEXT,
    statut VARCHAR(50) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, EN_COURS, TERMINEE, ANNULEE
    date_demande TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Table des Proformas (Les devis reçus)
CREATE TABLE proformas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    demande_achat_id UUID NOT NULL REFERENCES demandes_achat(id) ON DELETE CASCADE,
    fournisseur_id UUID NOT NULL REFERENCES fournisseurs(id),
    prix_unitaire_ht DECIMAL(15, 2) NOT NULL,
    tva_pourcentage DECIMAL(5, 2) DEFAULT 20.0,
    delai_livraison_jours INTEGER,
    date_reception TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    est_selectionne BOOLEAN DEFAULT FALSE,
    document_url VARCHAR(500) -- Chemin vers le fichier stocké
);

-- 4. Table des Bons de Commande (BC)
-- Créé uniquement après validation finance du proforma sélectionné
CREATE TABLE bons_commande (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    proforma_id UUID UNIQUE NOT NULL REFERENCES proformas(id),
    reference_bc VARCHAR(100) UNIQUE NOT NULL, -- Format ex: BC-2026-0001
    statut_finance VARCHAR(50) DEFAULT 'EN_ATTENTE_VALIDATION', -- VALIDEE, REJETEE
    montant_total_ttc DECIMAL(15, 2) NOT NULL,
    date_emission TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    date_livraison_estimee DATE
);
-- 5. Table des Bons de Reception (BR)
-- Créé uniquement après livraison
CREATE TABLE bons_reception (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bon_commande_id UUID NOT NULL REFERENCES bons_commande(id),
    date_reception TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    est_conforme BOOLEAN DEFAULT TRUE,
    observations TEXT
);
-- 6. Table des Factures d'achat (BR)
-- Créé uniquement après achat
CREATE TABLE factures_achat (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bon_commande_id UUID NOT NULL REFERENCES bons_commande(id),
    numero_facture_fournisseur VARCHAR(100), -- Le numéro écrit sur leur papier
    montant_total_ttc DECIMAL(15, 2) NOT NULL,
    est_payee BOOLEAN DEFAULT FALSE,
    date_facture DATE
);