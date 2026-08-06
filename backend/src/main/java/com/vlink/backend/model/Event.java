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

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private LocalDateTime endDate;

    @Column(length = 500)
    private String imageUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.DRAFT;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Type type = Type.OUTRO;

    @ManyToOne(optional = false)
    @JoinColumn(name = "organizer_id", nullable = false)
    private User organizer;

    // Não persistido — preenchido pelo EventController a partir das subscrições reais
    @Transient
    private int subscriberCount = 0;

    public enum Status { DRAFT, PUBLISHED, CLOSED }

    public enum Type { LIMPEZA, DOACAO, EDUCACAO, AMBIENTE, SOCIAL, OUTRO }

    //Validation of dates
    @PrePersist
    @PreUpdate
    private void validateDates() {
        if (startDate != null && startDate.isBefore(LocalDateTime.now()))
            throw new IllegalArgumentException("A data de início não pode ser no passado.");
        if (startDate != null && endDate != null && endDate.isBefore(startDate))
            throw new IllegalArgumentException("A data de fim não pode ser anterior à data de início.");
    }
}