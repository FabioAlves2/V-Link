package com.vlink.backend.service;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

// Best-effort, igual ao idioma de FileStorageService (deletePreviousImage): uma falha de email
// nunca deve impedir a operação principal (subscrever, encerrar, cancelar). Gate por
// app.mail.enabled em vez de depender só da ausência do bean JavaMailSender — assim dev/test
// não precisam de nenhuma configuração SMTP para arrancar ou passar testes.
@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from:no-reply@vlink.pt}")
    private String from;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.mailSenderProvider = mailSenderProvider;
    }

    public void sendSignupConfirmationEmail(User user, Event event) {
        send(user.getEmail(), "Inscrição confirmada: " + event.getTitle(),
            "Confirmámos a tua inscrição no evento \"" + event.getTitle() + "\", a " + event.getLocation()
                + ", com início em " + event.getStartDate().format(DATE_FORMAT) + ".");
    }

    public void sendEventReminderEmail(User user, Event event) {
        send(user.getEmail(), "Lembrete: " + event.getTitle() + " está a chegar",
            "O evento \"" + event.getTitle() + "\" começa em breve, a " + event.getStartDate().format(DATE_FORMAT)
                + ", em " + event.getLocation() + ".");
    }

    public void sendEventClosureEmail(User user, Event event) {
        send(user.getEmail(), "Evento encerrado: " + event.getTitle(),
            "O evento \"" + event.getTitle() + "\" foi encerrado pelo organizador.");
    }

    public void sendEventCancellationEmail(User user, Event event) {
        send(user.getEmail(), "Evento cancelado: " + event.getTitle(),
            "O evento \"" + event.getTitle() + "\" foi cancelado pelo organizador.");
    }

    private void send(String to, String subject, String text) {
        if (!enabled) {
            log.info("Email desativado (app.mail.enabled=false) — não enviado para {}: {}", to, subject);
            return;
        }
        JavaMailSender sender = mailSenderProvider.getIfAvailable();
        if (sender == null) {
            log.warn("app.mail.enabled=true mas não há JavaMailSender configurado — email não enviado para {}", to);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(text);
            sender.send(message);
        } catch (MailException ex) {
            log.error("Falha ao enviar email para {}: {}", to, ex.getMessage());
        }
    }
}
