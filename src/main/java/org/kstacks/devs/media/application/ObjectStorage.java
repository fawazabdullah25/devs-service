package org.kstacks.devs.media.application;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public interface ObjectStorage {
    record UploadGrant(URI uploadUrl, String objectKey, Map<String, String> headers, Instant expiresAt) {}

    UploadGrant signUpload(String objectKey, String contentType, long contentLength);
    URI signDownload(String objectKey);
    boolean exists(String objectKey);
}
