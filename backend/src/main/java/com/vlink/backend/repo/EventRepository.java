package com.vlink.backend.repo;

import com.vlink.backend.model.Event;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    // e.endDate >= :now exclui eventos PUBLISHED já terminados que o organizador nunca chegou
    // a "Encerrar" explicitamente — sem isto, um evento passado ficava visível na lista pública
    // para sempre (status continua PUBLISHED até alguém o fechar manualmente).
    @Query("""
        SELECT e FROM Event e WHERE
        e.status = 'PUBLISHED' AND
        e.endDate >= :now AND
        (:location IS NULL OR LOWER(e.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND
        (:date IS NULL OR CAST(e.startDate AS date) = :date) AND
        (:type IS NULL OR e.type = :type)
    """)
    List<Event> findByFilters(
        @Param("location") String location,
        @Param("date") LocalDate date,
        @Param("type") Event.Type type,
        @Param("now") LocalDateTime now
    );

    List<Event> findByOrganizerEmail(String email);

    // Bloqueia a linha do evento até ao fim da transação — usado por SubscriptionController.subscribe
    // para tornar a verificação de capacidade + inserção atómica perante pedidos concorrentes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}