package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.repo.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventRepository repo;

    // GET /events?location=porto&date=2025-06-01&type=LIMPEZA  (todos opcionais)
    @GetMapping
    public List<Event> getEvents(
        @RequestParam(required = false) String location,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Event.Type type
    ) {
        // Se nenhum filtro, devolve todos publicados
        if (location == null && date == null && type == null) {
            return repo.findByFilters(null, null, null);
        }
        return repo.findByFilters(location, date, type);
    }

    // GET /events/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable Long id) {
        return repo.findById(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /events  (só PROMOTER)
    @PostMapping
    public ResponseEntity<Event> create(@RequestBody Event event) {
        event.setStatus(Event.Status.PUBLISHED);
        return ResponseEntity.status(HttpStatus.CREATED).body(repo.save(event));
    }

    // PUT /events/{id}  (só PROMOTER)
    @PutMapping("/{id}")
    public ResponseEntity<Event> update(@PathVariable Long id, @RequestBody Event updated) {
        return repo.findById(id).map(e -> {
            e.setTitle(updated.getTitle());
            e.setDescription(updated.getDescription());
            e.setLocation(updated.getLocation());
            e.setCapacity(updated.getCapacity());
            e.setStartDate(updated.getStartDate());
            e.setEndDate(updated.getEndDate());
            e.setType(updated.getType());
            e.setImageUrl(updated.getImageUrl());
            e.setStatus(updated.getStatus());
            return ResponseEntity.ok(repo.save(e));
        }).orElse(ResponseEntity.notFound().build());
    }
}