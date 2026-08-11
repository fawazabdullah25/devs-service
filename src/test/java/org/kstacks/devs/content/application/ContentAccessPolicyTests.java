package org.kstacks.devs.content.application;

import org.junit.jupiter.api.Test;
import org.kstacks.devs.config.SecurityProperties;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAccessPolicyTests {
    private final ContentAccessPolicy policy = new ContentAccessPolicy(
        new SecurityProperties(false, false, "", "", Set.of("DEVS_ADMIN"), Set.of("STUDENT"), Set.of())
    );

    @Test
    void publicContentNeedsNoAccount() {
        var content = content(ContentVisibility.PUBLIC);
        assertThatCode(() -> policy.assertCanView(content, null)).doesNotThrowAnyException();
    }

    @Test
    void authenticatedContentRejectsAnonymousUsers() {
        var content = content(ContentVisibility.AUTHENTICATED);
        assertThatThrownBy(() -> policy.assertCanView(content, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void studentContentRequiresStudentRole() {
        var content = content(ContentVisibility.STUDENT_ONLY);
        var regular = new TestingAuthenticationToken("user", "secret", "ROLE_MEMBER");
        var student = new TestingAuthenticationToken("student", "secret", "ROLE_STUDENT");
        assertThatThrownBy(() -> policy.assertCanView(content, regular)).isInstanceOf(AccessDeniedException.class);
        assertThatCode(() -> policy.assertCanView(content, student)).doesNotThrowAnyException();
    }

    private LearningContentEntity content(ContentVisibility visibility) {
        return LearningContentEntity.draft("access-test", ContentKind.COURSE, visibility, "Access test", "Summary");
    }
}
