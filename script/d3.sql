-- Données de test basiques pour le module vente
INSERT INTO clients (id, code, nom, email, telephone, adresse, ville, pays, plafond_remise, plafond_credit)
VALUES
    (gen_random_uuid(), 'CL-0001', 'Client Alpha', 'alpha@client.com', '0320000001', 'Lot I', 'Antananarivo', 'MG', 5, 1000000),
    (gen_random_uuid(), 'CL-0002', 'Client Beta', 'beta@client.com', '0320000002', 'Lot II', 'Antsirabe', 'MG', 10, 2000000)
ON CONFLICT (code) DO NOTHING;

INSERT INTO utilisateurs (
    id, username, email, password, nom, prenom, role, actif, telephone, poste, service, created_at, updated_at
) VALUES
    (gen_random_uuid(), 'com1', 'com1@erp.com', '1234', 'Rakoto', 'Jean', 'COMMERCIAL', true, '0321111111', 'Commercial', 'Ventes', now(), now()),
    (gen_random_uuid(), 'rv1', 'rv1@erp.com', '1234', 'Ranaivo', 'Paul', 'RESPONSABLE_VENTES', true, '0322222222', 'Resp. Ventes', 'Ventes', now(), now()),
    (gen_random_uuid(), 'mag1', 'mag1@erp.com', '1234', 'Razafy', 'Lova', 'MAGASINIER_SORTIE', true, '0323333333', 'Magasinier', 'Logistique', now(), now()),
    (gen_random_uuid(), 'cc1', 'cc1@erp.com', '1234', 'Randria', 'Mina', 'COMPTABLE_CLIENT', true, '0324444444', 'Comptable Client', 'Finance', now(), now())
ON CONFLICT (username) DO NOTHING;
