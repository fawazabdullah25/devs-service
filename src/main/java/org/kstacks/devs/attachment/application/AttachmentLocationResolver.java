package org.kstacks.devs.attachment.application;

import org.kstacks.devs.config.AttachmentProperties;
import org.springframework.stereotype.Component;

import java.net.URI;

@Component
public class AttachmentLocationResolver {
    private final URI baseUrl;

    public AttachmentLocationResolver(AttachmentProperties properties) {
        var value = properties.publicBaseUrl().toString();
        this.baseUrl = URI.create(value.endsWith("/") ? value : value + "/");
    }

    public URI resolve(String objectKey) { return baseUrl.resolve(objectKey); }
}
