package com.vlink.backend.dto;

import com.vlink.backend.model.Subscription;

import java.time.LocalDateTime;

public record SubscriberResponse(
    Long userId,
    String name,
    String email,
    boolean checkedIn,
    LocalDateTime checkedInAt
) {
    public static SubscriberResponse from(Subscription s) {
        return new SubscriberResponse(
            s.getUser().getId(),
            s.getUser().getName(),
            s.getUser().getEmail(),
            s.isCheckedIn(),
            s.getCheckedInAt()
        );
    }
}
