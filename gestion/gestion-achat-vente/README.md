# Module Vente - Gestion

## Aperçu
Ce module ajoute le workflow Vente : devis → commande → livraison → facture → encaissement, avec intégration stock (réservation + sortie) et numérotation automatique.

## Schéma SQL
- Stock : `src/main/java/com/gestion/stock/sql/schema_stock.sql`
- Achats : `src/main/java/com/gestion/achat/schema.sql`
- Ventes : `src/main/java/com/gestion/vente/schema.sql`

## Démarrage rapide
1. Exécuter les schémas SQL dans votre base Postgres (ordre conseillé : stock, achats, ventes).
2. Démarrer l’application Spring Boot.
3. Ouvrir les vues :
   - `/ventes/devis/liste`
   - `/ventes/commandes/liste`
   - `/ventes/livraisons/liste`
   - `/ventes/factures/liste`

## API principales
- `POST /api/ventes/devis`
- `POST /api/ventes/devis/{id}/commande`
- `POST /api/ventes/commandes/{id}/livrer`
- `POST /api/ventes/commandes/{id}/facturer`
- `POST /api/ventes/factures/{id}/paiements`
- `POST /api/ventes/factures/{id}/avoirs`

## Notes
- Les réservations de stock sont faites à la création de commande si le mode est `IMMEDIATE`.
- Les sorties stock sont générées lors de la livraison.
- Les références suivent les formats : `DEV-YYYY-XXXXXX`, `CMD-YYYY-XXXXXX`, `BL-YYYY-XXXXXX`, `FAC-YYYY-XXXXXX`.
