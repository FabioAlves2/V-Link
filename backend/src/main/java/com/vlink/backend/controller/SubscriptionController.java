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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
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
        events.forEach(e -> e.setSubscriberCount((int) subscriptionRepo.countByEventId(e.getId())));
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
    // @Transactional + lock pessimista no evento: sem isto, dois pedidos concorrentes podiam
    // ambos passar a verificação de capacidade antes de qualquer um gravar, ultrapassando o limite.
    @Transactional
    @PostMapping("/{eventId}")
    public ResponseEntity<?> subscribe(@PathVariable Long eventId, Authentication auth) {
        if (subscriptionRepo.existsByUserEmailAndEventId(auth.getName(), eventId))
            return ResponseEntity.ok(Map.of("subscribed", true));

        Event event = eventRepo.findByIdForUpdate(eventId).orElse(null);
        if (event == null) return ResponseEntity.notFound().build();

        if (event.getStatus() != Event.Status.PUBLISHED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Este evento não está disponível para inscrições."));
        }
        // PUBLISHED não implica "ainda a decorrer" — um evento cujo endDate já passou continua
        // PUBLISHED até o organizador o "Encerrar" manualmente (ver EventRepository.findByFilters).
        if (event.getEndDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of("error", "Este evento já terminou e não aceita mais inscrições."));
        }

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
    // Bloqueado para eventos CLOSED *ou já terminados*: a inscrição (e o eventual
    // checkedIn/checkedInAt) é o registo histórico de participação e tem de sobreviver.
    // Sem o segundo check, um evento PUBLISHED cujo endDate já passou mas que o organizador
    // ainda não "Encerrou" manualmente ficava sem esta proteção — check-in não tem guard de
    // data (EventSubscriberController), por isso este é o mesmo buraco do caso CLOSED, só que
    // para eventos que já aconteceram mas nunca foram formalmente fechados.
    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> unsubscribe(@PathVariable Long eventId, Authentication auth) {
        Event event = eventRepo.findById(eventId).orElse(null);
        if (event != null && (event.getStatus() == Event.Status.CLOSED || event.getEndDate().isBefore(LocalDateTime.now()))) {
            return ResponseEntity.badRequest().body(Map.of("error",
                "Não é possível cancelar a inscrição de um evento já encerrado ou terminado."));
        }
        subscriptionRepo.deleteByUserEmailAndEventId(auth.getName(), eventId);
        return ResponseEntity.ok(Map.of("subscribed", false));
    }
}
