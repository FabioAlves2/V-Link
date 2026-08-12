package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlink.backend.repo.FavoriteRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class FavoriteControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired FavoriteRepository favoriteRepo;

    private String register(String prefix, String role) throws Exception {
        return registerWithEmail(prefix + "-" + UUID.randomUUID() + "@example.com", role);
    }

    private String registerWithEmail(String email, String role) throws Exception {
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
        String title = "Fav Event " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\"}"
            .formatted(title, futureDate(24), futureDate(26), status);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void favoriteThenUnfavoriteTogglesStatus() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(get("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(true));

        mockMvc.perform(get("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(true));

        mockMvc.perform(delete("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(false));

        mockMvc.perform(get("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void favoritingTwiceIsIdempotent() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerEmail = "fav-volunteer-" + UUID.randomUUID() + "@example.com";
        String volunteerToken = registerWithEmail(volunteerEmail, "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(true));

        assertEquals(1, favoriteRepo.findByUserEmail(volunteerEmail).size(),
            "a second POST must not create a duplicate favorite row");
    }

    @Test
    void favoritingADraftEventIsAllowed() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "DRAFT");

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void favoritingAnAlreadyEndedOrClosedEventIsAllowed() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        String pastBody = "{\"title\":\"Já terminado\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(pastBody))
            .andExpect(status().isOk());

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(true));
    }

    @Test
    void favoritingANonexistentEventReturns404() throws Exception {
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");

        mockMvc.perform(post("/favorites/999999").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isNotFound());
    }

    @Test
    void unfavoritingAnEventNeverFavoritedStillReturns200() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(delete("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void myFavoritesListsOnlyTheCallersEvents() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerToken = register("fav-volunteer", "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/favorites").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists());

        String otherVolunteerToken = register("fav-volunteer-other", "VOLUNTEER");
        mockMvc.perform(get("/favorites").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").doesNotExist());
    }

    // Regressão crítica: sem favoriteRepo.deleteByEventId(id) em EventController.delete(), isto
    // rebentava com uma FK violation (DataIntegrityViolationException) em vez de eliminar o evento.
    @Test
    void deletingAnEventWithFavoritesButNoSubscribersSucceeds() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerEmail = "fav-volunteer-" + UUID.randomUUID() + "@example.com";
        String volunteerToken = registerWithEmail(volunteerEmail, "VOLUNTEER");
        long eventId = createEvent(promoterToken, "DRAFT");

        mockMvc.perform(post("/favorites/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isNotFound());

        assertEquals(0, favoriteRepo.findByUserEmail(volunteerEmail).size());
    }

    // A verificação exists() e a inserção não são atómicas — dois pedidos verdadeiramente
    // concorrentes (ex.: dois separadores) podiam ambos passar o exists() antes de qualquer um
    // gravar, e o perdedor da corrida ao constraint único recebia um 409 mesmo o favorito já
    // estando garantido. Sem lock (não há capacidade a proteger), por isso o controller trata
    // esse conflito como sucesso idempotente — este teste garante que nenhum pedido concorrente
    // vê um erro e que só fica uma linha na tabela.
    @Test
    void concurrentFavoriteRequestsAllSucceedAndLeaveOnlyOneRow() throws Exception {
        String promoterToken = register("fav-promoter", "PROMOTER");
        String volunteerEmail = "fav-race-volunteer-" + UUID.randomUUID() + "@example.com";
        String volunteerToken = registerWithEmail(volunteerEmail, "VOLUNTEER");
        long eventId = createEvent(promoterToken, "PUBLISHED");

        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger unexpectedStatusCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    var result = mockMvc.perform(post("/favorites/" + eventId)
                            .header("Authorization", "Bearer " + volunteerToken))
                        .andReturn();
                    if (result.getResponse().getStatus() != 200) unexpectedStatusCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(0, unexpectedStatusCount.get(), "every concurrent favorite request must succeed with 200");
        assertEquals(1, favoriteRepo.findByUserEmail(volunteerEmail).size(),
            "concurrent favorite requests for the same user+event must never create more than one row");
    }
}
