package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlink.backend.repo.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
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
class SubscriptionControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired SubscriptionRepository subscriptionRepo;

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
        return createEventWithDates(promoterToken, capacity, start, end, "PUBLISHED");
    }

    private long createEventWithDates(String promoterToken, String start, String end, String status) throws Exception {
        return createEventWithDates(promoterToken, 5, start, end, status);
    }

    private long createEventWithDates(String promoterToken, int capacity, String start, String end, String status) throws Exception {
        String body = "{\"title\":\"Sub Test Event\",\"location\":\"Porto\",\"capacity\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\",\"type\":\"OUTRO\"}"
            .formatted(capacity, start, end, status);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void updateEvent(String promoterToken, long id, String start, String end, String status) throws Exception {
        updateEvent(promoterToken, id, 5, start, end, status);
    }

    private void updateEvent(String promoterToken, long id, int capacity, String start, String end, String status) throws Exception {
        String body = "{\"title\":\"Sub Test Event\",\"location\":\"Porto\",\"capacity\":%d,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\",\"type\":\"OUTRO\"}"
            .formatted(capacity, start, end, status);
        mockMvc.perform(put("/events/" + id).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
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
    void concurrentSubscribeRequestsNeverExceedCapacity() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken, 1);

        int volunteerCount = 8;
        List<String> tokens = new java.util.ArrayList<>();
        for (int i = 0; i < volunteerCount; i++) {
            tokens.add(register("sub-race-volunteer", "VOLUNTEER"));
        }

        ExecutorService pool = Executors.newFixedThreadPool(volunteerCount);
        CountDownLatch ready = new CountDownLatch(volunteerCount);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();

        for (String token : tokens) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    var result = mockMvc.perform(post("/subscriptions/" + eventId)
                            .header("Authorization", "Bearer " + token))
                        .andReturn();
                    if (result.getResponse().getStatus() == 200) successCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        assertEquals(1, successCount.get(), "exactly one concurrent subscribe should succeed for capacity=1");
        assertEquals(1, subscriptionRepo.countByEventId(eventId), "DB row count must never exceed capacity");
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
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists())
            .andExpect(jsonPath("$[0].subscriberCount").value(1));

        String otherVolunteerToken = register("sub-volunteer-other", "VOLUNTEER");
        mockMvc.perform(get("/subscriptions").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").doesNotExist());
    }

    @Test
    void subscribingToNonPublishedEventIsRejected() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String start = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String draftBody = "{\"title\":\"Draft Sub Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"DRAFT\"}"
            .formatted(start, end);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(draftBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long draftEventId = objectMapper.readTree(created).get("id").asLong();

        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + draftEventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void subscribingToClosedEventIsRejected() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past1 = LocalDateTime.now().minusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past2 = LocalDateTime.now().minusHours(22).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");
        updateEvent(promoterToken, eventId, past1, past2, "PUBLISHED"); // simula "já começou"
        updateEvent(promoterToken, eventId, past1, past2, "CLOSED"); // encerra

        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void unsubscribingFromClosedEventIsRejectedAndPreservesTheRecord() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        // endDate ainda no futuro de propósito — encerrar não depende do endDate já ter passado,
        // só do startDate, e é exatamente esse cenário que fazia o botão "Cancelar" continuar
        // visível em MySubscriptions.jsx (getStatus() ali só olhava para as datas).
        String futureEnd = LocalDateTime.now().plusHours(48).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past1 = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, futureEnd, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        updateEvent(promoterToken, eventId, past1, futureEnd, "PUBLISHED"); // simula "já começou"
        updateEvent(promoterToken, eventId, past1, futureEnd, "CLOSED"); // encerra antes do fim

        mockMvc.perform(delete("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        assertEquals(1, subscriptionRepo.countByEventId(eventId),
            "a inscrição (registo histórico de participação) não pode ser apagada de um evento encerrado");
    }

    @Test
    void unsubscribingFromAnAlreadyStartedButStillPublishedEventIsStillAllowed() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past1 = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        updateEvent(promoterToken, eventId, past1, future2, "PUBLISHED"); // já começou, mas NÃO encerrado

        mockMvc.perform(delete("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.subscribed").value(false));

        assertEquals(0, subscriptionRepo.countByEventId(eventId));
    }

    // PUBLISHED não implica "ainda a decorrer": o organizador pode nunca chegar a "Encerrar"
    // manualmente um evento cujo endDate já passou. subscribe() tem de olhar para a data, não só
    // para o status.
    @Test
    void subscribingToAnAlreadyEndedEventIsRejected() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past1 = LocalDateTime.now().minusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past2 = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");
        updateEvent(promoterToken, eventId, past1, past2, "PUBLISHED"); // já terminou, nunca foi encerrado

        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    // Mesma ideia do teste "ClosedEvent" acima, mas para um evento que já terminou (endDate no
    // passado) sem nunca ter sido formalmente "Encerrado" — o check-in não tem guard de data
    // (EventSubscriberController), por isso este é o mesmo risco de perder checkedIn/checkedInAt.
    @Test
    void unsubscribingFromAnAlreadyEndedButNeverClosedEventIsRejectedAndPreservesTheRecord() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String past1 = LocalDateTime.now().minusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past2 = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        updateEvent(promoterToken, eventId, past1, past2, "PUBLISHED"); // terminou, continua PUBLISHED

        mockMvc.perform(delete("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        assertEquals(1, subscriptionRepo.countByEventId(eventId),
            "a inscrição não pode ser apagada de um evento já terminado, mesmo que nunca tenha sido formalmente encerrado");
    }

    private long subscribeAndGetUserId(String promoterToken, String volunteerToken, long eventId) throws Exception {
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        String subscribers = mockMvc.perform(get("/events/" + eventId + "/subscribers").header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(subscribers).get(0).get("userId").asLong();
    }

    private void setAttendance(String promoterToken, long eventId, long userId, boolean checkedIn) throws Exception {
        mockMvc.perform(put("/events/" + eventId + "/subscribers/" + userId + "/attendance")
                .header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content("{\"checkedIn\":" + checkedIn + "}"))
            .andExpect(status().isOk());
    }

    @Test
    void summaryEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/subscriptions/summary"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void summarySplitsUpcomingAndPastCorrectly() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");

        long upcomingEventId = createEvent(promoterToken, 5); // start/end no futuro (helper padrão)

        String past1 = LocalDateTime.now().minusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String past2 = LocalDateTime.now().minusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long pastEventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + upcomingEventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/subscriptions/" + pastEventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        updateEvent(promoterToken, pastEventId, past1, past2, "PUBLISHED"); // move para o passado

        mockMvc.perform(get("/subscriptions/summary").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upcomingEvents.length()").value(1))
            .andExpect(jsonPath("$.upcomingEvents[0].id").value(upcomingEventId))
            .andExpect(jsonPath("$.pastEvents.length()").value(1))
            .andExpect(jsonPath("$.pastEvents[0].event.id").value(pastEventId))
            .andExpect(jsonPath("$.pastEvents[0].checkedIn").value(false));
    }

    @Test
    void summaryComputesTotalHoursOnlyFromCheckedInSubscriptions() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        // create() rejeita startDate no passado — cria sempre no futuro e move para o passado
        // via update(), tal como o resto desta classe (ver createAlreadyStartedEvent em EventControllerTest).
        long checkedInEventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");
        long notCheckedInEventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");

        long userIdChecked = subscribeAndGetUserId(promoterToken, volunteerToken, checkedInEventId);
        long userIdNotChecked = subscribeAndGetUserId(promoterToken, volunteerToken, notCheckedInEventId);

        String checkedStart = LocalDateTime.now().minusHours(6).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String checkedEnd = LocalDateTime.now().minusHours(4).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); // 2h
        updateEvent(promoterToken, checkedInEventId, checkedStart, checkedEnd, "PUBLISHED");
        setAttendance(promoterToken, checkedInEventId, userIdChecked, true);

        String notCheckedStart = LocalDateTime.now().minusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String notCheckedEnd = LocalDateTime.now().minusHours(1).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); // 2h, sem check-in
        updateEvent(promoterToken, notCheckedInEventId, notCheckedStart, notCheckedEnd, "PUBLISHED");

        mockMvc.perform(get("/subscriptions/summary").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.pastEvents.length()").value(2))
            .andExpect(jsonPath("$.totalHours").value(2.0));
    }

    @Test
    void summaryHoursComputationHandlesFractionalHours() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String future1 = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String future2 = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long eventId = createEventWithDates(promoterToken, future1, future2, "PUBLISHED");

        long userId = subscribeAndGetUserId(promoterToken, volunteerToken, eventId);

        String start = LocalDateTime.now().minusHours(3).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().minusHours(3).plusMinutes(90).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME); // 90 min
        updateEvent(promoterToken, eventId, start, end, "PUBLISHED");
        setAttendance(promoterToken, eventId, userId, true);

        mockMvc.perform(get("/subscriptions/summary").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalHours").value(1.5));
    }

    @Test
    void summaryReturnsEmptyListsAndZeroHoursForAVolunteerWithNoSubscriptions() throws Exception {
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");

        mockMvc.perform(get("/subscriptions/summary").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upcomingEvents.length()").value(0))
            .andExpect(jsonPath("$.pastEvents.length()").value(0))
            .andExpect(jsonPath("$.totalHours").value(0.0));
    }

    @Test
    void summaryOnlyIncludesTheCallersOwnSubscriptions() throws Exception {
        String promoterToken = register("sub-promoter", "PROMOTER");
        String volunteerToken = register("sub-volunteer", "VOLUNTEER");
        String otherVolunteerToken = register("sub-volunteer-other", "VOLUNTEER");
        long eventId = createEvent(promoterToken, 5);

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/subscriptions/summary").header("Authorization", "Bearer " + otherVolunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upcomingEvents.length()").value(0));
    }
}
