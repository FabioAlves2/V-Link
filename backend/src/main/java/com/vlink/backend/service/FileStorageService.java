package com.vlink.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Set;

public interface FileStorageService {

    Set<String> ALLOWED_IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    Map<String, String> IMAGE_EXTENSIONS = Map.of(
        "image/jpeg", "jpg",
        "image/png", "png",
        "image/webp", "webp"
    );

    String storeEventImage(Long eventId, MultipartFile file);

    void deleteEventImages(Long eventId);

    void deletePreviousImage(String previousImageUrl);
}
