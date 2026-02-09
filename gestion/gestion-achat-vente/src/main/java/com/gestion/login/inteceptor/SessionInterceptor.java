package com.gestion.login.inteceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class SessionInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        String requestURI = request.getRequestURI();

        // URLs publiques (pas besoin de session)
        if (requestURI.equals("/login") ||
                requestURI.equals("/loginManager") ||
                requestURI.equals("/home") ||
                requestURI.equals("/") ||
                requestURI.equals("signupManager") ||
                requestURI.startsWith("/css/") ||
                requestURI.startsWith("/js/") ||
                requestURI.startsWith("/images/")) {
            return true;
        }

        HttpSession session = request.getSession(false);

        // Vérifier si l'utilisateur est connecté
        if (session == null || session.getAttribute("userId") == null) {
            log.warn("Accès non autorisé à: {} - Session invalide", requestURI);
            response.sendRedirect("/login");
            return false;
        }

        // Vérifier les permissions par rôle (optionnel)
        String userRole = (String) session.getAttribute("userRole");
        if (!hasPermission(requestURI, userRole)) {
            log.warn("Accès refusé à: {} pour le rôle: {}", requestURI, userRole);
            response.sendRedirect("/access-denied");
            return false;
        }

        return true;
    }

    private boolean hasPermission(String uri, String userRole) {
        // Logique de permissions basée sur les rôles
        if (userRole == null)
            return false;

        if (uri.startsWith("/admin/")) {
            return userRole.equals("ADMIN");
        }

        // --- MODULE ACHATS (Adapté avec tes nouveaux rôles) ---
        if (uri.startsWith("/achats/") || uri.startsWith("/api/achats/")) {
            // Liste exhaustive basée sur ton Enum et ton Cahier des Charges
            return userRole.equals("ADMIN") ||
                    userRole.equals("DEMANDEUR") ||
                    userRole.equals("ACHETEUR") ||
                    userRole.equals("RESPONSABLE_ACHATS") ||
                    userRole.equals("APPROBATEUR_N1") ||
                    userRole.equals("APPROBATEUR_N2") ||
                    userRole.equals("APPROBATEUR_N3") ||
                    userRole.equals("DAF") ||
                    userRole.equals("DG") ||
                    userRole.equals("GESTIONNAIRE_STOCK") || // Inclus car ils voient les BR
                    userRole.equals("RESPONSABLE_STOCK");
        }

        // --- MODULE STOCK (Inchangé) ---
        if (uri.startsWith("/stock/")) {
            return userRole.equals("ADMIN") ||
                    userRole.equals("GESTIONNAIRE_STOCK") ||
                    userRole.equals("RESPONSABLE_STOCK") ||
                    userRole.equals("MAGASINIER_SORTIE");
        }

        // --- MODULE VENTES (Inchangé) ---
        if (uri.startsWith("/ventes/")) {
            return userRole.equals("ADMIN") ||
                userRole.equals("COMMERCIAL") ||
                userRole.equals("RESPONSABLE_VENTES") ||
                userRole.equals("MAGASINIER_SORTIE") ||
                userRole.equals("COMPTABLE_CLIENT") ||
                userRole.equals("GESTIONNAIRE_STOCK") || 
                userRole.equals("RESPONSABLE_STOCK") ||
                userRole.equalsIgnoreCase("MANAGER");
        }

        // --- MODULE FINANCE ---
        if (uri.startsWith("/finance/")) {
            return userRole.equals("ADMIN") || userRole.equals("DAF");
        }

        // --- MODULE COMPTABILITÉ (Inchangé) ---
        if (uri.startsWith("/comptabilite/")) {
            return userRole.equals("ADMIN") || userRole.equals("COMPTABLE");
        }

        return true;
    }
}