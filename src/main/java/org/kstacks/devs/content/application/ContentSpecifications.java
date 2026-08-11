package org.kstacks.devs.content.application;

import jakarta.persistence.criteria.JoinType;
import org.kstacks.devs.content.domain.ContentKind;
import org.kstacks.devs.content.domain.ContentVisibility;
import org.kstacks.devs.content.domain.LearningContentEntity;
import org.kstacks.devs.content.domain.PublicationStatus;
import org.kstacks.devs.content.domain.SpokenLanguage;
import org.springframework.data.jpa.domain.Specification;

import java.util.Locale;

public final class ContentSpecifications {
    private ContentSpecifications() {}

    public static Specification<LearningContentEntity> published() {
        return (root, query, builder) -> builder.equal(root.get("status"), PublicationStatus.PUBLISHED);
    }

    public static Specification<LearningContentEntity> publicVisibility() {
        return (root, query, builder) -> builder.equal(root.get("visibility"), ContentVisibility.PUBLIC);
    }

    public static Specification<LearningContentEntity> query(String value) {
        if (value == null || value.isBlank()) return Specification.unrestricted();
        var needle = "%" + value.trim().toLowerCase(Locale.ROOT) + "%";
        return (root, query, builder) -> builder.or(
            builder.like(builder.lower(root.get("titleEn")), needle),
            builder.like(builder.lower(root.get("titleAr")), needle),
            builder.like(builder.lower(root.get("summaryEn")), needle),
            builder.like(builder.lower(root.get("summaryAr")), needle)
        );
    }

    public static Specification<LearningContentEntity> kind(ContentKind value) {
        return value == null ? Specification.unrestricted() : (root, query, builder) -> builder.equal(root.get("kind"), value);
    }

    public static Specification<LearningContentEntity> level(String value) {
        return value == null || value.isBlank() ? Specification.unrestricted() : (root, query, builder) -> builder.equal(root.get("levelSlug"), value);
    }

    public static Specification<LearningContentEntity> language(SpokenLanguage value) {
        return value == null ? Specification.unrestricted() : (root, query, builder) -> builder.equal(root.get("spokenLanguage"), value);
    }

    public static Specification<LearningContentEntity> topic(String value) {
        if (value == null || value.isBlank()) return Specification.unrestricted();
        return (root, query, builder) -> {
            query.distinct(true);
            return builder.equal(root.joinSet("topicSlugs", JoinType.INNER), value);
        };
    }
}
