package com.vlink.backend.model;

import jakarta.persistence.*;
import lombok.*;

//JPA persistent class
@Entity @Table(name="users")

//Generate Getters. Setters and null constructor
@Getter @Setter @NoArgsConstructor
public class User {

    //Primary Key, auto-incremented in DB
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //Not NULL, max size 100
    @Column(nullable=false, length = 100)
    private String name;

    //Unique
    @Column(nullable=false, unique=true, length=150)
    private String email;
    
    //Type String, default = volunteer
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.VOLUNTEER;

    public enum Role {
        VOLUNTEER, ORGANIZER
    }    
    
}
