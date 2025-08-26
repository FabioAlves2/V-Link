package com.vlink.backend.model;

import java.time.LocalDateTime;

import jakarta.persistence.*;
import lombok.*;

@Entity @Table(name="events")
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

    // default 1
    @Column(nullable = false)
    private int capacity = 1;

    private LocalDateTime startDate;
    private LocalDateTime endDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable=false, length=16)
    private Status status = Status.DRAFT;
  
    public enum Status { DRAFT, PUBLISHED, CLOSED }
}
