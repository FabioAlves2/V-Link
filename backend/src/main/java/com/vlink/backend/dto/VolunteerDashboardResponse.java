package com.vlink.backend.dto;

import com.vlink.backend.model.Event;

import java.util.List;

public record VolunteerDashboardResponse(
    List<Event> upcomingEvents,
    List<PastEventEntry> pastEvents,
    double totalHours
) {
    public record PastEventEntry(Event event, boolean checkedIn) {}
}
