package com.gestion.manager.service;


import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

import com.gestion.manager.entity.Manager;
import com.gestion.manager.repository.ManagerRepository;


@Service
@RequiredArgsConstructor
public class AuthService {

    private final ManagerRepository repository;

    public Manager signup(String username, String password) {

        if (repository.findByUsername(username).isPresent()) {
            throw new RuntimeException("Username déjà utilisé");
        }

        Manager manager = Manager.builder()
                .username(username)
                .password(password) // en prod → BCrypt
                .build();

        return repository.save(manager);
    }

    public Manager login(String username, String password, HttpSession session) {

        Manager manager = repository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Utilisateur introuvable"));

        if (!manager.getPassword().equals(password)) {
            throw new RuntimeException("Mot de passe incorrect");
        }

        session.setAttribute("USER", manager);
        return manager;
    }

    public void logout(HttpSession session) {
        session.invalidate();
    }

    public boolean isLogged(HttpSession session) {
        return session.getAttribute("USER") != null;
    }
}
