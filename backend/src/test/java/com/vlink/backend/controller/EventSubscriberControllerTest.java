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
class EventSubscriberControllerTest {

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

    private long createEvent(String promoterToken) throws Exception {
        String start = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = "{\"title\":\"Subscribers Test Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(start, end);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private long subscribeAndGetUserId(String promoterToken, String volunteerToken, long eventId) throws Exception {
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        String subscribers = mockMvc.perform(get("/events/" + eventId + "/subscribers").header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(subscribers).get(0).get("userId").asLong();
    }

    @Test
    void organizerCanListSubscribersOfOwnEvent() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String volunteerToken = register("subs-volunteer", "VOLUNTEER");
        subscribeAndGetUserId(promoterToken, volunteerToken, eventId);

        mockMvc.perform(get("/events/" + eventId + "/subscribers").header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].checkedIn").value(false));
    }

    @Test
    void nonOwnerCannotListSubscribers() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String otherPromoterToken = register("subs-other-promoter", "PROMOTER");

        mockMvc.perform(get("/events/" + eventId + "/subscribers").header("Authorization", "Bearer " + otherPromoterToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void organizerCanToggleAttendanceOnAndOff() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String volunteerToken = register("subs-volunteer", "VOLUNTEER");
        long userId = subscribeAndGetUserId(promoterToken, volunteerToken, eventId);

        mockMvc.perform(put("/events/" + eventId + "/subscribers/" + userId + "/attendance")
                .header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":true}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedIn").value(true))
            .andExpect(jsonPath("$.checkedInAt").exists());

        mockMvc.perform(put("/events/" + eventId + "/subscribers/" + userId + "/attendance")
                .header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":false}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.checkedIn").value(false))
            .andExpect(jsonPath("$.checkedInAt").doesNotExist());
    }

    @Test
    void nonOwnerCannotToggleAttendance() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String volunteerToken = register("subs-volunteer", "VOLUNTEER");
        long userId = subscribeAndGetUserId(promoterToken, volunteerToken, eventId);
        String otherPromoterToken = register("subs-other-promoter", "PROMOTER");

        mockMvc.perform(put("/events/" + eventId + "/subscribers/" + userId + "/attendance")
                .header("Authorization", "Bearer " + otherPromoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":true}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void attendanceRequestWithMissingCheckedInIsRejected() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String volunteerToken = register("subs-volunteer", "VOLUNTEER");
        long userId = subscribeAndGetUserId(promoterToken, volunteerToken, eventId);

        mockMvc.perform(put("/events/" + eventId + "/subscribers/" + userId + "/attendance")
                .header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.checkedIn").exists());
    }

    @Test
    void togglingAttendanceForNonSubscriberReturns404() throws Exception {
        String promoterToken = register("subs-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);

        mockMvc.perform(put("/events/" + eventId + "/subscribers/999999/attendance")
                .header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":true}"))
            .andExpect(status().isNotFound());
    }
}
