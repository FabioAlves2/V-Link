package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SubscriptionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String register(String prefix, String role) throws Exception {
        String email = prefix + "-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Test\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"%s\"}"
            .formatted(email, role);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private long createEvent(String promoterToken, int capacity) throws Exception {
        String start = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = "{\"title\":\"Sub Test Event\",\"location\":\"Porto\",\"capacity\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(capacity, start, end);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void subscribeThenUnsubscribeTogglesStatus() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, 5);

        mockMvc.perform(get("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(false));

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(true));

        mockMvc.perform(get("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(true));

        mockMvc.perform(delete("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(false));

        mockMvc.perform(get("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(false));
    }

    @Test
    void subscribingTwiceIsIdempotent() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, 5);

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(true));
    }

    @Test
    void capacityIsEnforced() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken, 1);

        String firstVolunteer = register("sub-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + firstVolunteer))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(true));

        String secondVolunteer = register("sub-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + secondVolunteer))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void mySubscriptionsListsOnlyTheCallersEvents() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, 5);

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/subscriptions").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists());

        String otherVolunteerToken = register("sub-volunteer-other", "VOLUNTEER");
        mockMvc.perform(get("/subscriptions").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").doesNotExist());
    }
}
