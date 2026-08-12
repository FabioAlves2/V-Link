package com.vlink.backend.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "event_id"}))
@Getter @Setter @NoArgsConstructor
public class Subscription {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "checked_in", nullable = false)
    private boolean checkedIn = false;

    @Column(name = "checked_in_at")
    private LocalDateTime checkedInAt;

    // Marca quando o lembrete de "evento a começar em breve" foi enviado, para não o repetir a
    // cada execução do EventReminderScheduler. Reposto a null se o evento for reagendado
    // (EventController.update) — sem isso, quem já tinha sido avisado para o horário antigo
    // nunca seria avisado para o novo.
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;
}
