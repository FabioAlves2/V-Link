package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.SubscriptionRepository;
import com.vlink.backend.repo.UserRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
public class EventController {

    private final EventRepository repo;
    private final SubscriptionRepository subscriptionRepo;
    private final UserRepository userRepo;

    private Event withSubscriberCount(Event event) {
        event.setSubscriberCount((int) subscriptionRepo.countByEventId(event.getId()));
        return event;
    }

    // GET /events?location=porto&date=2025-06-01&type=LIMPEZA  (todos opcionais)
    @GetMapping
    public List<Event> getEvents(
        @RequestParam(required = false) String location,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Event.Type type
    ) {
        List<Event> events = repo.findByFilters(location, date, type);
        events.forEach(this::withSubscriberCount);
        return events;
    }

    // GET /events/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable Long id) {
        return repo.findById(id)
            .map(this::withSubscriberCount)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /events  (só PROMOTER)
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Event event, Authentication auth) {
        if (event.getStatus() == Event.Status.CLOSED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Um evento não pode ser criado já fechado."));
        }
        User organizer = userRepo.findByEmail(auth.getName()).orElseThrow();
        event.setId(null); // impede que um id vindo do cliente transforme isto num update de outro evento
        event.setOrganizer(organizer);
        event.setStatus(event.getStatus() == Event.Status.PUBLISHED ? Event.Status.PUBLISHED : Event.Status.DRAFT);
        return ResponseEntity.status(HttpStatus.CREATED).body(withSubscriberCount(repo.save(event)));
    }

    // PUT /events/{id}  (só o PROMOTER que criou o evento)
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody Event updated, Authentication auth) {
        return repo.findById(id).map(e -> {
            if (!e.getOrganizer().getEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Só o promotor que criou este evento o pode editar."));
            }
            e.setTitle(updated.getTitle());
            e.setDescription(updated.getDescription());
            e.setLocation(updated.getLocation());
            e.setCapacity(updated.getCapacity());
            e.setStartDate(updated.getStartDate());
            e.setEndDate(updated.getEndDate());
            e.setType(updated.getType());
            e.setImageUrl(updated.getImageUrl());
            e.setStatus(updated.getStatus());
            return ResponseEntity.ok(withSubscriberCount(repo.save(e)));
        }).orElse(ResponseEntity.notFound().build());
    }
}