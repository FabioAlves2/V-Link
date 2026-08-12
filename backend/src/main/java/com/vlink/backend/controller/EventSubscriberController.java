package com.vlink.backend.controller;

import com.vlink.backend.dto.AttendanceRequest;
import com.vlink.backend.dto.SubscriberResponse;
import com.vlink.backend.model.Event;
import com.vlink.backend.model.Subscription;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.SubscriptionRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Inscritos do Evento", description = "Vista do promotor sobre quem se inscreveu num evento seu, e marcação de presença.")
@SecurityRequirement(name = "bearerAuth")
public class EventSubscriberController {

    private final EventRepository eventRepo;
    private final SubscriptionRepository subscriptionRepo;

    @Operation(summary = "Lista os inscritos de um evento (só o promotor que o criou).")
    @GetMapping("/{eventId}/subscribers")
    public ResponseEntity<?> getSubscribers(@PathVariable Long eventId, Authentication auth) {
        return eventRepo.findById(eventId).map(event -> {
            ResponseEntity<?> forbidden = checkOwnership(event, auth);
            if (forbidden != null) return forbidden;
            List<SubscriberResponse> subscribers = subscriptionRepo.findByEventId(eventId)
                .stream().map(SubscriberResponse::from).toList();
            return ResponseEntity.ok(subscribers);
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Marca/desmarca a presença de um inscrito (só o promotor que criou o evento).")
    @PutMapping("/{eventId}/subscribers/{userId}/attendance")
    public ResponseEntity<?> setAttendance(
        @PathVariable Long eventId, @PathVariable Long userId,
        @Valid @RequestBody AttendanceRequest request, Authentication auth
    ) {
        return eventRepo.findById(eventId).map(event -> {
            ResponseEntity<?> forbidden = checkOwnership(event, auth);
            if (forbidden != null) return forbidden;
            return subscriptionRepo.findByEventIdAndUserId(eventId, userId).<ResponseEntity<?>>map(sub -> {
                sub.setCheckedIn(request.checkedIn());
                sub.setCheckedInAt(request.checkedIn() ? LocalDateTime.now() : null);
                return ResponseEntity.ok(SubscriberResponse.from(subscriptionRepo.save(sub)));
            }).orElse(ResponseEntity.notFound().build());
        }).orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<?> checkOwnership(Event event, Authentication auth) {
        if (!event.getOrganizer().getEmail().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Só o promotor que criou este evento pode ver os seus inscritos."));
        }
        return null;
    }
}
