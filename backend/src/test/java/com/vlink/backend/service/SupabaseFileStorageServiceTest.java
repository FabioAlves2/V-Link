package com.vlink.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Unit tests only — no real network call to Supabase. S3Client is swapped for a mock after
// construction (same ReflectionTestUtils idiom LocalFileStorageServiceTest uses for uploadDir),
// since it's built internally from constructor args rather than injected.
class SupabaseFileStorageServiceTest {

    private static final String PROJECT_URL = "https://xyzcompany.supabase.co";
    private static final String BUCKET = "event-images";
    private static final String PUBLIC_URL_BASE = PROJECT_URL + "/storage/v1/object/public/" + BUCKET;

    private S3Client s3Client;
    private SupabaseFileStorageService service;

    @BeforeEach
    void setUp() {
        service = new SupabaseFileStorageService(PROJECT_URL, BUCKET, "access-key", "secret-key", "us-east-1");
        s3Client = mock(S3Client.class);
        ReflectionTestUtils.setField(service, "s3Client", s3Client);
    }

    @Test
    void storeEventImageUploadsAndReturnsThePublicUrl() {
        MockMultipartFile file = new MockMultipartFile("image", "photo.png", "image/png", new byte[]{1, 2, 3});

        String url = service.storeEventImage(42L, file);

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3Client).putObject(captor.capture(), any(software.amazon.awssdk.core.sync.RequestBody.class));
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).startsWith("events/42/").endsWith(".png");
        assertThat(url).isEqualTo(PUBLIC_URL_BASE + "/" + captor.getValue().key());
    }

    @Test
    void storeEventImageRejectsUnsupportedContentType() {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf", new byte[]{1});

        assertThrows(IllegalArgumentException.class, () -> service.storeEventImage(1L, file));
    }

    @Test
    void deletePreviousImageDeletesAKeyMatchingOurPublicBucket() {
        service.deletePreviousImage(PUBLIC_URL_BASE + "/events/42/some-uuid.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("events/42/some-uuid.jpg");
    }

    @Test
    void deletePreviousImageIgnoresAUrlOutsideOurPublicBucket() {
        service.deletePreviousImage("/uploads/events/42/legacy-local-disk-image.jpg");

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void deleteEventImagesDeletesEveryObjectUnderTheEventPrefix() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
            ListObjectsV2Response.builder()
                .contents(
                    S3Object.builder().key("events/42/a.jpg").build(),
                    S3Object.builder().key("events/42/b.jpg").build())
                .build());

        service.deleteEventImages(42L);

        ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
        verify(s3Client).deleteObjects(captor.capture());
        List<String> deletedKeys = captor.getValue().delete().objects().stream()
            .map(o -> o.key())
            .toList();
        assertThat(deletedKeys).containsExactlyInAnyOrder("events/42/a.jpg", "events/42/b.jpg");
    }

    @Test
    void deleteEventImagesDoesNothingWhenTheEventHasNoImages() {
        when(s3Client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
            ListObjectsV2Response.builder().contents(List.of()).build());

        service.deleteEventImages(42L);

        verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
}
