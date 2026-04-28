package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.repo.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final EventRepository eventRepo;

    // email -> set de event IDs (em memória por agora)
    private final Map<String, Set<Long>> subscriptions = new ConcurrentHashMap<>();

    // GET /subscriptions — eventos subscritos pelo utilizador autenticado
    @GetMapping
    public ResponseEntity<List<Event>> mySubscriptions(Authentication auth) {
        Set<Long> ids = subscriptions.getOrDefault(auth.getName(), Set.of());
        List<Event> events = ids.stream()
            .map(eventRepo::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .toList();
        return ResponseEntity.ok(events);
    }

    // GET /subscriptions/{eventId} — verifica se está subscrito
    @GetMapping("/{eventId}")
    public ResponseEntity<Map<String, Boolean>> isSubscribed(
            @PathVariable Long eventId, Authentication auth) {
        boolean sub = subscriptions
            .getOrDefault(auth.getName(), Set.of())
            .contains(eventId);
        return ResponseEntity.ok(Map.of("subscribed", sub));
    }

    // POST /subscriptions/{eventId} — subscrever
    @PostMapping("/{eventId}")
    public ResponseEntity<?> subscribe(@PathVariable Long eventId, Authentication auth) {
        if (!eventRepo.existsById(eventId))
            return ResponseEntity.notFound().build();
        subscriptions.computeIfAbsent(auth.getName(), k -> ConcurrentHashMap.newKeySet()).add(eventId);
        return ResponseEntity.ok(Map.of("subscribed", true));
    }

    // DELETE /subscriptions/{eventId} — cancelar subscrição
    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> unsubscribe(@PathVariable Long eventId, Authentication auth) {
        subscriptions.getOrDefault(auth.getName(), Set.of()).remove(eventId);
        return ResponseEntity.ok(Map.of("subscribed", false));
    }
}