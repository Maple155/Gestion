package com.gestion.manager.controller;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import com.gestion.manager.dto.LoginRequest;
import com.gestion.manager.service.AuthService;

@Controller
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/loginManager")
    public String loginPage(HttpSession session) {
        if (authService.isLogged(session)) {
            return "redirect:/home";
        }
        return "loginManager";
    }

    @PostMapping("/loginManager")
    public String login(@ModelAttribute @Valid LoginRequest request,
                        HttpSession session,
                        Model model) {

        try {
            authService.login(request.getUsername(), request.getPassword(), session);
            return "redirect:/home";
        } catch (Exception e) {
            model.addAttribute("error", e.getMessage());
            return "loginManager";
        }
    }

    @PostMapping("/signupManager")
    public String signup(@ModelAttribute @Valid LoginRequest request,
                         Model model) {

        authService.signup(request.getUsername(), request.getPassword());
        model.addAttribute("success", "Compte créé");
        return "loginManager";
    }

    @GetMapping("/logoutManager")
    public String logout(HttpSession session) {
        authService.logout(session);
        return "redirect:/loginManager";
    }

    @GetMapping("/home")
    public String home(HttpSession session) {
        if (!authService.isLogged(session)) {
            return "redirect:/loginManager";
        }
        return "home";
    }
}

