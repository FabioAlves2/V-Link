package com.vlink.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

// Default storage backend — local disk under app.upload.dir, served via the /uploads/**
// static mapping. Active whenever app.storage.provider isn't explicitly "supabase" (dev,
// test, and the docker-compose demo all fall through to this, even though compose runs the
// prod Spring profile — storage backend is a deliberately separate switch from environment
// profile, see SupabaseFileStorageService).
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    @Value("${app.upload.dir}")
    private String uploadDir;

    public String storeEventImage(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Nenhum ficheiro foi enviado.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de ficheiro não suportado. Usa JPEG, PNG ou WEBP.");
        }
        try {
            Path eventDir = Paths.get(uploadDir, "events", String.valueOf(eventId)).toAbsolutePath().normalize();
            Files.createDirectories(eventDir);
            String filename = UUID.randomUUID() + "." + IMAGE_EXTENSIONS.get(contentType);
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
            Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
            Path file = Paths.get(uploadDir, previousImageUrl.substring("/uploads/".length()))
                .toAbsolutePath().normalize();
            // O prefixo "/uploads/" acima não impede "../" — normalize() resolve-o antes deste
            // check, por isso é preciso confirmar que o caminho final continua dentro de uploadDir.
            if (!file.startsWith(root)) return;
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
        }
    }
}
