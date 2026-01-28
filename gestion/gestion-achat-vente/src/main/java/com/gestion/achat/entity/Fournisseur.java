package com.gestion.achat.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "fournisseurs")
@Getter @Setter @NoArgsConstructor
public class Fournisseur {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String nom;

    private String email;
    private String telephone;
    private String adresse;
    
    private boolean actif = true;

    @CreationTimestamp
    private LocalDateTime createdAt;
}