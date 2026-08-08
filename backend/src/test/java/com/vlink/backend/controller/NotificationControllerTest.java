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
class NotificationControllerTest {

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

    private long createAndCloseEventWithSubscriber(String promoterToken, String volunteerToken) throws Exception {
        String start = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String title = "Notif Test " + UUID.randomUUID().toString().substring(0, 8);
        String createBody = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(title, start, end);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long eventId = objectMapper.readTree(created).get("id").asLong();

        // "Encerrar" só é permitido depois do evento já ter começado — move as datas para o
        // passado (mantendo PUBLISHED) antes de o fechar, simulando que já está a decorrer.
        // endDate fica no FUTURO de propósito ("a decorrer", não "já terminado") — subscrever
        // com endDate já passado é rejeitado (ver SubscriptionController). Feito ANTES de
        // inscrever o voluntário: de outro modo esta mudança de datas conta como um
        // reagendamento de um evento publicado com inscritos e gera uma notificação extra
        // (ver EventController.update) — o voluntário nunca deve "ver" o evento com as datas
        // futuras originais.
        String pastStart = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String futureEnd = LocalDateTime.now().plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String moveToPastBody = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(title, pastStart, futureEnd);
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(moveToPastBody))
            .andExpect(status().isOk());

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String closeBody = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(title, pastStart, futureEnd);
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        return eventId;
    }

    @Test
    void notificationsAreScopedToRecipient() throws Exception {
        String promoterToken = register("notif-promoter", "PROMOTER");
        String volunteerToken = register("notif-volunteer", "VOLUNTEER");
        createAndCloseEventWithSubscriber(promoterToken, volunteerToken);

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));

        String otherVolunteerToken = register("notif-other-volunteer", "VOLUNTEER");
        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void markingNotificationReadUpdatesFlagAndCount() throws Exception {
        String promoterToken = register("notif-promoter", "PROMOTER");
        String volunteerToken = register("notif-volunteer", "VOLUNTEER");
        createAndCloseEventWithSubscriber(promoterToken, volunteerToken);

        mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(1));

        String list = mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long notificationId = objectMapper.readTree(list).get(0).get("id").asLong();

        mockMvc.perform(put("/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.read").value(true));

        mockMvc.perform(get("/notifications/unread-count").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void cannotMarkAnotherUsersNotificationRead() throws Exception {
        String promoterToken = register("notif-promoter", "PROMOTER");
        String volunteerToken = register("notif-volunteer", "VOLUNTEER");
        createAndCloseEventWithSubscriber(promoterToken, volunteerToken);

        String list = mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        long notificationId = objectMapper.readTree(list).get(0).get("id").asLong();

        String otherVolunteerToken = register("notif-other-volunteer", "VOLUNTEER");
        mockMvc.perform(put("/notifications/" + notificationId + "/read").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isNotFound());
    }
}
