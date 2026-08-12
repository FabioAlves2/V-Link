package com.vlink.backend.controller;

import com.vlink.backend.model.Event;
import com.vlink.backend.model.Notification;
import com.vlink.backend.model.Subscription;
import com.vlink.backend.model.User;
import com.vlink.backend.repo.EventRepository;
import com.vlink.backend.repo.FavoriteRepository;
import com.vlink.backend.repo.NotificationRepository;
import com.vlink.backend.repo.SubscriptionRepository;
import com.vlink.backend.repo.UserRepository;
import com.vlink.backend.service.EmailService;
import com.vlink.backend.service.FileStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/events")
@RequiredArgsConstructor
@Tag(name = "Eventos", description = "Publicação, edição e listagem de eventos de voluntariado.")
public class EventController {

    private final EventRepository repo;
    private final SubscriptionRepository subscriptionRepo;
    private final UserRepository userRepo;
    private final NotificationRepository notificationRepo;
    private final FavoriteRepository favoriteRepo;
    private final FileStorageService fileStorageService;
    private final EmailService emailService;

    private Event withSubscriberCount(Event event) {
        event.setSubscriberCount((int) subscriptionRepo.countByEventId(event.getId()));
        return event;
    }

    // GET /events?location=porto&date=2025-06-01&type=LIMPEZA&keyword=praia  (todos opcionais)
    @Operation(summary = "Lista eventos publicados e ainda não terminados. Filtros opcionais e combináveis.")
    @GetMapping
    public List<Event> getEvents(
        @RequestParam(required = false) String location,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) Event.Type type,
        @RequestParam(required = false) String keyword
    ) {
        List<Event> events = repo.findByFilters(location, date, type, keyword, LocalDateTime.now());
        events.forEach(this::withSubscriberCount);
        return events;
    }

    // GET /events/mine  (só PROMOTER — os eventos do próprio, incluindo rascunhos)
    @Operation(summary = "Lista todos os eventos do promotor autenticado, incluindo rascunhos e encerrados.")
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/mine")
    public List<Event> myEvents(Authentication auth) {
        List<Event> events = repo.findByOrganizerEmail(auth.getName());
        events.forEach(this::withSubscriberCount);
        return events;
    }

    // GET /events/{id}
    @Operation(summary = "Detalhe de um evento — público, incluindo rascunhos/encerrados/já terminados (link direto).")
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEvent(@PathVariable Long id) {
        return repo.findById(id)
            .map(this::withSubscriberCount)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // POST /events  (só PROMOTER)
    @Operation(summary = "Cria um evento. Sem status ou status != PUBLISHED, fica como DRAFT (só PROMOTER).")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody Event event, Authentication auth) {
        if (event.getStatus() == Event.Status.CLOSED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Um evento não pode ser criado já fechado."));
        }
        if (event.getStartDate() != null && event.getStartDate().isBefore(LocalDateTime.now())) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Dados inválidos.",
                "errors", Map.of("startDate", "A data de início não pode ser no passado.")
            ));
        }
        User organizer = userRepo.findByEmail(auth.getName()).orElseThrow();
        event.setId(null); // impede que um id vindo do cliente transforme isto num update de outro evento
        event.setOrganizer(organizer);
        event.setStatus(event.getStatus() == Event.Status.PUBLISHED ? Event.Status.PUBLISHED : Event.Status.DRAFT);
        return ResponseEntity.status(HttpStatus.CREATED).body(withSubscriberCount(repo.save(event)));
    }

    // PUT /events/{id}  (só o PROMOTER que criou o evento)
    @Operation(summary = "Substitui um evento por completo (full-replace). Transições de status seguem uma máquina de estados — ver CLAUDE.md.")
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @Valid @RequestBody Event updated, Authentication auth) {
        return repo.findById(id).map(e -> {
            if (!e.getOrganizer().getEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Só o promotor que criou este evento o pode editar."));
            }
            // PUT é um full-replace: ao contrário de create() (onde omitir o estado tem o significado
            // deliberado de "guardar como rascunho"), aqui status/type omitidos não têm um default seguro —
            // sem esta verificação, silenciosamente despublicavam o evento ou apagavam o seu tipo.
            if (updated.getStatus() == null || updated.getType() == null) {
                Map<String, String> errors = new java.util.LinkedHashMap<>();
                if (updated.getStatus() == null) errors.put("status", "O estado é obrigatório.");
                if (updated.getType() == null) errors.put("type", "O tipo é obrigatório.");
                return ResponseEntity.badRequest().body(Map.of("error", "Dados inválidos.", "errors", errors));
            }
            // "Encerrar" é para eventos que já começaram (ou já decorreram) — um evento publicado
            // que ainda não começou deve ser cancelado (DELETE), não encerrado, porque cancelar
            // notifica os inscritos e remove as suas inscrições em vez de as deixar penduradas.
            if (e.getStatus() == Event.Status.PUBLISHED && updated.getStatus() == Event.Status.CLOSED
                    && e.getStartDate().isAfter(LocalDateTime.now())) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "Um evento que ainda não começou não pode ser encerrado — cancela-o em vez disso."
                ));
            }
            long currentSubscribers = subscriptionRepo.countByEventId(id);
            // Máquina de estados: sem isto, um PUBLISHED com inscritos podia ser "despublicado"
            // (PUT status:DRAFT) e a seguir eliminado via DELETE — que permite DRAFT sem olhar a
            // datas/inscritos — contornando por completo as regras de encerrar/cancelar acima.
            // Sem inscritos não há nada a proteger (equivale a nunca ter sido publicado), por isso
            // o bloqueio é condicional — ao contrário de um "não pode nunca" fixo, que impedia
            // despublicar um evento ainda não iniciado e sem ninguém inscrito, sem motivo real.
            if (e.getStatus() == Event.Status.PUBLISHED && updated.getStatus() == Event.Status.DRAFT
                    && currentSubscribers > 0) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "Um evento publicado com inscritos não pode voltar a rascunho. Cancela-o (se ainda não começou) ou encerra-o, em vez de o despublicar."));
            }
            // Do mesmo modo, CLOSED tem de ser terminal: "encerrado" é descrito em todo o código
            // como registo histórico permanente, o que deixa de ser verdade se puder ser reaberto.
            if (e.getStatus() == Event.Status.CLOSED && updated.getStatus() != Event.Status.CLOSED) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "Um evento encerrado não pode ser reaberto — o estado é permanente."));
            }
            // A capacidade não pode ficar abaixo dos inscritos já confirmados, ou "vagas
            // disponíveis" (capacity - inscritos) fica negativo na UI.
            if (updated.getCapacity() < currentSubscribers) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "A capacidade não pode ser inferior ao número de inscritos atuais (" + currentSubscribers + ")."));
            }
            boolean closingPublishedEvent = e.getStatus() == Event.Status.PUBLISHED
                && updated.getStatus() == Event.Status.CLOSED;
            boolean wasPublished = e.getStatus() == Event.Status.PUBLISHED;
            LocalDateTime oldStart = e.getStartDate();
            LocalDateTime oldEnd = e.getEndDate();
            e.setTitle(updated.getTitle());
            e.setDescription(updated.getDescription());
            e.setLocation(updated.getLocation());
            e.setCapacity(updated.getCapacity());
            e.setStartDate(updated.getStartDate());
            e.setEndDate(updated.getEndDate());
            e.setType(updated.getType());
            e.setImageUrl(updated.getImageUrl());
            e.setStatus(updated.getStatus());
            Event saved = repo.save(e);
            if (closingPublishedEvent) notifySubscribersOfClosure(saved);
            // Reagendar (mudar datas de um evento publicado que já tem inscritos) avisa-os — de
            // outro modo podiam perder o evento sem saber que mudou de horário. Não se aplica ao
            // encerrar/despublicar na mesma chamada: saved.getStatus() já não é PUBLISHED nesses casos.
            boolean rescheduled = wasPublished && saved.getStatus() == Event.Status.PUBLISHED
                && (!saved.getStartDate().equals(oldStart) || !saved.getEndDate().equals(oldEnd));
            // Sem isto, quem já tinha recebido o lembrete de "vai começar em breve" para o
            // horário antigo nunca seria avisado para o novo — o reminderSentAt sobrevive ao
            // reagendamento porque a Subscription não é recriada, só o Event é atualizado.
            if (rescheduled) {
                notifySubscribersOfReschedule(saved, oldStart, oldEnd);
                subscriptionRepo.clearReminderSentAt(saved.getId());
            }
            return ResponseEntity.ok(withSubscriberCount(saved));
        }).orElse(ResponseEntity.notFound().build());
    }

    // POST /events/{id}/image  (só o PROMOTER que criou o evento)
    @Operation(summary = "Envia/substitui a imagem de um evento (multipart, máx. 5MB). Remove a imagem anterior após guardar a nova.")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping(path = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadImage(@PathVariable Long id, @RequestParam("file") MultipartFile file, Authentication auth) {
        Event event = repo.findById(id).orElse(null);
        if (event == null) return ResponseEntity.notFound().build();
        if (!event.getOrganizer().getEmail().equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "Só o promotor que criou este evento pode alterar a sua imagem."));
        }
        try {
            String previousImageUrl = event.getImageUrl();
            event.setImageUrl(fileStorageService.storeEventImage(id, file));
            fileStorageService.deletePreviousImage(previousImageUrl); // só depois de guardar a nova
            return ResponseEntity.ok(withSubscriberCount(repo.save(event)));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
        }
    }

    private static final DateTimeFormatter NOTIFICATION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private void notifySubscribersOfReschedule(Event event, LocalDateTime oldStart, LocalDateTime oldEnd) {
        List<Subscription> subs = subscriptionRepo.findByEventId(event.getId());
        if (subs.isEmpty()) return;
        String message = "O evento \"" + event.getTitle() + "\" foi reagendado. Novo horário: "
            + event.getStartDate().format(NOTIFICATION_DATE_FORMAT) + " a "
            + event.getEndDate().format(NOTIFICATION_DATE_FORMAT) + ".";
        LocalDateTime now = LocalDateTime.now();
        notificationRepo.saveAll(subs.stream().map(s -> {
            Notification n = new Notification();
            n.setRecipient(s.getUser());
            n.setEvent(event);
            n.setMessage(message);
            n.setCreatedAt(now);
            return n;
        }).toList());
    }

    private void notifySubscribersOfClosure(Event event) {
        List<Subscription> subs = subscriptionRepo.findByEventId(event.getId());
        if (subs.isEmpty()) return;
        String message = "O evento \"" + event.getTitle() + "\" foi encerrado.";
        LocalDateTime now = LocalDateTime.now();
        notificationRepo.saveAll(subs.stream().map(s -> {
            Notification n = new Notification();
            n.setRecipient(s.getUser());
            n.setEvent(event);
            n.setMessage(message);
            n.setCreatedAt(now);
            return n;
        }).toList());
        subs.forEach(s -> emailService.sendEventClosureEmail(s.getUser(), event));
    }

    // DELETE /events/{id}  (só o PROMOTER que criou o evento)
    // Cancela (elimina) um rascunho, ou um evento publicado que ainda não começou. Um evento
    // encerrado ou já em curso não pode ser eliminado — usa PUT para o encerrar em vez disso.
    @Operation(summary = "Cancela (elimina) um rascunho ou um evento publicado que ainda não começou. Encerrados/já iniciados usam PUT para encerrar em vez disto.")
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> delete(@PathVariable Long id, Authentication auth) {
        return repo.findById(id).map(event -> {
            if (!event.getOrganizer().getEmail().equals(auth.getName())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Só o promotor que criou este evento o pode eliminar."));
            }
            boolean cancellingUpcoming = event.getStatus() == Event.Status.PUBLISHED
                && event.getStartDate().isAfter(LocalDateTime.now());
            boolean deletable = event.getStatus() == Event.Status.DRAFT || cancellingUpcoming;
            if (!deletable) {
                return ResponseEntity.badRequest().body(Map.of("error",
                    "Só é possível eliminar rascunhos, ou cancelar eventos publicados que ainda não começaram. "
                        + "Eventos encerrados ou já em curso não podem ser eliminados."));
            }
            if (cancellingUpcoming) notifySubscribersOfCancellation(event);
            notificationRepo.detachEvent(id);
            subscriptionRepo.deleteByEventId(id);
            favoriteRepo.deleteByEventId(id);
            fileStorageService.deleteEventImages(id);
            repo.delete(event);
            return ResponseEntity.noContent().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    private void notifySubscribersOfCancellation(Event event) {
        List<Subscription> subs = subscriptionRepo.findByEventId(event.getId());
        if (subs.isEmpty()) return;
        String message = "O evento \"" + event.getTitle() + "\" foi cancelado pelo organizador.";
        LocalDateTime now = LocalDateTime.now();
        notificationRepo.saveAll(subs.stream().map(s -> {
            Notification n = new Notification();
            n.setRecipient(s.getUser());
            n.setEvent(null); // sem ligação — o evento está a ser eliminado; a mensagem já contém o título
            n.setMessage(message);
            n.setCreatedAt(now);
            return n;
        }).toList());
        subs.forEach(s -> emailService.sendEventCancellationEmail(s.getUser(), event));
    }
}