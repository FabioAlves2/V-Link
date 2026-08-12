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
    // CAST(:param AS ...) on every occurrence, including the bare "IS NULL" checks: a parameter
    // used ONLY in "$n IS NULL" gives Postgres's parser no syntactic type to infer. The simple
    // query protocol tolerates this, but once this exact SQL text runs enough times, pgjdbc
    // switches to a server-side prepared statement — whose Describe step demands every parameter
    // resolve to a concrete type up front — and throws "could not determine data type of
    // parameter $n". H2 never hits this (no such server-side prepare step), so it was invisible
    // to the test suite; it surfaced live, one filter at a time, only after enough real requests
    // against Postgres crossed that threshold (Milestone 4's docker-compose demo).
    @Query("""
        SELECT e FROM Event e WHERE
        e.status = 'PUBLISHED' AND
        e.endDate >= :now AND
        (CAST(:location AS string) IS NULL OR LOWER(e.location) LIKE LOWER(CONCAT('%', CAST(:location AS string), '%'))) AND
        (CAST(:date AS date) IS NULL OR CAST(e.startDate AS date) = :date) AND
        (CAST(:type AS string) IS NULL OR e.type = :type) AND
        (CAST(:keyword AS string) IS NULL OR LOWER(e.title) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%'))
                          OR LOWER(e.description) LIKE LOWER(CONCAT('%', CAST(:keyword AS string), '%')))
    """)
    List<Event> findByFilters(
        @Param("location") String location,
        @Param("date") LocalDate date,
        @Param("type") Event.Type type,
        @Param("keyword") String keyword,
        @Param("now") LocalDateTime now
    );

    List<Event> findByOrganizerEmail(String email);

    // Bloqueia a linha do evento até ao fim da transação — usado por SubscriptionController.subscribe
    // para tornar a verificação de capacidade + inserção atómica perante pedidos concorrentes.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);
}