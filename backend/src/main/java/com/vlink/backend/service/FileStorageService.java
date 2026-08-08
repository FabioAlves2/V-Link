package com.vlink.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageService {

    private static final Set<String> ALLOWED_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp"
    );

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String storeEventImage(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Nenhum ficheiro foi enviado.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de ficheiro não suportado. Usa JPEG, PNG ou WEBP.");
        }
        try {
            Path eventDir = Paths.get(uploadDir, "events", String.valueOf(eventId)).toAbsolutePath().normalize();
            Files.createDirectories(eventDir);
            String filename = UUID.randomUUID() + "." + EXTENSIONS.get(contentType);
            Path target = eventDir.resolve(filename);
            file.transferTo(target);
            return "/uploads/events/" + eventId + "/" + filename;
        } catch (IOException ex) {
            throw new IllegalArgumentException("Não foi possível guardar a imagem.", ex);
        }
    }

    // Best-effort — uma falha do disco não deve impedir a eliminação do evento na base de dados.
    // Seguro chamar mesmo que a pasta nunca tenha existido (nenhuma imagem foi carregada).
    public void deleteEventImages(Long eventId) {
        try {
            Path eventDir = Paths.get(uploadDir, "events", String.valueOf(eventId)).toAbsolutePath().normalize();
            FileSystemUtils.deleteRecursively(eventDir);
        } catch (IOException ignored) {
        }
    }

    // Best-effort: apaga só o ficheiro anterior (não a pasta toda — o ficheiro novo já lá está
    // quando isto é chamado). Seguro com null/vazio ou um URL legado externo (não-/uploads/) —
    // esses são simplesmente ignorados em vez de se tentar apagar algo fora da nossa pasta.
    public void deletePreviousImage(String previousImageUrl) {
        if (previousImageUrl == null || !previousImageUrl.startsWith("/uploads/")) return;
        try {
            Path file = Paths.get(uploadDir, previousImageUrl.substring("/uploads/".length()))
                .toAbsolutePath().normalize();
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
