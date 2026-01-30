package com.gestion.stock.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/stock")
@RequiredArgsConstructor
@Slf4j
public class StockDashboardController {
    
    // @GetMapping("/dashboard")
    // public String dashboard(Model model, HttpSession session) {
    //     if (session.getAttribute("userId") == null) {
    //         return "redirect:/login";
    //     }
        
    //     model.addAttribute("title", "Dashboard Stock");
    //     model.addAttribute("activePage", "stock-dashboard");
        
    //     // Ajouter les données pour les KPI
    //     // Ces données devraient venir de vos services
        
    //     return "stock/dashboard";
    // }
    
    // @GetMapping("/articles/liste")
    // public String listeArticles(Model model, HttpSession session) {
    //     if (session.getAttribute("userId") == null) {
    //         return "redirect:/login";
    //     }
        
    //     model.addAttribute("title", "Articles");
    //     model.addAttribute("activePage", "stock-articles");
        
    //     return "stock/articles/liste";
    // }
    
    // @GetMapping("/lots/liste")
    // public String listeLots(Model model, HttpSession session) {
    //     if (session.getAttribute("userId") == null) {
    //         return "redirect:/login";
    //     }
        
    //     model.addAttribute("title", "Lots");
    //     model.addAttribute("activePage", "stock-lots");
        
    //     return "stock/lots/liste";
    // }
}