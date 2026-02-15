package com.gestion.vente.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import jakarta.servlet.http.HttpSession;

import com.gestion.vente.dto.CreateDevisRequest;
import com.gestion.vente.dto.CreateCommandeFromDevisRequest;
import com.gestion.vente.dto.CreateLivraisonRequest;
import com.gestion.vente.dto.CreatePaiementRequest;
import com.gestion.vente.dto.CreateAvoirRequest;
import com.gestion.vente.dto.LigneVenteRequest;
import com.gestion.vente.entity.*;
import com.gestion.vente.repository.*;
import com.gestion.vente.service.VenteService;
import com.gestion.vente.enums.ModePaiement;
import com.gestion.vente.enums.StatutCommandeClient;
import com.gestion.stock.repository.ArticleRepository;
import com.gestion.stock.entity.Article;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

import lombok.RequiredArgsConstructor;

@Controller
@RequestMapping("/ventes")
@RequiredArgsConstructor
public class VenteViewController {

    private static final BigDecimal PLAFOND_REMISE_COMMERCIAL = BigDecimal.valueOf(10);

    private final ClientRepository clientRepository;
    private final DevisVenteRepository devisRepository;
    private final CommandeClientRepository commandeRepository;
    private final LivraisonClientRepository livraisonRepository;
    private final FactureVenteRepository factureRepository;
    private final PaiementClientRepository paiementRepository;
    private final AvoirClientRepository avoirRepository;
    private final LigneCommandeClientRepository ligneCommandeRepository;
    private final BacklogStockVenteRepository backlogRepository;
    private final VenteService venteService;
    private final ArticleRepository articleRepository;

    @Value("${app.logo.path:static/logo.png}")
    private String logoPath;

    private void requireRole(HttpSession session, String... allowedRoles) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) {
            throw new RuntimeException("Rôle utilisateur manquant");
        }
        for (String allowed : allowedRoles) {
            if (allowed.equals(role)) {
                return;
            }
        }
        throw new RuntimeException("Accès refusé pour le rôle: " + role);
    }

    @GetMapping("/clients")
    public String listClients(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("activePage", "vente-clients");
        return "vente/clients";
    }

    @GetMapping("/kpi/responsable")
    public String kpiResponsable(Model model, HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");

        List<CommandeClient> commandes = commandeRepository.findAll();
        long totalCommandes = commandes.size();
        long annulees = commandes.stream()
            .filter(c -> c.getStatut() == StatutCommandeClient.ANNULEE)
            .count();
        long livrees = commandes.stream()
            .filter(c -> c.getStatut() == StatutCommandeClient.LIVREE || c.getStatut() == StatutCommandeClient.FACTUREE)
            .count();
        long enCours = commandes.stream()
            .filter(c -> c.getStatut() != StatutCommandeClient.LIVREE
                && c.getStatut() != StatutCommandeClient.FACTUREE
                && c.getStatut() != StatutCommandeClient.ANNULEE)
            .count();
        long enRetard = commandes.stream()
            .filter(c -> c.getDateLivraisonPrevue() != null)
            .filter(c -> c.getDateLivraisonPrevue().isBefore(java.time.LocalDate.now()))
            .filter(c -> c.getStatut() != StatutCommandeClient.LIVREE
                && c.getStatut() != StatutCommandeClient.FACTUREE
                && c.getStatut() != StatutCommandeClient.ANNULEE)
            .count();

        double tauxAnnulation = totalCommandes == 0 ? 0 : (annulees * 100.0 / totalCommandes);
        List<String> motifsAnnulation = commandes.stream()
            .filter(c -> c.getStatut() == StatutCommandeClient.ANNULEE)
            .map(CommandeClient::getNotes)
            .filter(n -> n != null && n.contains("Annulation:"))
            .map(n -> n.substring(n.indexOf("Annulation:") + "Annulation:".length()).trim())
            .toList();

        BigDecimal plafondRemise = BigDecimal.valueOf(10);
        List<LigneCommandeClient> lignes = ligneCommandeRepository.findAll();
        BigDecimal totalRemises = BigDecimal.ZERO;
        long exceptionsRemise = 0;
        for (LigneCommandeClient ligne : lignes) {
            BigDecimal remisePct = nz(ligne.getRemisePourcentage());
            BigDecimal brut = nz(ligne.getPrixUnitaireHt()).multiply(BigDecimal.valueOf(ligne.getQuantite()));
            BigDecimal remise = brut.multiply(remisePct).divide(BigDecimal.valueOf(100), 6, java.math.RoundingMode.HALF_UP);
            totalRemises = totalRemises.add(remise);
            if (remisePct.compareTo(plafondRemise) > 0) {
                exceptionsRemise++;
            }
        }

        List<AvoirClient> avoirs = avoirRepository.findAll();
        long volumeAvoirs = avoirs.size();
        BigDecimal valeurAvoirs = avoirs.stream()
            .map(AvoirClient::getMontant)
            .filter(m -> m != null)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CauseStat> causes = buildAvoirCauses(avoirs);

        List<BacklogStockVente> backlogs = backlogRepository.findAll();
        long backlogCount = backlogs.stream()
            .filter(b -> "EN_ATTENTE".equalsIgnoreCase(b.getStatut()))
            .count();
        int backlogQuantite = backlogs.stream()
            .filter(b -> "EN_ATTENTE".equalsIgnoreCase(b.getStatut()))
            .mapToInt(b -> b.getQuantiteManquante() != null ? b.getQuantiteManquante() : 0)
            .sum();

        model.addAttribute("totalCommandes", totalCommandes);
        model.addAttribute("enCours", enCours);
        model.addAttribute("livrees", livrees);
        model.addAttribute("enRetard", enRetard);
        model.addAttribute("tauxAnnulation", String.format("%.1f", tauxAnnulation));
        model.addAttribute("motifsAnnulation", motifsAnnulation);
        model.addAttribute("totalRemises", totalRemises);
        model.addAttribute("plafondRemise", plafondRemise);
        model.addAttribute("exceptionsRemise", exceptionsRemise);
        model.addAttribute("volumeAvoirs", volumeAvoirs);
        model.addAttribute("valeurAvoirs", valeurAvoirs);
        model.addAttribute("causesAvoirs", causes);
        model.addAttribute("backlogCount", backlogCount);
        model.addAttribute("backlogQuantite", backlogQuantite);
        model.addAttribute("activePage", "vente-kpi");
        return "vente/kpi-responsable";
    }

    @GetMapping("/devis/liste")
    public String listDevis(Model model, HttpSession session) {
        model.addAttribute("devis", devisRepository.findAllByOrderByDateDevisDesc());
        Object flashError = session.getAttribute("flashError");
        if (flashError != null) {
            model.addAttribute("flashError", flashError.toString());
            session.removeAttribute("flashError");
        }
        model.addAttribute("activePage", "vente-devis");
        return "vente/devis-liste";
    }

    @GetMapping("/devis/{id}/pdf")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> exporterDevisPdf(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        DevisVente devis = devisRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            float margin = 40;
            float yStart = page.getMediaBox().getHeight() - margin;
            float y = yStart;
            float leading = 14;

            PDPageContentStream content = new PDPageContentStream(document, page);

            float logoHeight = 32;
            float logoWidth = 120;
            drawLogo(document, content, margin, yStart - logoHeight + 6, logoWidth, logoHeight);

            writeText(content, "CORE ERP", margin, y, PDType1Font.HELVETICA_BOLD, 16);
            writeText(content, "Devis" , page.getMediaBox().getWidth() - margin - 60, y, PDType1Font.HELVETICA_BOLD, 14);

            y -= leading * 2;
            writeText(content, "Référence: " + devis.getReference(), margin, y, PDType1Font.HELVETICA_BOLD, 11);
            y -= leading;
            writeText(content, "Date: " + (devis.getDateDevis() != null ? devis.getDateDevis().toLocalDate() : ""), margin, y, PDType1Font.HELVETICA, 10);
            y -= leading;
            writeText(content, "Statut: " + devis.getStatut(), margin, y, PDType1Font.HELVETICA, 10);
            y -= leading * 1.5f;

            writeText(content, "Client", margin, y, PDType1Font.HELVETICA_BOLD, 11);
            y -= leading;
            writeText(content, devis.getClient().getNom(), margin, y, PDType1Font.HELVETICA, 10);
            y -= leading;
            if (devis.getClient().getAdresse() != null) {
                writeText(content, devis.getClient().getAdresse(), margin, y, PDType1Font.HELVETICA, 10);
                y -= leading;
            }
            if (devis.getClient().getVille() != null) {
                writeText(content, devis.getClient().getVille(), margin, y, PDType1Font.HELVETICA, 10);
                y -= leading;
            }

            y -= leading;
            float tableTop = y;
            float tableWidth = page.getMediaBox().getWidth() - margin * 2;
            float[] colWidths = new float[]{200, 40, 60, 50, 50, 60, 70};
            String[] headers = new String[]{"Article", "Qté", "PU HT", "Rem %", "TVA %", "Total HT", "Total TTC"};

            drawTableHeader(content, margin, y, colWidths, headers);
            y -= leading;

            for (LigneDevisVente ligne : devis.getLignes()) {
                if (y < margin + 80) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = page.getMediaBox().getHeight() - margin;
                    drawTableHeader(content, margin, y, colWidths, headers);
                    y -= leading;
                }

                String articleLabel = getArticleLabel(ligne.getArticleId());
                String[] row = new String[]{
                    articleLabel,
                    String.valueOf(ligne.getQuantite()),
                    formatMoney(ligne.getPrixUnitaireHt()),
                    formatPercent(ligne.getRemisePourcentage()),
                    formatPercent(ligne.getTvaPourcentage()),
                    formatMoney(ligne.getTotalHt()),
                    formatMoney(ligne.getTotalTtc())
                };
                drawTableRow(content, margin, y, colWidths, row);
                y -= leading;
            }

            y -= leading;
            if (y < margin + 60) {
                content.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                y = page.getMediaBox().getHeight() - margin;
            }

            float totalsX = page.getMediaBox().getWidth() - margin - 220;
            drawTotalsBox(content, totalsX, y, devis);

            float footerY = margin + 40;
            content.moveTo(margin, footerY + 10);
            content.lineTo(page.getMediaBox().getWidth() - margin, footerY + 10);
            content.stroke();
            writeText(content, "Validité: " + (devis.getValiditeJours() != null ? devis.getValiditeJours() : 15) + " jours", margin, footerY, PDType1Font.HELVETICA, 9);
            writeText(content, "Merci pour votre confiance.", margin, footerY - 12, PDType1Font.HELVETICA, 9);
            writeText(content, "Conditions: paiement selon accord client.", margin, footerY - 24, PDType1Font.HELVETICA, 9);

            content.close();

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            byte[] bytes = out.toByteArray();
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + devis.getReference() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
        } catch (IOException ex) {
            throw new RuntimeException("Erreur génération PDF", ex);
        }
    }

    private void writeText(PDPageContentStream content, String text, float x, float y,
                           org.apache.pdfbox.pdmodel.font.PDFont font, float size) throws IOException {
        content.setFont(font, size);
        content.beginText();
        content.newLineAtOffset(x, y);
        content.showText(text != null ? text : "");
        content.endText();
    }

    private void drawTableHeader(PDPageContentStream content, float x, float y, float[] widths, String[] headers) throws IOException {
        float currentX = x;
        content.setFont(PDType1Font.HELVETICA_BOLD, 9);
        for (int i = 0; i < headers.length; i++) {
            writeText(content, headers[i], currentX + 2, y, PDType1Font.HELVETICA_BOLD, 9);
            currentX += widths[i];
        }
        content.moveTo(x, y - 2);
        content.lineTo(x + sum(widths), y - 2);
        content.stroke();
    }

    private void drawTableRow(PDPageContentStream content, float x, float y, float[] widths, String[] cells) throws IOException {
        float currentX = x;
        content.setFont(PDType1Font.HELVETICA, 9);
        for (int i = 0; i < cells.length; i++) {
            String value = cells[i] != null ? cells[i] : "";
            writeText(content, value, currentX + 2, y, PDType1Font.HELVETICA, 9);
            currentX += widths[i];
        }
    }

    private void drawTotalsBox(PDPageContentStream content, float x, float y, DevisVente devis) throws IOException {
        float boxWidth = 220;
        float boxHeight = 60;
        content.addRect(x, y - boxHeight, boxWidth, boxHeight);
        content.stroke();
        writeText(content, "Total HT: " + formatMoney(devis.getTotalHt()), x + 10, y - 18, PDType1Font.HELVETICA_BOLD, 10);
        writeText(content, "TVA: " + formatMoney(devis.getTotalTva()), x + 10, y - 34, PDType1Font.HELVETICA, 10);
        writeText(content, "Total TTC: " + formatMoney(devis.getTotalTtc()), x + 10, y - 50, PDType1Font.HELVETICA_BOLD, 10);
    }

    private float sum(float[] values) {
        float total = 0;
        for (float v : values) {
            total += v;
        }
        return total;
    }

    private String formatMoney(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(2, java.math.RoundingMode.HALF_UP).toString();
    }

    private String formatPercent(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        return v.setScale(2, java.math.RoundingMode.HALF_UP) + "%";
    }

    private String getArticleLabel(UUID articleId) {
        return articleRepository.findById(articleId)
            .map(a -> a.getCodeArticle() + " - " + a.getLibelle())
            .orElse(articleId.toString());
    }

    private void drawLogo(PDDocument document, PDPageContentStream content, float x, float y, float width, float height) throws IOException {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(logoPath)) {
            if (is == null) {
                return;
            }
            BufferedImage image = ImageIO.read(is);
            if (image == null) {
                return;
            }
            PDImageXObject pdImage = LosslessFactory.createFromImage(document, image);
            content.drawImage(pdImage, x, y, width, height);
        }
    }

    @GetMapping("/devis/nouveau")
    public String nouveauDevis(Model model) {
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("articles", articleRepository.findAll());
        model.addAttribute("activePage", "vente-devis");
        return "vente/devis-nouveau";
    }

    @GetMapping("/devis/{id}/modifier")
    public String modifierDevisForm(@PathVariable UUID id, Model model, HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        DevisVente devis = devisRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Devis introuvable"));
        if (devis.getStatut() != com.gestion.vente.enums.StatutDevis.BROUILLON) {
            session.setAttribute("flashError", "Seuls les devis en brouillon sont modifiables");
            return "redirect:/ventes/devis/liste";
        }
        LigneDevisVente ligne = devis.getLignes().isEmpty() ? null : devis.getLignes().get(0);
        model.addAttribute("devis", devis);
        model.addAttribute("ligne", ligne);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("articles", articleRepository.findAll());
        model.addAttribute("activePage", "vente-devis");
        return "vente/devis-modifier";
    }

    @PostMapping("/devis/nouveau")
    public String creerDevis(@RequestParam UUID clientId,
                             @RequestParam UUID articleId,
                             @RequestParam Integer quantite,
                             @RequestParam BigDecimal prixUnitaireHt,
                             @RequestParam(required = false) BigDecimal remisePourcentage,
                             @RequestParam(required = false) BigDecimal tvaPourcentage,
                             HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        String role = (String) session.getAttribute("userRole");
        UUID userId = (UUID) session.getAttribute("userId");
        BigDecimal remise = remisePourcentage != null ? remisePourcentage : BigDecimal.ZERO;
        CreateDevisRequest request = new CreateDevisRequest();
        request.setClientId(clientId);
        request.setCreePar(userId);
        LigneVenteRequest ligne = new LigneVenteRequest();
        ligne.setArticleId(articleId);
        ligne.setQuantite(quantite);
        BigDecimal prixFinal = prixUnitaireHt;
        BigDecimal tvaFinal = tvaPourcentage;
        if (prixFinal == null || tvaFinal == null) {
            Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article introuvable"));
            if (prixFinal == null) {
                prixFinal = article.getPrixVenteHt();
            }
            if (tvaFinal == null) {
                tvaFinal = article.getTvaPourcentage();
            }
        }
        ligne.setPrixUnitaireHt(prixFinal);
        if (remisePourcentage != null) {
            ligne.setRemisePourcentage(remisePourcentage);
        }
        if (tvaFinal != null) {
            ligne.setTvaPourcentage(tvaFinal);
        }
        request.setLignes(List.of(ligne));
        venteService.creerDevis(request);
        if ("COMMERCIAL".equals(role) && remise.compareTo(PLAFOND_REMISE_COMMERCIAL) > 0) {
            session.setAttribute("flashError", "Devis en attente de validation responsable (remise au-dessus du plafond)");
        }
        return "redirect:/ventes/devis/liste";
    }

    @PostMapping("/devis/{id}/modifier")
    public String modifierDevis(@PathVariable UUID id,
                                @RequestParam UUID clientId,
                                @RequestParam UUID articleId,
                                @RequestParam Integer quantite,
                                @RequestParam BigDecimal prixUnitaireHt,
                                @RequestParam(required = false) BigDecimal remisePourcentage,
                                @RequestParam(required = false) BigDecimal tvaPourcentage,
                                HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        CreateDevisRequest request = new CreateDevisRequest();
        request.setClientId(clientId);
        LigneVenteRequest ligne = new LigneVenteRequest();
        ligne.setArticleId(articleId);
        ligne.setQuantite(quantite);
        BigDecimal prixFinal = prixUnitaireHt;
        BigDecimal tvaFinal = tvaPourcentage;
        if (prixFinal == null || tvaFinal == null) {
            Article article = articleRepository.findById(articleId)
                .orElseThrow(() -> new RuntimeException("Article introuvable"));
            if (prixFinal == null) {
                prixFinal = article.getPrixVenteHt();
            }
            if (tvaFinal == null) {
                tvaFinal = article.getTvaPourcentage();
            }
        }
        ligne.setPrixUnitaireHt(prixFinal);
        if (remisePourcentage != null) {
            ligne.setRemisePourcentage(remisePourcentage);
        }
        if (tvaFinal != null) {
            ligne.setTvaPourcentage(tvaFinal);
        }
        request.setLignes(List.of(ligne));
        try {
            venteService.modifierDevis(id, request);
            return "redirect:/ventes/devis/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @PostMapping("/devis/{id}/valider")
    public String validerDevis(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        UUID userId = (UUID) session.getAttribute("userId");
        try {
            venteService.validerDevis(id, userId);
            return "redirect:/ventes/devis/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @PostMapping("/devis/{id}/soumettre")
    public String soumettreDevis(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        try {
            venteService.soumettreDevis(id);
            return "redirect:/ventes/devis/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @PostMapping("/devis/{id}/refuser")
    public String refuserDevis(@PathVariable UUID id,
                               @RequestParam(required = false) String motif,
                               HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        UUID userId = (UUID) session.getAttribute("userId");
        try {
            venteService.refuserDevis(id, userId, motif);
            return "redirect:/ventes/devis/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @PostMapping("/devis/{id}/commande")
    public String transformerDevis(@PathVariable UUID id,
                                   @RequestParam(required = false) String modeReservation,
                                   HttpSession session) {
        requireRole(session, "ADMIN", "COMMERCIAL", "RESPONSABLE_VENTES");
        CreateCommandeFromDevisRequest request = new CreateCommandeFromDevisRequest();
        request.setModeReservation(modeReservation != null ? modeReservation : "IMMEDIATE");
        request.setCreePar((UUID) session.getAttribute("userId"));
        try {
            venteService.creerCommandeDepuisDevis(id, request);
            return "redirect:/ventes/commandes/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/devis/liste";
        }
    }

    @GetMapping("/commandes/liste")
    public String listCommandes(Model model, HttpSession session) {
        model.addAttribute("commandes", commandeRepository.findAllByOrderByDateCommandeDesc());
        Object flashError = session.getAttribute("flashError");
        if (flashError != null) {
            model.addAttribute("flashError", flashError.toString());
            session.removeAttribute("flashError");
        }
        model.addAttribute("activePage", "vente-commandes");
        return "vente/commandes-liste";
    }

    @PostMapping("/commandes/{id}/livrer")
    public String livrerCommande(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "MAGASINIER_SORTIE");
        CreateLivraisonRequest request = new CreateLivraisonRequest();
        request.setUtilisateurId((UUID) session.getAttribute("userId"));
        try {
            venteService.creerLivraison(id, request);
            return "redirect:/ventes/livraisons/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/commandes/liste";
        }
    }

    @PostMapping("/commandes/{id}/facturer")
    public String facturerCommande(@PathVariable UUID id, HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        try {
            venteService.genererFacture(id, null);
            return "redirect:/ventes/factures/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/commandes/liste";
        }
    }

    @PostMapping("/commandes/{id}/annuler")
    public String annulerCommande(@PathVariable UUID id,
                                  @RequestParam(required = false) String motif,
                                  HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        UUID userId = (UUID) session.getAttribute("userId");
        try {
            venteService.annulerCommande(id, userId, motif);
            return "redirect:/ventes/commandes/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/commandes/liste";
        }
    }

    @PostMapping("/commandes/{id}/debloquer")
    public String debloquerCommande(@PathVariable UUID id,
                                    @RequestParam(required = false) String motif,
                                    HttpSession session) {
        requireRole(session, "ADMIN", "RESPONSABLE_VENTES");
        UUID userId = (UUID) session.getAttribute("userId");
        try {
            venteService.debloquerCommande(id, userId, motif);
            return "redirect:/ventes/commandes/liste";
        } catch (RuntimeException ex) {
            session.setAttribute("flashError", ex.getMessage());
            return "redirect:/ventes/commandes/liste";
        }
    }

    @GetMapping("/livraisons/liste")
    public String listLivraisons(Model model) {
        model.addAttribute("livraisons", livraisonRepository.findAllByOrderByCreatedAtDesc());
        model.addAttribute("activePage", "vente-livraisons");
        return "vente/livraisons-liste";
    }

    @GetMapping("/factures/liste")
    public String listFactures(Model model) {
        List<FactureVente> factures = factureRepository.findAllByOrderByDateFactureDesc();
        BigDecimal montantPaye = factures.stream()
            .filter(f -> f.getStatut() == com.gestion.vente.enums.StatutFactureVente.PAYEE)
            .map(FactureVente::getTotalTtc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal montantRestant = factures.stream()
            .filter(f -> f.getStatut() != com.gestion.vente.enums.StatutFactureVente.PAYEE)
            .map(FactureVente::getTotalTtc)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        model.addAttribute("factures", factures);
        model.addAttribute("montantPaye", montantPaye);
        model.addAttribute("montantRestant", montantRestant);
        model.addAttribute("activePage", "vente-factures");
        return "vente/factures-liste";
    }

    @PostMapping("/factures/{id}/payer")
    public String payerFacture(@PathVariable UUID id,
                               @RequestParam java.math.BigDecimal montant,
                               @RequestParam(defaultValue = "VIREMENT") ModePaiement modePaiement,
                               @RequestParam(required = false) String notes,
                               HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        CreatePaiementRequest request = new CreatePaiementRequest();
        request.setMontant(montant);
        request.setModePaiement(modePaiement);
        request.setNotes(notes);
        venteService.enregistrerPaiement(id, request);
        return "redirect:/ventes/factures/liste";
    }

    @PostMapping("/factures/{id}/avoir")
    public String creerAvoir(@PathVariable UUID id,
                             @RequestParam java.math.BigDecimal montant,
                             @RequestParam String motif,
                             HttpSession session) {
        requireRole(session, "ADMIN", "COMPTABLE_CLIENT");
        CreateAvoirRequest request = new CreateAvoirRequest();
        request.setMontant(montant);
        request.setMotif(motif);
        venteService.creerAvoir(id, request);
        return "redirect:/ventes/avoirs/liste";
    }

    @GetMapping("/paiements/liste")
    public String listPaiements(Model model) {
        model.addAttribute("paiements", paiementRepository.findAllByOrderByDatePaiementDesc());
        model.addAttribute("activePage", "vente-paiements");
        return "vente/paiements-liste";
    }

    @GetMapping("/avoirs/liste")
    public String listAvoirs(Model model) {
        model.addAttribute("avoirs", avoirRepository.findAllByOrderByDateAvoirDesc());
        model.addAttribute("activePage", "vente-avoirs");
        return "vente/avoirs-liste";
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private List<CauseStat> buildAvoirCauses(List<AvoirClient> avoirs) {
        CauseStat retour = new CauseStat("Retour", 0, BigDecimal.ZERO);
        CauseStat prix = new CauseStat("Erreur prix", 0, BigDecimal.ZERO);
        CauseStat casse = new CauseStat("Casse", 0, BigDecimal.ZERO);
        CauseStat autre = new CauseStat("Autre", 0, BigDecimal.ZERO);

        for (AvoirClient a : avoirs) {
            String motif = a.getMotif() != null ? a.getMotif().toLowerCase() : "";
            BigDecimal montant = a.getMontant() != null ? a.getMontant() : BigDecimal.ZERO;
            if (motif.contains("retour")) {
                retour = retour.add(montant);
            } else if (motif.contains("prix")) {
                prix = prix.add(montant);
            } else if (motif.contains("casse")) {
                casse = casse.add(montant);
            } else {
                autre = autre.add(montant);
            }
        }
        return List.of(retour, prix, casse, autre);
    }

    private static class CauseStat {
        private final String label;
        private final long count;
        private final BigDecimal montant;

        private CauseStat(String label, long count, BigDecimal montant) {
            this.label = label;
            this.count = count;
            this.montant = montant;
        }

        private CauseStat add(BigDecimal montantAdd) {
            return new CauseStat(label, count + 1, montant.add(montantAdd));
        }

        public String getLabel() {
            return label;
        }

        public long getCount() {
            return count;
        }

        public BigDecimal getMontant() {
            return montant;
        }
    }
}
