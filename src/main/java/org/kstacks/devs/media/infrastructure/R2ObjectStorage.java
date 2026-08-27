package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.media.application.ObjectStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class R2ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration uploadExpiry;
    private final String allowedPrefix;

    R2ObjectStorage(S3Client client, S3Presigner presigner, String bucket, long uploadExpiryMinutes) {
        this(client, presigner, bucket, uploadExpiryMinutes, "");
    }

    R2ObjectStorage(S3Client client, S3Presigner presigner, String bucket, long uploadExpiryMinutes, String allowedPrefix) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.uploadExpiry = Duration.ofMinutes(Math.max(5, uploadExpiryMinutes));
        this.allowedPrefix = normalizePrefix(allowedPrefix);
    }

    @Override
    public UploadGrant signUpload(String objectKey, String contentType, long contentLength) {
        return signUpload(objectKey, contentType, contentLength, null);
    }

    @Override
    public UploadGrant signUpload(String objectKey, String contentType, long contentLength, String contentDisposition) {
        var objectRequest = PutObjectRequest.builder()
            .bucket(bucket).key(objectKey).contentType(contentType).contentLength(contentLength)
            .contentDisposition(contentDisposition).build();
        var request = PutObjectPresignRequest.builder().signatureDuration(uploadExpiry).putObjectRequest(objectRequest).build();
        var signed = presigner.presignPutObject(request);
        var headers = new java.util.LinkedHashMap<String, String>();
        headers.put("Content-Type", contentType);
        if (contentDisposition != null) headers.put("Content-Disposition", contentDisposition);
        return new UploadGrant(URI.create(signed.url().toString()), objectKey, Map.copyOf(headers), Instant.now().plus(uploadExpiry));
    }

    @Override
    public URI signDownload(String objectKey) {
        var objectRequest = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();
        var request = GetObjectPresignRequest.builder().signatureDuration(Duration.ofHours(1)).getObjectRequest(objectRequest).build();
        return signedUri(presigner.presignGetObject(request).url().toString());
    }

    @Override
    public boolean exists(String objectKey) {
        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build());
            return true;
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) return false;
            throw exception;
        }
    }

    @Override
    public long size(String objectKey) {
        return client.headObject(HeadObjectRequest.builder().bucket(bucket).key(objectKey).build()).contentLength();
    }

    @Override
    public void delete(String objectKey) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    @Override
    public List<String> list(String prefix) {
        validatePrefix(prefix);
        var keys = new ArrayList<String>();
        String continuation = null;
        do {
            var builder = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix);
            if (continuation != null) builder.continuationToken(continuation);
            var request = builder.build();
            var response = client.listObjectsV2(request);
            response.contents().forEach(object -> {
                if (!object.key().startsWith(prefix)) {
                    throw new IllegalStateException("R2 returned an object outside the requested prefix");
                }
                keys.add(object.key());
            });
            continuation = response.isTruncated() ? response.nextContinuationToken() : null;
        } while (continuation != null);
        return List.copyOf(keys);
    }

    @Override
    public void deletePrefix(String prefix) {
        var keys = list(prefix);
        for (var start = 0; start < keys.size(); start += 1_000) {
            var end = Math.min(start + 1_000, keys.size());
            var objects = keys.subList(start, end).stream()
                .map(key -> ObjectIdentifier.builder().key(key).build())
                .toList();
            var response = client.deleteObjects(DeleteObjectsRequest.builder()
                .bucket(bucket)
                .delete(Delete.builder().objects(objects).quiet(true).build())
                .build());
            if (response.hasErrors() && !response.errors().isEmpty()) {
                throw new IllegalStateException("R2 did not delete every object in the HLS package");
            }
        }
    }

    private void validatePrefix(String prefix) {
        if (prefix == null || prefix.isBlank() || !prefix.endsWith("/") ||
            prefix.startsWith("/") || prefix.contains("..") || prefix.contains("\\") ||
            prefix.contains(":") || prefix.contains("?") || prefix.contains("#") ||
            prefix.contains("%") || !prefix.matches("[A-Za-z0-9._~/-]+")) {
            throw new IllegalArgumentException("The object prefix is invalid");
        }
        if (allowedPrefix.isBlank() || !prefix.startsWith(allowedPrefix)) {
            throw new IllegalArgumentException("The object prefix is outside the configured HLS prefix");
        }
    }

    private String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) return "";
        var value = prefix.trim();
        return value.endsWith("/") ? value : value + "/";
    }

    private URI signedUri(String value) {
        return URI.create(value);
    }
}
