package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.media.application.ObjectStorage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Component
@ConditionalOnProperty(name = "devs.r2.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableObjectStorage implements ObjectStorage {
    @Override public UploadGrant signUpload(String objectKey, String contentType, long contentLength) { throw unavailable(); }
    @Override public boolean exists(String objectKey) { throw unavailable(); }
    @Override public long size(String objectKey) { throw unavailable(); }
    @Override public void delete(String objectKey) { throw unavailable(); }
    @Override public java.util.List<String> list(String prefix) { throw unavailable(); }
    @Override public void deletePrefix(String prefix) { throw unavailable(); }
    private ResponseStatusException unavailable() { return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "R2 is not configured"); }
}
