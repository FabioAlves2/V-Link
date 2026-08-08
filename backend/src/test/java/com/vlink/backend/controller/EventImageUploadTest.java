package com.vlink.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class EventImageUploadTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Value("${app.upload.dir}")
    private String uploadDir;

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

    private long createEvent(String promoterToken) throws Exception {
        String start = LocalDateTime.now().plusHours(24).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String end = LocalDateTime.now().plusHours(26).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        String body = "{\"title\":\"Image Test Event\",\"location\":\"Porto\",\"capacity\":5,\"startDate\":\"%s\",\"endDate\":\"%s\",\"status\":\"PUBLISHED\"}"
            .formatted(start, end);
        String created = mockMvc.perform(post("/events").header("Authorization", "Bearer " + promoterToken)
                .contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    void promoterCanUploadImageForOwnEvent() throws Exception {
        String promoterToken = register("img-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/events/" + eventId + "/image").file(file).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.imageUrl", org.hamcrest.Matchers.startsWith("/uploads/events/" + eventId + "/")));
    }

    @Test
    void nonOwnerCannotUploadImage() throws Exception {
        String promoterToken = register("img-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);
        String otherPromoterToken = register("img-other-promoter", "PROMOTER");

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());

        mockMvc.perform(multipart("/events/" + eventId + "/image").file(file).header("Authorization", "Bearer " + otherPromoterToken))
            .andExpect(status().isForbidden());
    }

    @Test
    void rejectsNonImageContentType() throws Exception {
        String promoterToken = register("img-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);

        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "hello".getBytes());

        mockMvc.perform(multipart("/events/" + eventId + "/image").file(file).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void deletingEventRemovesItsUploadedImageFromDisk() throws Exception {
        String promoterToken = register("img-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken); // PUBLISHED, datas futuras — elegível para cancelar

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "fake-image-bytes".getBytes());
        mockMvc.perform(multipart("/events/" + eventId + "/image").file(file).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk());

        Path eventDir = Paths.get(uploadDir, "events", String.valueOf(eventId)).toAbsolutePath().normalize();
        assertTrue(Files.exists(eventDir) && Files.list(eventDir).findAny().isPresent(), "a imagem devia ter sido gravada antes de eliminar o evento");

        mockMvc.perform(delete("/events/" + eventId).header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isNoContent());

        assertFalse(Files.exists(eventDir), "a pasta de imagens do evento devia ter sido removida ao eliminar o evento");
    }

    @Test
    void reuploadingEventImageDeletesThePreviousFile() throws Exception {
        String promoterToken = register("img-promoter", "PROMOTER");
        long eventId = createEvent(promoterToken);

        MockMultipartFile first = new MockMultipartFile("file", "first.jpg", "image/jpeg", "first-bytes".getBytes());
        String firstResponse = mockMvc.perform(multipart("/events/" + eventId + "/image").file(first)
                .header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String firstImageUrl = objectMapper.readTree(firstResponse).get("imageUrl").asText();
        Path firstFile = Paths.get(uploadDir, firstImageUrl.substring("/uploads/".length())).toAbsolutePath().normalize();
        assertTrue(Files.exists(firstFile), "o primeiro ficheiro devia existir depois do primeiro upload");

        MockMultipartFile second = new MockMultipartFile("file", "second.jpg", "image/jpeg", "second-bytes".getBytes());
        mockMvc.perform(multipart("/events/" + eventId + "/image").file(second)
                .header("Authorization", "Bearer " + promoterToken))
            .andExpect(status().isOk());

        assertFalse(Files.exists(firstFile), "o ficheiro anterior devia ter sido apagado ao ser substituído");
    }
}
