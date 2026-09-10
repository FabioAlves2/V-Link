package com.vlink.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

// Opt-in storage backend (app.storage.provider=supabase, Render only) — event images survive
// a redeploy/cold restart, unlike LocalFileStorageService's disk under a container's ephemeral
// filesystem. Talks to Supabase Storage's S3-compatible API (not its REST/anon-key layer, which
// the rest of the backend has never used and none of the tables have RLS policies for).
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "supabase")
public class SupabaseFileStorageService implements FileStorageService {

    // Not final: tests swap this for a mock via ReflectionTestUtils rather than adding a
    // test-only constructor overload.
    private S3Client s3Client;
    private final String bucket;
    private final String publicUrlBase;

    public SupabaseFileStorageService(
            @Value("${app.storage.supabase.project-url}") String projectUrl,
            @Value("${app.storage.supabase.bucket}") String bucket,
            @Value("${app.storage.supabase.access-key}") String accessKey,
            @Value("${app.storage.supabase.secret-key}") String secretKey,
            @Value("${app.storage.supabase.region:us-east-1}") String region) {
        this.bucket = bucket;
        String base = projectUrl.endsWith("/") ? projectUrl.substring(0, projectUrl.length() - 1) : projectUrl;
        this.publicUrlBase = base + "/storage/v1/object/public/" + bucket;
        // The S3-compatible API lives on a DIFFERENT subdomain (storage.supabase.co) than the
        // project's own API/public-object domain (<ref>.supabase.co) used above for
        // publicUrlBase — pointing the S3 client at the wrong one doesn't fail fast, it just
        // hangs until the client times out (found live: registration/login/event-creation all
        // worked, only the S3 upload call hung).
        String projectRef = base.replaceFirst("^https?://", "").split("\\.")[0];
        String s3Endpoint = "https://" + projectRef + ".storage.supabase.co/storage/v1/s3";
        this.s3Client = S3Client.builder()
            .endpointOverride(URI.create(s3Endpoint))
            .region(Region.of(region))
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
            .forcePathStyle(true)
            // The default SDK client has no bound on how long a call can hang — a single bad
            // connection would tie up a Tomcat thread indefinitely on Render's single-worker
            // free tier. Fail fast instead so a network problem surfaces as a real exception.
            .overrideConfiguration(o -> o
                .apiCallTimeout(Duration.ofSeconds(15))
                .apiCallAttemptTimeout(Duration.ofSeconds(8)))
            .build();
    }

    public String storeEventImage(Long eventId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Nenhum ficheiro foi enviado.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new IllegalArgumentException("Tipo de ficheiro não suportado. Usa JPEG, PNG ou WEBP.");
        }
        String key = "events/" + eventId + "/" + UUID.randomUUID() + "." + IMAGE_EXTENSIONS.get(contentType);
        try {
            s3Client.putObject(
                PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        } catch (IOException | SdkException ex) {
            throw new IllegalArgumentException("Não foi possível guardar a imagem.", ex);
        }
        return publicUrlBase + "/" + key;
    }

    // Best-effort, same idiom as LocalFileStorageService — a Storage failure shouldn't block
    // deleting the event itself.
    public void deleteEventImages(Long eventId) {
        deleteByPrefix("events/" + eventId + "/");
    }

    // Best-effort: apaga só o ficheiro anterior. Ignora null/vazio ou um URL fora do nosso
    // bucket público (legado ou externo).
    public void deletePreviousImage(String previousImageUrl) {
        String prefix = publicUrlBase + "/";
        if (previousImageUrl == null || !previousImageUrl.startsWith(prefix)) return;
        try {
            String key = previousImageUrl.substring(prefix.length());
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (SdkException ignored) {
        }
    }

    private void deleteByPrefix(String prefix) {
        try {
            List<ObjectIdentifier> toDelete = s3Client
                .listObjectsV2(ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).build())
                .contents().stream()
                .map(o -> ObjectIdentifier.builder().key(o.key()).build())
                .toList();
            if (!toDelete.isEmpty()) {
                s3Client.deleteObjects(DeleteObjectsRequest.builder()
                    .bucket(bucket)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build());
            }
        } catch (SdkException ignored) {
        }
    }
}
