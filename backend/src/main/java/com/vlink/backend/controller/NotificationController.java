package com.vlink.backend.controller;

import com.vlink.backend.dto.NotificationResponse;
import com.vlink.backend.model.Notification;
import com.vlink.backend.repo.NotificationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Notificações", description = "Notificações in-app do utilizador autenticado (fecho/cancelamento/reagendamento de eventos).")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationRepository repo;

    @Operation(summary = "Lista as notificações do utilizador autenticado, mais recentes primeiro.")
    @GetMapping
    public List<NotificationResponse> getNotifications(Authentication auth) {
        return repo.findByRecipientEmailOrderByCreatedAtDesc(auth.getName())
            .stream().map(NotificationResponse::from).toList();
    }

    @Operation(summary = "Devolve o número de notificações por ler (usado pelo badge da Navbar).")
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(Authentication auth) {
        return Map.of("count", repo.countByRecipientEmailAndReadFalse(auth.getName()));
    }

    @Operation(summary = "Marca uma notificação como lida (404 se não pertencer ao utilizador autenticado).")
    @PutMapping("/{id}/read")
    public ResponseEntity<?> markRead(@PathVariable Long id, Authentication auth) {
        return repo.findByIdAndRecipientEmail(id, auth.getName()).map(n -> {
            n.setRead(true);
            return ResponseEntity.ok(NotificationResponse.from(repo.save(n)));
        }).orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Marca todas as notificações do utilizador autenticado como lidas.")
    @PutMapping("/read-all")
    public ResponseEntity<?> markAllRead(Authentication auth) {
        repo.markAllReadForUser(auth.getName());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}
