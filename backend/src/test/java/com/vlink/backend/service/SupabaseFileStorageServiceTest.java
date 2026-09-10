package com.vlink.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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

    // Regression: the original implementation listed objects under "events/{id}/" via
    // ListObjectsV2 and batch-deleted the results — confirmed live against Supabase that
    // ListObjectsV2 doesn't reliably find an object a direct GET/DELETE by key can still reach,
    // and the batch DeleteObjects operation errors outright on this gateway. Both failures were
    // silent (wrapped in the same best-effort catch), leaving orphaned images with no visible
    // failure. deleteEventImages now takes the known current URL and deletes that single key
    // directly, the same proven path deletePreviousImage already uses.
    @Test
    void deleteEventImagesDeletesTheGivenKeyDirectlyRatherThanListingByPrefix() {
        service.deleteEventImages(42L, PUBLIC_URL_BASE + "/events/42/some-uuid.jpg");

        ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(s3Client).deleteObject(captor.capture());
        assertThat(captor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(captor.getValue().key()).isEqualTo("events/42/some-uuid.jpg");
    }

    @Test
    void deleteEventImagesDoesNothingWhenTheEventHasNoImage() {
        service.deleteEventImages(42L, null);

        verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
}
