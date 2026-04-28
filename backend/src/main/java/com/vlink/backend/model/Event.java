package com.vlink.backend.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name = "events")
@Getter @Setter @NoArgsConstructor
public class Event {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    private String title;

    @Column(length = 300)
    private String description;

    @Column(nullable = false, length = 100)
    private String location;

    @Column(nullable = false)
    private int capacity = 1;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Type type = Type.OUTRO;

    public enum Status { DRAFT, PUBLISHED, CLOSED }

    public enum Type { LIMPEZA, DOACAO, EDUCACAO, AMBIENTE, SOCIAL, OUTRO }
}