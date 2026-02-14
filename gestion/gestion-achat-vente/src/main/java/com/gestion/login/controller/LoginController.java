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
    
    // Stockage temporaire des sessions actives (pour simplifier)
    private static final Map<String, HttpSession> activeSessions = new HashMap<>();
    
    @GetMapping("/login")
    public String showLoginPage(
            @RequestParam(value = "error", required = false) String error,
            @RequestParam(value = "logout", required = false) String logout,
            @RequestParam(value = "username", required = false) String username,
            HttpSession session,
            Model model) {
        
        // Si déjà connecté, rediriger vers le dashboard
        if (session.getAttribute("userId") != null) {
            return "redirect:/dashboard";
        }
        
        if (error != null) {
            model.addAttribute("error", "Identifiants incorrects");
        }
        
        if (logout != null) {
            model.addAttribute("logout", true);
        }
        
        if (username != null) {
            model.addAttribute("username", username);
        }
        
        return "login";
    }
    
    @PostMapping("/login")
    public String login(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam(required = false, defaultValue = "false") boolean remember,
            HttpSession session,
            Model model) {
        
        try {
            log.info("Tentative de connexion pour l'utilisateur: {}", username);
            
            // Rechercher l'utilisateur
            Optional<Utilisateur> utilisateurOpt = utilisateurRepository.findByUsername(username)
                    .or(() -> utilisateurRepository.findByEmail(username));
            
            if (utilisateurOpt.isEmpty()) {
                log.warn("Utilisateur non trouvé: {}", username);
                model.addAttribute("error", "Identifiants incorrects");
                model.addAttribute("username", username);
                return "login";
            }
            
            Utilisateur utilisateur = utilisateurOpt.get();
            
            // Vérifier si le compte est actif
            if (!utilisateur.isActif()) {
                log.warn("Compte désactivé: {}", username);
                model.addAttribute("error", "Compte désactivé");
                model.addAttribute("username", username);
                return "login";
            }
            
            // Vérifier le mot de passe (simplifié - dans la réalité, utiliser BCrypt)
            if (!password.equals(utilisateur.getPassword())) { // À remplacer par BCrypt
                log.warn("Mot de passe incorrect pour: {}", username);
                model.addAttribute("error", "Identifiants incorrects");
                model.addAttribute("username", username);
                return "login";
            }
            
            // Vérifier si l'utilisateur a déjà une session active
            String sessionKey = username + "-session";
            if (activeSessions.containsKey(sessionKey)) {
                HttpSession oldSession = activeSessions.get(sessionKey);
                try {
                    oldSession.invalidate();
                } catch (IllegalStateException e) {
                    // Session déjà invalidée
                }
            }
            
            // Stocker les informations dans la session
            session.setAttribute("userId", utilisateur.getId());
            session.setAttribute("username", utilisateur.getUsername());
            session.setAttribute("userRole", utilisateur.getRole().name());
            session.setAttribute("userFullName", utilisateur.getPrenom() + " " + utilisateur.getNom());
            
            // Stocker la session active
            activeSessions.put(sessionKey, session);
            
            utilisateurRepository.save(utilisateur);
            
            log.info("Connexion réussie pour: {} (Role: {})", username, utilisateur.getRole());
            
            // Redirection selon le rôle
            return "redirect:/dashboard";
            
        } catch (Exception e) {
            log.error("Erreur lors de la connexion", e);
            model.addAttribute("error", "Erreur technique: " + e.getMessage());
            return "login";
        }
    }
    
    @GetMapping("/logout")
    public String logout(HttpSession session) {
        if (session != null) {
            String username = (String) session.getAttribute("username");
            if (username != null) {
                activeSessions.remove(username + "-session");
            }
            session.invalidate();
        }
        return "redirect:/login?logout=true";
    }
    
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        
        // Redirection selon le rôle
        String role = (String) session.getAttribute("userRole");
        if (role != null) {
            switch (role) {
                case "ADMIN":
                    return "redirect:/achats/demandes";
                case "GESTIONNAIRE_STOCK":
                case "RESPONSABLE_STOCK":
                    return "redirect:/main/dashboard";
                case "COMMERCIAL":
                    return "redirect:/ventes/devis/liste";

                case "RESPONSABLE_VENTES":
                    return "redirect:/ventes/devis/liste";

                case "MAGASINIER_SORTIE":
                    return "redirect:/ventes/devis/liste";
                case "COMPTABLE_CLIENT":
                    return "redirect:/ventes/devis/liste";
                case "COMPTABLE":
                    return "redirect:/comptabilite/dashboard";
                case "FINANCE":
                case "DAF":
                    return "redirect:/finance/daf";
                case "MANAGER":
                    return "redirect:home";
                default:
                    return "redirect:/achats/demandes";
            }
        }
        
        return "redirect:/achats/demandes";
    }
    @GetMapping("/home")
    public String managerHome(HttpSession session) {
        if (session.getAttribute("userId") == null) {
            return "redirect:/login";
        }
        return "home"; // Affiche home.html
    }
    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }
}