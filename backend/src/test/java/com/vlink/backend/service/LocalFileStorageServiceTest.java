package com.vlink.backend.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalFileStorageServiceTest {

    // Regressão: deletePreviousImage() só validava um prefixo "/uploads/" *antes* de
    // normalizar o caminho — "../" no resto da string escapava do diretório de uploads depois
    // de normalize() resolver os ".." (path traversal -> eliminação arbitrária de ficheiros).
    @Test
    void deletePreviousImageNeverDeletesOutsideTheUploadDirectory() throws IOException {
        Path uploadDir = Files.createTempDirectory("vlink-upload-test");
        Path canary = Files.createTempFile(uploadDir.getParent(), "canary-outside-upload-dir", ".txt");
        assertTrue(Files.exists(canary));

        LocalFileStorageService service = new LocalFileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());

        String traversalUrl = "/uploads/../" + canary.getFileName();
        service.deletePreviousImage(traversalUrl);

        assertTrue(Files.exists(canary), "um imageUrl com travessia de caminho nunca deve apagar ficheiros fora de uploadDir");

        Files.deleteIfExists(canary);
        Files.deleteIfExists(uploadDir);
    }

    @Test
    void deletePreviousImageStillDeletesAFileActuallyInsideTheUploadDirectory() throws IOException {
        Path uploadDir = Files.createTempDirectory("vlink-upload-test");
        Path inside = Files.createFile(uploadDir.resolve("real-image.png"));
        assertTrue(Files.exists(inside));

        LocalFileStorageService service = new LocalFileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", uploadDir.toString());

        service.deletePreviousImage("/uploads/real-image.png");

        assertTrue(Files.notExists(inside), "um ficheiro genuinamente dentro de uploadDir continua a ser apagado");

        Files.deleteIfExists(uploadDir);
    }
}
