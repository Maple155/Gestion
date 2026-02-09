package com.gestion.vente.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clients")
@Getter
@Setter
@NoArgsConstructor
public class Client {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private String nom;

    private String email;
    private String telephone;
    private String adresse;
    private String ville;
    private String pays;

    private boolean actif = true;

    private BigDecimal plafondRemise = BigDecimal.ZERO;
    private BigDecimal plafondCredit = BigDecimal.ZERO;
    private String conditionsPaiement;

    @CreationTimestamp
    private LocalDateTime createdAt;
}
