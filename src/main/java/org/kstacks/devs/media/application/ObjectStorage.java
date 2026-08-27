package org.kstacks.devs.media.application;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public interface ObjectStorage {
    record UploadGrant(URI uploadUrl, String objectKey, Map<String, String> headers, Instant expiresAt) {}

    UploadGrant signUpload(String objectKey, String contentType, long contentLength);
    default UploadGrant signUpload(String objectKey, String contentType, long contentLength, String contentDisposition) {
        return signUpload(objectKey, contentType, contentLength);
    }
    URI signDownload(String objectKey);
    boolean exists(String objectKey);
    default long size(String objectKey) { return -1; }
    default void delete(String objectKey) { throw new UnsupportedOperationException("Object deletion is unavailable"); }
    default List<String> list(String prefix) { throw new UnsupportedOperationException("Object listing is unavailable"); }
    default void deletePrefix(String prefix) { throw new UnsupportedOperationException("Prefix deletion is unavailable"); }
}
