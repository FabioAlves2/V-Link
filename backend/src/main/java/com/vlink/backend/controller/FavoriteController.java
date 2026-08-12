package com.vlink.backend.controller;

import com.vlink.backend.dto.FavoriteResponse;
import com.vlink.backend.model.Event;
import com.vlink.backend.model.Favorite;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.FavoriteRepository;
import com.vlink.backend.repo.SubscriptionRepository;
import com.vlink.backend.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/favorites")
@RequiredArgsConstructor
public class FavoriteController {

    private final EventRepository eventRepo;
    private final UserRepository userRepo;
    private final FavoriteRepository favoriteRepo;
    private final SubscriptionRepository subscriptionRepo;

    // GET /favorites — eventos favoritados pelo utilizador autenticado
    @GetMapping
    public ResponseEntity<List<Event>> myFavorites(Authentication auth) {
        List<Event> events = favoriteRepo.findByUserEmail(auth.getName())
            .stream()
            .map(Favorite::getEvent)
            .toList();
        events.forEach(e -> e.setSubscriberCount((int) subscriptionRepo.countByEventId(e.getId())));
        return ResponseEntity.ok(events);
    }

    // GET /favorites/{eventId} — verifica se está favoritado
    @GetMapping("/{eventId}")
    public ResponseEntity<FavoriteResponse> isFavorited(@PathVariable Long eventId, Authentication auth) {
        boolean fav = favoriteRepo.existsByUserEmailAndEventId(auth.getName(), eventId);
        return ResponseEntity.ok(FavoriteResponse.of(fav));
    }

    // POST /favorites/{eventId} — favoritar (idempotente; sem restrição de estado/data/capacidade,
    // é só um bookmark, não um compromisso)
    @PostMapping("/{eventId}")
    public ResponseEntity<?> favorite(@PathVariable Long eventId, Authentication auth) {
        if (favoriteRepo.existsByUserEmailAndEventId(auth.getName(), eventId))
            return ResponseEntity.ok(FavoriteResponse.of(true));

        Event event = eventRepo.findById(eventId).orElse(null);
        if (event == null) return ResponseEntity.notFound().build();

        User user = userRepo.findByEmail(auth.getName()).orElseThrow();

        Favorite fav = new Favorite();
        fav.setUser(user);
        fav.setEvent(event);
        fav.setCreatedAt(LocalDateTime.now());
        try {
            favoriteRepo.save(fav);
        } catch (DataIntegrityViolationException ex) {
            // Duas chamadas verdadeiramente concorrentes (ex.: dois separadores/dispositivos) podem
            // ambas passar o exists() acima antes de qualquer uma gravar — a que perde a corrida ao
            // constraint único cai aqui. O favorito já existe (foi a outra chamada que o criou), por
            // isso continua a ser um sucesso idempotente, não um erro para quem fez este pedido.
        }

        return ResponseEntity.ok(FavoriteResponse.of(true));
    }

    // DELETE /favorites/{eventId} — desfavoritar (idempotente)
    @DeleteMapping("/{eventId}")
    public ResponseEntity<?> unfavorite(@PathVariable Long eventId, Authentication auth) {
        favoriteRepo.deleteByUserEmailAndEventId(auth.getName(), eventId);
        return ResponseEntity.ok(FavoriteResponse.of(false));
    }
}
