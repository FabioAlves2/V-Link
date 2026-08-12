package com.vlink.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.SubscriptionRepository;
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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// A base de dados H2 (e o contexto Spring, incluindo o EmailService mockado) é partilhada entre
// TODAS as classes de teste que usam a mesma configuração — não só entre métodos desta classe
// (ver CLAUDE.md). O scheduler processa a tabela subscriptions inteira, por isso subscrições
// deixadas por outras classes (ex.: EmailNotificationTest) com startDate ainda dentro da janela
// e reminderSentAt nulo também disparam aqui. As asserções por isso têm de estar sempre
// ancoradas ao eventId criado neste teste (argThat), nunca a uma contagem global de invocações.
@SpringBootTest
@AutoConfigureMockMvc
class EventReminderSchedulerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EventReminderScheduler scheduler;
    @Autowired EventRepository eventRepo;
    @Autowired SubscriptionRepository subscriptionRepo;
    @MockitoBean EmailService emailService;

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

    private String date(int hoursFromNow) {
        return LocalDateTime.now().plusHours(hoursFromNow).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private long createEventWithDates(String promoterToken, String start, String end, String status) throws Exception {
        String title = "Reminder Event " + UUID.randomUUID().toString().substring(0, 8);
        String body = "{\"title\":\"%s\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\"}"
            .formatted(title, start, end, status);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    private void updateEventDates(String promoterToken, long id, String start, String end, String status) throws Exception {
        String body = "{\"title\":\"Reminder Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"%s\",\"type\":\"OUTRO\"}"
            .formatted(start, end, status);
        mockMvc.perform(put("/events/" + id).header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk());
    }

    private Event forEvent(long eventId) {
        return argThat(e -> e != null && e.getId() != null && e.getId().equals(eventId));
    }

    @Test
    void sendsAReminderForAnEventStartingWithinTheWindow() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED"); // dentro da janela de 24h

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        scheduler.sendUpcomingEventReminders();

        verify(emailService, times(1)).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void doesNotSendAReminderTwiceOnASecondRun() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        scheduler.sendUpcomingEventReminders();
        scheduler.sendUpcomingEventReminders();

        verify(emailService, times(1)).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void doesNotSendAReminderForAnEventOutsideTheWindow() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(48), date(50), "PUBLISHED"); // fora da janela de 24h

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        scheduler.sendUpcomingEventReminders();

        verify(emailService, never()).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void doesNotSendAReminderForAnEventThatAlreadyStarted() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(24), date(26), "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        updateEventDates(promoterToken, eventId, date(-2), date(2), "PUBLISHED"); // já começou

        scheduler.sendUpcomingEventReminders();

        verify(emailService, never()).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void doesNotSendAReminderForAClosedEvent() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        // Bypassa a máquina de estados do controller (que não deixa fechar um evento futuro) só
        // para isolar o filtro de status da query — não é um caminho alcançável via API.
        Event event = eventRepo.findById(eventId).orElseThrow();
        event.setStatus(Event.Status.CLOSED);
        eventRepo.save(event);

        scheduler.sendUpcomingEventReminders();

        verify(emailService, never()).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void resetsReminderSentAtWhenTheEventIsRescheduled() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        scheduler.sendUpcomingEventReminders();
        verify(emailService, times(1)).sendEventReminderEmail(any(User.class), forEvent(eventId));

        updateEventDates(promoterToken, eventId, date(11), date(13), "PUBLISHED"); // reagendado, ainda dentro da janela

        scheduler.sendUpcomingEventReminders();
        verify(emailService, times(2)).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    @Test
    void doesNotSendAReminderForAnEventCancelledAfterSubscribing() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerToken = register("reminder-volunteer", "VOLUNTEER");
        long eventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED"); // ainda não começou

        mockMvc.perform(post("/subscriptions/" + eventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        scheduler.sendUpcomingEventReminders();

        verify(emailService, never()).sendEventReminderEmail(any(User.class), forEvent(eventId));
    }

    // Regressão: o método é @Transactional, e sem isolamento por item uma excepção inesperada a
    // processar UM item do lote (aqui simulada com o mock a lançar) revertia o reminderSentAt já
    // marcado nos itens anteriores do MESMO lote — fazendo-os ser avisados a dobrar na próxima
    // execução. Este teste garante que o item que falha não arrasta os outros para o rollback.
    @Test
    void aFailureSendingOneReminderDoesNotRollBackOthersAlreadyProcessedInTheSameBatch() throws Exception {
        String promoterToken = register("reminder-promoter", "PROMOTER");
        String volunteerEmail = "reminder-volunteer-" + UUID.randomUUID() + "@example.com";
        String volunteerToken = registerWithEmail(volunteerEmail, "VOLUNTEER");

        long okEventId = createEventWithDates(promoterToken, date(10), date(12), "PUBLISHED");
        long failingEventId = createEventWithDates(promoterToken, date(11), date(13), "PUBLISHED");

        mockMvc.perform(post("/subscriptions/" + okEventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());
        mockMvc.perform(post("/subscriptions/" + failingEventId).header("Authorization", "Bearer " + volunteerToken))
            .andExpect(status().isOk());

        doThrow(new RuntimeException("falha inesperada, não uma MailException"))
            .when(emailService).sendEventReminderEmail(any(User.class), forEvent(failingEventId));

        scheduler.sendUpcomingEventReminders();

        boolean okEventReminderPersisted = subscriptionRepo.findByUserEmail(volunteerEmail).stream()
            .filter(s -> s.getEvent().getId().equals(okEventId))
            .anyMatch(s -> s.getReminderSentAt() != null);

        assertTrue(okEventReminderPersisted,
            "the ok event's reminderSentAt must survive even though a later/earlier item in the same batch threw");
    }
}
