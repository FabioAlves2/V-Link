package com.vlink.backend.repo;

import com.vlink.backend.model.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    List<Subscription> findByUserEmail(String email);
    boolean existsByUserEmailAndEventId(String email, Long eventId);
    long countByEventId(Long eventId);
    List<Subscription> findByEventId(Long eventId);
    Optional<Subscription> findByEventIdAndUserId(Long eventId, Long userId);

    @Transactional
    void deleteByUserEmailAndEventId(String email, Long eventId);

    @Transactional
    void deleteByEventId(Long eventId);

    // Elegível para lembrete: ainda não avisado, o evento continua publicado e começa dentro
    // da janela configurada (app.mail.reminder-window-hours). CLOSED nunca entra aqui (encerrar
    // exige que o evento já tenha começado, logo startDate já não pode estar no futuro).
    @Query("""
        SELECT s FROM Subscription s WHERE
        s.reminderSentAt IS NULL AND
        s.event.status = 'PUBLISHED' AND
        s.event.startDate BETWEEN :now AND :windowEnd
    """)
    List<Subscription> findPendingReminders(@Param("now") LocalDateTime now, @Param("windowEnd") LocalDateTime windowEnd);

    @Modifying
    @Transactional
    @Query("UPDATE Subscription s SET s.reminderSentAt = null WHERE s.event.id = :eventId")
    void clearReminderSentAt(@Param("eventId") Long eventId);
}
