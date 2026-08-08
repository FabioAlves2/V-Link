package com.vlink.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "recipient_id", nullable = false)
    private User recipient;

    @ManyToOne
    @JoinColumn(name = "event_id")
    private Event event;

    @Column(nullable = false, length = 300)
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
