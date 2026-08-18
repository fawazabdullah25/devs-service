package org.kstacks.devs.media.application;

import org.kstacks.devs.config.StaticHlsProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.regex.Pattern;

@Component
public class StaticHlsLocationResolver {
    private static final Pattern SAFE_PATH = Pattern.compile("[A-Za-z0-9._~/-]+");

    private final URI baseUrl;
    private final String allowedPrefix;

    public StaticHlsLocationResolver(StaticHlsProperties properties) {
        this.baseUrl = normalizeBaseUrl(properties.baseUrl());
        this.allowedPrefix = normalizePrefix(properties.allowedPathPrefix());
    }

    public String manifestPath(String input) {
        var path = path(input);
        if (!path.endsWith(".m3u8")) {
            throw badRequest("The HLS manifest path must end in .m3u8");
        }
        return path;
    }

    public String captionPath(String input) {
        var path = path(input);
        if (!path.endsWith(".vtt")) {
            throw badRequest("Caption paths must end in .vtt");
        }
        return path;
    }

    public String childPath(String parentPath, String childReference) {
        if (childReference == null || childReference.isBlank() || childReference.startsWith("/")) {
            throw badRequest("The HLS manifest contains an invalid child path");
        }
        var slash = parentPath.lastIndexOf('/');
        return path((slash < 0 ? "" : parentPath.substring(0, slash + 1)) + childReference.trim());
    }

    public URI resolve(String relativePath) {
        var path = path(relativePath);
        var resolved = baseUrl.resolve(path);
        if (!sameOrigin(baseUrl, resolved)) {
            throw badRequest("The media path must stay on the configured media host");
        }
        return resolved;
    }

    private String path(String input) {
        if (input == null) throw badRequest("The media path is required");
        var path = input.trim();
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/") || path.contains("//") ||
            path.contains("..") || path.contains("\\") || path.contains(":") || path.contains("?") ||
            path.contains("#") || path.contains("%") || !SAFE_PATH.matcher(path).matches()) {
            throw badRequest("The media path must be a safe relative path");
        }
        if (!allowedPrefix.isEmpty() && !path.startsWith(allowedPrefix)) {
            throw badRequest("The media path is outside the configured prefix");
        }
        return path;
    }

    private URI normalizeBaseUrl(URI input) {
        var scheme = input.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) ||
            input.getHost() == null || input.getUserInfo() != null || input.getQuery() != null || input.getFragment() != null) {
            throw new IllegalStateException("devs.static-hls.base-url must be an HTTP(S) origin URL");
        }
        var value = input.toString();
        return URI.create(value.endsWith("/") ? value : value + "/");
    }

    private String normalizePrefix(String input) {
        var prefix = input == null ? "" : input.trim();
        if (prefix.isEmpty()) return "";
        if (prefix.startsWith("/") || prefix.contains("..") || prefix.contains("\\") ||
            prefix.contains(":") || prefix.contains("?") || prefix.contains("#") || prefix.contains("%") ||
            !SAFE_PATH.matcher(prefix).matches()) {
            throw new IllegalStateException("devs.static-hls.allowed-path-prefix must be a safe relative path prefix");
        }
        return prefix.endsWith("/") ? prefix : prefix + "/";
    }

    private boolean sameOrigin(URI left, URI right) {
        return left.getScheme().equalsIgnoreCase(right.getScheme()) &&
            left.getHost().equalsIgnoreCase(right.getHost()) &&
            effectivePort(left) == effectivePort(right);
    }

    private int effectivePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    private ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
