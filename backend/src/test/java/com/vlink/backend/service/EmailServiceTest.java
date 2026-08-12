package com.vlink.backend.service;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.User;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class EmailServiceTest {

    private User user(String email) {
        User u = new User();
        u.setEmail(email);
        u.setName("Test User");
        return u;
    }

    private Event event() {
        Event e = new Event();
        e.setTitle("Test Event");
        e.setLocation("Porto");
        e.setStartDate(LocalDateTime.now().plusHours(24));
        e.setEndDate(LocalDateTime.now().plusHours(26));
        return e;
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> providerOf(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    @Test
    void sendDoesNotThrowWhenTheUnderlyingMailSenderThrows() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("smtp down")).when(mailSender).send(any(SimpleMailMessage.class));

        EmailService service = new EmailService(providerOf(mailSender));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "no-reply@vlink.pt");

        assertDoesNotThrow(() -> service.sendSignupConfirmationEmail(user("volunteer@example.com"), event()));
    }

    @Test
    void sendDoesNothingAndNeverTouchesTheMailSenderWhenMailIsDisabled() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailService service = new EmailService(providerOf(mailSender));
        ReflectionTestUtils.setField(service, "enabled", false);
        ReflectionTestUtils.setField(service, "from", "no-reply@vlink.pt");

        service.sendSignupConfirmationEmail(user("volunteer@example.com"), event());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendUsesTheConfiguredFromAddressAndTheRecipientsEmail() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        EmailService service = new EmailService(providerOf(mailSender));
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "from", "no-reply@vlink.pt");

        service.sendSignupConfirmationEmail(user("volunteer@example.com"), event());

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        assertEquals("no-reply@vlink.pt", captor.getValue().getFrom());
        assertEquals("volunteer@example.com", captor.getValue().getTo()[0]);
    }
}
