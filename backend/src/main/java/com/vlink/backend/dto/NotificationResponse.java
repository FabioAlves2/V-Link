package com.vlink.backend.dto;

import com.vlink.backend.model.Notification;

import java.time.LocalDateTime;

public record NotificationResponse(
    Long id,
    String message,
    boolean read,
    LocalDateTime createdAt,
    Long eventId
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
            n.getId(),
            n.getMessage(),
            n.isRead(),
            n.getCreatedAt(),
            n.getEvent() != null ? n.getEvent().getId() : null
        );
    }
}
