package com.vlink.backend.service;

import com.vlink.backend.model.Subscription;
import com.vlink.backend.repo.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

// Corre periodicamente (app.mail.reminder-cron) e avisa quem está inscrito num evento
// PUBLISHED que começa dentro de app.mail.reminder-window-hours e ainda não foi avisado.
// Cancelar/desinscrever remove a própria Subscription (nada a avisar); reagendar repõe
// reminderSentAt a null explicitamente (ver EventController.update).
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final SubscriptionRepository subscriptionRepo;
    private final EmailService emailService;

    @Value("${app.mail.reminder-window-hours:24}")
    private int windowHours;

    @Scheduled(cron = "${app.mail.reminder-cron:0 */15 * * * *}")
    @Transactional
    public void sendUpcomingEventReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Subscription> pending = subscriptionRepo.findPendingReminders(now, now.plusHours(windowHours));
        // Isolado por item: EmailService já trata falhas de envio (MailException) como best-effort,
        // mas sem este try/catch aqui, qualquer outra excepção inesperada durante UM item (ex.: dados
        // corrompidos nesse Subscription específico) escapava do forEach e, por o método ser
        // @Transactional, revertia o reminderSentAt já marcado nos itens anteriores do MESMO lote —
        // fazendo-os receber um lembrete a dobrar na próxima execução. Um item que falhe aqui
        // simplesmente fica elegível para tentar de novo na próxima execução.
        for (Subscription s : pending) {
            try {
                emailService.sendEventReminderEmail(s.getUser(), s.getEvent());
                s.setReminderSentAt(now);
            } catch (Exception ex) {
                log.error("Falha ao processar o lembrete da subscrição {}: {}", s.getId(), ex.getMessage());
            }
        }
        subscriptionRepo.saveAll(pending);
    }
}
