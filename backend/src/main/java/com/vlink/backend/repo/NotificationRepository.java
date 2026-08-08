package com.vlink.backend.repo;

import com.vlink.backend.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByRecipientEmailOrderByCreatedAtDesc(String email);
    long countByRecipientEmailAndReadFalse(String email);
    Optional<Notification> findByIdAndRecipientEmail(Long id, String email);

    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.read = true WHERE n.recipient.email = :email AND n.read = false")
    void markAllReadForUser(@Param("email") String email);

    // Solta a ligação ao evento sem apagar a notificação — usado antes de eliminar um Event
    // para que o destinatário mantenha o histórico (a mensagem já contém o título do evento).
    @Modifying
    @Transactional
    @Query("UPDATE Notification n SET n.event = null WHERE n.event.id = :eventId")
    void detachEvent(@Param("eventId") Long eventId);
}
