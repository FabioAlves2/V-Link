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

    // currentImageUrl is unused by the local-disk implementation (it deletes the whole
    // events/{id} folder), but SupabaseFileStorageService needs it: an event's stored image
    // key includes a random UUID that can't be reconstructed from eventId alone, and Supabase
    // Storage's ListObjectsV2 doesn't reliably find objects by prefix to discover it either
    // (confirmed live — see SupabaseFileStorageService).
    void deleteEventImages(Long eventId, String currentImageUrl);

    void deletePreviousImage(String previousImageUrl);
}
