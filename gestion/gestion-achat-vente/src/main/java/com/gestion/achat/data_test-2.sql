-- On récupère les IDs générés précédemment pour les réutiliser
-- NOTE : Dans un script SQL pur, on utilise des variables ou on récupère les derniers IDs

DO $$
DECLARE
    f_id UUID;
    da_id UUID;
    prof_id UUID;
    bc_id UUID;
BEGIN
    -- 1. Récupération d'un fournisseur existant
    SELECT id INTO f_id FROM fournisseurs LIMIT 1;

    -- 2. Création d'une Demande d'Achat (DA)
    da_id := gen_random_uuid();
    INSERT INTO demandes_achat (id, produit_id, quantite_demandee, motif, statut, date_demande)
    VALUES (da_id, gen_random_uuid(), 10, 'Test Facturation Système', 'APPROUVEE', NOW());

    -- 3. Création d'un Proforma sélectionné pour cette DA
    prof_id := gen_random_uuid();
    INSERT INTO proformas (id, demande_achat_id, fournisseur_id, prix_unitaire_ht, tva_pourcentage, delai_livraison_jours, est_selectionne, date_reception)
    VALUES (prof_id, da_id, f_id, 150.00, 20.0, 5, TRUE, NOW());

    -- 4. Création du Bon de Commande (BC) associé
    bc_id := gen_random_uuid();
    INSERT INTO bons_commande (id, proforma_id, reference_bc, statut_finance, montant_total_ttc, date_emission)
    VALUES (bc_id, prof_id, 'BC-2026-TEST-001', 'VALIDEE', 1800.00, NOW());

    -- 5. Création du Bon de Réception (BR) conforme
    INSERT INTO bons_reception (id, bon_commande_id, date_reception, conforme, observations)
    VALUES (gen_random_uuid(), bc_id, NOW(), TRUE, 'Réception de test sans litige');

    -- 6. CRÉATION DE LA FACTURE (L'objectif final)
    INSERT INTO factures_achat (id, bon_commande_id, numero_facture_fournisseur, date_facture, montant_total_ttc, est_payee)
    VALUES (
        gen_random_uuid(), 
        bc_id, 
        'FAC-ALPHA-999', 
        CURRENT_DATE, 
        1800.00, 
        FALSE -- Non payée pour qu'elle apparaisse dans "À régler"
    );

    RAISE NOTICE 'Facture de test créée avec succès pour le BC-2026-TEST-001';
END $$;