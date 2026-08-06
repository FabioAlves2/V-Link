package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.Subscription;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.SubscriptionRepository;
import com.vlink.backend.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final SubscriptionRepository subscriptionRepo;

    // GET /subscriptions — eventos subscritos pelo utilizador autenticado
    @GetMapping
    public ResponseEntity<List<Event>> mySubscriptions(Authentication auth) {
        List<Event> events = subscriptionRepo.findByUserEmail(auth.getName())
            .stream()
            .map(Subscription::getEvent)
            .toList();
        return ResponseEntity.ok(events);
    }

    // GET /subscriptions/{eventId} — verifica se está subscrito
    @GetMapping("/{eventId}")
    public ResponseEntity<Map<String, Boolean>> isSubscribed(
            @PathVariable Long eventId, Authentication auth) {
        boolean sub = subscriptionRepo.existsByUserEmailAndEventId(auth.getName(), eventId);
        return ResponseEntity.ok(Map.of("subscribed", sub));
    }

    // POST /subscriptions/{eventId} — subscrever
    @PostMapping("/{eventId}")
    public ResponseEntity<?> subscribe(@PathVariable Long eventId, Authentication auth) {
        if (subscriptionRepo.existsByUserEmailAndEventId(auth.getName(), eventId))
            return ResponseEntity.ok(Map.of("subscribed", true));

        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null) return ResponseEntity.notFound().build();

        if (subscriptionRepo.countByEventId(eventId) >= event.getCapacity())
            return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("error", "Este evento já não tem vagas disponíveis."));

        User user = userRepo.findByEmail(auth.getName()).orElseThrow();

        Subscription sub = new Subscription();
        sub.setUser(user);
        sub.setEvent(event);
        subscriptionRepo.save(sub);

        return ResponseEntity.ok(Map.of("subscribed", true));
    }

    // DELETE /subscriptions/{eventId} — cancelar subscrição
    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> unsubscribe(@PathVariable Long eventId, Authentication auth) {
        subscriptionRepo.deleteByUserEmailAndEventId(auth.getName(), eventId);
        return ResponseEntity.ok(Map.of("subscribed", false));
    }
}
