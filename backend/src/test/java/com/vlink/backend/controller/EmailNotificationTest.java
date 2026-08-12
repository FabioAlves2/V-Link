package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import com.vlink.backend.service.EmailService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EmailNotificationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean EmailService emailService;

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

    private String futureDate(int hoursFromNow) {
        return LocalDateTime.now().plusHours(hoursFromNow).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private long createEvent(String promoterToken, String status) throws Exception {
        String title = "Email Test Event " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\"}"
            .formatted(title, futureDate(24), futureDate(26), status);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private long createAlreadyStartedEvent(String promoterToken, String status) throws Exception {
        long id = createEvent(promoterToken, status);
        String moveToPastBody = "{\"title\":\"Já a decorrer\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-2), futureDate(2), status);
        mockMvc.perform(put("/events/" + id).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(moveToPastBody))
            .andExpect(status().isOk());
        return id;
    }

    @Test
    void subscribingToAPublishedEventSendsASignupConfirmationEmail() throws Exception {
        String promoterToken = register("email-promoter", "PROMOTER");
        String volunteerToken = register("email-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        verify(emailService, times(1)).sendSignupConfirmationEmail(any(User.class), any(Event.class));
    }

    @Test
    void resubscribingToAnAlreadySubscribedEventDoesNotSendASecondConfirmationEmail() throws Exception {
        String promoterToken = register("email-promoter", "PROMOTER");
        String volunteerToken = register("email-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        verify(emailService, times(1)).sendSignupConfirmationEmail(any(User.class), any(Event.class));
    }

    @Test
    void closingAPublishedEventWithSubscribersSendsAClosureEmailToEachSubscriber() throws Exception {
        String promoterToken = register("email-promoter", "PROMOTER");
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");
        String volunteerToken = register("email-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String closeBody = "{\"title\":\"Closing\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        verify(emailService, times(1)).sendEventClosureEmail(any(User.class), any(Event.class));
    }

    @Test
    void closingAnEventWithNoSubscribersSendsNoEmail() throws Exception {
        String promoterToken = register("email-promoter", "PROMOTER");
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        String closeBody = "{\"title\":\"Closing\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        verify(emailService, never()).sendEventClosureEmail(any(User.class), any(Event.class));
    }

    @Test
    void cancellingAnUpcomingPublishedEventWithSubscribersSendsACancellationEmailToEachSubscriber() throws Exception {
        String promoterToken = register("email-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken, "PUBLISHED"); // ainda não começou
        String volunteerToken = register("email-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        verify(emailService, times(1)).sendEventCancellationEmail(any(User.class), any(Event.class));
    }
}
