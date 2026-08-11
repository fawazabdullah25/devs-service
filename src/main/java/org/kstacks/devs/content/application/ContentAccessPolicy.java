package org.kstacks.devs.content.application;

import org.kstacks.devs.config.SecurityProperties;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

@Component
public class ContentAccessPolicy {
    private final SecurityProperties properties;

    public ContentAccessPolicy(SecurityProperties properties) {
        this.properties = properties;
    }

    public void assertCanView(LearningContentEntity content, Authentication authentication) {
        if (content.getVisibility() == ContentVisibility.PUBLIC) return;
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            throw new AccessDeniedException("An account is required for this content");
        }
        if (content.getVisibility() == ContentVisibility.AUTHENTICATED) return;
        var isStudent = authentication.getAuthorities().stream()
            .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
            .anyMatch(properties.studentRoles()::contains);
        if (!isStudent) throw new AccessDeniedException("A student account is required for this content");
    }
}
