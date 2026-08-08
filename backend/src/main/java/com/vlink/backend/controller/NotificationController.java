package com.vlink.backend.controller;

import com.vlink.backend.dto.NotificationResponse;
import com.vlink.backend.model.Notification;
import com.vlink.backend.repo.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repo;

    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication auth) {
        return repo.findByRecipientEmailOrderByCreatedAtDesc(auth.getName())
            .stream().map(NotificationResponse::from).toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(Authentication auth) {
        return Map.of("count", repo.countByRecipientEmailAndReadFalse(auth.getName()));
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication auth) {
        return repo.findByIdAndRecipientEmail(id, auth.getName()).map(n -> {
            n.setRead(true);
            return ResponseEntity.ok(NotificationResponse.from(repo.save(n)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead(Authentication auth) {
        repo.markAllReadForUser(auth.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
