package org.kstacks.devs.media.infrastructure;

import org.kstacks.devs.media.application.VideoProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@Component
@ConditionalOnProperty(name = "devs.mux.enabled", havingValue = "false", matchIfMissing = true)
public class UnavailableVideoProvider implements VideoProvider {
    @Override
    public CreatedAsset createAsset(URI sourceUrl) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Mux is not configured");
    }
}
