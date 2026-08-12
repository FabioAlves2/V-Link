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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
        return register("promoter", "PROMOTER");
    }

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

    private long createEvent(String promoterToken, String status) throws Exception {
        String title = "Event " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\"}"
            .formatted(title, futureDate(24), futureDate(26), status);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private String futureDate(int hoursFromNow) {
        return LocalDateTime.now().plusHours(hoursFromNow).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    // Cria um evento (datas futuras, exigidas por create()) e move-o de imediato para o passado
    // via um PUT normal — simula "este evento já começou" para testar o encerramento, já que
    // update() não rejeita datas passadas (só create() o faz). endDate fica no FUTURO de propósito
    // ("a decorrer", não "já terminado") — subscrever depois de chamar isto tem de continuar
    // permitido; só endDate no passado é que agora bloqueia subscribe/unsubscribe (ver
    // SubscriptionController). Um teste que precise de um evento também já terminado deve
    // mover endDate para o passado explicitamente, depois de qualquer subscrição necessária.
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

    // Um evento PUBLISHED cujo endDate já passou fica invisível na lista pública mesmo que o
    // organizador nunca o tenha "Encerrado" manualmente — status continua PUBLISHED, só a data
    // é que já passou (ver EventRepository.findByFilters).
    @Test
    void publishedEventPastItsEndDateIsExcludedFromPublicList() throws Exception {
        String promoterToken = registerPromoter();
        String title = "Já terminado " + UUID.randomUUID().toString().substring(0, 8);
        long eventId = createEvent(promoterToken, "PUBLISHED");

        String moveToPastBody = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(title, futureDate(-4), futureDate(-2));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(moveToPastBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString(title))));

        // Continua a existir e acessível diretamente (ex.: link partilhado, dashboard do organizador).
        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
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

        String updateBody = "{\"title\":\"Hijacked\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));

        mockMvc.perform(put("/events/" + id).header("Authorization", "Bearer " + otherToken)
                .contentType(MediaType.APPLICATION_JSON).content(updateBody))
            .andExpect(status().isForbidden());
    }

    @Test
    void myEventsListsOnlyOwnEventsRegardlessOfStatus() throws Exception {
        String ownerToken = registerPromoter();
        long draftId = createEvent(ownerToken, "DRAFT");
        long publishedId = createEvent(ownerToken, "PUBLISHED");

        String otherToken = registerPromoter();
        long otherEventId = createEvent(otherToken, "PUBLISHED");

        mockMvc.perform(get("/events/mine").header("Authorization", "Bearer " + ownerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + draftId + ")]").exists())
            .andExpect(jsonPath("$[?(@.id == " + publishedId + ")]").exists())
            .andExpect(jsonPath("$[?(@.id == " + otherEventId + ")]").doesNotExist());
    }

    @Test
    void myEventsRequiresPromoterRole() throws Exception {
        String volunteerToken = register("volunteer", "VOLUNTEER");

        mockMvc.perform(get("/events/mine").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void closingPublishedEventCreatesNotificationForEachSubscriber() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        String volunteerToken = register("volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String closeBody = "{\"title\":\"Closing\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].message").value(containsString("encerrado")))
            .andExpect(jsonPath("$[0].eventId").value(eventId));
    }

    @Test
    void publicEventResponseDoesNotExposeOrganizerEmail() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.organizer.email").doesNotExist())
            .andExpect(jsonPath("$.organizer.name").exists());
    }

    @Test
    void updatingOrClosingAnAlreadyStartedEventIsAllowed() throws Exception {
        String promoterToken = registerPromoter();
        // Já move o evento para o passado (mantendo-o PUBLISHED) — simula que já começou.
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        String closeBody = "{\"title\":\"Já decorreu\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));

        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    void updateWithMissingStatusOrTypeIsRejectedInsteadOfSilentlyDefaulting() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");

        // Body omite "status" e "type" de propósito — antes da correção, isto despublicava
        // silenciosamente o evento (status voltava ao default DRAFT do campo Java).
        String bodyMissingBoth = "{\"title\":\"Sem estado\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\"}"
            .formatted(futureDate(24), futureDate(26));

        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(bodyMissingBoth))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.status").exists())
            .andExpect(jsonPath("$.errors.type").exists());

        // Confirma que o evento continua PUBLISHED — o pedido inválido não teve qualquer efeito.
        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void concurrentCloseRequestsNeverNotifyTwice() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        String volunteerToken = register("volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String closeBody = "{\"title\":\"Closing\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));

        int attempts = 6;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger unexpectedStatusCount = new AtomicInteger();

        for (int i = 0; i < attempts; i++) {
            pool.submit(() -> {
                try {
                    ready.countDown();
                    start.await();
                    var result = mockMvc.perform(put("/events/" + eventId)
                            .header("Authorization", "Bearer " + promoterToken)
                            .contentType(MediaType.APPLICATION_JSON).content(closeBody))
                        .andReturn();
                    int status = result.getResponse().getStatus();
                    if (status == 200) successCount.incrementAndGet();
                    // Um "perdedor" do lock otimista deve receber 409 (ver ApiExceptionHandler),
                    // nunca um 500 — qualquer outro código indica uma falha não tratada.
                    else if (status != 409) unexpectedStatusCount.incrementAndGet();
                } catch (Exception ignored) {
                }
            });
        }

        ready.await();
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        // Não fixamos successCount em exatamente 1: dependendo do timing, um pedido que chegue
        // depois do evento já estar CLOSED também recebe 200 (é um no-op idempotente — o guard
        // "closingPublishedEvent" só é verdadeiro na transição real PUBLISHED->CLOSED). O que a
        // correção garante é o invariante abaixo: nunca mais do que uma notificação por subscritor.
        assertEquals(0, unexpectedStatusCount.get(), "every losing request must fail cleanly with 409, never 500");

        String notifications = mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        List<?> list = objectMapper.readValue(notifications, List.class);
        assertEquals(1, list.size(), "closing must notify each subscriber exactly once, even under concurrent close attempts");
    }

    @Test
    void closingDraftEventDoesNotNotifyAnyone() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "DRAFT");

        String closeBody = "{\"title\":\"Closing\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void closingAnEventThatHasNotStartedIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED"); // datas futuras — ainda não começou

        String closeBody = "{\"title\":\"Ainda não começou\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));

        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    @Test
    void deletingDraftEventSucceeds() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "DRAFT");

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancellingUpcomingPublishedEventDeletesItAndNotifiesSubscribers() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED"); // datas futuras — ainda não começou

        String volunteerToken = register("volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isNotFound());

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].message").value(containsString("cancelado")))
            .andExpect(jsonPath("$[0].eventId").value(nullValue()));
    }

    @Test
    void deletingAlreadyStartedPublishedEventIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk());
    }

    @Test
    void deletingClosedEventIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");

        String closeBody = "{\"title\":\"Fechado\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isBadRequest());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk());
    }

    @Test
    void nonOwnerCannotDeleteEvent() throws Exception {
        String ownerToken = registerPromoter();
        long eventId = createEvent(ownerToken, "DRAFT");
        String otherToken = registerPromoter();

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + otherToken))
            .andExpect(status().isForbidden());
    }

    private void closeEvent(String promoterToken, long eventId) throws Exception {
        String closeBody = "{\"title\":\"Fechado\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"CLOSED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(closeBody))
            .andExpect(status().isOk());
    }

    // Sem isto, um PUBLISHED com inscritos podia ser "despublicado" (PUT status:DRAFT) e depois
    // eliminado via DELETE, que permite DRAFT sem olhar a datas/inscritos — contornando por
    // completo as regras de encerrar/cancelar.
    @Test
    void unpublishingAPublishedEventWithSubscribersIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");
        String volunteerToken = register("unpublish-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String draftBody = "{\"title\":\"Voltar a rascunho\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"DRAFT\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(draftBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PUBLISHED"));
    }

    // Sem inscritos não há nada a proteger — despublicar equivale a "isto nunca chegou a ter
    // interesse real", o mesmo estado que teria se tivesse sido guardado como rascunho desde
    // sempre. Bloquear isto incondicionalmente (versão anterior desta regra) impedia despublicar
    // um evento recém-publicado por engano e sem ninguém inscrito, sem motivo de segurança real.
    @Test
    void unpublishingAPublishedEventWithNoSubscribersSucceeds() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");

        String draftBody = "{\"title\":\"Voltar a rascunho\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"DRAFT\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(draftBody))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void reopeningAClosedEventAsPublishedIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");
        closeEvent(promoterToken, eventId);

        String reopenBody = "{\"title\":\"Reabrir\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(reopenBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    // A mesma máquina de estados fecha também esta porta: sem o guard, reabrir como DRAFT em vez
    // de PUBLISHED contornava-o de igual forma e o evento ficava elegível para DELETE outra vez.
    @Test
    void reopeningAClosedEventAsDraftIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createAlreadyStartedEvent(promoterToken, "PUBLISHED");
        closeEvent(promoterToken, eventId);

        String reopenAsDraftBody = "{\"title\":\"Reabrir\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"DRAFT\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(-24), futureDate(-22));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(reopenAsDraftBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    void reducingCapacityBelowCurrentSubscriberCountIsRejected() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");

        for (int i = 0; i < 3; i++) {
            String volunteerToken = register("cap-guard-volunteer", "VOLUNTEER");
            mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
                .andExpect(status().isOk());
        }

        String tooSmallBody = "{\"title\":\"Menos vagas\",\"location\":\"Porto\",\"capacity\":2,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(tooSmallBody))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());

        String exactlyEnoughBody = "{\"title\":\"Vagas suficientes\",\"location\":\"Porto\",\"capacity\":3,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(24), futureDate(26));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(exactlyEnoughBody))
            .andExpect(status().isOk());
    }

    @Test
    void reschedulingAPublishedEventWithSubscribersNotifiesThem() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");
        String volunteerToken = register("reschedule-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        String rescheduleBody = "{\"title\":\"Novo horário\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(futureDate(48), futureDate(50));
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(rescheduleBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].message", containsString("reagendado")));
    }

    @Test
    void editingAPublishedEventWithoutChangingDatesDoesNotNotifySubscribers() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");
        String volunteerToken = register("no-reschedule-volunteer", "VOLUNTEER");
        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        // Reutiliza as datas exatamente como persistidas (não recalcula futureDate(...) de novo
        // aqui) — duas chamadas independentes a LocalDateTime.now() diferem por alguns
        // milissegundos, o que já chegava para o guard de reagendamento (comparação exata) ver
        // isso como uma mudança de datas e notificar por engano.
        String getResponse = mockMvc.perform(get("/events/" + eventId))
            .andReturn().getResponse().getContentAsString();
        String persistedStart = objectMapper.readTree(getResponse).get("startDate").asText();
        String persistedEnd = objectMapper.readTree(getResponse).get("endDate").asText();

        String sameDatesBody = "{\"title\":\"Só o título mudou\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(persistedStart, persistedEnd);
        mockMvc.perform(put("/events/" + eventId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(sameDatesBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/notifications").header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isEmpty());
    }

    private long createEventWithTitleAndDescription(String promoterToken, String title, String description,
            String location, String type, String status) throws Exception {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        String body = "{\"title\":\"%s\",\"description\":%s,\"location\":\"%s\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\",\"type\":\"%s\"}"
            .formatted(title, descriptionJson, location, futureDate(24), futureDate(26), status, type);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void keywordFilterMatchesTitle() throws Exception {
        String promoterToken = registerPromoter();
        String matchTitle = "Limpeza da Praia " + UUID.randomUUID().toString().substring(0, 8);
        String otherTitle = "Evento Qualquer " + UUID.randomUUID().toString().substring(0, 8);
        createEventWithTitleAndDescription(promoterToken, matchTitle, null, "Porto", "OUTRO", "PUBLISHED");
        createEventWithTitleAndDescription(promoterToken, otherTitle, null, "Porto", "OUTRO", "PUBLISHED");

        mockMvc.perform(get("/events").param("keyword", "praia"))
            .andExpect(status().isOk())
            .andExpect(content().string(containsString(matchTitle)))
            .andExpect(content().string(not(containsString(otherTitle))));
    }

    @Test
    void keywordFilterMatchesDescription() throws Exception {
        String promoterToken = registerPromoter();
        String title = "Evento " + UUID.randomUUID().toString().substring(0, 8);
        long eventId = createEventWithTitleAndDescription(promoterToken, title,
            "Uma tarde dedicada à reciclagem de resíduos.", "Porto", "OUTRO", "PUBLISHED");

        mockMvc.perform(get("/events").param("keyword", "reciclagem"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists());
    }

    @Test
    void keywordFilterIsCaseInsensitive() throws Exception {
        String promoterToken = registerPromoter();
        String title = "Limpeza da Praia " + UUID.randomUUID().toString().substring(0, 8);
        long eventId = createEventWithTitleAndDescription(promoterToken, title, null, "Porto", "OUTRO", "PUBLISHED");

        mockMvc.perform(get("/events").param("keyword", "PRAIA"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists());
    }

    @Test
    void keywordFilterCombinesWithLocationAndType() throws Exception {
        String promoterToken = registerPromoter();
        String matchTitle = "Praia Limpa " + UUID.randomUUID().toString().substring(0, 8);
        long matchId = createEventWithTitleAndDescription(promoterToken, matchTitle, null, "Lisboa", "AMBIENTE", "PUBLISHED");
        // mesma keyword no título, mas localização/tipo diferentes — não deve aparecer com os três filtros juntos
        String sameKeywordDifferentLocationTitle = "Praia Bonita " + UUID.randomUUID().toString().substring(0, 8);
        createEventWithTitleAndDescription(promoterToken, sameKeywordDifferentLocationTitle, null, "Porto", "SOCIAL", "PUBLISHED");

        mockMvc.perform(get("/events")
                .param("keyword", "praia").param("location", "Lisboa").param("type", "AMBIENTE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + matchId + ")]").exists())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void eventsWithNullDescriptionAreExcludedWhenKeywordDoesNotMatchTitle() throws Exception {
        String promoterToken = registerPromoter();
        String title = "Sem descrição " + UUID.randomUUID().toString().substring(0, 8);
        long eventId = createEventWithTitleAndDescription(promoterToken, title, null, "Porto", "OUTRO", "PUBLISHED");

        // Palavra que não está no título e não pode estar na descrição (é null) — a query não
        // deve rebentar com LOWER(NULL) LIKE ..., deve simplesmente excluir o evento.
        mockMvc.perform(get("/events").param("keyword", "inexistente"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").doesNotExist());

        mockMvc.perform(get("/events").param("keyword", "descrição"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + eventId + ")]").exists());
    }

    @Test
    void keywordFilterStillExcludesDraftAndAlreadyEndedEvents() throws Exception {
        String promoterToken = registerPromoter();
        String keyword = "praia" + UUID.randomUUID().toString().substring(0, 8);

        long draftId = createEventWithTitleAndDescription(promoterToken, "Draft " + keyword, null, "Porto", "OUTRO", "DRAFT");

        String endedTitle = "Terminado " + keyword;
        long endedId = createEventWithTitleAndDescription(promoterToken, endedTitle, null, "Porto", "OUTRO", "PUBLISHED");
        String moveToPastBody = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"OUTRO\"}"
            .formatted(endedTitle, futureDate(-4), futureDate(-2));
        mockMvc.perform(put("/events/" + endedId).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(moveToPastBody))
            .andExpect(status().isOk());

        mockMvc.perform(get("/events").param("keyword", keyword))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + draftId + ")]").doesNotExist())
            .andExpect(jsonPath("$[?(@.id == " + endedId + ")]").doesNotExist());
    }

    @Test
    void dateFilterMatchesEventsStartingOnThatExactDateAndExcludesOtherDays() throws Exception {
        String promoterToken = registerPromoter();
        LocalDateTime matchStart = LocalDateTime.now().plusDays(5).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime otherStart = matchStart.plusDays(3);
        String matchTitle = "Evento Data " + UUID.randomUUID().toString().substring(0, 8);
        String otherTitle = "Evento Outro Dia " + UUID.randomUUID().toString().substring(0, 8);

        long matchId = createEventWithStartDate(promoterToken, matchTitle, matchStart);
        long otherId = createEventWithStartDate(promoterToken, otherTitle, otherStart);

        mockMvc.perform(get("/events").param("date", matchStart.toLocalDate().toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + matchId + ")]").exists())
            .andExpect(jsonPath("$[?(@.id == " + otherId + ")]").doesNotExist());
    }

    @Test
    void allFourFiltersCombinedTogetherStillReturnOnlyTheExactMatch() throws Exception {
        String promoterToken = registerPromoter();
        LocalDateTime start = LocalDateTime.now().plusDays(6).withHour(9).withMinute(0).withSecond(0).withNano(0);
        String matchTitle = "Praia Combinada " + UUID.randomUUID().toString().substring(0, 8);
        long matchId = createEventWithTitleDescriptionAndStartDate(
            promoterToken, matchTitle, "Limpeza da faixa costeira.", "Aveiro", "AMBIENTE", start);

        // Mesma keyword e mesmo dia, mas localização e tipo diferentes — não deve passar os quatro filtros juntos.
        String sameKeywordTitle = "Praia Parecida " + UUID.randomUUID().toString().substring(0, 8);
        createEventWithTitleDescriptionAndStartDate(
            promoterToken, sameKeywordTitle, "Limpeza da faixa costeira.", "Faro", "SOCIAL", start);

        mockMvc.perform(get("/events")
                .param("location", "Aveiro")
                .param("date", start.toLocalDate().toString())
                .param("type", "AMBIENTE")
                .param("keyword", "praia"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].id").value(matchId));
    }

    @Test
    void listingEventsWithNoFiltersAtAllStillSucceeds() throws Exception {
        String promoterToken = registerPromoter();
        createEventWithTitleAndDescription(promoterToken, "Sem Filtros " + UUID.randomUUID().toString().substring(0, 8), null, "Porto", "OUTRO", "PUBLISHED");

        mockMvc.perform(get("/events"))
            .andExpect(status().isOk());
    }

    private long createEventWithStartDate(String promoterToken, String title, LocalDateTime start) throws Exception {
        return createEventWithTitleDescriptionAndStartDate(promoterToken, title, null, "Porto", "OUTRO", start);
    }

    private long createEventWithTitleDescriptionAndStartDate(String promoterToken, String title, String description,
            String location, String type, LocalDateTime start) throws Exception {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        String body = "{\"title\":\"%s\",\"description\":%s,\"location\":\"%s\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\",\"type\":\"%s\"}"
            .formatted(title, descriptionJson, location,
                start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                start.plusHours(2).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                type);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void getEventByIdSucceedsForAGenuinelyAnonymousRequestWithNoAuthorizationHeader() throws Exception {
        String promoterToken = registerPromoter();
        long eventId = createEvent(promoterToken, "PUBLISHED");

        // Sem cabeçalho Authorization — a página pública de detalhe (Feature 5) depende disto.
        mockMvc.perform(get("/events/" + eventId))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(eventId));
    }
}
