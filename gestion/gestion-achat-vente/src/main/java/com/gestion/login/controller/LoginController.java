package com.gestion.login.controller;

import com.gestion.stock.entity.Utilisateur;
import com.gestion.stock.repository.UtilisateurRepository;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@Slf4j
public class LoginController {
    
    private final UtilisateurRepository utilisateurRepository;
    private static final Map<String, HttpSession> activeSessions = new HashMap<>();
    
    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            HttpSession session,
            Model model) {
        
        if (session.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        
        if (error != null) model.addAttribute("error", "Identifiants incorrects");
        if (logout != null) model.addAttribute("logout", true);
        
        return "login";
    }
    
    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            HttpSession session,
            Model model) {
        
        try {
            Optional<Utilisateur> utilisateurOpt = utilisateurRepository.findByUsername(username)
                    .or(() -> utilisateurRepository.findByEmail(username));
            
            if (utilisateurOpt.isEmpty() || !password.equals(utilisateurOpt.get().getPassword())) {
                model.addAttribute("error", "Identifiants incorrects");
                return "login";
            }
            
            Utilisateur utilisateur = utilisateurOpt.get();
            if (!utilisateur.isActif()) {
                model.addAttribute("error", "Compte désactivé");
                return "login";
            }
            
            // Nettoyage session existante
            if (activeSessions.containsKey(utilisateur.getUsername())) {
                try { activeSessions.get(utilisateur.getUsername()).invalidate(); } catch (Exception e) {}
            }
            
            // --- SYNC AVEC LE LAYOUT ---
            session.setAttribute("userId", utilisateur.getId());
            session.setAttribute("username", utilisateur.getUsername());
            session.setAttribute("userRole", utilisateur.getRole().name()); // ADMIN, DAF, etc.
            session.setAttribute("userFullName", utilisateur.getPrenom() + " " + utilisateur.getNom());
            
            activeSessions.put(utilisateur.getUsername(), session);
            
            return "redirect:/dashboard";
            
        } catch (Exception e) {
            model.addAttribute("error", "Erreur système");
            return "login";
        }
    }

    @GetMapping("/dashboard")
    public String dispatch(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) return "redirect:/login";

        // REDIRECTION BASÉE SUR TON FINANCE-CONTROLLER
        switch (role) {
            case "DAF":
            case "ADMIN":
                // L'admin et le DAF ont une vue globale (Mismatches / Audit)
                return "redirect:/finance/daf"; 
            
            case "FINANCE":
            case "COMPTABLE":
                // Le comptable arrive sur la gestion des BC à valider et Factures
                return "redirect:/achats/dashboard";

            case "RESPONSABLE_ACHATS":
            case "ACHETEUR":
                return "redirect:/achats/dashboard";

            case "RESPONSABLE_VENTES":
            case "COMMERCIAL":
                return "redirect:/ventes/devis/liste";

            case "GESTIONNAIRE_STOCK":
            case "RESPONSABLE_STOCK":
            case "MAGASINIER":
                return "redirect:/main/dashboard";
            case "MANAGER":
                return "redirect:/home";
            default:
                return "redirect:/achats/demandes";
        }
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            activeSessions.remove((String) session.getAttribute("username"));
            session.invalidate();
        }
        return "redirect:/login?logout=true";
    }

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/home")
    public String home(HttpSession session) {
        String role = (String) session.getAttribute("userRole");
        if (role == null) {
            return "redirect:/login";
        }
        if (!"MANAGER".equals(role)) {
            return "redirect:/dashboard";
        }
        return "home";
    }
}