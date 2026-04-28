package com.vlink.backend.repo;

import com.vlink.backend.model.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    @Query("""
        SELECT e FROM Event e WHERE
        e.status = 'PUBLISHED' AND
        (:location IS NULL OR LOWER(e.location) LIKE LOWER(CONCAT('%', :location, '%'))) AND
        (:date IS NULL OR CAST(e.startDate AS date) = :date) AND
        (:type IS NULL OR e.type = :type)
    """)
    List<Event> findByFilters(
        @Param("location") String location,
        @Param("date") LocalDate date,
        @Param("type") Event.Type type
    );
}