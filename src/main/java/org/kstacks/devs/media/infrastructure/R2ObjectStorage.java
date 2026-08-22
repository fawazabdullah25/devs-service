package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.media.application.ObjectStorage;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

final class R2ObjectStorage implements ObjectStorage {
    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Duration uploadExpiry;

    R2ObjectStorage(S3Client client, S3Presigner presigner, String bucket, long uploadExpiryMinutes) {
        this.client = client;
        this.presigner = presigner;
        this.bucket = bucket;
        this.uploadExpiry = Duration.ofMinutes(Math.max(5, uploadExpiryMinutes));
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

    private URI signedUri(String value) {
        return URI.create(value);
    }
}
