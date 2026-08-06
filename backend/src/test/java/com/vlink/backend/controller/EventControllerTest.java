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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    private String registerPromoter() throws Exception {
        String email = "promoter-" + UUID.randomUUID() + "@example.com";
        String body = "{\"name\":\"Promoter\",\"email\":\"%s\",\"password\":\"password123\",\"role\":\"PROMOTER\"}"
            .formatted(email);
        String response = mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String futureDate(int hoursFromNow) {
        return LocalDateTime.now().plusHours(hoursFromNow).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    @Test
    void createWithoutStatusDefaultsToDraftAndIsHiddenFromPublicList() throws Exception {
        String token = registerPromoter();
        String title = "Draft " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
            .formatted(title, futureDate(24), futureDate(26));

        mockMvc.perform(post("/events").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("DRAFT"));

        mockMvc.perform(get("/events"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(title))));
    }

    @Test
    void createWithClosedStatusIsRejected() throws Exception {
        String token = registerPromoter();
        String body = "{\"title\":\"Closed Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\"}"
            .formatted(futureDate(24), futureDate(26));

        mockMvc.perform(post("/events").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest());
    }

    @Test
    void createWithPastStartDateIsRejected() throws Exception {
        String token = registerPromoter();
        String body = "{\"title\":\"Past Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
            .formatted(futureDate(-24), futureDate(-22));

        mockMvc.perform(post("/events").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.startDate").exists());
    }

    @Test
    void onlyOwningPromoterCanUpdateEvent() throws Exception {
        String ownerToken = registerPromoter();
        String otherToken = registerPromoter();

        String createBody = "{\"title\":\"Owned Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(futureDate(24), futureDate(26));
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + ownerToken)
                .contentType(MediaType.APPLICATION_JSON).content(createBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(created).get("id").asLong();

        String updateBody = "{\"title\":\"Hijacked\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(futureDate(24), futureDate(26));

        mockMvc.perform(put("/events/" + id).header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isForbidden());
    }
}
